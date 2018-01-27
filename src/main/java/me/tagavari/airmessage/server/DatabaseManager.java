package me.tagavari.airmessage.server;

import me.tagavari.airmessage.common.SharedValues;
import org.java_websocket.WebSocket;
import org.jooq.*;
import org.jooq.impl.DSL;

import java.io.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

class DatabaseManager implements Runnable {
	//Creating the reference variables
	private static final long checkTime = 5 * 1000;
	private static final long pollTime = 100;
	private static final String databaseLocation = "jdbc:sqlite:" + System.getProperty("user.home") + "/Library/Messages/chat.db";
	/* private static final ArrayList<String> validServices = new ArrayList<String>() {{
		add("iMessage");
		add("SMS");
	}}; */
	
	private static final RetrievalFilter timeRetrievalFilter = new RetrievalFilter() {
		@Override
		Condition filter() {
			return DSL.field("message.date").greaterThan(connectFetchTime);
		}
	};
	
	private static final RetrievalFilter identifierRetrievalFilter = new RetrievalFilter() {
		@Override
		Condition filter() {
			return DSL.field("message.ROWID").greaterThan(latestEntryID);
		}
	};
	
	//Creating the support variables
	private boolean dbSupportsSendStyle = false;
	private boolean dbSupportsAssociation = false;
	
	//Creating the other variables
	//private static long lastFetchTime;
	private static long connectFetchTime;
	private static long latestEntryID = -1;
	
	private static Thread thread = null;
	private static Connection connection;
	static final ArrayList<Object> databaseRequests = new ArrayList<>();
	
	private HashMap<String, Integer> messageStates = new HashMap<>();
	
	static boolean start() {
		//Checking if there is already an instance
		if(thread != null) {
			//Logging the exception
			Main.getLogger().severe("Instance of database manager already exists");
			
			//Returning false
			return false;
		}
		
		//Connecting to the database
		try {
			connection = DriverManager.getConnection(databaseLocation);
		} catch(SQLException exception) {
			//Logging a message
			Main.getLogger().severe("Failed to connect to chat message database: " + exception.getMessage());
			Main.getLogger().severe("No incoming messages will be received");
			
			//Returning false
			return false;
		}
		
		//Getting the time variables
		Main.getLogger().info("Using time system " + Main.getTimeHelper().toString() + " with current time " + System.currentTimeMillis() + " -> " + Main.getTimeHelper().toDatabaseTime(System.currentTimeMillis()));
		connectFetchTime = Main.getTimeHelper().toDatabaseTime(System.currentTimeMillis());
		
		//Starting the thread
		thread = new Thread(new DatabaseManager());
		thread.start();
		
		//Returning true
		return true;
	}
	
	static void stop() {
		//Interrupting the thread
		thread.interrupt();
	}
	
