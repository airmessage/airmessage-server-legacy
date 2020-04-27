package me.tagavari.airmessageserver.connection;

import io.sentry.Sentry;
import io.sentry.event.BreadcrumbBuilder;
import me.tagavari.airmessageserver.common.Blocks;
import me.tagavari.airmessageserver.server.*;
import org.jooq.impl.DSL;
import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessagePackException;
import org.msgpack.core.MessageUnpacker;

import java.io.*;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

public class CommunicationsManager implements DataProxyListener<ClientRegistration> {
	//Creating the communications values
	protected final DataProxy<ClientRegistration> dataProxy;
	
	//Creating the state values
	private final Timer keepAliveTimer = new Timer();
	
	public CommunicationsManager(DataProxy<ClientRegistration> dataProxy) {
		this.dataProxy = dataProxy;
	}
	
	public DataProxy<ClientRegistration> getDataProxy() {
		return dataProxy;
	}
	
	private final AtomicBoolean isRunning = new AtomicBoolean(false);
	
	public void start() {
		//Returning if the server is already running
		if(isRunning.get()) return;
		
		//Setting the server as running
		isRunning.set(true);
		
		//Registering a listener
		dataProxy.addMessageListener(this);
		
		//Starting the proxy
		dataProxy.startServer();
	}
	
	public void stop() {
		//Returning if the server isn't running
		if(!isRunning.get()) return;
		
		//Disconnecting the proxy
		dataProxy.stopServer();
		
		//Unregistering a listener
		dataProxy.removeMessageListener(this);
	}
	
	public boolean isRunning() {
		return isRunning.get();
	}
	
