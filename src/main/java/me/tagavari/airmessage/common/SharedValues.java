package me.tagavari.airmessage.common;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;

public class SharedValues {
	public static final int mmCommunicationsVersion = 3;
	
	public static final String headerCommVer = "MMS-Comm-Version";
	public static final String headerSoftVersion = "MMS-Soft-Version";
	public static final String headerSoftVersionCode = "MMS-Soft-Version-Code";
	public static final String headerPassword = "Password";
	
	public static final int resultBadRequest = 4000;
	public static final int resultClientOutdated = 4001;
	public static final int resultServerOutdated = 4002;
	public static final int resultUnauthorized = 4003;
	
	public static final byte wsFrameUpdate = 0;
	public static final byte wsFrameTimeRetrieval = 1;
	public static final byte wsFrameMassRetrieval = 2;
	public static final byte wsFrameChatInfo = 3;
	public static final byte wsFrameModifierUpdate = 4;
	public static final byte wsFrameAttachmentReq = 5;
	public static final byte wsFrameAttachmentReqConfirmed = 6;
	public static final byte wsFrameAttachmentReqFailed = 7;
	
	public static final byte wsFrameSendResult = 100;
	public static final byte wsFrameSendTextExisting = 101;
	public static final byte wsFrameSendTextNew = 102;
	public static final byte wsFrameSendFileExisting = 103;
	public static final byte wsFrameSendFileNew = 104;
	
	//NHT = Net Header Type
	public static final int nhtClose = -1;
	public static final int nhtPing = -2;
	public static final int nhtPong = -3;
	public static final int nhtAuthentication = 0;
	public static final int nhtMessageUpdate = 1;
	public static final int nhtTimeRetrieval = 2;
	public static final int nhtMassRetrieval = 3;
	public static final int nhtConversationUpdate = 4;
	public static final int nhtModifierUpdate = 5;
	public static final int nhtAttachmentReq = 6;
	public static final int nhtAttachmentReqConfirm = 7;
	public static final int nhtAttachmentReqFail = 8;
	
	public static final int nhtSendResult = 100;
	public static final int nhtSendTextExisting = 101;
	public static final int nhtSendTextNew = 102;
	public static final int nhtSendFileExisting = 103;
	public static final int nhtSendFileNew = 104;
	
	public static final int nhtAuthenticationOK = 0;
	public static final int nhtAuthenticationUnauthorized = 1;
	public static final int nhtAuthenticationBadRequest = 2;
	public static final int nhtAuthenticationVersionMismatch = 3;
	
	public static final String hashAlgorithm = "MD5";
	
	public static class ConversationInfo implements Serializable {
		private static final long serialVersionUID = 100;
		
		public String guid;
		public boolean available;
		public String service;
		public String name;
		public ArrayList<String> members;
		
		//Conversation unavailable
		public ConversationInfo(String guid) {
			//Setting the values
			this.guid = guid;
			this.available = false;
			this.name = null;
			this.members = null;
		}
		
		//Conversation available
		public ConversationInfo(String guid, String service, String name, ArrayList<String> members) {
			//Setting the values
			this.guid = guid;
			this.available = true;
			this.service = service;
			this.name = name;
			this.members = members;
		}
		
		private void writeObject(ObjectOutputStream stream) throws IOException {
			//Writing the fields
			stream.writeUTF(guid);
			stream.writeBoolean(available);
			stream.writeUTF(service);
			stream.writeObject(name);
			stream.writeObject(members);
		}
	}
	
	public static abstract class ConversationItem implements Serializable, Cloneable {
		private static final long serialVersionUID = 101;
		
		public String guid;
		public String chatGuid;
		public long date;
		
		public ConversationItem(String guid, String chatGuid, long date) {
			this.guid = guid;
			this.chatGuid = chatGuid;
			this.date = date;
		}
	}
	
	public static class MessageInfo extends ConversationItem {
		private static final long serialVersionUID = 102;
		
		public static final int stateCodeGhost = 0;
		public static final int stateCodeIdle = 1;
		public static final int stateCodeSent = 2;
		public static final int stateCodeDelivered = 3;
		public static final int stateCodeRead = 4;
		
		public String text;
		public String sender;
		public ArrayList<AttachmentInfo> attachments;
		public ArrayList<StickerModifierInfo> stickers;
		public ArrayList<TapbackModifierInfo> tapbacks;
		public String sendEffect;
		public int stateCode;
		public int errorCode;
		public long dateRead;
		
		public MessageInfo(String guid, String chatGuid, long date, String text, String sender, ArrayList<AttachmentInfo> attachments, ArrayList<StickerModifierInfo> stickers, ArrayList<TapbackModifierInfo> tapbacks, String sendEffect, int stateCode, int errorCode, long dateRead) {
			//Calling the super constructor
			super(guid, chatGuid, date);
			
			//Setting the variables
			this.text = text;
			this.sender = sender;
			this.attachments = attachments;
			this.stickers = stickers;
			this.tapbacks = tapbacks;
			this.sendEffect = sendEffect;
			this.stateCode = stateCode;
			this.errorCode = errorCode;
			this.dateRead = dateRead;
		}
		
		private void writeObject(ObjectOutputStream stream) throws IOException {
			//Writing the fields
			stream.writeUTF(guid);
			stream.writeUTF(chatGuid);
			stream.writeLong(date);
			
			stream.writeObject(text);
			stream.writeObject(sender);
			stream.writeObject(attachments);
			stream.writeObject(stickers);
			stream.writeObject(tapbacks);
			stream.writeObject(sendEffect);
			stream.writeInt(stateCode);
			stream.writeInt(errorCode);
			stream.writeLong(dateRead);
		}
	}
	
