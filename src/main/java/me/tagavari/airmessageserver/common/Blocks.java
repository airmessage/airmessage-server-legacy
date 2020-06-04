package me.tagavari.airmessageserver.common;

import org.msgpack.core.MessageBufferPacker;

import java.io.IOException;
import java.util.List;

public class Blocks {
	public interface Block {
		void writeObject(MessageBufferPacker packer) throws IOException;
	}
	
	public static class ConversationInfo implements Block {
		public String guid;
		public boolean available;
		public String service;
		public String name;
		public String[] members;
		
		//Conversation unavailable
		public ConversationInfo(String guid) {
			//Setting the values
			this.guid = guid;
			this.available = false;
			this.service = null;
			this.name = null;
			this.members = null;
		}
		
		//Conversation available
		public ConversationInfo(String guid, String service, String name, String[] members) {
			//Setting the values
			this.guid = guid;
			this.available = true;
			this.service = service;
			this.name = name;
			this.members = members;
		}
		
		@Override
		public void writeObject(MessageBufferPacker packer) throws IOException {
			//Writing the fields
			packer.packString(guid);
			packer.packBoolean(available);
			if(available) {
				packer.packString(service);
				packString(packer, name);
				packer.packArrayHeader(members.length);
				for(String member : members) packer.packString(member);
			}
		}
	}
	
	public static abstract class ConversationItem implements Block {
		public long serverID;
		public String guid;
		public String chatGuid;
		public long date;
		
		public ConversationItem(long serverID, String guid, String chatGuid, long date) {
			this.serverID = serverID;
			this.guid = guid;
			this.chatGuid = chatGuid;
			this.date = date;
		}
		
		@Override
		public void writeObject(MessageBufferPacker packer) throws IOException {
			packer.packInt(getItemType());
			
			packer.packLong(serverID);
			packer.packString(guid);
			packer.packString(chatGuid);
			packer.packLong(date);
		}
		
		abstract int getItemType();
	}
	
	public static class MessageInfo extends ConversationItem {
		private static final int itemType = 0;
		
		public static final int stateCodeIdle = 0;
		public static final int stateCodeSent = 1;
		public static final int stateCodeDelivered = 2;
		public static final int stateCodeRead = 3;
		
		public static final int errorCodeOK = 0;
		public static final int errorCodeUnknown = 1; //Unknown error code
		public static final int errorCodeNetwork = 2; //Network error
		public static final int errorCodeUnregistered = 3; //Not registered with iMessage
		
		public String text;
		public String subject;
		public String sender;
		public List<AttachmentInfo> attachments;
		public List<StickerModifierInfo> stickers;
		public List<TapbackModifierInfo> tapbacks;
		public String sendEffect;
		public int stateCode;
		public int errorCode;
		public long dateRead;
		
		public MessageInfo(long serverID, String guid, String chatGuid, long date, String text, String subject, String sender, List<AttachmentInfo> attachments, List<StickerModifierInfo> stickers, List<TapbackModifierInfo> tapbacks, String sendEffect, int stateCode, int errorCode, long dateRead) {
			//Calling the super constructor
			super(serverID, guid, chatGuid, date);
			
			//Setting the variables
			this.text = text;
			this.subject = subject;
			this.sender = sender;
			this.attachments = attachments;
			this.stickers = stickers;
			this.tapbacks = tapbacks;
			this.sendEffect = sendEffect;
			this.stateCode = stateCode;
			this.errorCode = errorCode;
			this.dateRead = dateRead;
		}
		
		@Override
		public void writeObject(MessageBufferPacker packer) throws IOException {
			//Writing the fields
			super.writeObject(packer);
			
			packString(packer, text);
			packString(packer, subject);
			packString(packer, sender);
			packer.packArrayHeader(attachments.size());
			for(AttachmentInfo item : attachments) item.writeObject(packer);
			packer.packArrayHeader(stickers.size());
			for(StickerModifierInfo item : stickers) item.writeObject(packer);
			packer.packArrayHeader(tapbacks.size());
			for(TapbackModifierInfo item : tapbacks) item.writeObject(packer);
			packString(packer, sendEffect);
			packer.packInt(stateCode);
			packer.packInt(errorCode);
			packer.packLong(dateRead);
		}
		
		@Override
		int getItemType() {
			return itemType;
		}
	}
	
	public static class GroupActionInfo extends ConversationItem {
		private static final int itemType = 1;
		
		public static final int subtypeUnknown = 0;
		public static final int subtypeJoin = 1;
		public static final int subtypeLeave = 2;
		
		public String agent;
		public String other;
		public int groupActionType;
		