	@Override
	public void onStart() {
		//Updating the state
		UIHelper.getDisplay().asyncExec(() -> {
			Main.setServerState(Main.serverStateRunning);
			SystemTrayManager.updateStatusMessage();
		});
		
		//Starting the keepalive timer
		keepAliveTimer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				//Sending a ping to all connected clients
				boolean result = sendMessageHeaderOnly(null, CommConst.nhtPing, false);
				
				//Starting ping response timers
				if(result) {
					for(ClientRegistration connection : dataProxy.getConnections()) {
						connection.startPingExpiryTimer(CommConst.pingTimeout, () -> initiateClose(connection));
					}
				}
			}
		}, CommConst.keepAliveMillis, CommConst.keepAliveMillis);
		
		Main.getLogger().info("Server started");
	}
	
	@Override
	public void onStop(int code) {
		//Updating the state
		int serverState = ConnectionManager.proxyErrorToServerState(code);
		UIHelper.getDisplay().asyncExec(() -> {
			Main.setServerState(serverState);
			SystemTrayManager.updateStatusMessage();
		});
		
		//Setting the server as not running
		isRunning.set(false);
		
		//Cancelling the keepalive timer
		keepAliveTimer.cancel();
		
		Main.getLogger().info("Server stopped");
	}
	
	@Override
	public void onOpen(ClientRegistration client) {
		//Generating the transmission check
		byte[] transmissionCheck = new byte[CommConst.transmissionCheckLength];
		Main.getSecureRandom().nextBytes(transmissionCheck);
		client.setTransmissionCheck(transmissionCheck);
		
		//Sending this server's information
		try(MessageBufferPacker packer = MessagePack.newDefaultBufferPacker()) {
			packer.packInt(CommConst.nhtInformation);
			
			packer.packInt(CommConst.mmCommunicationsVersion);
			packer.packInt(CommConst.mmCommunicationsSubVersion);
			packer.packBinaryHeader(transmissionCheck.length);
			packer.addPayload(transmissionCheck);
			
			dataProxy.sendMessage(client, packer.toByteArray(), false);
		} catch(IOException exception) {
			exception.printStackTrace();
		}
		
		//Starting the handshake expiry timer
		client.startHandshakeExpiryTimer(CommConst.handshakeTimeout, () -> initiateClose(client));
		
		//Updating the UI
		UIHelper.getDisplay().asyncExec(SystemTrayManager::updateConnectionsMessage);
	}
	
	@Override
	public void onClose(ClientRegistration client) {
		//Updating the UI
		UIHelper.getDisplay().asyncExec(SystemTrayManager::updateConnectionsMessage);
	}
	
	@Override
	public void onMessage(ClientRegistration client, byte[] data, boolean wasEncrypted) {
		//Resetting the ping timer
		client.cancelPingExpiryTimer();
		
		//Wrapping the data in a MessagePack unpacker
		MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(data);
		try {
			//Reading the message type
			int messageType = unpacker.unpackInt();
			
			//Logging the event
			{
				//Adding a breadcrumb
				int contentLength = data.length;
				
				Map<String, String> dataMap = new HashMap<>(2);
				dataMap.put("Message type", Integer.toString(messageType));
				dataMap.put("Content length", Integer.toString(contentLength));
				Sentry.getContext().recordBreadcrumb(new BreadcrumbBuilder().setCategory(Constants.sentryBCatPacket).setMessage("New packet received").setData(dataMap).build());
				
				Main.getLogger().log(Level.FINEST, "New message received: " + messageType + " / " + contentLength);
			}
			
			/*
			 * Standard requests
			 * If a request that contains sensitive data wasn't encrypted, don't accept it
			 */
			if(!wasEncrypted) {
				//Responding to standard requests
				switch(messageType) {
					case CommConst.nhtClose -> dataProxy.disconnectClient(client);
					case CommConst.nhtPing -> sendMessageHeaderOnly(client, CommConst.nhtPong, false);
					case CommConst.nhtAuthentication -> handleMessageAuthentication(client, unpacker);
				}
			} else {
				//The client can't perform any sensitive tasks unless they are authenticated
				if(client.isClientRegistered()) {
					switch(messageType) {
						case CommConst.nhtTimeRetrieval -> handleMessageTimeRetrieval(client, unpacker);
						case CommConst.nhtMassRetrieval -> handleMessageMassRetrieval(client, unpacker);
						case CommConst.nhtConversationUpdate -> handleMessageConversationUpdate(client, unpacker);
						case CommConst.nhtAttachmentReq -> handleMessageAttachmentRequest(client, unpacker);
						case CommConst.nhtCreateChat -> handleMessageCreateChat(client, unpacker);
						case CommConst.nhtSendTextExisting -> handleMessageSendTextExisting(client, unpacker);
						case CommConst.nhtSendTextNew -> handleMessageSendTextNew(client, unpacker);
						case CommConst.nhtSendFileExisting -> handleMessageSendFileExisting(client, unpacker);
						case CommConst.nhtSendFileNew -> handleMessageSendFileNew(client, unpacker);
					}
				}
			}
		} catch(IOException | MessagePackException exception) {
			Main.getLogger().log(Level.WARNING, exception.getMessage(), exception);
		} finally {
			try {
				unpacker.close();
			} catch(IOException exception) {
				Main.getLogger().log(Level.WARNING, exception.getMessage(), exception);
			}
		}
	}
	
	private void handleMessageAuthentication(ClientRegistration client, MessageUnpacker unpacker) throws IOException {
		//Stopping the registration timer
		client.cancelHandshakeExpiryTimer();
		
		byte[] transmissionCheck;
		String installationID;
		String clientName, platformID;
		try {
			//Decrypting the message
			byte[] secureData = unpacker.readPayload(unpacker.unpackBinaryHeader());
			byte[] data = EncryptionHelper.decrypt(secureData);
			
			MessageUnpacker secureUnpacker = MessagePack.newDefaultUnpacker(data);
			try {
				//Reading the data
				transmissionCheck = secureUnpacker.readPayload(CommConst.transmissionCheckLength);
				installationID = secureUnpacker.unpackString();
				clientName = secureUnpacker.unpackString();
				platformID = secureUnpacker.unpackString();
			} finally {
				secureUnpacker.close();
			}
		} catch(GeneralSecurityException exception) {
			//Logging the exception
			Main.getLogger().log(Level.INFO, exception.getMessage(), exception);
			
			//Sending a message and closing the connection
			try(MessageBufferPacker packer = MessagePack.newDefaultBufferPacker()) {
				packer.packInt(CommConst.nhtAuthentication);
				packer.packInt(CommConst.nstAuthenticationUnauthorized);
				dataProxy.sendMessage(client, packer.toByteArray(), false, () -> initiateClose(client));
			}
			
			return;
		} catch(IOException | MessagePackException exception) {
			//Logging the exception
			Main.getLogger().log(Level.INFO, exception.getMessage(), exception);
			
			//Sending a message and closing the connection
			try(MessageBufferPacker packer = MessagePack.newDefaultBufferPacker()) {
				packer.packInt(CommConst.nhtAuthentication);
				packer.packInt(CommConst.nstAuthenticationBadRequest);
				dataProxy.sendMessage(client, packer.toByteArray(), false, () -> initiateClose(client));
			}
			
			return;
		}
		
		//Validating the transmission
		if(client.checkClearTransmissionCheck(transmissionCheck)) {
			//Disconnecting clients with the same installation ID
			Collection<ClientRegistration> connections = new HashSet<>(dataProxy.getConnections()); //Avoid concurrent modifications
			for(ClientRegistration connectedClient : connections) {
				if(installationID.equals(connectedClient.getInstallationID())) {
					initiateClose(client);
				}
			}
			
			//Marking the client as registered
			client.setClientRegistered(true);
			client.setRegistration(installationID, clientName, platformID);
			
			//Sending a message
			try(MessageBufferPacker packer = MessagePack.newDefaultBufferPacker()) {
				packer.packInt(CommConst.nhtAuthentication);
				packer.packInt(CommConst.nstAuthenticationOK);
				packer.packString(PreferencesManager.getInstallationID()); //Installation ID
				packer.packString(Main.getDeviceName()); //Device name
				packer.packString(System.getProperty("os.version")); //System version
				packer.packString(Constants.SERVER_VERSION); //Software version
				
				dataProxy.sendMessage(client, packer.toByteArray(), true);
			}
		} else {
			//Sending a message and closing the connection
			try(MessageBufferPacker packer = MessagePack.newDefaultBufferPacker()) {
				packer.packInt(CommConst.nhtAuthentication);
				packer.packInt(CommConst.nstAuthenticationUnauthorized);
				dataProxy.sendMessage(client, packer.toByteArray(), false, () -> initiateClose(client));
			}
		}
	}
	
	private void handleMessageTimeRetrieval(ClientRegistration client, MessageUnpacker unpacker) throws IOException {
		//Reading the request data
		long timeLower = unpacker.unpackLong();
		long timeUpper = unpacker.unpackLong();
		
		//Creating a new request and queuing it
		DatabaseManager.getInstance().addClientRequest(new DatabaseManager.CustomRetrievalRequest(
				client,
				() -> DSL.field("message.date").greaterThan(Main.getTimeHelper().toDatabaseTime(timeLower)).and(DSL.field("message.date").lessThan(Main.getTimeHelper().toDatabaseTime(timeUpper))),
				CommConst.nhtTimeRetrieval));
	}
	
	private void handleMessageMassRetrieval(ClientRegistration client, MessageUnpacker unpacker) throws IOException {
		//Reading the request data
		short requestID = unpacker.unpackShort(); //The request ID to avoid collisions
		
		boolean restrictMessages = unpacker.unpackBoolean(); //Should we filter messages by date?
		long timeSinceMessages = restrictMessages ? unpacker.unpackLong() : -1; //If so, download messages since when?
		
		boolean downloadAttachments = unpacker.unpackBoolean(); //Should we download attachments
		boolean restrictAttachmentsDate = false; //Should we filter attachments by date?
		long timeSinceAttachments = -1; //If so, download attachments since when?
		boolean restrictAttachmentsSize = false; //Should we filter attachments by size?
		long attachmentsSizeLimit = -1; //If so, download attachments smaller than how many bytes?
		
		String[] attachmentFilterWhitelist = null; //Only download attachment files if they're on this list
		String[] attachmentFilterBlacklist = null; //Don't download attachment files if they're on this list
		boolean attachmentFilterDLOther = false; //Download attachment files if they're not on either list
		
		if(downloadAttachments) {
			restrictAttachmentsDate = unpacker.unpackBoolean();
			if(restrictAttachmentsDate) timeSinceAttachments = unpacker.unpackLong();
			
			restrictAttachmentsSize = unpacker.unpackBoolean();
			if(restrictAttachmentsSize) attachmentsSizeLimit = unpacker.unpackLong();
			
			attachmentFilterWhitelist = new String[unpacker.unpackArrayHeader()];
			for(int i = 0; i < attachmentFilterWhitelist.length; i++) attachmentFilterWhitelist[i] = unpacker.unpackString();
			attachmentFilterBlacklist = new String[unpacker.unpackArrayHeader()];
			for(int i = 0; i < attachmentFilterBlacklist.length; i++) attachmentFilterBlacklist[i] = unpacker.unpackString();
			attachmentFilterDLOther = unpacker.unpackBoolean();
		}
		
		//Creating a new request and queuing it
		DatabaseManager.getInstance().addClientRequest(new DatabaseManager.MassRetrievalRequest(client, requestID, restrictMessages, timeSinceMessages, downloadAttachments, restrictAttachmentsDate, timeSinceAttachments, restrictAttachmentsSize, attachmentsSizeLimit, attachmentFilterWhitelist, attachmentFilterBlacklist, attachmentFilterDLOther));
	}
	
	private void handleMessageConversationUpdate(ClientRegistration client, MessageUnpacker unpacker) throws IOException {
		//Reading the chat GUID list
		String[] chatGUIDs = new String[unpacker.unpackArrayHeader()];
		for(int i = 0; i < chatGUIDs.length; i++) chatGUIDs[i] = unpacker.unpackString();
		
		//Creating a new request and queuing it
		DatabaseManager.getInstance().addClientRequest(new DatabaseManager.ConversationInfoRequest(client, chatGUIDs));
	}
	
	private void handleMessageAttachmentRequest(ClientRegistration client, MessageUnpacker unpacker) throws IOException {
		//Reading the request information
		short requestID = unpacker.unpackShort(); //The request ID to avoid collisions
		int chunkSize = unpacker.unpackInt(); //How many bytes to upload per packet
		String fileGUID = unpacker.unpackString(); //The GUID of the file to download
		
		//Sending a reply
		try(MessageBufferPacker packer = MessagePack.newDefaultBufferPacker()) {
			packer.packInt(CommConst.nhtAttachmentReqConfirm);
			packer.packShort(requestID);
			
			dataProxy.sendMessage(client, packer.toByteArray(), true);
		}
		
		//Adding the request
		DatabaseManager.getInstance().addClientRequest(new DatabaseManager.FileRequest(client, fileGUID, requestID, chunkSize));
	}
	
	private void handleMessageCreateChat(ClientRegistration client, MessageUnpacker unpacker) throws IOException {
		//Reading the request information
		short requestID = unpacker.unpackShort(); //The request ID to avoid collisions
		String[] chatMembers = new String[unpacker.unpackArrayHeader()]; //The members of this conversation
		for(int i = 0; i < chatMembers.length; i++) chatMembers[i] = unpacker.unpackString();
		String service = unpacker.unpackString(); //The service of this conversation
		
		//Creating the chat
		Constants.Tuple<Integer, String> result = AppleScriptManager.createChat(chatMembers, service);
		
		//Sending a response
		sendMessageRequestResponse(client, CommConst.nhtCreateChat, requestID, result.item1, result.item2);
	}
	
	private void handleMessageSendTextExisting(ClientRegistration client, MessageUnpacker unpacker) throws IOException {
		//Reading the request information
		short requestID = unpacker.unpackShort(); //The request ID to avoid collisions
		String chatGUID = unpacker.unpackString(); //The GUID of the chat to send a message to
		String message = unpacker.unpackString(); //The message to send
		
		//Sending the message
		Constants.Tuple<Integer, String> result = AppleScriptManager.sendExistingMessage(chatGUID, message);
		
		//Sending the response
		sendMessageRequestResponse(client, CommConst.nhtSendResult, requestID, result.item1, result.item2);
	}
	
	private void handleMessageSendTextNew(ClientRegistration client, MessageUnpacker unpacker) throws IOException {
		//Reading the request information
		short requestID = unpacker.unpackShort(); //The request ID to avoid collisions
		String[] members = new String[unpacker.unpackArrayHeader()]; //The members of the chat to send the message to
		for(int i = 0; i < members.length; i++) members[i] = unpacker.unpackString();
		String service = unpacker.unpackString(); //The service of the chat
		String message = unpacker.unpackString(); //The message to send
		
		//Sending the message
		Constants.Tuple<Integer, String> result = AppleScriptManager.sendNewMessage(members, message, service);
		
		//Sending the response
		sendMessageRequestResponse(client, CommConst.nhtSendResult, requestID, result.item1, result.item2);
	}
	
	private void handleMessageSendFileExisting(ClientRegistration client, MessageUnpacker unpacker) throws IOException {
		//Reading the request information
		short requestID = unpacker.unpackShort(); //The request ID to avoid collisions
		int requestIndex = unpacker.unpackInt(); //The index of this request, to ensure that packets are received and written in order
		boolean isLast = unpacker.unpackBoolean(); //Is this the last packet?
		String chatGUID = unpacker.unpackString(); //The GUID of the chat to send the message to
		byte[] compressedBytes = unpacker.readPayload(unpacker.unpackBinaryHeader()); //The file bytes to append
		String fileName = requestIndex == 0 ? unpacker.unpackString() : null; //The name of the file to send
		
		//Forwarding the data
		AppleScriptManager.addFileFragment(client, requestID, chatGUID, fileName, requestIndex, compressedBytes, isLast);
	}
	
	private void handleMessageSendFileNew(ClientRegistration client, MessageUnpacker unpacker) throws IOException {
		//Reading the request information
		short requestID = unpacker.unpackShort(); //The request ID to avoid collisions
		int requestIndex = unpacker.unpackInt(); //The index of this request, to ensure that packets are received and written in order
		boolean isLast = unpacker.unpackBoolean(); //Is this the last packet?
		String[] members = new String[unpacker.unpackArrayHeader()]; //The members of the chat to send the message to
		for(int i = 0; i < members.length; i++) members[i] = unpacker.unpackString();
		byte[] compressedBytes = unpacker.readPayload(unpacker.unpackBinaryHeader()); //The file bytes to append
		String fileName = null; //The name of the file to send
		String service = null; //The service of the conversation
		if(requestIndex == 0) {
			fileName = unpacker.unpackString();
			service = unpacker.unpackString();
		}
		
		//Forwarding the data
		AppleScriptManager.addFileFragment(client, requestID, members, service, fileName, requestIndex, compressedBytes, isLast);
	}
	
	public void initiateClose(ClientRegistration client) {
		try(MessageBufferPacker packer = MessagePack.newDefaultBufferPacker()) {
			packer.packInt(CommConst.nhtClose);
			
			dataProxy.sendMessage(client, packer.toByteArray(), false, () -> dataProxy.disconnectClient(client));
		} catch(IOException exception) {
			Main.getLogger().log(Level.WARNING, exception.getMessage(), exception);
			
			//Disconnecting the client anyways
			dataProxy.disconnectClient(client);
		}
	}
	
	//Helper function for sending responses to basic requests with a request ID, result code, and result description (either error message or result details)
	public boolean sendMessageRequestResponse(ClientRegistration client, int header, short requestID, int resultCode, String details) {
		//Returning if the connection is not open
		if(!client.isConnected()) return false;
		
		//Sending a reply
		try(MessageBufferPacker packer = MessagePack.newDefaultBufferPacker()) {
			packer.packInt(header);
			packer.packShort(requestID);
			packer.packInt(resultCode); //Result code
			if(details == null) packer.packNil();
			else packer.packString(details);
			
			dataProxy.sendMessage(client, packer.toByteArray(), true);
			
			return true;
		} catch(IOException exception) {
			Main.getLogger().log(Level.SEVERE, exception.getMessage(), exception);
			Sentry.capture(exception);
			
			return false;
		}
	}
	
	//Helper function for sending a packet with only a header and an empty body
	public boolean sendMessageHeaderOnly(ClientRegistration client, int header, boolean encrypt) {
		try(MessageBufferPacker packer = MessagePack.newDefaultBufferPacker()) {
			packer.packInt(header);
			
			dataProxy.sendMessage(client, packer.toByteArray(), encrypt);
			
			return true;
		} catch(IOException exception) {
			Main.getLogger().log(Level.SEVERE, exception.getMessage(), exception);
			Sentry.capture(exception);
			
			return false;
		}
	}
	
	public boolean sendMessageUpdate(Collection<Blocks.ConversationItem> items) {
		try(MessageBufferPacker packer = MessagePack.newDefaultBufferPacker()) {
			packer.packInt(CommConst.nhtMessageUpdate);
			
			packer.packArrayHeader(items.size());
			for(Blocks.Block item : items) item.writeObject(packer);
			
			dataProxy.sendMessage(null, packer.toByteArray(), true);
			
			return true;
		} catch(IOException exception) {
			Main.getLogger().log(Level.SEVERE, exception.getMessage(), exception);
			Sentry.capture(exception);
			
			return false;
		}
	}
	
	public boolean sendMessageUpdate(ClientRegistration client, int header, Collection<Blocks.ConversationItem> items) {
		try(MessageBufferPacker packer = MessagePack.newDefaultBufferPacker()) {
			packer.packInt(header);
			
			packer.packArrayHeader(items.size());
			for(Blocks.Block item : items) item.writeObject(packer);
			
			dataProxy.sendMessage(client, packer.toByteArray(), true);
			
			return true;
		} catch(IOException exception) {
			Main.getLogger().log(Level.SEVERE, exception.getMessage(), exception);
			Sentry.capture(exception);
			
			return false;
		}
	}
	
	public boolean sendConversationInfo(ClientRegistration client, Collection<Blocks.ConversationInfo> items) {
		try(MessageBufferPacker packer = MessagePack.newDefaultBufferPacker()) {
			packer.packInt(CommConst.nhtConversationUpdate);
			
			packer.packArrayHeader(items.size());
			for(Blocks.Block item : items) item.writeObject(packer);
			
			dataProxy.sendMessage(client, packer.toByteArray(), true);
			
			return true;
		} catch(IOException exception) {
			Main.getLogger().log(Level.SEVERE, exception.getMessage(), exception);
			Sentry.capture(exception);
			
			return false;
		}
	}
	
	public boolean sendFileChunk(ClientRegistration client, short requestID, int requestIndex, long fileLength, boolean isLast, String fileGUID, byte[] chunkData) {
		try(MessageBufferPacker packer = MessagePack.newDefaultBufferPacker()) {
			packer.packInt(CommConst.nhtAttachmentReq);
			
			packer.packShort(requestID);
			packer.packInt(requestIndex);
			if(requestIndex == 0) packer.packLong(fileLength);
			packer.packBoolean(isLast);
			
			packer.packString(fileGUID);
			packer.packBinaryHeader(chunkData.length);
			packer.addPayload(chunkData);
			
			dataProxy.sendMessage(client, packer.toByteArray(), true);
			
			return true;
		} catch(IOException exception) {
			Main.getLogger().log(Level.SEVERE, exception.getMessage(), exception);
			Sentry.capture(exception);
			
			return false;
		}
	}
	
	public boolean sendMassRetrievalInitial(ClientRegistration client, short requestID, Collection<Blocks.ConversationInfo> conversations, int messageCount) {
		try(MessageBufferPacker packer = MessagePack.newDefaultBufferPacker()) {
			packer.packInt(CommConst.nhtMassRetrieval);
			
			packer.packShort(requestID);
			packer.packInt(0); //Request index
			
			packer.packArrayHeader(conversations.size());
			for(Blocks.Block item : conversations) item.writeObject(packer);
			
			packer.packInt(messageCount);
			
			dataProxy.sendMessage(client, packer.toByteArray(), true);
			
			return true;
		} catch(IOException exception) {
			Main.getLogger().log(Level.SEVERE, exception.getMessage(), exception);
			Sentry.capture(exception);
			
			return false;
		}
	}
	
	public boolean sendMassRetrievalMessages(ClientRegistration client, short requestID, int packetIndex, Collection<Blocks.ConversationItem> conversationItems) {
		try(MessageBufferPacker packer = MessagePack.newDefaultBufferPacker()) {
			packer.packInt(CommConst.nhtMassRetrieval);
			
			packer.packShort(requestID);
			packer.packInt(packetIndex);
			
			packer.packArrayHeader(conversationItems.size());
			for(Blocks.Block item : conversationItems) item.writeObject(packer);
			
			dataProxy.sendMessage(client, packer.toByteArray(), true);
			
			return true;
		} catch(IOException exception) {
			Main.getLogger().log(Level.SEVERE, exception.getMessage(), exception);
			Sentry.capture(exception);
			
			return false;
		}
	}
	
	public boolean sendMassRetrievalFileChunk(ClientRegistration client, short requestID, int requestIndex, String fileName, boolean isLast, String fileGUID, byte[] chunkData) {
		try(MessageBufferPacker packer = MessagePack.newDefaultBufferPacker()) {
			packer.packInt(CommConst.nhtMassRetrievalFile);
			
			packer.packShort(requestID);
			packer.packInt(requestIndex);
			if(requestIndex == 0) packer.packString(fileName);
			packer.packBoolean(isLast);
			
			packer.packString(fileGUID);
			packer.packBinaryHeader(chunkData.length);
			packer.addPayload(chunkData);
			
			dataProxy.sendMessage(client, packer.toByteArray(), true);
			
			return true;
		} catch(IOException exception) {
			Main.getLogger().log(Level.SEVERE, exception.getMessage(), exception);
			Sentry.capture(exception);
			
			return false;
		}
	}
	
	public boolean sendModifierUpdate(Collection<Blocks.ModifierInfo> items) {
		try(MessageBufferPacker packer = MessagePack.newDefaultBufferPacker()) {
			packer.packInt(CommConst.nhtModifierUpdate);
			
			packer.packArrayHeader(items.size());
			for(Blocks.Block item : items) item.writeObject(packer);
			
			dataProxy.sendMessage(null, packer.toByteArray(), true);
			
			return true;
		} catch(IOException exception) {
			Main.getLogger().log(Level.SEVERE, exception.getMessage(), exception);
			Sentry.capture(exception);
			
			return false;
		}
	}
}