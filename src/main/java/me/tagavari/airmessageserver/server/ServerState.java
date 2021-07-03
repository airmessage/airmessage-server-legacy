package me.tagavari.airmessageserver.server;

public enum ServerState {
	SETUP("message.status.waiting", null, Constants.typeStatus),
	STARTING("message.status.starting", null, Constants.typeStatus),
	CONNECTING("message.status.connecting", null, Constants.typeStatus),
	RUNNING("message.status.running", null, Constants.typeStatus),
	STOPPED("message.status.stopped", null, Constants.typeStatus),
	
	ERROR_DATABASE("message.status.error.database", "message.disk_access_error", Constants.typeError), //Couldn't connect to database
	
	ERROR_INTERNAL("message.status.error.internal", "message.error.connect.internal", Constants.typeError), //Internal error
	ERROR_EXTERNAL("message.status.error.external", "message.error.connect.external", Constants.typeError), //External error
	ERROR_INTERNET("message.status.error.internet", "message.error.connect.internet", Constants.typeErrorRecoverable), //No internet connection
	
	ERROR_TCP_PORT("message.status.error.port", null, Constants.typeError), //Port unavailable
	
	ERROR_CONN_BADREQUEST("message.status.error.bad_request", "message.error.connect.bad_request", Constants.typeError), //Bad request
	ERROR_CONN_OUTDATED("message.status.error.outdated", "message.error.connect.outdated", Constants.typeError), //Client out of date
	ERROR_CONN_VALIDATION("message.status.error.account_validation", "message.error.connect.account_validation", Constants.typeError), //Account access not valid
	ERROR_CONN_TOKEN("message.status.error.token_refresh", "message.error.connect.token_refresh", Constants.typeError), //Token refresh
	ERROR_CONN_ACTIVATION("message.status.error.no_activation", "message.error.connect.no_activation", Constants.typeError), //Not activated
	ERROR_CONN_CONFLICT("message.status.error.account_conflict", "message.error.connect.account_conflict", Constants.typeError); //Logged in from another location
	
	public final String messageID;
	public final String messageIDLong;
	public final int type;
	
	ServerState(String messageID, String messageIDLong, int type) {
		this.messageID = messageID;
		this.messageIDLong = messageIDLong;
		this.type = type;
	}
	
	public static class Constants {
		public static final int typeStatus = 0;
		public static final int typeError = 1;
		public static final int typeErrorRecoverable = 2;
	}
}