	public static class AttachmentInfo implements Serializable {
		private static final long serialVersionUID = 103;
		public String guid;
		public String name;
		public String type;
		public byte[] checksum;
		
		public AttachmentInfo(String guid, String name, String type, byte[] checksum) {
			//Setting the variables
			this.guid = guid;
			this.name = name;
			this.type = type;
			this.checksum = checksum;
		}
		
		private void writeObject(ObjectOutputStream stream) throws IOException {
			stream.writeUTF(guid);
			stream.writeObject(name);
			stream.writeObject(type);
			stream.writeObject(checksum);
		}
	}
	
	public static class GroupActionInfo extends ConversationItem {
		private static final long serialVersionUID = 104;
		
		public String agent;
		public String other;
		public int groupActionType;
		
		public GroupActionInfo(String guid, String chatGuid, long date, String agent, String other, int groupActionType) {
			//Calling the super constructor
			super(guid, chatGuid, date);
			
			//Setting the variables
			this.agent = agent;
			this.other = other;
			this.groupActionType = groupActionType;
		}
		
		private void writeObject(ObjectOutputStream stream) throws IOException {
			//Writing the fields
			stream.writeUTF(guid);
			stream.writeUTF(chatGuid);
			stream.writeLong(date);
			
			stream.writeObject(agent);
			stream.writeObject(other);
			stream.writeInt(groupActionType);
		}
	}
	
	public static class ChatRenameActionInfo extends ConversationItem {
		private static final long serialVersionUID = 105;
		
		public String agent;
		public String newChatName;
		
		public ChatRenameActionInfo(String guid, String chatGuid, long date, String agent, String newChatName) {
			//Calling the super constructor
			super(guid, chatGuid, date);
			
			//Setting the variables
			this.agent = agent;
			this.newChatName = newChatName;
		}
		
		private void writeObject(ObjectOutputStream stream) throws IOException {
			//Writing the fields
			stream.writeUTF(guid);
			stream.writeUTF(chatGuid);
			stream.writeLong(date);
			
			stream.writeObject(agent);
			stream.writeObject(newChatName);
		}
	}
	
	public static abstract class ModifierInfo implements Serializable {
		private static final long serialVersionUID = 106;
		
		public String message;
		
		public ModifierInfo(String message) {
			this.message = message;
		}
	}
	
	public static class ActivityStatusModifierInfo extends ModifierInfo {
		private static final long serialVersionUID = 107;
		
		public int state;
		public long dateRead;
		
		public ActivityStatusModifierInfo(String message, int state, long dateRead) {
			//Calling the super constructor
			super(message);
			
			//Setting the values
			this.state = state;
			this.dateRead = dateRead;
		}
		
		private void writeObject(ObjectOutputStream stream) throws IOException {
			//Writing the fields
			stream.writeUTF(message);
			
			stream.writeInt(state);
			stream.writeLong(dateRead);
		}
	}
	
	public static class StickerModifierInfo extends ModifierInfo {
		private static final long serialVersionUID = 108;
		
		public int messageIndex;
		public String fileGuid;
		public String sender;
		public long date;
		public byte[] image;
		
		public StickerModifierInfo(String message, int messageIndex, String fileGuid, String sender, long date, byte[] image) {
			//Calling the super constructor
			super(message);
			
			//Setting the values
			this.messageIndex = messageIndex;
			this.fileGuid = fileGuid;
			this.sender = sender;
			this.date = date;
			this.image = image;
		}
		
		private void writeObject(ObjectOutputStream stream) throws IOException {
			//Writing the fields
			stream.writeUTF(message);
			
			stream.writeInt(messageIndex);
			stream.writeUTF(fileGuid);
			stream.writeObject(sender);
			stream.writeLong(date);
			stream.writeObject(image);
		}
	}
	
	public static class TapbackModifierInfo extends ModifierInfo {
		private static final long serialVersionUID = 109;
		
		//Creating the reference values
		public static final int tapbackBaseAdd = 2000;
		public static final int tapbackBaseRemove = 3000;
		public static final int tapbackLove = 0;
		public static final int tapbackLike = 1;
		public static final int tapbackDislike = 2;
		public static final int tapbackLaugh = 3;
		public static final int tapbackEmphasis = 4;
		public static final int tapbackQuestion = 5;
		
		public int messageIndex;
		public String sender;
		public int code;
		
		public TapbackModifierInfo(String message, int messageIndex, String sender, int code) {
			//Calling the super constructor
			super(message);
			
			//Setting the values
			this.messageIndex = messageIndex;
			this.sender = sender;
			this.code = code;
		}
		
		private void writeObject(ObjectOutputStream stream) throws IOException {
			//Writing the fields
			stream.writeUTF(message);
			
			stream.writeInt(messageIndex);
			stream.writeObject(sender);
			stream.writeInt(code);
		}
	}
	
	/* public static byte[] compress(byte[] data, int length) {
		Deflater compressor = new Deflater();
		compressor.setInput(data, 0, length);
		compressor.finish();
		byte[] compressedData = new byte[length];
		int compressedLen = compressor.deflate(compressedData);
		compressor.end();
		return Arrays.copyOf(compressedData, compressedLen);
	}
	
	public static byte[] decompress(byte[] data) throws IOException {
		Inflater inflater = new Inflater();
		inflater.setInput(data);
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream(data.length);
		byte[] buffer = new byte[1024];
		while(!inflater.finished()) {
			int count = inflater.inflate(buffer);
			outputStream.write(buffer, 0, count);
		}
		outputStream.close();
		return outputStream.toByteArray();
	} */
}