	@Override
	public void run() {
		//Creating the message array variable
		DataFetchResult dataFetchResult = null;
		
		//Creating the time variables
		long lastCheckTime = System.currentTimeMillis() - 5 * 1000;
		
		//Reading the schema
		try {
			//Checking if the DB supports send styles
			ResultSet resultSet = connection.getMetaData().getColumns(null, null, "message", "expressive_send_style_id");
			dbSupportsSendStyle = resultSet.next();
			resultSet.close();
			
			//Checking if the DB supports association (tapback & stickers)
			resultSet = connection.getMetaData().getColumns(null, null, "message", "associated_message_guid");
			dbSupportsAssociation = resultSet.next();
			resultSet.close();
		} catch(SQLException exception) {
			//Printing the stack trace
			exception.printStackTrace();
		}
		
		//Looping until the thread is interrupted
		while(!thread.isInterrupted()) {
			try {
				//Looping while 5 seconds has not passed
				while(System.currentTimeMillis() - lastCheckTime < checkTime) {
					//Sleeping for the poll time
					Thread.sleep(pollTime);
					
					//Creating the queued request lists
					ArrayList<ConversationInfoRequest> queuedConversationRequests = new ArrayList<>();
					ArrayList<FileRequest> queuedFileRequests = new ArrayList<>();
					ArrayList<CustomRetrievalRequest> queuedCustomRetrievalRequests = new ArrayList<>();
					ArrayList<MassRetrievalRequest> queuedMassRetrievalRequests = new ArrayList<>();
					
					//Locking the conversation info requests
					synchronized(databaseRequests) {
						//Checking if there is a request
						if(!databaseRequests.isEmpty()) {
							//Sorting the requests
							for(Object request : databaseRequests) {
								if(request instanceof ConversationInfoRequest)
									queuedConversationRequests.add((ConversationInfoRequest) request);
								else if(request instanceof FileRequest) queuedFileRequests.add((FileRequest) request);
								else if(request instanceof CustomRetrievalRequest) queuedCustomRetrievalRequests.add((CustomRetrievalRequest) request);
								else if(request instanceof MassRetrievalRequest) queuedMassRetrievalRequests.add((MassRetrievalRequest) request);
							}
							
							//Clearing the requests
							databaseRequests.clear();
						}
					}
					
					//Fulfilling the queued requests (if there are any)
					if(!queuedConversationRequests.isEmpty()) fulfillConversationRequests(queuedConversationRequests);
					if(!queuedFileRequests.isEmpty()) fulfillFileRequests(queuedFileRequests);
					if(!queuedCustomRetrievalRequests.isEmpty()) fulfillCustomRetrievalRequests(queuedCustomRetrievalRequests);
					if(!queuedMassRetrievalRequests.isEmpty()) fulfillMassRetrievalRequests(queuedMassRetrievalRequests);
				}
				
				//Fetching new messages
				dataFetchResult = fetchData(latestEntryID == -1 ? timeRetrievalFilter : identifierRetrievalFilter);
				
				//Updating the message states
				updateMessageStates(dataFetchResult.isolatedModifiers);
				
				//Updating the latest message ID
				if(dataFetchResult.latestMessageID > latestEntryID) latestEntryID = dataFetchResult.latestMessageID;
				
				//Updating the last check time
				lastCheckTime = System.currentTimeMillis();
			} catch(IOException | NoSuchAlgorithmException exception) {
				//Printing the stack trace
				exception.printStackTrace();
			} catch(InterruptedException exception) {
				//Returning
				return;
			}
			
			//Skipping the remainder of the iteration if there are no new messages
			if(dataFetchResult == null || dataFetchResult.conversationItems.isEmpty()) continue;
			
			//Serializing the data
			try(ByteArrayOutputStream bos = new ByteArrayOutputStream(); ObjectOutputStream out = new ObjectOutputStream(bos)) {
				out.writeByte(SharedValues.wsFrameUpdate); //Message type - update
				out.writeObject(dataFetchResult.conversationItems); //Message list
				out.flush();
				
				//Sending the data
				WSServerManager.publishMessage(bos.toByteArray());
			} catch(IOException exception) {
				//Printing the stack trace
				exception.printStackTrace();
			}
		}
	}
	
	private void fulfillConversationRequests(ArrayList<ConversationInfoRequest> requests) throws IOException {
		//Creating the DSL context
		DSLContext create = DSL.using(connection, SQLDialect.SQLITE);
		
		//Iterating over the requests
		for(ConversationInfoRequest request : requests) {
			//Creating the conversation info list
			ArrayList<SharedValues.ConversationInfo> conversationInfoList = new ArrayList<>();
			
			//Iterating over their conversations
			for(String conversationGUID : request.conversationsGUIDs) {
				//Fetching the conversation information
				String conversationTitle;
				String conversationService;
				{
					//Running the SQL
					Result<org.jooq.Record2<String, String>> results = create.select(DSL.field("chat.display_name", String.class), DSL.field("chat.service_name", String.class))
							.from(DSL.table("chat"))
							.where(DSL.field("chat.guid").equal(conversationGUID))
							.fetch();
					
					//Checking if there are no results
					if(results.isEmpty()) {
						//Adding an unavailable conversation info
						conversationInfoList.add(new SharedValues.ConversationInfo(conversationGUID));
						
						//Skipping the remainder of the iteration
						continue;
					}
					
					//Setting the conversation information
					conversationTitle = results.getValue(0, DSL.field("chat.display_name", String.class));
					conversationService = results.getValue(0, DSL.field("chat.service_name", String.class));
				}
				
				//Fetching the conversation members
				ArrayList<String> conversationMembers = new ArrayList<>();
				{
					//Running the SQL
					Result<org.jooq.Record1<String>> results = create.select(DSL.field("handle.id", String.class))
							.from(DSL.table("handle"))
							.innerJoin(DSL.table("chat_handle_join")).on(DSL.field("handle.ROWID").equal(DSL.field("chat_handle_join.handle_id")))
							.innerJoin(DSL.table("chat")).on(DSL.field("chat_handle_join.chat_id").equal(DSL.field("chat.ROWID")))
							.where(DSL.field("chat.guid").equal(conversationGUID))
							.fetch();
					
					//Checking if there are no results
					if(results.isEmpty()) {
						//Adding an unavailable conversation info
						conversationInfoList.add(new SharedValues.ConversationInfo(conversationGUID));
						
						//Skipping the remainder of the iteration
						continue;
					}
					
					//Adding the members
					for(Record1<String> result : results) conversationMembers.add(result.getValue(DSL.field("handle.id", String.class)));
				}
				
				//Adding the conversation info
				conversationInfoList.add(new SharedValues.ConversationInfo(conversationGUID, conversationService, conversationTitle, conversationMembers));
			}
			
			//Preparing to serialize the data
			try(ByteArrayOutputStream bos = new ByteArrayOutputStream(); ObjectOutputStream out = new ObjectOutputStream(bos)) {
				//Serializing the data
				out.writeByte(SharedValues.wsFrameChatInfo); //Message type - chat information
				out.writeObject(conversationInfoList); //Conversation list
				out.flush();
				
				//Sending the conversation info
				if(request.connection.isOpen()) request.connection.send(bos.toByteArray());
			}
		}
	}
	