		public GroupActionInfo(long serverID, String guid, String chatGuid, long date, String agent, String other, int groupActionType) {
			//Calling the super constructor
			super(serverID, guid, chatGuid, date);
			
			//Setting the variables
			this.agent = agent;
			this.other = other;
			this.groupActionType = groupActionType;
		}
		
		@Override
		public void writeObject(MessageBufferPacker packer) throws IOException {
			//Writing the fields
			super.writeObject(packer);
			
			packString(packer, agent);
			packString(packer, other);
			packer.packInt(groupActionType);
		}
		
		@Override
		int getItemType() {
			return itemType;
		}
	}
	
	public static class ChatRenameActionInfo extends ConversationItem {
		private static final int itemType = 2;
		
		public String agent;
		public String newChatName;
		
		public ChatRenameActionInfo(long serverID, String guid, String chatGuid, long date, String agent, String newChatName) {
			//Calling the super constructor
			super(serverID, guid, chatGuid, date);
			
			//Setting the variables
			this.agent = agent;
			this.newChatName = newChatName;
		}
		
		@Override
		public void writeObject(MessageBufferPacker packer) throws IOException {
			//Writing the fields
			super.writeObject(packer);
			
			packString(packer, agent);
			packString(packer, newChatName);
		}
		
		@Override
		int getItemType() {
			return itemType;
		}
	}
	
	public static class AttachmentInfo implements Block {
		public String guid;
		public String name;
		public String type;
		public long size;
		public byte[] checksum;
		
		public AttachmentInfo(String guid, String name, String type, long size, byte[] checksum) {
			//Setting the variables
			this.guid = guid;
			this.name = name;
			this.type = type;
			this.size = size;
			this.checksum = checksum;
		}
		
		@Override
		public void writeObject(MessageBufferPacker packer) throws IOException {
			packer.packString(guid);
			packer.packString(name);
			packString(packer, type);
			packer.packLong(size);
			if(checksum == null) packer.packNil();
			else {
				packer.packBinaryHeader(checksum.length);
				packer.addPayload(checksum);
			}
		}
	}
	
	public static abstract class ModifierInfo implements Block {
		public String message;
		
		public ModifierInfo(String message) {
			this.message = message;
		}
		
		@Override
		public void writeObject(MessageBufferPacker packer) throws IOException {
			packer.packInt(getItemType());
			
			packer.packString(message);
		}
		
		abstract int getItemType();
	}
	
	public static class ActivityStatusModifierInfo extends ModifierInfo {
		private static final int itemType = 0;
		
		public int state;
		public long dateRead;
		
		public ActivityStatusModifierInfo(String message, int state, long dateRead) {
			//Calling the super constructor
			super(message);
			
			//Setting the values
			this.state = state;
			this.dateRead = dateRead;
		}
		
		public void writeObject(MessageBufferPacker packer) throws IOException {
			//Writing the fields
			super.writeObject(packer);
			
			packer.packInt(state);
			packer.packLong(dateRead);
		}
		
		@Override
		int getItemType() {
			return itemType;
		}
	}
	
	public static class StickerModifierInfo extends ModifierInfo {
		private static final int itemType = 1;
		
		public int messageIndex;
		public String fileGuid;
		public String sender;
		public long date;
		public byte[] data;
		public String type;
		
		public StickerModifierInfo(String message, int messageIndex, String fileGuid, String sender, long date, byte[] data, String type) {
			//Calling the super constructor
			super(message);
			
			//Setting the values
			this.messageIndex = messageIndex;
			this.fileGuid = fileGuid;
			this.sender = sender;
			this.date = date;
			this.data = data;
			this.type = type;
		}
		
		@Override
		public void writeObject(MessageBufferPacker packer) throws IOException {
			//Writing the fields
			super.writeObject(packer);
			
			packer.packInt(messageIndex);
			packer.packString(fileGuid);
			packString(packer, sender);
			packer.packLong(date);
			packer.packBinaryHeader(data.length);
			packer.addPayload(data);
			packer.packString(type);
		}
		
		@Override
		int getItemType() {
			return itemType;
		}
	}
	
	public static class TapbackModifierInfo extends ModifierInfo {
		private static final int itemType = 2;
		
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
		
		@Override
		public void writeObject(MessageBufferPacker packer) throws IOException {
			//Writing the fields
			super.writeObject(packer);
			
			packer.packInt(messageIndex);
			packString(packer, sender);
			packer.packInt(code);
		}
		
		@Override
		int getItemType() {
			return itemType;
		}
	}
	
	private static void packString(MessageBufferPacker packer, String string) throws IOException {
		if(string == null) packer.packNil();
		else packer.packString(string);
	}
}