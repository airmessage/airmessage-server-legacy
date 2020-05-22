package me.tagavari.airmessageserver.connection.connect;

import me.tagavari.airmessageserver.connection.DataProxy;
import me.tagavari.airmessageserver.server.Main;
import me.tagavari.airmessageserver.server.ServerState;
import org.java_websocket.framing.CloseFrame;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.*;

public class DataProxyConnect extends DataProxy<ClientSocket> implements ConnectionListener {
	private static final Random random = new Random();
	private static final long handshakeTimeout = 8 * 1000;
	private static final long disconnectReconnectMaxAttempts = 8; //The max num of attempts before capping the delay time - not before giving up
	
	//Creating the state values
	private final Map<Integer, ClientSocket> connectionList = Collections.synchronizedMap(new HashMap<>());
	private ConnectWebSocketClient connectClient;
	
	private final boolean connectRegistration;
	private final String connectIDToken;
	private final String connectUserID;
	
	private Timer handshakeTimeoutTimer;
	
	private int disconnectReconnectAttempts = 0;
	private Timer disconnectReconnectTimer;
	private final TimerTask disconnectReconnectTimerTask = new TimerTask() {
		@Override
		public void run() {
			connectClient.connect();
		}
	};
	
	/**
	 * DataProxyConnect constructor
	 * @param registration TRUE if this is a new registration, FALSE if this client has already been registered
	 * @param key The account's ID token if this is a new registration, or the user ID if this client has already been registered
	 */
	public DataProxyConnect(boolean registration, String key) {
		if(registration) {
			connectRegistration = true;
			connectIDToken = key;
			connectUserID = null;
		} else {
			connectRegistration = false;
			connectIDToken = null;
			connectUserID = key;
		}
	}
	
	private void addClient(int connectionID) {
		ClientSocket client = new ClientSocket(connectionID);
		
		connectionList.put(connectionID, client);
		notifyOpen(client);
	}
	
	private void removeClient(int connectionID) {
		ClientSocket client = connectionList.remove(connectionID);
		if(client != null) notifyClose(client);
	}
	
	@Override
	public void startServer() {
		//Getting the client
		if(connectRegistration) {
			connectClient = ConnectWebSocketClient.createInstanceRegister(connectIDToken, this);
		} else {
			connectClient = ConnectWebSocketClient.createInstanceExisting(connectUserID, this);
		}
		
		//Connecting the client
		connectClient.connect();
	}
	
	@Override
	public void stopServer() {
		//Disconnecting the client
		connectClient.close();
		
		//Stopping the reconnection timer
		stopReconnectionTimer();
	}
	
	@Override
	public void sendMessage(ClientSocket client, byte[] content, boolean encrypt, Runnable sentRunnable) {
		//Constructing and sending the message
		ByteBuffer byteBuffer;
		if(client == null) {
			byteBuffer = ByteBuffer.allocate(Integer.BYTES + content.length);
			byteBuffer.putInt(NHT.nhtServerProxyBroadcast);
		} else {
			byteBuffer = ByteBuffer.allocate(Integer.BYTES * 2 + content.length);
			byteBuffer.putInt(NHT.nhtServerProxy);
			byteBuffer.putInt(client.getConnectionID());
		}
		byteBuffer.put(content);
		
		//Sending the data
		connectClient.send(byteBuffer.array());
		
		//Running the sent runnable immediately
		if(sentRunnable != null) sentRunnable.run();
	}
	
	@Override
	public void disconnectClient(ClientSocket client) {
		disconnectClient(client.getConnectionID());
	}
	
	private void disconnectClient(int connectionID) {
		//Constructing and sending the message
		ByteBuffer byteBuffer = ByteBuffer.allocate(Integer.BYTES * 2);
		byteBuffer.putInt(NHT.nhtServerClose);
		byteBuffer.putInt(connectionID);
		
		connectClient.send(byteBuffer);
		
		//Removing the client
		removeClient(connectionID);
	}
	
	@Override
	public Collection<ClientSocket> getConnections() {
		return connectionList.values();
	}
	