	private void fulfillFileRequests(ArrayList<FileRequest> requests) throws IOException {
		//Creating the DSL context
		DSLContext create = DSL.using(connection, SQLDialect.SQLITE);
		
		//Iterating over the requests
		for(FileRequest request : requests) {
			//Fetching information from the database
			Result<org.jooq.Record1<String>> results = create.select(DSL.field("filename", String.class))
					.from(DSL.table("attachment"))
					.where(DSL.field("guid").equal(request.fileGuid))
					.fetch();
			
			//Creating the result variables
			File file = null;
			boolean succeeded = true;
			
			//Setting the succeeded variable to false if there are no results
			if(results.isEmpty()) succeeded = false;
			else {
				//Getting the file
				String filePath = results.getValue(0, DSL.field("filename", String.class));
				if(filePath.startsWith("~")) filePath = filePath.replaceFirst("~", System.getProperty("user.home"));
				file = new File(filePath);
				
				//Setting the succeeded variable to false if the file doesn't exist
				if(!file.exists()) succeeded = false;
			}
			
			//Checking if there have been no errors so far
			if(succeeded) {
				//Streaming the file
				try(FileInputStream inputStream = new FileInputStream(file)) {
					//Preparing to read the data
					byte[] buffer = new byte[request.chunkSize];
					int bytesRead;
					int requestIndex = 0;
					
					//Attempting to read the data
					if((bytesRead = inputStream.read(buffer)) != -1) {
						while(true) {
							//Copying and compressing the buffer
							byte[] compressedChunk = SharedValues.compress(buffer, bytesRead);
							
							//Reading more data
							boolean moreDataRead = (bytesRead = inputStream.read(buffer)) != -1;
							
							//Preparing to serialize the data
							try(ByteArrayOutputStream bos = new ByteArrayOutputStream();
								ObjectOutputStream out = new ObjectOutputStream(bos)) {
								out.writeByte(SharedValues.wsFrameAttachmentReq); //Message type - attachment request
								out.writeUTF(request.fileGuid); //File GUID
								out.writeShort(request.requestID); //Request ID
								out.writeInt(requestIndex); //Request index
								out.writeObject(compressedChunk); //Compressed chunk compressedData
								out.reset();
								if(requestIndex == 0) out.writeLong(file.length()); //File length
								out.writeBoolean(!moreDataRead); //Is last
								out.flush();
								
								//Sending the data
								if(request.connection.isOpen()) request.connection.send(bos.toByteArray());
							}
							
							//Adding to the request index
							requestIndex++;
							
							//Breaking from the loop if there is no more data to read
							if(!moreDataRead) break;
						}
					} else {
						//Setting the succeeded variable to false
						succeeded = false;
					}
				}
			}
			
			//Checking if the attempt was a failure
			if(!succeeded) {
				//Preparing to serialize the data
				try(ByteArrayOutputStream bos = new ByteArrayOutputStream();
					ObjectOutputStream out = new ObjectOutputStream(bos)) {
					out.writeByte(SharedValues.wsFrameAttachmentReqFailed); //Message type - attachment request
					out.writeShort(request.requestID); //Request ID
					out.writeUTF(request.fileGuid); //File GUID
					out.flush();
					
					//Sending the data
					if(request.connection.isOpen()) request.connection.send(bos.toByteArray());
				}
				
				//Skipping the remainder of the iteration
				continue;
			}
		}
	}
	
	private void fulfillCustomRetrievalRequests(ArrayList<CustomRetrievalRequest> requests) throws IOException, NoSuchAlgorithmException {
		//Iterating over the requests
		for(CustomRetrievalRequest request : requests) {
			//Returning their data
			DataFetchResult result = fetchData(request.filter);
			if(request.connection.isOpen()) {
				//Serializing the data
				try(ByteArrayOutputStream bos = new ByteArrayOutputStream(); ObjectOutputStream out = new ObjectOutputStream(bos)) {
					out.writeByte(request.messageResponseType); //Message type - update
					out.writeObject(result.conversationItems); //Message list
					out.flush();
					
					//Sending the data
					request.connection.send(bos.toByteArray());
				} catch(IOException exception) {
					//Printing the stack trace
					exception.printStackTrace();
				}
			}
		}
	}
	
