package me.tagavari.airmessage.common;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.spec.KeySpec;

public class SharedValues {
	/* public static class ConversationInfo implements Serializable {
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
			this.service = null;
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
	} */
	
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
	
	public static class EncryptableData implements Serializable {
		//Creating the reference values
		private static final int saltLen = 8; //8 bytes
		private static final int ivLen = 12; //12 bytes (instead of 16 because of GCM)
		private static final String keyFactoryAlgorithm = "PBKDF2WithHmacSHA256";
		private static final String keyAlgorithm = "AES";
		private static final String cipherTransformation = "AES/GCM/NoPadding";
		private static final int keyIterationCount = 10000;
		private static final int keyLength = 128; //128 bits
		
		//private static final long serialVersionUID = 0;
		private byte[] salt;
		private byte[] iv;
		public byte[] data;
		private transient boolean dataEncrypted = false;
		
		
		public EncryptableData(byte[] data) {
			this.data = data;
		}
		
		public EncryptableData encrypt(String password) throws ClassCastException, GeneralSecurityException {
			//Creating a secure random
			SecureRandom random = new SecureRandom();
			
			//Generating a salt
			salt = new byte[saltLen];
			random.nextBytes(salt);
			
			//Creating the key
			SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance(keyFactoryAlgorithm);
			KeySpec keySpec = new PBEKeySpec(password.toCharArray(), salt, 10000, keyLength);
			SecretKey secretKey = secretKeyFactory.generateSecret(keySpec);
			SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getEncoded(), keyAlgorithm);
			
			//Generating the IV
			iv = new byte[ivLen];
			random.nextBytes(iv);
			GCMParameterSpec gcmSpec = new GCMParameterSpec(keyLength, iv);
			
			Cipher cipher = Cipher.getInstance(cipherTransformation);
			cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, gcmSpec);
			
			//Encrypting the data
			data = cipher.doFinal(data);
			dataEncrypted = true;
			
			//Returning the object
			return this;
		}
		
		public EncryptableData decrypt(String password) throws ClassCastException, GeneralSecurityException {
			//Creating the key
			SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance(keyFactoryAlgorithm);
			KeySpec keySpec = new PBEKeySpec(password.toCharArray(), salt, keyIterationCount, keyLength);
			SecretKey secretKey = secretKeyFactory.generateSecret(keySpec);
			SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getEncoded(), keyAlgorithm);
			
			//Creating the IV
			GCMParameterSpec gcmSpec = new GCMParameterSpec(keyLength, iv);
			
			//Creating the cipher
			Cipher cipher = Cipher.getInstance(cipherTransformation);
			cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, gcmSpec);
			
			//Deciphering the data
			data = cipher.doFinal(data);
			dataEncrypted = false;
			
			//Returning the object
			return this;
		}
		
		private void writeObject(ObjectOutputStream stream) throws IOException {
			//Throwing an exception if the data isn't encrypted
			if(!dataEncrypted) throw new RuntimeException("Data serialization attempt before encryption!");
			
			//Writing the data
			stream.write(salt);
			stream.write(iv);
			stream.writeInt(data.length);
			stream.write(data);
		}
		
		private void readObject(ObjectInputStream stream) throws IOException {
			//Reading the data
			salt = new byte[saltLen];
			stream.readFully(salt);
			
			iv = new byte[ivLen];
			stream.readFully(iv);
			
			data = new byte[stream.readInt()];
			stream.readFully(data);
		}
	}
}