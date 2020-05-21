package me.tagavari.airmessageserver.connection;

import me.tagavari.airmessageserver.server.ServerState;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public abstract class DataProxy<C extends ClientRegistration> {
	//Error codes
	//General
	public static final int createServerResultOK = 0;
	public static final int createServerResultInternal = 1;
	public static final int createServerResultExternal = 2;
	//TCP server
	public static final int createServerResultServerPort = 100;
	//Connect relay
	public static final int createServerResultConnectTokenRefresh = 200;
	public static final int createServerResultConnectNoSubscription = 201;
	public static final int createServerResultConnectOtherLocation = 202;
	
	private final Set<DataProxyListener<C>> messageListenerSet = new HashSet<>(1);
	
	/**
	 * Starts this server, allowing it to accept incoming connections
	 */
	public abstract void startServer();
	
	/**
	 * Stops the server, disconnecting all connected clients
	 */
	public abstract void stopServer();
	
	/**
	 * Sends a message to the specified client
	 * @param client A representation of the client object to send the data to
	 * @param content The message's body
	 * @param encrypt Whether or not this message should be encrypted
	 */
	public void sendMessage(C client, byte[] content, boolean encrypt) {
		sendMessage(client, content, encrypt, null);
	}
	
	/**
	 * Sends a message to the specified client
	 * @param client A representation of the client object to send the data to
	 * @param content The message's body
	 * @param encrypt Whether or not this message should be encrypted
	 * @param sentRunnable A runnable to be executed when the message is sent
	 *                     Leave NULL to disable this functionality
	 *                     Please note that this runnable will be called on the writer thread!
	 */
	public abstract void sendMessage(C client, byte[] content, boolean encrypt, Runnable sentRunnable);
	
	/**
	 * Disconnects a client from this server
	 * @param client The client to disconnect
	 */
	public abstract void disconnectClient(C client);
	
	/**
	 * Gets a list of currently connected clients
	 * @return List of currently connected clients
	 */
	public abstract Collection<C> getConnections();
	
	protected void notifyStart() {
		for(DataProxyListener<C> messageListener : messageListenerSet) messageListener.onStart();
	}
	
	protected void notifyPause(ServerState code) {
		for(DataProxyListener<C> messageListener : messageListenerSet) messageListener.onPause(code);
	}
	
	protected void notifyStop(ServerState code) {
		for(DataProxyListener<C> messageListener : messageListenerSet) messageListener.onStop(code);
	}
	
	protected void notifyOpen(C client) {
		for(DataProxyListener<C> messageListener : messageListenerSet) messageListener.onOpen(client);
	}
	
	protected void notifyClose(C client) {
		for(DataProxyListener<C> messageListener : messageListenerSet) messageListener.onClose(client);
	}
	
	protected void notifyMessage(C client, byte[] data, boolean wasEncrypted) {
		for(DataProxyListener<C> messageListener : messageListenerSet) messageListener.onMessage(client, data, wasEncrypted);
	}
	
	/**
	 * Adds a listener to be notified upon incoming messages
	 * @param messageListener The message listener to add
	 */
	public void addMessageListener(DataProxyListener<C> messageListener) {
		messageListenerSet.add(messageListener);
	}
	
	/**
	 * Removes a listener to be notified upon incoming messages
	 * @param messageListener The message listener to remove
	 */
	public void removeMessageListener(DataProxyListener<C> messageListener) {
		messageListenerSet.remove(messageListener);
	}
	
	/**
	 * Called to check whether this data proxy requires authenticating connecting clients
	 * @return TRUE if this proxy requires authentication
	 */
	public abstract boolean requiresAuthentication();
	
	/**
	 * Checks if this current communications setup should be kept alive
	 * @return TRUE if persistence should be enabled
	 */
	public abstract boolean requiresPersistence();
}