	private void fulfillMassRetrievalRequests(ArrayList<MassRetrievalRequest> requests) throws IOException, NoSuchAlgorithmException {
		//Iterating over the requests
		for(MassRetrievalRequest request : requests) {
			//Reading the message data
			DataFetchResult messageResult = fetchData(null);
			if(messageResult == null) continue;
			
			//Creating the DSL context
			DSLContext create = DSL.using(connection, SQLDialect.SQLITE);
			
			//Fetching the conversation information
			List<SharedValues.ConversationInfo> conversationInfoList = new ArrayList<>();
			
			//Running the SQL
			Result<org.jooq.Record3<String, String, String>> conversationResults = create.select(DSL.field("chat.guid", String.class), DSL.field("chat.display_name", String.class), DSL.field("chat.service_name", String.class))
					.from(DSL.table("chat"))
					.fetch();
			
			//Iterating over the results
			for(int i = 0; i < conversationResults.size(); i++) {
				//Setting the conversation information
				String conversationGUID = conversationResults.getValue(i, DSL.field("chat.guid", String.class));
				String conversationTitle = conversationResults.getValue(i, DSL.field("chat.display_name", String.class));
				String conversationService = conversationResults.getValue(i, DSL.field("chat.service_name", String.class));
				
				//Fetching the conversation members
				ArrayList<String> conversationMembers = new ArrayList<>();
				{
					//Running the SQL
					Result<org.jooq.Record1<String>> results = create.select(DSL.field("handle.id", String.class))
							.from(DSL.table("handle"))
							.innerJoin(DSL.table("chat_handle_join")).on(DSL.field("handle.ROWID").equal(DSL.field("chat_handle_join.handle_id")))
							.innerJoin(DSL.table("chat")).on(DSL.field("chat_handle_join.chat_id").equal(DSL.field("chat.ROWID")))
							.where(DSL.field("chat.guid").equal(conversationGUID))
							.fetch();
					
					//Adding the members
					for(Record1<String> result : results) conversationMembers.add(result.getValue(DSL.field("handle.id", String.class)));
				}
				
				//Adding the conversation info
				conversationInfoList.add(new SharedValues.ConversationInfo(conversationGUID, conversationService, conversationTitle, conversationMembers));
			}
			
			//Serializing the data
			try(ByteArrayOutputStream bos = new ByteArrayOutputStream(); ObjectOutputStream out = new ObjectOutputStream(bos)) {
				out.writeByte(SharedValues.wsFrameMassRetrieval); //Message type - mass retrieval
				out.writeObject(messageResult.conversationItems); //Message list
				out.writeObject(conversationInfoList); //Conversation list
				out.flush();
				
				//Sending the data
				request.connection.send(bos.toByteArray());
			} catch(IOException exception) {
				//Printing the stack trace
				exception.printStackTrace();
			}
		}
	}
	
