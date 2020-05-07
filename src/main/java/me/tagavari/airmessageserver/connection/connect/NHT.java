package me.tagavari.airmessageserver.connection.connect;

class NHT {
	//AirMessage Connect communications version
	static final int commVer = 1;
	
	//Shared het header types
	/*
	The connected device has been connected successfully
	 */
	static final int nhtConnectionOK = 0;
	
	//Client-only net header types
	
	/*
	Proxy the message to the server (client -> connect)
	
	payload - data
	 */
	static final int nhtClientProxy = 100;
	
	//Server-only net header types
	
	/*
	Notify a new client connection (connect -> server)
	
	int - connection ID
	 */
	static final int nhtServerOpen = 200;
	
	/*
	Close a connected client (server -> connect)
	Notify a closed connection (connect -> server)
	
	int - connection ID
	 */
	static final int nhtServerClose = 201;
	
	/*
	Proxy the message to the client (server -> connect)
	Receive data from a connected client (connect -> server)
	
	int - connection ID
	payload - data
	 */
	static final int nhtServerProxy = 210;
	
	/*
	Proxy the message to all connected clients (server -> connect)
	
	payload - data
	 */
	static final int nhtServerProxyBroadcast = 211;
	
	/*
	Notify a client over FCM (server -> connect)
	
	int - connection ID
	 */
	static final int nhtServerNotify = 211;
	
	//Disconnection codes
	static final int closeCodeNoGroup = 4000; //There is no active group with a matching ID
	static final int closeCodeNoCapacity = 4001; //The client's group is at capacity
	static final int closeCodeAccountValidation = 4002; //This account couldn't be validated
	static final int closeCodeServerTokenRefresh = 4003; //The server's provided installation ID is out of date; log in again to re-link this device
	static final int closeCodeNoSubscription = 4004; //This user does not have an active subscription
	static final int closeCodeOtherLocation = 4005; //Logged in from another location
}