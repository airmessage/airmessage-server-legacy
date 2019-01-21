package me.tagavari.airmessage.server;

import io.sentry.Sentry;
import io.sentry.event.BreadcrumbBuilder;
import me.tagavari.airmessage.common.Blocks;
import org.jooq.impl.DSL;

import javax.net.ssl.SSLException;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

class NetServerManager {
	//Creating the transmission header values
	public static final int mmCommunicationsVersion = 4;
	public static final int mmCommunicationsSubVersion = 4;
	
	//NHT = Net Header Type
	public static final int nhtClose = -1;
	public static final int nhtPing = -2;
	public static final int nhtPong = -3;
	public static final int nhtInformation = 0;
	public static final int nhtAuthentication = 1;
	public static final int nhtMessageUpdate = 2;
	public static final int nhtTimeRetrieval = 3;
	public static final int nhtMassRetrieval = 4;
	public static final int nhtMassRetrievalFinish = 10;
	public static final int nhtMassRetrievalFile = 11;
	public static final int nhtConversationUpdate = 5;
	public static final int nhtModifierUpdate = 6;
	public static final int nhtAttachmentReq = 7;
	public static final int nhtAttachmentReqConfirm = 8;
	public static final int nhtAttachmentReqFail = 9;
	
	public static final int nhtSendResult = 100;
	public static final int nhtSendTextExisting = 101;
	public static final int nhtSendTextNew = 102;
	public static final int nhtSendFileExisting = 103;
	public static final int nhtSendFileNew = 104;
	
	public static final int nhtAuthenticationOK = 0;
	public static final int nhtAuthenticationUnauthorized = 1;
	public static final int nhtAuthenticationBadRequest = 2;
	
	public static final String stringCharset = "UTF-8";
	public static final String hashAlgorithm = "MD5";
	public static final String transmissionCheck = "4yAIlVK0Ce_Y7nv6at_hvgsFtaMq!lZYKipV40Fp5E%VSsLSML";
	
	//Creating the other reference values
	static final int createServerResultOK = 0;
	static final int createServerResultPort = 1;
	static final int createServerResultInternal = 2;
	private static final int maxPacketAllocation = 50 * 1024 * 1024; //50 MB
	
	//private static final long keepAliveMillis = 30 * 1000; //30 seconds
	private static final long keepAliveMillis = 30 * 60 * 1000; //30 minutes
	private static final long pingTimeout = 30 * 1000; //30 seconds
	
	//Creating the values
	private static boolean serverRunning = false;
	private static int currentPort = -1;
	private static ListenerThread listenerThread = null;
	private static WriterThread writerThread = null;
	private static final List<SocketManager> connectionList = Collections.synchronizedList(new ArrayList<>());
	
	static int createServer(int port, boolean recreate) {
		//Returning OK if the server is already running
		if(serverRunning && (!recreate || port == currentPort)) return createServerResultOK;
		
		//Destroying the server if it exists
		if(serverRunning) destroyServer();
		
		//Returning false if the requested port is already bound
		if(!Constants.checkPortAvailability(port)) return createServerResultPort;
		
		try {
			//Creating the server socket
			ServerSocket serverSocket = new ServerSocket(port);
			
			//Starting the listener thread
			listenerThread = new ListenerThread(serverSocket);
			listenerThread.start();
		} catch(Exception exception) {
			Main.getLogger().log(Level.SEVERE, exception.getMessage(), exception);
			Sentry.capture(exception);
			return createServerResultInternal;
		}
		
		//Starting the writer thread
		writerThread = new WriterThread();
		writerThread.start();
		
		//Setting the port
		serverRunning = true;
		currentPort = port;
		
		//Returning true
		return createServerResultOK;
	}
	
	static int createServerErrorToServerState(int value) {
		switch(value) {
			default:
				throw new IllegalArgumentException("Expected a create server result error; instead got " + value);
			case createServerResultPort:
				return Main.serverStateFailedServerPort;
			case createServerResultInternal:
				return Main.serverStateFailedServerInternal;
		}
	}
	
	static void destroyServer() {
		if(listenerThread != null) listenerThread.closeServer();
		serverRunning = false;
	}
	