	private DataFetchResult fetchData(RetrievalFilter filter) throws IOException, NoSuchAlgorithmException {
		//Creating the DSL context
		DSLContext context = DSL.using(connection, SQLDialect.SQLITE);
		
		List<SelectField<?>> fields = new ArrayList<>(Arrays.asList(new SelectField<?>[] {DSL.field("message.ROWID", Long.class), DSL.field("message.guid", String.class), DSL.field("message.date", Long.class), DSL.field("message.item_type", Integer.class), DSL.field("message.group_action_type", Integer.class), DSL.field("message.text", String.class), DSL.field("message.error", Integer.class), DSL.field("message.date_read", Long.class), DSL.field("message.is_from_me", Boolean.class), DSL.field("message.group_title", String.class),
				DSL.field("message.is_sent", Boolean.class), DSL.field("message.is_read", Boolean.class), DSL.field("message.is_delivered", Boolean.class),
				DSL.field("sender_handle.id", String.class), DSL.field("other_handle.id", String.class),
				DSL.field("chat.guid", String.class)}));
		if(dbSupportsSendStyle) fields.add(DSL.field("message.expressive_send_style_id", String.class));
		if(dbSupportsAssociation) {
			fields.add(DSL.field("message.associated_message_guid", String.class));
			fields.add(DSL.field("message.associated_message_type", Integer.class));
			fields.add(DSL.field("message.associated_message_range_location", Integer.class));
		}
		
		SelectOnConditionStep<?> buildStep
				= context.select(fields)
				.from(DSL.table("message"))
				.innerJoin(DSL.table("chat_message_join")).on(DSL.field("message.ROWID").eq(DSL.field("chat_message_join.message_id")))
				.innerJoin(DSL.table("chat")).on(DSL.field("chat_message_join.chat_id").eq(DSL.field("chat.ROWID")))
				.leftJoin(DSL.table("handle").as("sender_handle")).on(DSL.field("message.handle_id").eq(DSL.field("sender_handle.ROWID")))
				.leftJoin(DSL.table("handle").as("other_handle")).on(DSL.field("message.other_handle").eq(DSL.field("other_handle.ROWID")));
		Result<?> generalMessageRecords = filter != null ? buildStep.where(filter.filter()).fetch() : buildStep.fetch();
		
		//Fetching the basic message info
		/* SelectOnConditionStep<Record16<Long, String, Long, Integer, Integer, String, String, Integer, Boolean, String, Boolean, Boolean, Boolean, String, String, String>> buildStep
				= create.select(DSL.field("message.ROWID", Long.class), DSL.field("message.guid", String.class), DSL.field("message.date", Long.class), DSL.field("message.item_type", Integer.class), DSL.field("message.group_action_type", Integer.class), DSL.field("message.text", String.class), DSL.field("message.expressive_send_style_id", String.class), DSL.field("message.error", Integer.class), DSL.field("message.is_from_me", Boolean.class), DSL.field("message.group_title", String.class),
				DSL.field("message.is_sent", Boolean.class), DSL.field("message.is_read", Boolean.class), DSL.field("message.is_delivered", Boolean.class),
				DSL.field("sender_handle.id", String.class), DSL.field("other_handle.id", String.class),
				DSL.field("chat.guid", String.class))
				.from(DSL.table("message"))
				.innerJoin(DSL.table("chat_message_join")).on(DSL.field("message.ROWID").eq(DSL.field("chat_message_join.message_id")))
				.innerJoin(DSL.table("chat")).on(DSL.field("chat_message_join.chat_id").eq(DSL.field("chat.ROWID")))
				.leftJoin(DSL.table("handle").as("sender_handle")).on(DSL.field("message.handle_id").eq(DSL.field("sender_handle.ROWID")))
				.leftJoin(DSL.table("handle").as("other_handle")).on(DSL.field("message.other_handle").eq(DSL.field("other_handle.ROWID")));
		Result<Record16<Long, String, Long, Integer, Integer, String, String, Integer, Boolean, String, Boolean, Boolean, Boolean, String, String, String>> generalMessageRecords = filter != null ? buildStep.where(filter.filter()).fetch() : buildStep.fetch(); */
		
		//Creating the result list
		ArrayList<SharedValues.ConversationItem> conversationItems = new ArrayList<>();
		ArrayList<SharedValues.ModifierInfo> isolatedModifiers = new ArrayList<>();
		long latestMessageID = -1;
		
		//Iterating over the results
		for(int i = 0; i < generalMessageRecords.size(); i++) {
			//Getting the other parameters
			long rowID = generalMessageRecords.getValue(i, DSL.field("message.ROWID", Long.class));
			String guid = generalMessageRecords.getValue(i, DSL.field("message.guid", String.class));
			String chatGUID = generalMessageRecords.getValue(i, DSL.field("chat.guid", String.class));
			long date = generalMessageRecords.getValue(i, DSL.field("message.date", Long.class));
			/* Object dateObject = generalMessageRecords.getValue(i, "message.date");
			if(Long.class.isInstance(dateObject)) date = (long) dateObject;
			else date = (int) dateObject; */
			
			String sender = generalMessageRecords.getValue(i, DSL.field("message.is_from_me", Boolean.class)) ? null : generalMessageRecords.getValue(i, DSL.field("sender_handle.id", String.class));
			int itemType = generalMessageRecords.getValue(i, DSL.field("message.item_type", Integer.class));
			
			//Updating the latest message ID
			if(rowID > latestMessageID) latestMessageID = rowID;
			
			//Checking if the item is a message
			if(itemType == 0) {
				//Checking if the database supports association
				if(dbSupportsAssociation) {
					//Getting the association info
					String associatedMessage = generalMessageRecords.getValue(i, DSL.field("message.associated_message_guid", String.class));
					int associationType = generalMessageRecords.getValue(i, DSL.field("message.associated_message_type", Integer.class));
					int associationIndex = generalMessageRecords.getValue(i, DSL.field("message.associated_message_range_location", Integer.class));
					
					//Checking if there is an association
					if(associationType != 0) {
						//Example association string: p:0/69C164B2-2A14-4462-87FA-3D79094CFD83
						//Splitting the association between the protocol and GUID
						String[] associationData = associatedMessage.split(":");
						String associatedMessageGUID = "";
						
						if(associationData[0].equals("bp")) { //Associated with message extension (content from iMessage apps)
							associatedMessageGUID = associationData[1];
						} else if(associationData[0].equals("p")) { //Standard association
							associatedMessageGUID = associationData[1].split("/")[1];
						}
						
						//Checking if the association is a sticker
						if(associationType >= 1000 && associationType < 2000) {
							//Retrieving the sticker attachment
							Result<Record2<String, String>> fileRecord = context.select(DSL.field("attachment.guid", String.class), DSL.field("attachment.filename", String.class))
									.from(DSL.table("message_attachment_join"))
									.join(DSL.table("attachment")).on(DSL.field("message_attachment_join.attachment_id").eq(DSL.field("attachment.ROWID")))
									.where(DSL.field("message_attachment_join.message_id").eq(rowID))
									.fetch();
							
							//Skipping the remainder of the iteration if there are no records
							if(fileRecord.isEmpty()) continue;
							
							//Getting the file
							File file = new File(fileRecord.getValue(0, DSL.field("attachment.filename", String.class)).replaceFirst("~", System.getProperty("user.home")));
							
							//Skipping the remainder of the iteration if the file is invalid
							if(!file.exists()) continue;
							
							//Reading the file
							byte[] fileBytes = Files.readAllBytes(file.toPath());
							
							//Compressing the data
							fileBytes = SharedValues.compress(fileBytes, fileBytes.length);
							
							//Getting the file guid
							String fileGuid = fileRecord.getValue(0, DSL.field("attachment.guid", String.class));
							
							//Creating the modifier
							SharedValues.StickerModifierInfo modifier = new SharedValues.StickerModifierInfo(associatedMessageGUID, associationIndex, fileGuid, sender, date, fileBytes);
							
							//Finding the associated message in memory
							SharedValues.MessageInfo matchingItem = null;
							for(SharedValues.ConversationItem allItems : conversationItems) {
								if(!associatedMessageGUID.equals(allItems.guid) || !(allItems instanceof SharedValues.MessageInfo)) continue;
								matchingItem = (SharedValues.MessageInfo) allItems;
								break;
							}
							//Adding the sticker to the message if it was found
							if(matchingItem != null) matchingItem.stickers.add(modifier);
							//Otherwise adding the modifier to the isolated list
							isolatedModifiers.add(modifier);
							
							//Skipping the remainder of the iteration
							continue;
						}
						//Otherwise checking if the association is a tapback response
						else if(associationType < 4000) { //2000 - 2999 = tapback added / 3000 - 3999 = tapback removed
							//Creating the modifier
							SharedValues.TapbackModifierInfo modifier = new SharedValues.TapbackModifierInfo(associatedMessageGUID, associationIndex, sender, associationType);
							
							//Finding the associated message in memory
							SharedValues.MessageInfo matchingItem = null;
							if(associationType < 3000) { //If the message is an added tapback
								for(SharedValues.ConversationItem allItems : conversationItems) {
									if(!associatedMessageGUID.equals(allItems.guid) || !(allItems instanceof SharedValues.MessageInfo)) continue;
									matchingItem = (SharedValues.MessageInfo) allItems;
									break;
								}
							}
							
							//Adding the tapback to the message if it was found
							if(matchingItem != null) matchingItem.tapbacks.add(modifier);
							//Otherwise adding the modifier to the isolated list
							isolatedModifiers.add(modifier);
							
							//Skipping the remainder of the iteration
							continue;
						}
					}
				}
				
				//Getting the detail parameters
				String text = generalMessageRecords.getValue(i, DSL.field("message.text", String.class));
				//if(text != null) text = text.replace("", "");
				if(text != null) {
					text = text.replace(Character.toString('\uFFFC'), "");
					text = text.replace(Character.toString('\uFFFD'), "");
					if(text.isEmpty()) text = null;
				}
				String sendStyle = dbSupportsSendStyle ? generalMessageRecords.getValue(i, DSL.field("message.expressive_send_style_id", String.class)) : null;
				int stateCode = determineMessageState(generalMessageRecords.getValue(i, DSL.field("message.is_sent", Boolean.class)),
						generalMessageRecords.getValue(i, DSL.field("message.is_delivered", Boolean.class)),
						generalMessageRecords.getValue(i, DSL.field("message.is_read", Boolean.class)));
				int errorCode = generalMessageRecords.getValue(i, DSL.field("message.error", Integer.class));
				long dateRead = generalMessageRecords.getValue(i, DSL.field("message.date_read", Long.class));
				
				//Fetching the attachments
				List<SelectField<?>> attachmentFields = new ArrayList<>(Arrays.asList(new SelectField<?>[] {DSL.field("attachment.guid", String.class), DSL.field("attachment.filename", String.class), DSL.field("attachment.transfer_name", String.class), DSL.field("attachment.mime_type", String.class)}));
				if(dbSupportsAssociation) attachmentFields.add(DSL.field("attachment.is_sticker", Boolean.class));
				
				Result<?> fileRecords = context.select(attachmentFields)
						.from(DSL.table("message_attachment_join"))
						.join(DSL.table("attachment")).on(DSL.field("message_attachment_join.attachment_id").eq(DSL.field("attachment.ROWID")))
						.where(DSL.field("message_attachment_join.message_id").eq(rowID))
						.fetch();
				
				//Processing the attachments
				ArrayList<SharedValues.AttachmentInfo> files = new ArrayList<>();
				for(int f = 0; f < fileRecords.size(); f++) {
					//Skipping the remainder of the iteration if the attachment is a sticker
					if(dbSupportsAssociation && fileRecords.getValue(f, DSL.field("attachment.is_sticker", Boolean.class))) continue;
					
					//Adding the file
					String fileName = fileRecords.getValue(f, DSL.field("attachment.filename", String.class));
					files.add(new SharedValues.AttachmentInfo(fileRecords.getValue(f, DSL.field("attachment.guid", String.class)),
							fileRecords.getValue(f, DSL.field("attachment.transfer_name", String.class)),
							fileRecords.getValue(f, DSL.field("attachment.mime_type", String.class)),
							//The checksum will be calculated if the message is outgoing
							sender == null && fileName != null ? calculateChecksum(new File(fileName.replaceFirst("~", System.getProperty("user.home")))) : null));
				}
				
				//Adding the conversation item
				conversationItems.add(new SharedValues.MessageInfo(guid, chatGUID, Main.getTimeHelper().toUnixTime(date), text, sender, files, new ArrayList<>(), new ArrayList<>(), sendStyle, stateCode, errorCode, Main.getTimeHelper().toUnixTime(dateRead)));
			}
			//Checking if the item is a group action
			else if(itemType == 1) {
				//Getting the detail parameters
				String other = generalMessageRecords.getValue(i, DSL.field("other_handle.id", String.class));
				int groupActionType = generalMessageRecords.getValue(i, DSL.field("message.group_action_type", Integer.class));
				
				//Adding the conversation item
				conversationItems.add(new SharedValues.GroupActionInfo(guid, chatGUID, Main.getTimeHelper().toUnixTime(date), sender, other, groupActionType));
			}
			//Otherwise checking if the item is a chat rename
			else if(itemType == 2) {
				//Getting the detail parameters
				String newChatName = generalMessageRecords.getValue(i, DSL.field("message.group_title", String.class));
				
				//Adding the conversation item
				conversationItems.add(new SharedValues.ChatRenameActionInfo(guid, chatGUID, Main.getTimeHelper().toUnixTime(date), sender, newChatName));
			}
		}
		
		//Returning null if the item list is empty
		//if(conversationItems.isEmpty()) return null;
		
		//Adding applicable isolated modifiers to their messages
		/* for(Iterator<SharedValues.ModifierInfo> iterator = isolatedModifiers.iterator(); iterator.hasNext();) {
			//Getting the modifier
			SharedValues.ModifierInfo modifier = iterator.next();
			
			//Checking if the modifier is a sticker
			if(modifier instanceof SharedValues.StickerModifierInfo || modifier instanceof SharedValues.TapbackModifierInfo) {
				//Iterating over all the items
				for(SharedValues.ConversationItem allItems : conversationItems) {
					//Skipping the remainder of the iteration if the item doesn't match
					if(!modifier.message.equals(allItems.guid) || !(allItems instanceof SharedValues.MessageInfo)) continue;
					
					//Getting the message info
					SharedValues.MessageInfo matchingItem = (SharedValues.MessageInfo) allItems;
					
					//Adding the modifier
					if(modifier instanceof SharedValues.StickerModifierInfo) matchingItem.stickers.add((SharedValues.StickerModifierInfo) modifier);
					else if(modifier instanceof SharedValues.TapbackModifierInfo) matchingItem.tapbacks.add((SharedValues.TapbackModifierInfo) modifier);
					
					//Removing the item from the isolated modifier list
					iterator.remove();
					
					//Breaking from the loop
					break;
				}
			}
		} */
		
		//Logging a debug message
		if(!conversationItems.isEmpty()) Main.getLogger().finest("Found " + conversationItems.size() + " new item(s) from latest scan");
		
		//Returning the result
		return new DataFetchResult(conversationItems, isolatedModifiers, latestMessageID);
	}
	
