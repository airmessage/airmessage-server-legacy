package me.tagavari.airmessageserver.connection.direct;

public interface ReaderThreadListener {
	/**
	 * Called when a new message is received
	 * @param type The type of this message
	 * @param data This message's body content
	 */
	void processData(int type, byte[] data);
	
	/**
	 * Called when an exception occurs in the connection, and the connection must be killed
	 * @param cleanup TRUE if this connection should be closed gracefully, notifying the receiving party
	 */
	void cancelConnection(boolean cleanup);
}