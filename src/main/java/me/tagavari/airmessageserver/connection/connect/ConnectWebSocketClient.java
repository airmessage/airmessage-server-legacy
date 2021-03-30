package me.tagavari.airmessageserver.connection.connect;

import io.sentry.Sentry;
import me.tagavari.airmessageserver.server.Main;
import me.tagavari.airmessageserver.server.PreferencesManager;
import org.java_websocket.WebSocket;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.exceptions.InvalidDataException;
import org.java_websocket.exceptions.WebsocketNotConnectedException;
import org.java_websocket.framing.CloseFrame;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.ServerHandshake;

import javax.net.ssl.*;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

class ConnectWebSocketClient extends WebSocketClient {
	//Creating the constants
	private static final String connectHostname = "wss://connect.airmessage.org";
	//private static final String connectHostname = "ws://localhost:1259";
	private static final int connectTimeout = 8 * 1000; //8 seconds
	
	//Creating the callbacks
	private final ConnectionListener connectionListener;
	
	static ConnectWebSocketClient createInstanceRegister(String idToken, ConnectionListener connectionListener) {
		Map<String, String> headers = new HashMap<>();
		headers.put("Origin", "app");
		
		String query = new QueryBuilder()
				.with("communications", NHT.commVer)
				.with("is_server", true)
				.with("installation_id", PreferencesManager.getInstallationID())
				.with("id_token", idToken)
				.toString();
		
		try {
			return new ConnectWebSocketClient(new URI(connectHostname + "?" + query), headers, connectTimeout, connectionListener);
		} catch(URISyntaxException exception) {
			Sentry.captureException(exception);
			Main.getLogger().log(Level.SEVERE, exception.getMessage(), exception);
			return null;
		}
	}
	
	static ConnectWebSocketClient createInstanceExisting(String userID, ConnectionListener connectionListener) {
		Map<String, String> headers = new HashMap<>();
		headers.put("Origin", "app");
		
		String query = new QueryBuilder()
				.with("communications", NHT.commVer)
				.with("is_server", true)
				.with("installation_id", PreferencesManager.getInstallationID())
				.with("user_id", userID)
				.toString();
		
		try {
			return new ConnectWebSocketClient(new URI(connectHostname + "?" + query), headers, connectTimeout, connectionListener);
		} catch(URISyntaxException exception) {
			Sentry.captureException(exception);
			Main.getLogger().log(Level.SEVERE, exception.getMessage(), exception);
			return null;
		}
	}
	
	public ConnectWebSocketClient(URI serverUri, Map<String, String> httpHeaders, int connectTimeout, ConnectionListener connectionListener) {
		super(serverUri, new Draft_6455(), httpHeaders, connectTimeout);
		
		this.connectionListener = connectionListener;
		
		setConnectionLostTimeout(10 * 60); //Every 10 mins
	}
	
	/**
	 * Attempts to send data to this client,
	 * and returns instead of throwing an exception
	 * @param data The data to send
	 * @return TRUE if this message was sent
	 */
	public boolean sendSafe(byte[] data) {
		try {
			send(data);
		} catch(WebsocketNotConnectedException exception) {
			Main.getLogger().log(Level.WARNING, exception.getMessage(), exception);
			return false;
		}
		
		return true;
	}
	
	@Override
	public void onOpen(ServerHandshake handshakeData) {
		Main.getLogger().log(Level.INFO, "Connection to Connect relay opened");
		connectionListener.handleConnect();
	}
	
	@Override
	public void onMessage(ByteBuffer bytes) {
		connectionListener.processData(bytes);
	}
	
	@Override
	public void onMessage(String message) {
	
	}
	
	@Override
	public void onClose(int code, String reason, boolean remote) {
		Main.getLogger().log(Level.INFO,  "Connection to Connect relay lost: " + code + " / " + reason);
		connectionListener.handleDisconnect(code, reason);
	}
	
	@Override
	public void onError(Exception exception) {
		Main.getLogger().log(Level.WARNING, exception.getMessage(), exception);
	}
}