	private void updateMessageStates(ArrayList<SharedValues.ModifierInfo> modifierList) {
		//Creating the DSL context
		DSLContext context = DSL.using(connection, SQLDialect.SQLITE);
		
		//Fetching the data
		Result<Record6<Long, String, Boolean, Boolean, Boolean, Long>> results = context.select(DSL.field("message.date", Long.class).max(), DSL.field("message.guid", String.class), DSL.field("message.is_sent", Boolean.class), DSL.field("message.is_delivered", Boolean.class), DSL.field("message.is_read", Boolean.class), DSL.field("message.date_read", Long.class))
				.from(DSL.table("chat"))
				.join(DSL.table("chat_message_join")).on(DSL.field("chat.ROWID").eq(DSL.field("chat_message_join.chat_id")))
				.join(DSL.table("message")).on(DSL.field("chat_message_join.message_id").eq(DSL.field("message.ROWID")))
				.where(DSL.field("message.is_from_me").isTrue())
				.groupBy(DSL.field("chat.ROWID")).fetch();
		
		//Creating the result list
		//ArrayList<Object> modifierList = new ArrayList<>();
		
		//Iterating over the results
		HashMap<String, Integer> messageStatesCache = messageStates;
		messageStates = new HashMap<>();
		for(int i = 0; i < results.size(); i++) {
			//Getting the result information
			String resultGuid = results.getValue(i, DSL.field("message.guid", String.class));
			int resultState = determineMessageState(results.getValue(i, DSL.field("message.is_sent", Boolean.class)),
					results.getValue(i, DSL.field("message.is_delivered", Boolean.class)),
					results.getValue(i, DSL.field("message.is_read", Boolean.class)));
			
			//Adding the state to the list
			messageStates.put(resultGuid, resultState);
			
			//Skipping the remainder of the iteration if the message state was not previously cached
			if(!messageStatesCache.containsKey(resultGuid)) continue;
			
			//Getting the message states
			int cacheState = messageStatesCache.get(resultGuid);
			
			//Checking if the states don't match
			if(cacheState != resultState) {
				//Logging a debug message
				Main.getLogger().finest("New activity status for message " + resultGuid + ": " + cacheState + " -> " + resultState);
				
				//Adding the modifier to the list
				modifierList.add(new SharedValues.ActivityStatusModifierInfo(resultGuid, resultState, Main.getTimeHelper().toUnixTime(results.getValue(i, DSL.field("message.date_read", Long.class)))));
			}
		}
		
		//Returning if there are no modifiers to send
		if(modifierList.isEmpty()) return;

		//Serializing the data
		try(ByteArrayOutputStream bos = new ByteArrayOutputStream(); ObjectOutputStream out = new ObjectOutputStream(bos)) {
			out.writeByte(SharedValues.wsFrameModifierUpdate); //Message type - modifier update
			out.writeObject(modifierList); //Modifier list
			out.flush();
			
			//Sending the data
			WSServerManager.publishMessage(bos.toByteArray());
		} catch(IOException exception) {
			//Printing the stack trace
			exception.printStackTrace();
		}
	}
	
