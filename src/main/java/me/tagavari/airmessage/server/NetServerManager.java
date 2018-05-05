package me.tagavari.airmessage.server;

import io.sentry.Sentry;
import io.sentry.event.BreadcrumbBuilder;
import me.tagavari.airmessage.common.SharedValues;
import org.jooq.impl.DSL;

import javax.net.ServerSocketFactory;
import javax.net.ssl.SSLException;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

class NetServerManager {
	//Creating the reference values
	static final int createServerResultOK = 0;
	static final int createServerResultPort = 1;
	static final int createServerResultInternal = 2;
	
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
			ServerSocketFactory ssf = SecurityManager.conjureSSLContext().getServerSocketFactory();
			ServerSocket serverSocket = ssf.createServerSocket(port);
			
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
	static void sendPacket(SocketManager target, int type, byte[] content) {
		//Returning if the connection is not ready for a transfer
		if(writerThread == null || (target != null && !target.isConnected())) return;
		
		//Queuing the request
		writerThread.sendPacket(new WriterThread.PacketStruct(target, type, content));
	}
	
	static void sendMessageRequestResponse(SocketManager target, short requestID, boolean result) {
		//Returning if the connection is not open
		if(!target.isConnected()) return;
		
		//Preparing to serialize
		try(ByteArrayOutputStream bos = new ByteArrayOutputStream(); ObjectOutputStream out = new ObjectOutputStream(bos)) {
			out.writeShort(requestID); //Request ID
			out.writeBoolean(result); //Result
			out.flush();
			
			//Sending the data
			sendPacket(target, SharedValues.nhtSendResult, bos.toByteArray());
		} catch(IOException exception) {
			Main.getLogger().log(Level.WARNING, exception.getMessage(), exception);
			Sentry.capture(exception);
		}
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
					for(SocketManager connection : connectionList) connection.testConnectionSync();
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
					if(packet.target == null) for(SocketManager target : connectionList) target.sendDataSync(packet.type, packet.content);
					else packet.target.sendDataSync(packet.type, packet.content);
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
			Runnable sentRunnable = null;
			
			PacketStruct(SocketManager target, int type, byte[] content) {
				this.target = target;
				this.type = type;
				this.content = content;
			}
			
			PacketStruct(SocketManager target, int type, byte[] content, Runnable sentRunnable) {
				this(target, type, content);
				this.sentRunnable = sentRunnable;
			}
		}
	}
	
	static class SocketManager {
		private static final long registrationTime = 1000 * 10; //10 seconds
		
		/* HEADER DATA
		 * 4 bytes: int - message type
		 * 4 bytes: int - content length
		 * Remainder: array - content
		 */
		private final Socket socket;
		private final ReaderThread readerThread;
		private final OutputStream outputStream;
		private final AtomicBoolean isConnected = new AtomicBoolean(true);
		
		private Timer registrationExpiryTimer;
		private boolean clientRegistered = false;
		
		private final Lock pingResponseTimerLock = new ReentrantLock();
		private Timer pingResponseTimer = new Timer();
		
		private SocketManager(Socket socket) throws IOException {
			//Setting the socket information
			this.socket = socket;
			readerThread = new ReaderThread(socket.getInputStream());
			readerThread.start();
			outputStream = socket.getOutputStream();

			//Starting the state timer
			registrationExpiryTimer = new Timer();
			registrationExpiryTimer.schedule(new TimerTask() {
				@Override
				public void run() {
					//Stopping the registration timer
					registrationExpiryTimer.cancel();
					registrationExpiryTimer = null;
					
					//Closing the connection
					initiateClose();
				}
			}, registrationTime);
			
			//Adding the connection
			connectionList.add(this);
			
			//Updating the UI
			UIHelper.getDisplay().asyncExec(SystemTrayManager::updateConnectionsMessage);
			
			//Logging the connection
			Main.getLogger().info("Client connected from " + socket.getInetAddress().getHostName() + " (" + socket.getInetAddress().getHostAddress() + ")");
		}
		
		synchronized boolean sendDataSync(int messageType, byte[] data) {
			if(!isConnected()) return false;
			
			try {
				outputStream.write(ByteBuffer.allocate(Integer.SIZE / 8 * 2).putInt(messageType).putInt(data.length).array());
				outputStream.write(data);
				outputStream.flush();
				
				return true;
			} catch(SocketException exception) {
				if(isConnected() && Constants.checkDisconnected(exception)) closeConnection();
				
				return false;
			} catch(IOException exception) {
				if(isConnected() && Constants.checkDisconnected(exception)) closeConnection();
				else {
					Main.getLogger().log(Level.WARNING, exception.getMessage(), exception);
					Sentry.capture(exception);
				}
				
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
			
			//Checking if the client is registered
			if(clientRegistered) {
				switch(messageType) {
					case SharedValues.nhtClose:
						closeConnection();
						break;
					case SharedValues.nhtPing:
						writerThread.sendPacket(new WriterThread.PacketStruct(this, SharedValues.nhtPong, new byte[0]));
						break;
					case SharedValues.nhtTimeRetrieval: {
						//Reading the data
						final long timeLower;
						final long timeUpper;
						try(ByteArrayInputStream bis = new ByteArrayInputStream(data); ObjectInputStream in = new ObjectInputStream(bis)) {
							timeLower = in.readLong();
							timeUpper = in.readLong();
						} catch(IOException | RuntimeException exception) {
							Main.getLogger().log(Level.WARNING, exception.getMessage(), exception);
							break;
						}
						
						//Creating a new request and queuing it
						DatabaseManager.getInstance().addClientRequest(new DatabaseManager.CustomRetrievalRequest(
								this,
								() -> DSL.field("message.date").greaterThan(Main.getTimeHelper().toDatabaseTime(timeLower)).and(DSL.field("message.date").lessThan(Main.getTimeHelper().toDatabaseTime(timeUpper))),
								SharedValues.nhtTimeRetrieval));
						
						break;
					}
					case SharedValues.nhtMassRetrieval: {
						//Creating a new request and queuing it
						DatabaseManager.getInstance().addClientRequest(new DatabaseManager.MassRetrievalRequest(this));
						
						break;
					}
					case SharedValues.nhtConversationUpdate: {
						//Reading the chat GUID list
						List<String> list;
						try(ByteArrayInputStream bis = new ByteArrayInputStream(data); ObjectInputStream in = new ObjectInputStream(bis)) {
							int count = in.readInt();
							list = new ArrayList<>();
							for(int i = 0; i < count; i++) list.add(in.readUTF());
						} catch(IOException | RuntimeException exception) {
							Main.getLogger().log(Level.WARNING, exception.getMessage(), exception);
							break;
						}
						
						//Creating a new request and queuing it
						DatabaseManager.getInstance().addClientRequest(new DatabaseManager.ConversationInfoRequest(this, list));
						
						break;
					}
					case SharedValues.nhtAttachmentReq: {
						//Getting the request information
						short requestID;
						String fileGUID;
						int chunkSize;
						
						try(ByteArrayInputStream bis = new ByteArrayInputStream(data); ObjectInputStream in = new ObjectInputStream(bis)) {
							requestID = in.readShort();
							fileGUID = in.readUTF();
							chunkSize = in.readInt();
						} catch(IOException | RuntimeException exception) {
							Main.getLogger().log(Level.WARNING, exception.getMessage(), exception);
							break;
						}
						
						//Sending a reply
						try(ByteArrayOutputStream bos = new ByteArrayOutputStream(); ObjectOutputStream out = new ObjectOutputStream(bos)) {
							out.writeShort(requestID); //Request ID
							out.writeUTF(fileGUID); //File GUID
							out.flush();
							
							//Sending the data
							sendPacket(this, SharedValues.nhtAttachmentReqConfirm, bos.toByteArray());
						} catch(IOException exception) {
							Main.getLogger().log(Level.WARNING, exception.getMessage(), exception);
							Sentry.capture(exception);
							
							break;
						}
						
						//Adding the request
						DatabaseManager.getInstance().addClientRequest(new DatabaseManager.FileRequest(this, fileGUID, requestID, chunkSize));
						
						break;
					}
					case SharedValues.nhtSendTextExisting: {
						//Getting the request information
						short requestID;
						String chatGUID;
						String message;
						
						try(ByteArrayInputStream bis = new ByteArrayInputStream(data); ObjectInputStream in = new ObjectInputStream(bis)) {
							requestID = in.readShort();
							chatGUID = in.readUTF();
							message = in.readUTF();
						} catch(IOException | RuntimeException exception) {
							Main.getLogger().log(Level.WARNING, exception.getMessage(), exception);
							break;
						}
						
						//Sending the message
						boolean result = AppleScriptManager.sendExistingMessage(chatGUID, message);
						
						//Sending the response
						sendMessageRequestResponse(this, requestID, result);
						
						break;
					}
					case SharedValues.nhtSendTextNew: {
						//Getting the request information
						short requestID;
						String[] chatMembers;
						String message;
						String service;
						
						try(ByteArrayInputStream bis = new ByteArrayInputStream(data); ObjectInputStream in = new ObjectInputStream(bis)) {
							requestID = in.readShort();
							chatMembers = new String[in.readInt()];
							for(int i = 0; i < chatMembers.length; i++) chatMembers[i] = in.readUTF();
							message = in.readUTF();
							service = in.readUTF();
						} catch(IOException | RuntimeException exception) {
							Main.getLogger().log(Level.WARNING, exception.getMessage(), exception);
							break;
						}
						
						//Sending the message
						boolean result = AppleScriptManager.sendNewMessage(chatMembers, message, service);
						
						//Sending the response
						sendMessageRequestResponse(this, requestID, result);
						
						break;
					}
					case SharedValues.nhtSendFileExisting: {
						//Getting the request information
						short requestID;
						int requestIndex;
						String chatGUID;
						byte[] compressedBytes;
						String fileName = null;
						boolean isLast;
						
						try(ByteArrayInputStream bis = new ByteArrayInputStream(data); ObjectInputStream in = new ObjectInputStream(bis)) {
							requestID = in.readShort();
							requestIndex = in.readInt();
							chatGUID = in.readUTF();
							compressedBytes = new byte[in.readInt()];
							in.readFully(compressedBytes);
							if(requestIndex == 0) fileName = in.readUTF();
							isLast = in.readBoolean();
						} catch(IOException | RuntimeException exception) {
							Main.getLogger().log(Level.WARNING, exception.getMessage(), exception);
							break;
						}
						
						//Forwarding the data
						AppleScriptManager.addFileFragment(this, requestID, chatGUID, fileName, requestIndex, compressedBytes, isLast);
						
						break;
					}
					case SharedValues.nhtSendFileNew: {
						//Getting the request information
						short requestID;
						int requestIndex;
						String[] chatMembers;
						byte[] compressedBytes;
						String fileName = null;
						String service = null;
						boolean isLast;
						
						try(ByteArrayInputStream bis = new ByteArrayInputStream(data); ObjectInputStream in = new ObjectInputStream(bis)) {
							requestID = in.readShort();
							requestIndex = in.readInt();
							chatMembers = new String[in.readInt()];
							for(int i = 0; i < chatMembers.length; i++) chatMembers[i] = in.readUTF();
							compressedBytes = new byte[in.readInt()];
							in.readFully(compressedBytes);
							if(requestIndex == 0) {
								fileName = in.readUTF();
								service = in.readUTF();
							}
							isLast = in.readBoolean();
						} catch(IOException | RuntimeException exception) {
							Main.getLogger().log(Level.WARNING, exception.getMessage(), exception);
							break;
						}
						
						//Forwarding the data
						AppleScriptManager.addFileFragment(this, requestID, chatMembers, service, fileName, requestIndex, compressedBytes, isLast);
						
						break;
					}
				}
			} else {
				if(messageType != SharedValues.nhtAuthentication) return;
				
				//Stopping the registration timer
				if(registrationExpiryTimer != null) {
					registrationExpiryTimer.cancel();
					registrationExpiryTimer = null;
				}
				
				//Reading the data
				int[] clientVersions;
				String password;
				try(ByteArrayInputStream bis = new ByteArrayInputStream(data); ObjectInputStream in = new ObjectInputStream(bis)) {
					int verCount = in.readInt();
					clientVersions = new int[verCount];
					for(int i = 0; i < verCount; i++) clientVersions[i] = in.readInt();
					
					password = in.readUTF();
				} catch(EOFException | UTFDataFormatException exception) {
					Main.getLogger().log(Level.WARNING, exception.getMessage(), exception);
					
					//Sending a message and closing the connection
					try(ByteArrayOutputStream bos = new ByteArrayOutputStream(); ObjectOutputStream out = new ObjectOutputStream(bos)) {
						out.writeInt(SharedValues.mmCommunicationsVersion); //Communications protocol version
						out.writeInt(SharedValues.nhtAuthenticationBadRequest); //Authentication result
						out.flush();
						
						writerThread.sendPacket(new WriterThread.PacketStruct(this, SharedValues.nhtAuthentication, bos.toByteArray(), this::closeConnection));
					} catch(IOException buildException) {
						//Recording the error
						Main.getLogger().log(Level.WARNING, buildException.getMessage(), buildException);
						Sentry.capture(buildException);
						
						//Closing the connection
						closeConnection();
					}
					
					return;
				} catch(IOException | RuntimeException exception) {
					Main.getLogger().log(Level.WARNING, exception.getMessage(), exception);
					return;
				}
				
				//Finding an applicable version
				boolean versionsApplicable = false;
				for(int version : clientVersions) {
					if(SharedValues.mmCommunicationsVersion == version) {
						versionsApplicable = true;
						break;
					}
				}
				
				//Sending a message if the versions are not applicable
				if(!versionsApplicable) {
					//Sending a message and closing the connection
					try(ByteArrayOutputStream bos = new ByteArrayOutputStream(); ObjectOutputStream out = new ObjectOutputStream(bos)) {
						out.writeInt(SharedValues.mmCommunicationsVersion); //Communications protocol version
						out.writeInt(SharedValues.nhtAuthenticationVersionMismatch); //Authentication result
						out.flush();
						
						writerThread.sendPacket(new WriterThread.PacketStruct(this, SharedValues.nhtAuthentication, bos.toByteArray(), this::initiateClose));
					} catch(IOException exception) {
						//Recording the error
						Main.getLogger().log(Level.WARNING, exception.getMessage(), exception);
						Sentry.capture(exception);
						
						//Closing the connection
						initiateClose();
					}
					
					return;
				}
				
				//Validating the password
				boolean passwordValid = PreferencesManager.matchPassword(password);
				
				if(passwordValid) {
					//Sending a message
					try(ByteArrayOutputStream bos = new ByteArrayOutputStream(); ObjectOutputStream out = new ObjectOutputStream(bos)) {
						out.writeInt(SharedValues.mmCommunicationsVersion); //Communications protocol version
						out.writeInt(SharedValues.nhtAuthenticationOK); //Authentication result
						out.flush();
						
						writerThread.sendPacket(new WriterThread.PacketStruct(this, SharedValues.nhtAuthentication, bos.toByteArray()));
					} catch(IOException exception) {
						//Recording the error
						Main.getLogger().log(Level.WARNING, exception.getMessage(), exception);
						Sentry.capture(exception);
						
						//Closing the connection
						initiateClose();
					}
					
					//Marking the client as registered
					clientRegistered = true;
				} else {
					//Sending a message and closing the connection
					try(ByteArrayOutputStream bos = new ByteArrayOutputStream(); ObjectOutputStream out = new ObjectOutputStream(bos)) {
						out.writeInt(SharedValues.mmCommunicationsVersion); //Communications protocol version
						out.writeInt(SharedValues.nhtAuthenticationUnauthorized); //Authentication result
						out.flush();
						
						writerThread.sendPacket(new WriterThread.PacketStruct(this, SharedValues.nhtAuthentication, bos.toByteArray(), this::initiateClose));
					} catch(IOException exception) {
						//Recording the error
						Main.getLogger().log(Level.WARNING, exception.getMessage(), exception);
						Sentry.capture(exception);
						
						//Closing the connection
						initiateClose();
					}
				}
			}
		}
		
		/* void initiateClose(int code) {
			//Sending a message and closing the connection
			writerThread.sendPacket(new WriterThread.PacketStruct(this, SharedValues.nhtClose, ByteBuffer.allocate(Integer.SIZE / 8).putInt(code).array(), this::closeConnection));
			
			//Closing the connection
			//closeConnection();
		} */
		
		void initiateClose() {
			//Sending a message and closing the connection
			writerThread.sendPacket(new WriterThread.PacketStruct(this, SharedValues.nhtClose, new byte[0], this::closeConnection));
		}
		
		void initiateCloseSync() {
			sendDataSync(SharedValues.nhtClose, new byte[0]);
			closeConnection();
		}
		
		private void closeConnection() {
			//Removing the connection record
			if(connectionList.contains(this)) connectionList.remove(this);
			
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
		
		/**
		 * Sends a ping to the client, and awaits the connection
		 * The connection will be closed if no response is received within the time limit
		 */
		void testConnectionSync() {
			sendDataSync(SharedValues.nhtPing, new byte[0]);
			
			pingResponseTimerLock.lock();
			try {
				if(pingResponseTimer == null) {
					pingResponseTimer = new Timer();
					pingResponseTimer.schedule(new TimerTask() {
						@Override
						public void run() {
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
			private final InputStream inputStream;
			
			ReaderThread(InputStream inputStream) {
				this.inputStream = inputStream;
			}
			
			@Override
			public void run() {
				while(!isInterrupted() && isConnected()) {
					try {
						//Reading the header data
						byte[] header = new byte[Integer.SIZE / 8 * 2];
						{
							int bytesRemaining = header.length;
							int offset = 0;
							int readCount;
							
							while(bytesRemaining > 0) {
								readCount = inputStream.read(header, offset, bytesRemaining);
								if(readCount == -1) { //No data read, stream is closed
									closeConnection();
									return;
								}
								
								offset += readCount;
								bytesRemaining -= readCount;
							}
						}
						//Creating the values
						ByteBuffer headerBuffer = ByteBuffer.wrap(header);
						int messageType = headerBuffer.getInt();
						int contentLen = headerBuffer.getInt();
						
						//Adding a breadcrumb
						{
							Map<String, String> dataMap = new HashMap<>(2);
							dataMap.put("Message type", Integer.toString(messageType));
							dataMap.put("Content length", Integer.toString(contentLen));
							Sentry.getContext().recordBreadcrumb(new BreadcrumbBuilder().setCategory(Constants.sentryBCatPacket).setMessage("New packet received").setData(dataMap).build());
						}
						
						//Reading the content
						byte[] content = new byte[contentLen];
						int bytesRemaining = contentLen;
						int offset = 0;
						int readCount;
						while(bytesRemaining > 0) {
							readCount = inputStream.read(content, offset, bytesRemaining);
							if(readCount == -1) { //No data read, stream is closed
								closeConnection();
								return;
							}
							
							offset += readCount;
							bytesRemaining -= readCount;
						}
						
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
					} catch(SSLException exception) {
						if(Main.MODE_DEBUG) Main.getLogger().log(Level.WARNING, Main.PREFIX_DEBUG + exception.getMessage(), exception);
						closeConnection();
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