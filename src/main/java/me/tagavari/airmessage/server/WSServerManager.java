package me.tagavari.airmessage.server;

class WSServerManager {
	/* //Server instance
	private static WSServerManager serverManager = null;
	
	//Creating the instance values
	//private final BidiMap<String, WebSocket> clientList = new DualHashBidiMap<>();
	
	//Creating the other values
	private static int connectionCount = 0;
	private static int currentPort = -1;
	
	static boolean startServer(int port) {
		//Returning true if an instance already exists
		if(serverManager != null) return true;
		
		//Returning false if the port is already bound
		if(!Constants.checkPortAvailability(port)) return false;
		
		//Creating the server
		createServer(port);
		
		//Returning true
		return true;
	}
	
	static boolean restartServer(int port) {
		//Returning if the requested port matches the current port
		if(port == currentPort) return true;
		
		//Stopping the server
		stopServer();
		
		//Returning false if the port is already bound
		if(!Constants.checkPortAvailability(port)) return false;
		
		//Creating the server
		createServer(port);
		
		//Returning true
		return true;
	}
	
	private static void createServer(int port) {
		//Enabling verbose WebSocket logging
		//WebSocketImpl.DEBUG = true;
		
		//Creating the server
		serverManager = new WSServerManager(port, new ArrayList<>(Arrays.asList(new DraftMMS())));
		serverManager.setWebSocketFactory(new DefaultSSLWebSocketServerFactory(SecurityManager.conjureSSLContext()));
		serverManager.setConnectionLostTimeout(0); //Disabled, unnecessary as the server doesn't do anything when clients disconnect
		
		//Starting the server
		serverManager.start();
		
		//Setting the current port
		currentPort = port;
	}
	
	static void stopServer() {
		try {
			//Stopping the server
			if(serverManager != null) serverManager.stop();
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
		if(serverManager != null) serverManager.sendToAll(message);
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
	
	static int getConnectionCount() {
		return connectionCount;
	}
	
	@Override
	public void onOpen(WebSocket connection, ClientHandshake handshake) {
		//Updating the connection count
		connectionCount = connections().size();
		
		//Updating the connection message
		UIHelper.getDisplay().asyncExec(SystemTrayManager::updateConnectionsMessage);
		
		//Logging the client's connection
		if(connection.getRemoteSocketAddress() != null) Main.getLogger().info("Client connected with IP address " + connection.getRemoteSocketAddress().getAddress().getHostAddress());
	}
	
	@Override
	public void onClose(WebSocket connection, int code, String reason, boolean remote) {
		//Updating the connection count
		connectionCount = connections().size();
		
		//Updating the connection message
		if(!UIHelper.getDisplay().isDisposed()) UIHelper.getDisplay().asyncExec(SystemTrayManager::updateConnectionsMessage);
		
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
					DatabaseManager.getInstance().addClientRequest(new DatabaseManager.CustomRetrievalRequest(
							connection,
							() -> DSL.field("message.date").greaterThan(Main.getTimeHelper().toDatabaseTime(timeLower)).and(DSL.field("message.date").lessThan(Main.getTimeHelper().toDatabaseTime(timeUpper))),
							SharedValues.wsFrameTimeRetrieval));
					
					break;
				}
				case SharedValues.wsFrameMassRetrieval: { //Mass retrieval request
					//Creating a new request and queuing it
					DatabaseManager.getInstance().addClientRequest(new DatabaseManager.MassRetrievalRequest(connection));
					
					break;
				}
				case SharedValues.wsFrameChatInfo: { //Chat info request
					//Getting the chat GUID list
					ArrayList<String> list = (ArrayList<String>) in.readObject();
					
					//Creating a new request and queuing it
					DatabaseManager.getInstance().addClientRequest(new DatabaseManager.ConversationInfoRequest(connection, list));
					
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
					DatabaseManager.getInstance().addClientRequest(new DatabaseManager.FileRequest(connection, fileGUID, requestID, chunkSize));
					
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
	public void onStart() {
	}
	
	@Override
	public void onWebsocketPing(WebSocket conn, Framedata f) {
		Main.getLogger().finest("Intercepting ping");
	}
	
	@Override
	public void onWebsocketPong(WebSocket conn, Framedata f) {
		Main.getLogger().finest("Intercepting pong");
	}
	
	@Override
	public ServerHandshakeBuilder onWebsocketHandshakeReceivedAsServer(WebSocket conn, Draft draft, ClientHandshake request) throws InvalidDataException {
		//Rejecting the handshake if no value was provided
		if(!request.hasFieldValue(SharedValues.headerCommVer))
			throw new InvalidDataException(SharedValues.resultBadRequest, "Communications version not provided: " + SharedValues.resultBadRequest);
		
		//Parsing the versions
		String[] versions = request.getFieldValue(SharedValues.headerCommVer).split("\\|");
		List<Integer> clientVersions = new ArrayList<>();
		for(String version : versions) if(version.matches("^\\d+$")) clientVersions.add(Integer.parseInt(version));
		
		//Rejecting the handshake if no valid values were provided
		if(clientVersions.isEmpty())
			throw new InvalidDataException(SharedValues.resultBadRequest, "Communications version not provided: " + SharedValues.resultBadRequest);
		
		//Finding an applicable version
		boolean versionsApplicable = false;
		for(int version : clientVersions)
			if(SharedValues.mmCommunicationsVersion == version) {
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
		if(!PreferencesManager.matchPassword(request.getFieldValue(SharedValues.headerPassword)))
			throw new InvalidDataException(SharedValues.resultUnauthorized, "Couldn't validate credentials: " + SharedValues.resultUnauthorized);
		
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
		public boolean equals(Object object) {
			if(this == object) return true;
			if(object == null || getClass() != object.getClass()) return false;
			
			DraftMMS draft = (DraftMMS) object;
			return getExtension() != null ? getExtension().equals(draft.getExtension()) : draft.getExtension() == null;
		}
		
		@Override
		public Draft copyInstance() {
			ArrayList<IExtension> newExtensions = new ArrayList<>();
			for(IExtension extension : getKnownExtensions()) newExtensions.add(extension.copyInstance());
			return new DraftMMS(newExtensions);
		}
	} */
}