	/**
	 * Sends a packet to connected clients
	 * @param target The target to send the packet to, null to send to all
	 * @param type The type of message sent in the header
	 * @param content The content to send
	 */
	static void sendPacket(SocketManager target, int type, byte[] content, boolean isSensitive) {
		//Returning if the connection is not ready for a transfer
		if(writerThread == null || (target != null && !target.isConnected())) return;
		
		//Queuing the request
		writerThread.sendPacket(new WriterThread.PacketStruct(target, type, content, isSensitive));
	}
	
	static void sendMessageRequestResponse(SocketManager target, short requestID, boolean result) {
		//Returning if the connection is not open
		if(!target.isConnected()) return;
		
		//Sending the response
		sendPacket(target, nhtSendResult, ByteBuffer.allocate(Short.SIZE / 8 + 1).putShort(requestID).put((byte) (result ? 1 : 0)).array(), false);
	}
	
	static int getConnectionCount() {
		return connectionList.size();
	}
	
	private static class ListenerThread extends Thread {
		//Creating the server values
		private ServerSocket serverSocket;
		private final Timer pingTimer = new Timer();
		
		ListenerThread(ServerSocket serverSocket) {
			//Setting the socket
			this.serverSocket = serverSocket;
			
			//Starting the timer
			pingTimer.scheduleAtFixedRate(new TimerTask() {
				@Override
				public void run() {
					//Sending a ping to all clients
					for(SocketManager connection : new ArrayList<>(connectionList)) connection.testConnectionSync();
				}
			}, keepAliveMillis, keepAliveMillis);
		}
		
		@Override
		public void run() {
			//Accepting new connections
			while(!isInterrupted()) {
				try {
					new SocketManager(serverSocket.accept());
				/* } catch(SocketException exception) {
					Main.getLogger().log(Level.WARNING, exception.getMessage(), exception);
					return; */
				} catch(IOException exception) {
					Main.getLogger().log(Level.WARNING, exception.getMessage(), exception);
				}
			}
		}
		
		void closeServer() {
			//Stopping the ping timer
			pingTimer.cancel();
			
			//Closing the connections (copying list to prevent ConcurrentModificationException)
			for(SocketManager connection : new ArrayList<>(connectionList)) connection.initiateCloseSync();
			
			//Closing the socket
			try {
				serverSocket.close();
			} catch(IOException exception) {
				Main.getLogger().log(Level.WARNING, exception.getMessage(), exception);
			}
			
			//Stopping the threads
			interrupt();
			writerThread.interrupt();
		}
	}
	
	static class WriterThread extends Thread {
		//Creating the queue
		private final BlockingQueue<PacketStruct> uploadQueue = new LinkedBlockingQueue<>();
		
		@Override
		public void run() {
			try {
				while(!isInterrupted()) {
					PacketStruct packet = uploadQueue.take();
					if(packet.target == null) for(SocketManager target : new ArrayList<>(connectionList)) {
						if(!packet.isSensitive || target.isClientRegistered()) target.sendDataSync(packet.type, packet.content);
					}
					else {
						if(!packet.isSensitive || packet.target.isClientRegistered()) packet.target.sendDataSync(packet.type, packet.content);
					}
					if(packet.sentRunnable != null) packet.sentRunnable.run();
				}
			} catch(InterruptedException exception) {
				return;
			}
		}
		
		void sendPacket(PacketStruct packet) {
			uploadQueue.add(packet);
		}
		
		static class PacketStruct {
			final SocketManager target;
			final int type;
			final byte[] content;
			final boolean isSensitive;
			Runnable sentRunnable = null;
			
			PacketStruct(SocketManager target, int type, byte[] content, boolean isSensitive) {
				this.target = target;
				this.type = type;
				this.content = content;
				this.isSensitive = isSensitive;
			}
			
			PacketStruct(SocketManager target, int type, byte[] content, boolean isSensitive, Runnable sentRunnable) {
				this(target, type, content, isSensitive);
				this.sentRunnable = sentRunnable;
			}
		}
	}
	
	static class SocketManager {
		private static final long registrationTime = 1000 * 10; //10 seconds
		private int testID = new Random().nextInt(100);
		
		/* HEADER DATA
		 * 4 bytes: int - message type
		 * 4 bytes: int - content length
		 * content length: array - content
		 */
		private final Socket socket;
		private final ReaderThread readerThread;
		private final DataOutputStream outputStream;
		private final AtomicBoolean isConnected = new AtomicBoolean(true);
		
