package me.tagavari.airmessageserver.connection;

import io.sentry.Sentry;
import me.tagavari.airmessageserver.common.Blocks;
import me.tagavari.airmessageserver.server.*;
import org.jooq.impl.DSL;

import java.io.*;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
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
				//Sending a ping to all clients
				dataProxy.sendMessage(null, CommConst.nhtPing, new byte[0]);
				
				//Starting ping response timers
				for(ClientRegistration connection : dataProxy.getConnections()) {
					connection.startPingExpiryTimer(CommConst.pingTimeout, () -> initiateClose(connection));
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
		//Sending the server version
		dataProxy.sendMessage(client, CommConst.nhtInformation, ByteBuffer.allocate(Integer.SIZE * 2 / 8).putInt(CommConst.mmCommunicationsVersion).putInt(CommConst.mmCommunicationsSubVersion).array());
		
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
	public void onMessage(ClientRegistration client, int messageType, byte[] data) {
		//Resetting the ping timer
		client.cancelPingExpiryTimer();
		
		//Responding to standard requests
		if(messageType == CommConst.nhtClose) {
			dataProxy.disconnectClient(client);
			return;
		}
		else if(messageType == CommConst.nhtPing) {
			dataProxy.sendMessage(client, CommConst.nhtPong, new byte[0]);
			return;
		}
		
		//Checking if the client is registered
		if(client.isClientRegistered()) {
			switch(messageType) {
				case CommConst.nhtTimeRetrieval: {
					//Reading the data
					final long timeLower;
					final long timeUpper;
					
					ByteBuffer byteBuffer = ByteBuffer.wrap(data);
					timeLower = byteBuffer.getLong();
					timeUpper = byteBuffer.getLong();
					
					//Creating a new request and queuing it
					DatabaseManager.getInstance().addClientRequest(new DatabaseManager.CustomRetrievalRequest(
							client,
							() -> DSL.field("message.date").greaterThan(Main.getTimeHelper().toDatabaseTime(timeLower)).and(DSL.field("message.date").lessThan(Main.getTimeHelper().toDatabaseTime(timeUpper))),
							CommConst.nhtTimeRetrieval));
					
					break;
				}
				case CommConst.nhtMassRetrieval: {
					//Getting the request information
					short requestID;
					boolean restrictMessages = false;
					long timeSinceMessages = -1;
					boolean downloadAttachments = false;
					boolean restrictAttachments = false;
					long timeSinceAttachments = -1;
					boolean restrictAttachmentSize = false;
					long attachmentSizeLimit = -1;
					String[] attachmentFilterWhitelist = null;
					String[] attachmentFilterBlacklist = null;
					boolean attachmentFilterDLOther = false;
					
					//Reading the request data
					try(ByteArrayInputStream src = new ByteArrayInputStream(data); ObjectInputStream in = new ObjectInputStream(src)) {
						requestID = in.readShort();
						if(restrictMessages = in.readBoolean()) timeSinceMessages = in.readLong();
						if(downloadAttachments = in.readBoolean()) {
							if(restrictAttachments = in.readBoolean()) timeSinceAttachments = in.readLong();
							if(restrictAttachmentSize = in.readBoolean()) attachmentSizeLimit = in.readLong();
							
							attachmentFilterWhitelist = new String[in.readInt()];
							for(int i = 0; i < attachmentFilterWhitelist.length; i++) attachmentFilterWhitelist[i] = in.readUTF();
							attachmentFilterBlacklist = new String[in.readInt()];
							for(int i = 0; i < attachmentFilterBlacklist.length; i++) attachmentFilterBlacklist[i] = in.readUTF();
							attachmentFilterDLOther = in.readBoolean();
						}
					} catch(IOException | RuntimeException exception) {
						Main.getLogger().log(Level.WARNING, exception.getMessage(), exception);
						break;
					}
					
					//Creating a new request and queuing it
					DatabaseManager.getInstance().addClientRequest(new DatabaseManager.MassRetrievalRequest(client, requestID, restrictMessages, timeSinceMessages, downloadAttachments, restrictAttachments, timeSinceAttachments, restrictAttachmentSize, attachmentSizeLimit, attachmentFilterWhitelist, attachmentFilterBlacklist, attachmentFilterDLOther));
					
					break;
				}
				case CommConst.nhtConversationUpdate: {
					//Reading the chat GUID list
					List<String> list;
					try(ByteArrayInputStream src = new ByteArrayInputStream(data); ObjectInputStream in = new ObjectInputStream(src)) {
						Blocks.EncryptableData dataSec = Blocks.EncryptableData.readObject(in);
						dataSec.decrypt(PreferencesManager.getPrefPassword());
						
						try(ByteArrayInputStream srcSec = new ByteArrayInputStream(dataSec.data); ObjectInputStream inSec = new ObjectInputStream(srcSec)) {
							int count = inSec.readInt();
							list = new ArrayList<>(count);
							for(int i = 0; i < count; i++) list.add(inSec.readUTF());
						}
					} catch(IOException | RuntimeException | GeneralSecurityException exception) {
						Main.getLogger().log(Level.WARNING, exception.getMessage(), exception);
						break;
					}
					
					//Creating a new request and queuing it
					DatabaseManager.getInstance().addClientRequest(new DatabaseManager.ConversationInfoRequest(client, list));
					
					break;
				}
				case CommConst.nhtAttachmentReq: {
					//Getting the request information
					short requestID;
					int chunkSize;
					
					String fileGUID;
					
					try(ByteArrayInputStream src = new ByteArrayInputStream(data); ObjectInputStream in = new ObjectInputStream(src)) {
						requestID = in.readShort();
						chunkSize = in.readInt();
						
						Blocks.EncryptableData dataSec = Blocks.EncryptableData.readObject(in);
						dataSec.decrypt(PreferencesManager.getPrefPassword());
						
						try(ByteArrayInputStream srcSec = new ByteArrayInputStream(dataSec.data); ObjectInputStream inSec = new ObjectInputStream(srcSec)) {
							fileGUID = inSec.readUTF();
						}
					} catch(IOException | RuntimeException | GeneralSecurityException exception) {
						Main.getLogger().log(Level.WARNING, exception.getMessage(), exception);
						break;
					}
					
					//Sending a reply
					dataProxy.sendMessage(client, CommConst.nhtAttachmentReqConfirm, ByteBuffer.allocate(Short.SIZE / 8).putShort(requestID).array());
					
					//Adding the request
					DatabaseManager.getInstance().addClientRequest(new DatabaseManager.FileRequest(client, fileGUID, requestID, chunkSize));
					
					break;
				}
				case CommConst.nhtCreateChat: {
					//Getting the request information
					short requestID;
					
					String[] chatMembers;
					String service;
					
					try(ByteArrayInputStream src = new ByteArrayInputStream(data); ObjectInputStream in = new ObjectInputStream(src)) {
						requestID = in.readShort();
						
						Blocks.EncryptableData dataSec = Blocks.EncryptableData.readObject(in);
						dataSec.decrypt(PreferencesManager.getPrefPassword());
						
						try(ByteArrayInputStream srcSec = new ByteArrayInputStream(dataSec.data); ObjectInputStream inSec = new ObjectInputStream(srcSec)) {
							chatMembers = new String[inSec.readInt()];
							for(int i = 0; i < chatMembers.length; i++) chatMembers[i] = inSec.readUTF();
							service = inSec.readUTF();
						}
					} catch(IOException | RuntimeException | GeneralSecurityException exception) {
						Main.getLogger().log(Level.WARNING, exception.getMessage(), exception);
						break;
					}
					
					//Sending the message
					Constants.Tuple<Integer, String> result = AppleScriptManager.createChat(chatMembers, service);
					
					//Serializing the data
					try(ByteArrayOutputStream trgt = new ByteArrayOutputStream(); ObjectOutputStream out = new ObjectOutputStream(trgt);
						ByteArrayOutputStream trgtSec = new ByteArrayOutputStream(); ObjectOutputStream outSec = new ObjectOutputStream(trgtSec)) {
						out.writeShort(requestID); //Request ID
						
						outSec.writeInt(result.item1); //Result code
						if(result.item1 != CommConst.nstCreateChatOK) {
							outSec.writeBoolean(result.item2 != null); //Details message
							if(result.item2 != null) outSec.writeUTF(result.item2);
						} else {
							outSec.writeUTF(result.item2);
						}
						outSec.flush();
						
						new Blocks.EncryptableData(trgtSec.toByteArray()).encrypt(PreferencesManager.getPrefPassword()).writeObject(out); //Encrypted data
						
						out.flush();
						
						//Sending the data
						dataProxy.sendMessage(client, CommConst.nhtCreateChat, trgt.toByteArray());
					} catch(IOException | GeneralSecurityException exception) {
						Main.getLogger().log(Level.WARNING, exception.getMessage(), exception);
						Sentry.capture(exception);
					}
					
					break;
				}
				case CommConst.nhtSendTextExisting: {
					//Getting the request information
					short requestID;
					
					String chatGUID;
					String message;
					
					try(ByteArrayInputStream src = new ByteArrayInputStream(data); ObjectInputStream in = new ObjectInputStream(src)) {
						requestID = in.readShort();
						
						Blocks.EncryptableData dataSec = Blocks.EncryptableData.readObject(in);
						dataSec.decrypt(PreferencesManager.getPrefPassword());
						
						try(ByteArrayInputStream srcSec = new ByteArrayInputStream(dataSec.data); ObjectInputStream inSec = new ObjectInputStream(srcSec)) {
							chatGUID = inSec.readUTF();
							message = inSec.readUTF();
						}
					} catch(IOException | RuntimeException | GeneralSecurityException exception) {
						Main.getLogger().log(Level.WARNING, exception.getMessage(), exception);
						break;
					}
					
					//Sending the message
					Constants.Tuple<Integer, String> result = AppleScriptManager.sendExistingMessage(chatGUID, message);
					
					//Sending the response
					sendMessageRequestResponse(client, requestID, result.item1, result.item2);
					
					break;
				}
				case CommConst.nhtSendTextNew: {
					//Getting the request information
					short requestID;
					
					String[] chatMembers;
					String message;
					String service;
					
					try(ByteArrayInputStream src = new ByteArrayInputStream(data); ObjectInputStream in = new ObjectInputStream(src)) {
						requestID = in.readShort();
						
						Blocks.EncryptableData dataSec = Blocks.EncryptableData.readObject(in);
						dataSec.decrypt(PreferencesManager.getPrefPassword());
						
						try(ByteArrayInputStream srcSec = new ByteArrayInputStream(dataSec.data); ObjectInputStream inSec = new ObjectInputStream(srcSec)) {
							chatMembers = new String[inSec.readInt()];
							for(int i = 0; i < chatMembers.length; i++) chatMembers[i] = inSec.readUTF();
							message = inSec.readUTF();
							service = inSec.readUTF();
						}
					} catch(IOException | RuntimeException | GeneralSecurityException exception) {
						Main.getLogger().log(Level.WARNING, exception.getMessage(), exception);
						break;
					}
					
					//Sending the message
					Constants.Tuple<Integer, String> result = AppleScriptManager.sendNewMessage(chatMembers, message, service);
					
					//Sending the response
					sendMessageRequestResponse(client, requestID, result.item1, result.item2);
					
					break;
				}
				case CommConst.nhtSendFileExisting: {
					//Getting the request information
					short requestID;
					int requestIndex;
					boolean isLast;
					
					String chatGUID;
					byte[] compressedBytes;
					String fileName = null;
					
					try(ByteArrayInputStream src = new ByteArrayInputStream(data); ObjectInputStream in = new ObjectInputStream(src)) {
						requestID = in.readShort();
						requestIndex = in.readInt();
						isLast = in.readBoolean();
						
						Blocks.EncryptableData dataSec = Blocks.EncryptableData.readObject(in);
						dataSec.decrypt(PreferencesManager.getPrefPassword());
						
						try(ByteArrayInputStream srcSec = new ByteArrayInputStream(dataSec.data); ObjectInputStream inSec = new ObjectInputStream(srcSec)) {
							chatGUID = inSec.readUTF();
							compressedBytes = new byte[inSec.readInt()];
							inSec.readFully(compressedBytes);
							if(requestIndex == 0) fileName = inSec.readUTF();
						}
					} catch(IOException | RuntimeException | GeneralSecurityException exception) {
						Main.getLogger().log(Level.WARNING, exception.getMessage(), exception);
						break;
					}
					
					//Forwarding the data
					AppleScriptManager.addFileFragment(client, requestID, chatGUID, fileName, requestIndex, compressedBytes, isLast);
					
					break;
				}
				case CommConst.nhtSendFileNew: {
					//Getting the request information
					short requestID;
					int requestIndex;
					boolean isLast;
					
					String[] chatMembers;
					byte[] compressedBytes;
					String fileName = null;
					String service = null;
					
					try(ByteArrayInputStream src = new ByteArrayInputStream(data); ObjectInputStream in = new ObjectInputStream(src)) {
						requestID = in.readShort();
						requestIndex = in.readInt();
						isLast = in.readBoolean();
						
						Blocks.EncryptableData dataSec = Blocks.EncryptableData.readObject(in);
						dataSec.decrypt(PreferencesManager.getPrefPassword());
						
						try(ByteArrayInputStream srcSec = new ByteArrayInputStream(dataSec.data); ObjectInputStream inSec = new ObjectInputStream(srcSec)) {
							chatMembers = new String[inSec.readInt()];
							for(int i = 0; i < chatMembers.length; i++) chatMembers[i] = inSec.readUTF();
							compressedBytes = new byte[inSec.readInt()];
							inSec.readFully(compressedBytes);
							if(requestIndex == 0) {
								fileName = inSec.readUTF();
								service = inSec.readUTF();
							}
						}
					} catch(IOException | RuntimeException | GeneralSecurityException exception) {
						Main.getLogger().log(Level.WARNING, exception.getMessage(), exception);
						break;
					}
					
					//Forwarding the data
					AppleScriptManager.addFileFragment(client, requestID, chatMembers, service, fileName, requestIndex, compressedBytes, isLast);
					
					break;
				}
			}
		} else {
			if(messageType != CommConst.nhtAuthentication) return;
			
			//Stopping the registration timer
			client.cancelHandshakeExpiryTimer();
			
			//Reading the data
			String transmissionWord;
			try(ByteArrayInputStream src = new ByteArrayInputStream(data); ObjectInputStream in = new ObjectInputStream(src)) {
				Blocks.EncryptableData dataSec = Blocks.EncryptableData.readObject(in);
				dataSec.decrypt(PreferencesManager.getPrefPassword());
				transmissionWord = new String(dataSec.data, CommConst.stringCharset);
			} catch(IOException | RuntimeException | GeneralSecurityException exception) {
				//Logging the exception
				Main.getLogger().log(Level.INFO, exception.getMessage(), exception);
				
				//Sending a message and closing the connection
				int responseCode = exception instanceof GeneralSecurityException ? CommConst.nstAuthenticationUnauthorized : CommConst.nstAuthenticationBadRequest;
				dataProxy.sendMessage(client, CommConst.nhtAuthentication, ByteBuffer.allocate(Integer.SIZE / 4).putInt(responseCode).array(), () -> initiateClose(client));
				
				return;
			}
			
			//Validating the transmission
			if(CommConst.transmissionCheck.equals(transmissionWord)) {
				//Marking the client as registered
				client.setClientRegistered(true);
				
				//Sending a message
				dataProxy.sendMessage(client, CommConst.nhtAuthentication, ByteBuffer.allocate(Integer.SIZE / 4).putInt(CommConst.nstAuthenticationOK).array());
			} else {
				//Sending a message and closing the connection
				dataProxy.sendMessage(client, CommConst.nhtAuthentication, ByteBuffer.allocate(Integer.SIZE / 4).putInt(CommConst.nstAuthenticationUnauthorized).array(), () -> initiateClose(client));
			}
		}
	}
	
	public void initiateClose(ClientRegistration client) {
		dataProxy.sendMessage(client, CommConst.nhtClose, new byte[0], () -> dataProxy.disconnectClient(client));
	}
	
	public void sendMessageRequestResponse(ClientRegistration target, short requestID, int result, String details) {
		//Returning if the connection is not open
		if(!target.isConnected()) return;
		
		//Serializing the data
		try(ByteArrayOutputStream trgt = new ByteArrayOutputStream(); ObjectOutputStream out = new ObjectOutputStream(trgt);
			ByteArrayOutputStream trgtSec = new ByteArrayOutputStream(); ObjectOutputStream outSec = new ObjectOutputStream(trgtSec)) {
			out.writeShort(requestID); //Request ID
			
			outSec.writeInt(result); //Result code
			outSec.writeBoolean(details != null); //Details message
			if(details != null) outSec.writeUTF(details);
			outSec.flush();
			
			new Blocks.EncryptableData(trgtSec.toByteArray()).encrypt(PreferencesManager.getPrefPassword()).writeObject(out); //Encrypted data
			
			out.flush();
			
			//Sending the data
			dataProxy.sendMessage(target, CommConst.nhtSendResult, trgt.toByteArray());
		} catch(IOException | GeneralSecurityException exception) {
			Main.getLogger().log(Level.WARNING, exception.getMessage(), exception);
			Sentry.capture(exception);
		}
	}
}