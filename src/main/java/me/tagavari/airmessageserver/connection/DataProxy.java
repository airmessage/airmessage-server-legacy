package me.tagavari.airmessageserver.connection;

import me.tagavari.airmessageserver.server.Main;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public abstract class DataProxy<C extends ClientRegistration> {
	public static final int createServerResultOK = 0;
	public static final int createServerResultPort = 1;
	public static final int createServerResultInternal = 2;
	
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
	 * @param type The type of message
	 * @param content The message's body
	 */
	public void sendMessage(C client, int type, byte[] content) {
		sendMessage(client, type, content, null);
	}
	
	/**
	 * Sends a message to the specified client
	 * @param client A representation of the client object to send the data to
	 * @param type The type of message
	 * @param content The message's body
	 * @param sentRunnable A runnable to be executed when the message is sent
	 *                     Leave NULL to disable this functionality
	 *                     Please note that this runnable will be called on the writer thread!
	 */
	public abstract void sendMessage(C client, int type, byte[] content, Runnable sentRunnable);
	
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
	
	protected void notifyStop(int code) {
		for(DataProxyListener<C> messageListener : messageListenerSet) messageListener.onStop(code);
	}
	
	protected void notifyOpen(C client) {
		for(DataProxyListener<C> messageListener : messageListenerSet) messageListener.onOpen(client);
	}
	
	protected void notifyClose(C client) {
		for(DataProxyListener<C> messageListener : messageListenerSet) messageListener.onClose(client);
	}
	
	protected void notifyMessage(C client, int type, byte[] data) {
		for(DataProxyListener<C> messageListener : messageListenerSet) messageListener.onMessage(client, type, data);
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
	
	public static int createServerErrorToServerState(int value) {
		switch(value) {
			default:
				throw new IllegalArgumentException("Expected a create server result error; instead got " + value);
			case createServerResultPort:
				return Main.serverStateFailedServerPort;
			case createServerResultInternal:
				return Main.serverStateFailedServerInternal;
		}
	}
}