		private Timer handshakeExpiryTimer;
		private boolean clientRegistered = false;
		
		private final Lock pingResponseTimerLock = new ReentrantLock();
		private Timer pingResponseTimer = new Timer();
		
		private SocketManager(Socket socket) throws IOException {
			//Setting the socket information
			this.socket = socket;
			readerThread = new ReaderThread(new DataInputStream(socket.getInputStream()));
			readerThread.start();
			outputStream = new DataOutputStream(socket.getOutputStream());
			
			//Adding the connection
			connectionList.add(this);
			
			//Sending the server version
			sendPacket(this, nhtInformation, ByteBuffer.allocate(Integer.SIZE * 2 / 8).putInt(mmCommunicationsVersion).putInt(mmCommunicationsSubVersion).array(), false);
			
			//Starting the state timer
			handshakeExpiryTimer = new Timer();
			handshakeExpiryTimer.schedule(new TimerTask() {
				@Override
				public void run() {
					//Stopping the registration timer
					handshakeExpiryTimer.cancel();
					handshakeExpiryTimer = null;
					
					//Closing the connection
					initiateClose();
				}
			}, registrationTime);
			
			//Updating the UI
			UIHelper.getDisplay().asyncExec(SystemTrayManager::updateConnectionsMessage);
			
			//Logging the connection
			Main.getLogger().info("Client connected from " + socket.getInetAddress().getHostName() + " (" + socket.getInetAddress().getHostAddress() + ")");
		}
		
		synchronized boolean sendDataSync(int messageType, byte[] data) {
			if(!isConnected()) return false;
			
			try {
				outputStream.writeInt(messageType);
				outputStream.writeInt(data.length);
				outputStream.write(data);
				outputStream.flush();
				
				return true;
			} catch(SocketException | SSLException exception) {
				Main.getLogger().log(Level.WARNING, exception.getMessage(), exception);
				if(isConnected() && Constants.checkDisconnected(exception)) closeConnection();
				
				return false;
			} catch(IOException exception) {
				Main.getLogger().log(Level.WARNING, exception.getMessage(), exception);
				if(isConnected() && Constants.checkDisconnected(exception)) closeConnection();
				else Sentry.capture(exception);
				
				return false;
			}
		}
		