	@Override
	public void handleConnect() {
		//Starting the timeout timer
		handshakeTimeoutTimer = new Timer();
		handshakeTimeoutTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				connectClient.close();
			}
		}, handshakeTimeout);
	}
	
	@Override
	public void handleDisconnect(int code, String reason) {
		//Cancelling the timeout timer
		if(handshakeTimeoutTimer != null) handshakeTimeoutTimer.cancel();
		
		//Mapping the code
		ServerState localError = switch(code) {
			case CloseFrame.NEVER_CONNECTED, CloseFrame.BUGGYCLOSE, CloseFrame.FLASHPOLICY, CloseFrame.ABNORMAL_CLOSE, CloseFrame.NORMAL -> ServerState.ERROR_INTERNET;
			case CloseFrame.PROTOCOL_ERROR -> ServerState.ERROR_CONN_BADREQUEST;
			case CloseFrame.POLICY_VALIDATION -> ServerState.ERROR_CONN_OUTDATED;
			case NHT.closeCodeAccountValidation -> ServerState.ERROR_CONN_VALIDATION;
			case NHT.closeCodeServerTokenRefresh -> ServerState.ERROR_CONN_TOKEN;
			case NHT.closeCodeNoSubscription -> ServerState.ERROR_CONN_SUBSCRIPTION;
			case NHT.closeCodeOtherLocation -> ServerState.ERROR_CONN_CONFLICT;
			default -> ServerState.ERROR_EXTERNAL;
		};
		
		//If there was a connection error, just try to reconnect later
		if(localError == ServerState.ERROR_INTERNET && !Main.isSetupMode()) {
			//Clearing connected clients
			connectionList.clear();
			
			//Notifying the listeners
			notifyPause(localError);
			
			//Scheduling the reconnection timer
			startReconnectionTimer();
		}
		//Otherwise, fail and let the user deal with the error
		else {
			notifyStop(localError);
		}
	}
	
	@Override
	public void processData(ByteBuffer bytes) {
		try {
			//Unpacking the message
			int type = bytes.getInt();
			
			switch(type) {
				case NHT.nhtConnectionOK -> {
					//Cancelling the timeout timer
					if(handshakeTimeoutTimer != null) handshakeTimeoutTimer.cancel();
					
					//Notifying the listeners that the connection is now good
					notifyStart();
					
					//Resetting the failed connection attempt counter
					disconnectReconnectAttempts = 0;
				}
				case NHT.nhtServerOpen -> {
					//Reading the data
					int connectionID = bytes.getInt();
					
					//Adding the connection
					addClient(connectionID);
				}
				case NHT.nhtServerClose -> {
					//Reading the data
					int connectionID = bytes.getInt();
					
					//Removing the connection
					removeClient(connectionID);
				}
				case NHT.nhtServerProxy -> {
					//Reading the data
					int connectionID = bytes.getInt();
					byte[] data = new byte[bytes.remaining()];
					bytes.get(data);
					
					//Getting the client
					ClientSocket client = connectionList.get(connectionID);
					
					//Disconnecting the client if it couldn't be found
					if(client == null) {
						disconnectClient(connectionID);
						return;
					}
					
					//Notifying the communications manager
					notifyMessage(client, data, true);
				}
			}
		} catch(BufferUnderflowException exception) {
			exception.printStackTrace();
		}
	}
	
	@Override
	public boolean requiresAuthentication() {
		return false;
	}
	
	@Override
	public boolean requiresPersistence() {
		return false;
	}
	
	private void startReconnectionTimer() {
		//Initializing the timer
		if(disconnectReconnectTimer == null) {
			disconnectReconnectTimer = new Timer();
		}
		
		//Wait an exponentially increasing wait period + a random delay
		int randomDelay = random.nextInt(1000);
		disconnectReconnectTimer.schedule(disconnectReconnectTimerTask, powerN(2, disconnectReconnectAttempts) * 1000 + randomDelay);
		
		//Adding to the attempt counter
		if(disconnectReconnectAttempts < disconnectReconnectMaxAttempts) {
			disconnectReconnectAttempts++;
		}
	}
	
	private void stopReconnectionTimer() {
		//Returning if there is no timer
		if(disconnectReconnectTimer == null) return;
		
		//Cancelling the timer
		disconnectReconnectTimer.cancel();
		disconnectReconnectTimer = null;
	}
	
	private static long powerN(long number, int power) {
		long res = 1;
		long sq = number;
		while(power > 0) {
			if(power % 2 == 1) res *= sq;
			sq = sq * sq;
			power /= 2;
		}
		return res;
	}
}