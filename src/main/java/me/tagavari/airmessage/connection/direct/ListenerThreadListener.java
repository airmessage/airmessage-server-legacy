package me.tagavari.airmessage.connection.direct;

public interface ListenerThreadListener {
	/**
	 * Called when a new client connects
	 * @param client The client that issued this callback
	 */
	void acceptClient(ClientSocket client);
	
	/**
	 * Called when a new message is received
	 * @param client The client that issued this callback
	 * @param type The type of this message
	 * @param data This message's body content
	 */
	void processData(ClientSocket client, int type, byte[] data);
	
	/**
	 * Called when an exception occurs in the connection, and the connection must be killed
	 * @param client The client that issued this callback
	 * @param cleanup TRUE if this connection should be closed gracefully, notifying the receiving party
	 */
	void cancelConnection(ClientSocket client, boolean cleanup);
}