		private void processData(int messageType, byte[] data) {
			//Resetting the ping timer
			pingResponseTimerLock.lock();
			try {
				if(pingResponseTimer != null) {
					pingResponseTimer.cancel();
					pingResponseTimer = null;
				}
			} finally {
				pingResponseTimerLock.unlock();
			}
			
			//Responding to standard requests
			if(messageType == nhtClose) {
				closeConnection();
				return;
			}
			else if(messageType == nhtPing) {
				writerThread.sendPacket(new WriterThread.PacketStruct(this, nhtPong, new byte[0], false));
				return;
			}
			
			//Checking if the client is registered
			if(clientRegistered) {
				switch(messageType) {
					case nhtTimeRetrieval: {
						//Reading the data
						final long timeLower;
						final long timeUpper;
						
						ByteBuffer byteBuffer = ByteBuffer.wrap(data);
						timeLower = byteBuffer.getLong();
						timeUpper = byteBuffer.getLong();
						
						//Creating a new request and queuing it
						DatabaseManager.getInstance().addClientRequest(new DatabaseManager.CustomRetrievalRequest(
								this,
								() -> DSL.field("message.date").greaterThan(Main.getTimeHelper().toDatabaseTime(timeLower)).and(DSL.field("message.date").lessThan(Main.getTimeHelper().toDatabaseTime(timeUpper))),
								nhtTimeRetrieval));
						
						break;
					}
					case nhtMassRetrieval: {
						//Getting the request information
						short requestID;
						boolean useAdvanced;
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
							if(useAdvanced = in.readBoolean()) {
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
							}
						} catch(IOException | RuntimeException exception) {
							Main.getLogger().log(Level.WARNING, exception.getMessage(), exception);
							break;
						}
						
						//Creating a new request and queuing it
						DatabaseManager.getInstance().addClientRequest(new DatabaseManager.MassRetrievalRequest(this, requestID, useAdvanced, restrictMessages, timeSinceMessages, downloadAttachments, restrictAttachments, timeSinceAttachments, restrictAttachmentSize, attachmentSizeLimit, attachmentFilterWhitelist, attachmentFilterBlacklist, attachmentFilterDLOther));
						
						break;
					}
					case nhtConversationUpdate: {
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
						DatabaseManager.getInstance().addClientRequest(new DatabaseManager.ConversationInfoRequest(this, list));
						
						break;
					}
					case nhtAttachmentReq: {
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
						sendPacket(this, nhtAttachmentReqConfirm, ByteBuffer.allocate(Short.SIZE / 8).putShort(requestID).array(), false);
						
						//Adding the request
						DatabaseManager.getInstance().addClientRequest(new DatabaseManager.FileRequest(this, fileGUID, requestID, chunkSize));
						
						break;
					}
					case nhtSendTextExisting: {
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
						boolean result = AppleScriptManager.sendExistingMessage(chatGUID, message);
						
						//Sending the response
						sendMessageRequestResponse(this, requestID, result);
						
						break;
					}
					case nhtSendTextNew: {
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
						boolean result = AppleScriptManager.sendNewMessage(chatMembers, message, service, false);
						
						//Sending the response
						sendMessageRequestResponse(this, requestID, result);
						
						break;
					}
					case nhtSendFileExisting: {
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
						AppleScriptManager.addFileFragment(this, requestID, chatGUID, fileName, requestIndex, compressedBytes, isLast);
						
						break;
					}
					case nhtSendFileNew: {
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
						AppleScriptManager.addFileFragment(this, requestID, chatMembers, service, fileName, requestIndex, compressedBytes, isLast);
						
						break;
					}
				}
			} else {
				if(messageType != nhtAuthentication) return;
				
				//Stopping the registration timer
				if(handshakeExpiryTimer != null) {
					handshakeExpiryTimer.cancel();
					handshakeExpiryTimer = null;
				}
				
				//Reading the data
				String transmissionWord;
				try(ByteArrayInputStream src = new ByteArrayInputStream(data); ObjectInputStream in = new ObjectInputStream(src)) {
					Blocks.EncryptableData dataSec = Blocks.EncryptableData.readObject(in);
					dataSec.decrypt(PreferencesManager.getPrefPassword());
					transmissionWord = new String(dataSec.data, stringCharset);
				} catch(IOException | RuntimeException | GeneralSecurityException exception) {
					//Logging the exception
					Main.getLogger().log(Level.INFO, exception.getMessage(), exception);
					
					//Sending a message and closing the connection
					int responseCode = exception instanceof GeneralSecurityException ? nhtAuthenticationUnauthorized : nhtAuthenticationBadRequest;
					writerThread.sendPacket(new WriterThread.PacketStruct(this, nhtAuthentication, ByteBuffer.allocate(Integer.SIZE / 4).putInt(responseCode).array(), false, this::initiateClose));
					
					return;
				}
				
				//Validating the transmission
				if(transmissionCheck.equals(transmissionWord)) {
					//Marking the client as registered
					clientRegistered = true;
					
					//Sending a message
					writerThread.sendPacket(new WriterThread.PacketStruct(this, nhtAuthentication, ByteBuffer.allocate(Integer.SIZE / 4).putInt(nhtAuthenticationOK).array(), false));
				} else {
					//Sending a message and closing the connection
					writerThread.sendPacket(new WriterThread.PacketStruct(this, nhtAuthentication, ByteBuffer.allocate(Integer.SIZE / 4).putInt(nhtAuthenticationUnauthorized).array(), false, this::initiateClose));
				}
			}
		}
		
		/* void initiateClose(int code) {
			//Sending a message and closing the connection
			writerThread.sendPacket(new WriterThread.PacketStruct(this, nhtClose, ByteBuffer.allocate(Integer.SIZE / 8).putInt(code).array(), this::closeConnection));
			
			//Closing the connection
			//closeConnection();
		} */
		
		void initiateClose() {
			//Sending a message and closing the connection
			writerThread.sendPacket(new WriterThread.PacketStruct(this, nhtClose, new byte[0], false, this::closeConnection));
		}
		
		void initiateCloseSync() {
			sendDataSync(nhtClose, new byte[0]);
			closeConnection();
		}
		
