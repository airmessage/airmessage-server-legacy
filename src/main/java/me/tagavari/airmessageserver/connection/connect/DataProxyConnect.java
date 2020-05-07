package me.tagavari.airmessageserver.connection.connect;

import com.sun.java.accessibility.util.AccessibilityListenerList;
import me.tagavari.airmessageserver.connection.DataProxy;
import me.tagavari.airmessageserver.server.ServerState;
import org.java_websocket.framing.CloseFrame;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.*;

public class DataProxyConnect extends DataProxy<ClientSocket> implements ConnectionListener {
	private static final long handshakeTimeout = 8 * 1000;
	
	//Creating the state values
	private final Map<Integer, ClientSocket> connectionList = Collections.synchronizedMap(new HashMap<>());
	private ConnectWebSocketClient connectClient;
	
	private final boolean connectRegistration;
	private final String connectIDToken;
	private final String connectUserID;
	
	private Timer handshakeTimeoutTimer;
	
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
		
		//Notifying of the error
		notifyStop(localError);
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
}