	private static int determineMessageState(boolean isSent, boolean isDelivered, boolean isRead) {
		//Determining the state code
		int stateCode = SharedValues.MessageInfo.stateCodeIdle;
		if(isSent) stateCode = SharedValues.MessageInfo.stateCodeSent;
		if(isDelivered) stateCode = SharedValues.MessageInfo.stateCodeDelivered;
		if(isRead) stateCode = SharedValues.MessageInfo.stateCodeRead;
		
		//Returning the state code
		return stateCode;
	}
	
	private static byte[] calculateChecksum(File file) throws IOException, NoSuchAlgorithmException {
		//Returning null if the file isn't ready
		if(!file.exists() || !file.isFile()) return null;
		
		//Preparing to read the file
		MessageDigest messageDigest = MessageDigest.getInstance(SharedValues.hashAlgorithm);
		try (FileInputStream inputStream = new FileInputStream(file)) {
			byte[] dataBytes = new byte[1024];
			
			//Reading the file
			int bytesRead;
			while ((bytesRead = inputStream.read(dataBytes)) != -1) messageDigest.update(dataBytes, 0, bytesRead);
			
			//Returning the file hash
			return messageDigest.digest();
		}
	}
	
	private class DataFetchResult {
		final ArrayList<SharedValues.ConversationItem> conversationItems;
		final ArrayList<SharedValues.ModifierInfo> isolatedModifiers;
		final long latestMessageID;
		