		private void closeConnection() {
			//Removing the connection record
			connectionList.remove(this);
			
			//Returning if the connection is not open
			if(!isConnected()) return;
			
			//Setting the connection as closed
			isConnected.set(false);
			
			try {
				//Closing the socket
				socket.close();
			} catch(IOException exception) {
				Main.getLogger().log(Level.WARNING, exception.getMessage(), exception);
			}
			
			//Resetting the ping timer
			pingResponseTimerLock.lock();
			try {
				if(pingResponseTimer != null) {
					pingResponseTimer.cancel();
					pingResponseTimer = null;
				}
			} finally {
				pingResponseTimerLock.unlock();
			}
			
			//Finishing the reader thread
			readerThread.interrupt();
			
			//Updating the UI
			UIHelper.getDisplay().asyncExec(SystemTrayManager::updateConnectionsMessage);
			
			//Logging the connection
			Main.getLogger().info("Client disconnected from " + socket.getInetAddress().getHostName() + " (" + socket.getInetAddress().getHostAddress() + ")");
		}
		
		boolean isConnected() {
			return isConnected.get() && socket.isConnected();
		}
		
		boolean isClientRegistered() {
			return clientRegistered;
		}
		
		/**
		 * Sends a ping to the client, and awaits the connection
		 * The connection will be closed if no response is received within the time limit
		 */
		void testConnectionSync() {
			sendDataSync(nhtPing, new byte[0]);
			
			pingResponseTimerLock.lock();
			try {
				if(pingResponseTimer == null) {
					pingResponseTimer = new Timer();
					pingResponseTimer.schedule(new TimerTask() {
						@Override
						public void run() {
							Main.getLogger().log(Level.INFO, "Disconnecting inactive client (didn't respond to ping)");
							
							try {
								closeConnection();
								
								pingResponseTimerLock.lock();
								try {
									if(pingResponseTimer != null) pingResponseTimer.cancel();
								} finally {
									pingResponseTimerLock.unlock();
								}
							} catch(Exception exception) {
								Main.getLogger().log(Level.SEVERE, exception.getMessage(), exception);
								Sentry.capture(exception);
							}
						}
					}, pingTimeout);
				}
			} finally {
				pingResponseTimerLock.unlock();
			}
		}
		
		private class ReaderThread extends Thread {
			//Creating the stream
			private final DataInputStream inputStream;
			
			ReaderThread(DataInputStream inputStream) {
				this.inputStream = inputStream;
			}
			
			@Override
			public void run() {
				while(!isInterrupted() && isConnected()) {
					try {
						//Reading the header data
						int messageType = inputStream.readInt();
						int contentLen = inputStream.readInt();
						
						//Adding a breadcrumb
						{
							Map<String, String> dataMap = new HashMap<>(2);
							dataMap.put("Message type", Integer.toString(messageType));
							dataMap.put("Content length", Integer.toString(contentLen));
							Sentry.getContext().recordBreadcrumb(new BreadcrumbBuilder().setCategory(Constants.sentryBCatPacket).setMessage("New packet received").setData(dataMap).build());
						}
						
						Main.getLogger().log(Level.FINEST, "New message received: " + messageType + " / " + contentLen);
						//Checking if the content length is greater than the maximum packet allocation
						if(contentLen > maxPacketAllocation) {
							//Logging the error
							Main.getLogger().log(Level.WARNING, "Rejecting large packet (size " + contentLen + ")");
							Sentry.getContext().recordBreadcrumb(new BreadcrumbBuilder().setCategory(Constants.sentryBCatPacket).setMessage("Rejecting large packet (type: " + messageType + " - size: " + contentLen + ")").build());
							
							//Closing the connection
							initiateClose();
							return;
						}
						
						//Reading the content
						byte[] content = new byte[contentLen];
						inputStream.readFully(content);
						
						//Processing the data
						processData(messageType, content);
					} catch(OutOfMemoryError exception) {
						//Logging the error
						Main.getLogger().log(Level.WARNING, exception.getMessage(), exception);
						Sentry.capture(exception);
						
						//Closing the connection
						initiateClose();
						
						//Breaking
						break;
					} catch(SocketException | SSLException | EOFException | RuntimeException exception) {
						Main.getLogger().log(Level.WARNING, exception.getMessage(), exception);
						closeConnection();
						
						//Breaking
						break;
					} catch(IOException exception) {
						if(isConnected()) {
							//Logging the error
							Main.getLogger().log(Level.WARNING, exception.getMessage(), exception);
						}
						
						//Closing the connection
						initiateClose();
						
						//Breaking
						break;
					}
				}
			}
		}
	}
}