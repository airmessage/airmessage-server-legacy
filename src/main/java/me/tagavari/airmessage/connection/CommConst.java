package me.tagavari.airmessage.connection;

public class CommConst {
	//Transmission header values
	public static final int mmCommunicationsVersion = 4;
	public static final int mmCommunicationsSubVersion = 6;
	
	//NHT - Net header type
	public static final int nhtClose = -1;
	public static final int nhtPing = -2;
	public static final int nhtPong = -3;
	public static final int nhtInformation = 0;
	public static final int nhtAuthentication = 1;
	public static final int nhtMessageUpdate = 2;
	public static final int nhtTimeRetrieval = 3;
	public static final int nhtMassRetrieval = 4;
	public static final int nhtMassRetrievalFinish = 10;
	public static final int nhtMassRetrievalFile = 11;
	public static final int nhtConversationUpdate = 5;
	public static final int nhtModifierUpdate = 6;
	public static final int nhtAttachmentReq = 7;
	public static final int nhtAttachmentReqConfirm = 8;
	public static final int nhtAttachmentReqFail = 9;
	public static final int nhtCreateChat = 12;
	
	public static final int nhtSendResult = 100;
	public static final int nhtSendTextExisting = 101;
	public static final int nhtSendTextNew = 102;
	public static final int nhtSendFileExisting = 103;
	public static final int nhtSendFileNew = 104;
	
	public static final String stringCharset = "UTF-8";
	public static final String hashAlgorithm = "MD5";
	@Deprecated
	public static final String transmissionCheck = "4yAIlVK0Ce_Y7nv6at_hvgsFtaMq!lZYKipV40Fp5E%VSsLSML";
	
	//NST - Net subtype
	public static final int nstAuthenticationOK = 0;
	public static final int nstAuthenticationUnauthorized = 1;
	public static final int nstAuthenticationBadRequest = 2;
	
	public static final int nstSendResultOK = 0;
	public static final int nstSendResultScriptError = 1; //Some unknown AppleScript error
	public static final int nstSendResultBadRequest = 2; //Invalid data received
	public static final int nstSendResultUnauthorized = 3; //System rejected request to send message
	public static final int nstSendResultNoConversation = 4; //A valid conversation wasn't found
	public static final int nstSendResultRequestTimeout = 5; //File data blocks stopped being received
	
	public static final int nstAttachmentReqNotFound = 1; //File GUID not found
	public static final int nstAttachmentReqNotSaved = 2; //File (on disk) not found
	public static final int nstAttachmentReqUnreadable = 3; //No access to file
	public static final int nstAttachmentReqIO = 4; //IO error
	
	public static final int nstCreateChatOK = 0;
	public static final int nstCreateChatScriptError = 1; //Some unknown AppleScript error
	public static final int nstCreateChatBadRequest = 2; //Invalid data received
	public static final int nstCreateChatUnauthorized = 3; //System rejected request to send message
	
	//Timeouts
	public static final long handshakeTimeout = 10 * 1000; //10 seconds
	public static final long pingTimeout = 30 * 1000; //30 seconds
	public static final long keepAliveMillis = 30 * 60 * 1000; //30 minutes
	
	public static final long maxPacketAllocation = 50 * 1024 * 1024; //50 MB
}