		DataFetchResult(ArrayList<SharedValues.ConversationItem> conversationItems, ArrayList<SharedValues.ModifierInfo> isolatedModifiers, long latestMessageID) {
			this.conversationItems = conversationItems;
			this.isolatedModifiers = isolatedModifiers;
			this.latestMessageID = latestMessageID;
		}
	}
	
	static class ConversationInfoRequest {
		final WebSocket connection;
		final ArrayList<String> conversationsGUIDs;
		
		ConversationInfoRequest(WebSocket connection, ArrayList<String> conversationsGUIDs) {
			//Setting the values
			this.connection = connection;
			this.conversationsGUIDs = conversationsGUIDs;
		}
	}
	
	static abstract class RetrievalFilter {
		abstract Condition filter();
	}
	
	static class CustomRetrievalRequest {
		final WebSocket connection;
		final RetrievalFilter filter;
		final byte messageResponseType;
		
		CustomRetrievalRequest(WebSocket connection, RetrievalFilter filter, byte messageResponseType) {
			//Setting the values
			this.connection = connection;
			this.filter = filter;
			this.messageResponseType = messageResponseType;
		}
	}
	
	static class MassRetrievalRequest {
		final WebSocket connection;
		
		MassRetrievalRequest(WebSocket connection) {
			this.connection = connection;
		}
	}
	
	static class FileRequest {
		final WebSocket connection;
		final String fileGuid;
		final short requestID;
		final int chunkSize;
		
		FileRequest(WebSocket connection, String fileGuid, short requestID, int chunkSize) {
			this.connection = connection;
			this.fileGuid = fileGuid;
			this.requestID = requestID;
			this.chunkSize = chunkSize;
		}
	}
}