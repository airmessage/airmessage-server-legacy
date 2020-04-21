package me.tagavari.airmessageserver.connection;

public interface DataProxyListener<C> {
	/**
	 * Called when the proxy is started successfully
	 */
	void onStart();
	
	/**
	 * Called when the proxy is stopped
	 * (either as directed or due to an exception)
	 * @param code The error code
	 */
	void onStop(int code);
	
	/**
	 * Called when a new client is connected
	 * @param client The client that connected
	 */
	void onOpen(C client);
	
	/**
	 * Called when a client is disconnected
	 * @param client The client that disconnected
	 */
	void onClose(C client);
	
	/**
	 * Called when a message is received
	 * @param client The client that sent the message
	 * @param type The type of message
	 * @param content The message's body
	 */
	void onMessage(C client, int type, byte[] content);
}