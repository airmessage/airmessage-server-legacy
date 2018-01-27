package me.tagavari.airmessage.server;

import me.tagavari.airmessage.common.SharedValues;
import org.java_websocket.WebSocket;
import org.java_websocket.drafts.Draft;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.exceptions.InvalidDataException;
import org.java_websocket.exceptions.InvalidHandshakeException;
import org.java_websocket.extensions.IExtension;
import org.java_websocket.framing.Framedata;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.HandshakeBuilder;
import org.java_websocket.handshake.ServerHandshakeBuilder;
import org.java_websocket.server.DefaultSSLWebSocketServerFactory;
import org.java_websocket.server.WebSocketServer;
import org.jooq.Condition;
import org.jooq.impl.DSL;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class WSServerManager extends WebSocketServer {
	//Server instance
	private static WSServerManager serverManager = null;
	
	//Creating the instance values
	//private final BidiMap<String, WebSocket> clientList = new DualHashBidiMap<>();
	
	static void startServer() {
		//Returning if an instance already exists
		if(serverManager != null) return;
		//WebSocketImpl.DEBUG = true;
		
		serverManager = new WSServerManager(1359, new ArrayList<>(Arrays.asList(new DraftMMS())));
		//serverManager = new WSServerManager(1359);
		serverManager.setWebSocketFactory(new DefaultSSLWebSocketServerFactory(SecurityManager.createSSLContext()));
		serverManager.setConnectionLostTimeout(20 * 60); //20 minutes
		
		//Starting the server
		serverManager.start();
	}
	
	static void stopServer() {
		try {
			//Stopping the server
			serverManager.stop();
		} catch(IOException | InterruptedException exception) {
			//Logging the exception
			exception.printStackTrace();
		}
	}
	
	private WSServerManager(int port) {
		super(new InetSocketAddress(port));
	}
	
	private WSServerManager(int port, List<Draft> drafts) {
		super(new InetSocketAddress(port), drafts);
	}
	
	static void publishMessage(byte[] message) {
		serverManager.sendToAll(message);
	}
	
	private void sendToAll(byte[] message) {
		//Sending the message to all connections
		synchronized(connections()) {
			for(WebSocket connection : connections()) {
				connection.send(message);
			}
		}
	}
	
	static void sendMessageResponse(WebSocket connection, short requestID, boolean result) {
		//Returning if the connection is not open
		if(!connection.isOpen()) return;
		
		//Preparing to serialize
		try(ByteArrayOutputStream bos = new ByteArrayOutputStream();
			ObjectOutputStream out = new ObjectOutputStream(bos)) {
			out.writeByte(SharedValues.wsFrameSendResult); //Message type - Send request result
			out.writeShort(requestID); //Request ID
			out.writeBoolean(result); //Result
			out.flush();
			
			//Sending the data
			connection.send(bos.toByteArray());
		} catch(IOException exception) {
			//Printing the stack trace
			exception.printStackTrace();
		}
	}
	
	@Override
	public void onOpen(WebSocket connection, ClientHandshake handshake) {
		//Logging the client's connection
		if(connection.getRemoteSocketAddress() != null) Main.getLogger().info("Client connected with IP address " + connection.getRemoteSocketAddress().getAddress().getHostAddress());
	}
	
	@Override
	public void onClose(WebSocket connection, int code, String reason, boolean remote) {
		if(connection.getRemoteSocketAddress() == null) Main.getLogger().info("Client disconnected");
		else Main.getLogger().info("Client disconnected with IP address " + connection.getRemoteSocketAddress().getAddress().getHostAddress());
	}
	
	@Override
	public void onMessage(WebSocket connection, String message) {
		//Returning the message
		connection.send(message);
	}
	
	@Override
	public void onMessage(WebSocket connection, ByteBuffer bytes) {
		//Processing the message
		byte[] array = new byte[bytes.remaining()];
		bytes.get(array);
		
		try(ByteArrayInputStream bis = new ByteArrayInputStream(array); ObjectInputStream in = new ObjectInputStream(bis)) {
			//Reading the message type
			byte messageType = in.readByte();
			
			switch(messageType) {
				case SharedValues.wsFrameTimeRetrieval: { //Time-based retrieval request
					//Reading the data
					final long timeLower = in.readLong();
					final long timeUpper = in.readLong();
					
					//Creating a new request and queuing it
					synchronized(DatabaseManager.databaseRequests) {
						DatabaseManager.databaseRequests.add(new DatabaseManager.CustomRetrievalRequest(connection, new DatabaseManager.RetrievalFilter() {
							@Override
							Condition filter() {
								return DSL.field("message.date").greaterThan(Main.getTimeHelper().toDatabaseTime(timeLower)).and(DSL.field("message.date").lessThan(Main.getTimeHelper().toDatabaseTime(timeUpper)));
							}
						}, SharedValues.wsFrameTimeRetrieval));
					}
					
					break;
				}
				case SharedValues.wsFrameMassRetrieval: { //Mass retrieval request
					//Creating a new request and queuing it
					synchronized(DatabaseManager.databaseRequests) {
						DatabaseManager.databaseRequests.add(new DatabaseManager.MassRetrievalRequest(connection));
					}
					
					break;
				}
				case SharedValues.wsFrameChatInfo: { //Chat info request
					//Getting the chat GUID list
					ArrayList<String> list = (ArrayList<String>) in.readObject();
					
					//Creating a new request and queuing it
					synchronized(DatabaseManager.databaseRequests) {
						DatabaseManager.databaseRequests.add(new DatabaseManager.ConversationInfoRequest(connection, list));
					}
					
					break;
				}
				case SharedValues.wsFrameAttachmentReq: { //Attachment data request
					//Getting the request information
					short requestID = in.readShort();
					String fileGUID = in.readUTF();
					int chunkSize = in.readInt();
					
					//Preparing to serialize
					try(ByteArrayOutputStream bos = new ByteArrayOutputStream();
						ObjectOutputStream out = new ObjectOutputStream(bos)) {
						out.writeByte(SharedValues.wsFrameAttachmentReqConfirmed); //Message type - attachment request processed
						out.writeShort(requestID); //Request ID
						out.writeUTF(fileGUID); //File GUID
						out.flush();
						
						//Sending the data
						connection.send(bos.toByteArray());
					}
					
					//Creating a new request and queuing it
					synchronized(DatabaseManager.databaseRequests) {
						DatabaseManager.databaseRequests.add(new DatabaseManager.FileRequest(connection, fileGUID, requestID, chunkSize));
					}
					
					break;
				}
				case SharedValues.wsFrameSendTextExisting: { //Existing text message
					//Getting the request information
					short requestID = in.readShort();
					String chatGUID = in.readUTF();
					String message = in.readUTF();
					
					//Sending the message
					boolean result = AppleScriptManager.sendExistingMessage(chatGUID, message);
					
					//Sending the response
					sendMessageResponse(connection, requestID, result);
					
					break;
				}
				case SharedValues.wsFrameSendTextNew: { //New text message
					//Getting the request information
					short requestID = in.readShort();
					String[] chatMembers = (String[]) in.readObject();
					String message = in.readUTF();
					String service = in.readUTF();
					
					//Sending the message
					boolean result = AppleScriptManager.sendNewMessage(chatMembers, message, service);
					
					//Sending the response
					sendMessageResponse(connection, requestID, result);
					
					break;
				}
				case SharedValues.wsFrameSendFileExisting: { //Existing file message
					//Getting the request information
					short requestID = in.readShort();
					int requestIndex = in.readInt();
					String chatGUID = in.readUTF();
					byte[] compressedBytes = (byte[]) in.readObject();
					String fileName = null;
					if(requestIndex == 0) fileName = in.readUTF();
					boolean isLast = in.readBoolean();
					
					//Forwarding the data
					AppleScriptManager.addFileFragment(connection, requestID, chatGUID, fileName, requestIndex, compressedBytes, isLast);
					
					break;
				}
				case SharedValues.wsFrameSendFileNew: { //New file message
					//Getting the request information
					short requestID = in.readShort();
					String[] chatMembers = (String[]) in.readObject();
					int requestIndex = in.readInt();
					byte[] compressedBytes = (byte[]) in.readObject();
					String fileName = null;
					String service = null;
					if(requestIndex == 0) {
						fileName = in.readUTF();
						service = in.readUTF();
					}
					boolean isLast = in.readBoolean();
					
					//Forwarding the data
					AppleScriptManager.addFileFragment(connection, requestID, chatMembers, service, fileName, requestIndex, compressedBytes, isLast);
					
					break;
				}
			}
		} catch(IOException | ClassNotFoundException | ClassCastException exception) {
			//Printing the stack trace
			exception.printStackTrace();
		}
	}
	
	@Override
	public void onError(WebSocket webSocket, Exception exception) {
		exception.printStackTrace();
	}
	
	@Override
	public void onStart() {}
	
	@Override
	public void onWebsocketPing(WebSocket conn, Framedata f) {
		Main.getLogger().finest("Intercepting ping");
		super.onWebsocketPing(conn, f);
	}
	
	@Override
	public void onWebsocketPong(WebSocket conn, Framedata f) {
		Main.getLogger().finest("Intercepting pong");
		super.onWebsocketPong(conn, f);
	}
	
	@Override
	public ServerHandshakeBuilder onWebsocketHandshakeReceivedAsServer(WebSocket conn, Draft draft, ClientHandshake request) throws InvalidDataException {
		//Rejecting the handshake if no value was provided
		if(!request.hasFieldValue(SharedValues.headerCommVer)) throw new InvalidDataException(SharedValues.resultBadRequest, "Communications version not provided: " + SharedValues.resultBadRequest);
		
		//Parsing the versions
		String[] versions = request.getFieldValue(SharedValues.headerCommVer).split("\\|");
		List<Integer> clientVersions = new ArrayList<>();
		for(String version : versions) if(version.matches("^\\d+$")) clientVersions.add(Integer.parseInt(version));
		
		//Rejecting the handshake if no valid values were provided
		if(clientVersions.isEmpty()) throw new InvalidDataException(SharedValues.resultBadRequest, "Communications version not provided: " + SharedValues.resultBadRequest);
		
		//Finding an applicable version
		boolean versionsApplicable = false;
		for(int version : clientVersions) if(SharedValues.mmCommunicationsVersion == version) {
			versionsApplicable = true;
			break;
		}
		
		//Rejecting the handshake if there is a communications version problem
		if(!versionsApplicable) {
			int mainClientVersion = clientVersions.get(0);
			if(SharedValues.mmCommunicationsVersion < mainClientVersion) throw new InvalidDataException(SharedValues.resultServerOutdated, "Server out of date (v" + SharedValues.mmCommunicationsVersion + " server / v" + mainClientVersion + " client): " + SharedValues.resultServerOutdated);
			if(SharedValues.mmCommunicationsVersion > mainClientVersion) throw new InvalidDataException(SharedValues.resultClientOutdated, "Client out of date (v" + SharedValues.mmCommunicationsVersion + " server / v" + mainClientVersion + " client): " + SharedValues.resultClientOutdated);
		}
		
		//Rejecting the handshake if the password couldn't be validated
		if(!SecurityManager.authenticate(request.getFieldValue(SharedValues.headerPassword))) throw new InvalidDataException(SharedValues.resultUnauthorized, "Couldn't validate credentials: " + SharedValues.resultUnauthorized);
		
		//Returning the result of the super method
		return super.onWebsocketHandshakeReceivedAsServer(conn, draft, request);
	}
	
	private static class DraftMMS extends Draft_6455 {
		DraftMMS() {
		}
		
		DraftMMS(IExtension inputExtension) {
			super(inputExtension);
		}
		
		DraftMMS(List<IExtension> inputExtensions) {
			super(inputExtensions);
		}
		
		@Override
		public HandshakeBuilder postProcessHandshakeResponseAsServer(ClientHandshake request, ServerHandshakeBuilder response) throws InvalidHandshakeException {
			//Calling the super method
			super.postProcessHandshakeResponseAsServer(request, response);
			
			//Adding the server information
			response.put(SharedValues.headerSoftVersion, Constants.SERVER_VERSION);
			response.put(SharedValues.headerSoftVersionCode, Integer.toString(Constants.SERVER_VERSION_CODE));
			response.put(SharedValues.headerCommVer, Integer.toString(SharedValues.mmCommunicationsVersion));
			
			//Returning the response
			return response;
		}
		
		@Override
		public boolean equals(Object o) {
			if( this == o ) return true;
			if( o == null || getClass() != o.getClass() ) return false;
			
			DraftMMS that = ( DraftMMS ) o;
			
			return getExtension() != null ? getExtension().equals( that.getExtension() ) : that.getExtension() == null;
		}
		
		@Override
		public Draft copyInstance() {
			ArrayList<IExtension> newExtensions = new ArrayList<IExtension>();
			for( IExtension extension : getKnownExtensions() ) {
				newExtensions.add( extension.copyInstance() );
			}
			return new DraftMMS(newExtensions);
		}
	}
}