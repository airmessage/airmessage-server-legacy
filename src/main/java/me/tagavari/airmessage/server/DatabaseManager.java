package me.tagavari.airmessage.server;

import io.sentry.Sentry;
import me.tagavari.airmessage.common.SharedValues;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

class DatabaseManager {
	//Creating the reference variables
	//private static final long checkTime = 5 * 1000;
	//private static final long pollTime = 100;
	private static final String databaseLocation = "jdbc:sqlite:" + System.getProperty("user.home") + "/Library/Messages/chat.db";
	/* private static final ArrayList<String> validServices = new ArrayList<String>() {{
		add("iMessage");
		add("SMS");
	}}; */
	
	//Creating the instance value
	private static DatabaseManager instance;
	
	//Creating the schema support values
	private boolean dbSupportsSendStyle = false;
	private boolean dbSupportsAssociation = false;
	
	//Creating the thread values
	ScannerThread scannerThread;
	RequestThread requestThread;
	
	//Creating the other values
	private HashMap<String, MessageState> messageStates = new HashMap<>();
	
	static boolean start(long scanFrequency) {
		//Checking if there is already an instance
		if(instance != null) {
			//Logging the exception
			//Main.getLogger().severe("Instance of database manager already exists");
			
			//Returning true
			return true;
		}
		
		//Creating the database connections
		Connection[] connections = new Connection[2];
		int connectionsEstablished = 0;
		try {
			for(; connectionsEstablished < connections.length; connectionsEstablished++) connections[connectionsEstablished] = DriverManager.getConnection(databaseLocation);
		} catch(SQLException exception) {
			//Logging a message
			Main.getLogger().severe("Failed to connect to chat message database: " + exception.getMessage());
			
			//Closing the connections
			for(int i = 0; i < connectionsEstablished; i++) {
				try {
					connections[i].close();
				} catch(SQLException exception2) {
					exception2.printStackTrace();
				}
			}
			
			//Main.getLogger().severe("No incoming messages will be received");
			
			//Returning false
			return false;
		}
		
		//Creating the instance
		instance = new DatabaseManager(connections, scanFrequency);
		
		//Getting the time variables
		//connectFetchTime = Main.getTimeHelper().toDatabaseTime(System.currentTimeMillis());
		
		//Returning true
		return true;
	}
	
	static void stop() {
		//Getting the instance
		if(DatabaseManager.instance == null) return;
		
		//Interrupting the thread
		DatabaseManager.instance.requestThread.interrupt();
		DatabaseManager.instance.scannerThread.interrupt();
	}
	
	private DatabaseManager(Connection[] connections, long scanFrequency) {
		//Reading the schema
		Connection connection = connections[0];
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
			Sentry.capture(exception);
			exception.printStackTrace();
		}
		
		//Creating the threads
		scannerThread = new ScannerThread(connections[0], scanFrequency);
		scannerThread.start();
		requestThread = new RequestThread(connections[1]);
		requestThread.start();
	}
	
	static DatabaseManager getInstance() {
		return instance;
	}
	
	//The thread that actively scans the database for new messages
	class ScannerThread extends Thread {
		//Creating the connection variables
		private final Connection connection;
		
		//Creating the time values
		private long latestEntryID = -1;
		private final long creationTime;
		//private long lastCheckTime;
		
		//Creating the lock values
		private final Lock scanFrequencyLock = new ReentrantLock();
		private final java.util.concurrent.locks.Condition scanFrequencyCondition = scanFrequencyLock.newCondition();
		private long scanFrequency;
		
		private ScannerThread(Connection connection, long scanFrequency) {
			//Setting the values
			this.connection = connection;
			
			creationTime = Main.getTimeHelper().toDatabaseTime(System.currentTimeMillis());
			
			this.scanFrequency = scanFrequency;
		}
		
		@Override
		public void run() {
			//Creating the message array variable
			DataFetchResult dataFetchResult = null;
			
			//Looping until the thread is interrupted
			while(!isInterrupted()) {
				try {
					//Sleeping for the scan frequency
					scanFrequencyLock.lock();
					try {
						scanFrequencyCondition.await(scanFrequency, TimeUnit.MILLISECONDS);
					} finally {
						scanFrequencyLock.unlock();
					}
					
					//Fetching new messages
					dataFetchResult = fetchData(connection,
							latestEntryID == -1 ?
									() -> DSL.field("message.date").greaterThan(creationTime) :
									() -> DSL.field("message.ROWID").greaterThan(latestEntryID));
					
					//Updating the latest entry ID
					if(dataFetchResult.latestMessageID > latestEntryID) latestEntryID = dataFetchResult.latestMessageID;
					
					//Updating the last check time
					//lastCheckTime = System.currentTimeMillis();
				} catch(IOException | NoSuchAlgorithmException exception) {
					Sentry.capture(exception);
					exception.printStackTrace();
				} catch(InterruptedException exception) {
					//Returning
					return;
				}
				
				//Checking if there are no new messages
				if(dataFetchResult != null && !dataFetchResult.conversationItems.isEmpty()) {
					try(ByteArrayOutputStream bos = new ByteArrayOutputStream(); ObjectOutputStream out = new ObjectOutputStream(bos)) {
						//Serializing the data
						out.writeInt(dataFetchResult.conversationItems.size());
						for(SharedValues.ConversationItem item : dataFetchResult.conversationItems) out.writeObject(item);
						out.flush();
						
						//Sending the data
						NetServerManager.sendPacket(null, SharedValues.nhtMessageUpdate, bos.toByteArray());
					} catch(IOException exception) {
						Sentry.capture(exception);
						exception.printStackTrace();
					}
				}
				
				//Updating the message states
				updateMessageStates(connection, dataFetchResult == null ? new ArrayList<>() : dataFetchResult.isolatedModifiers);
			}
		}
		
		void updateScanFrequency(long frequency) {
			//Updating the value
			scanFrequencyLock.lock();
			try {
				if(frequency != scanFrequency) {
					scanFrequency = frequency;
					scanFrequencyCondition.signal();
				}
			} finally {
				scanFrequencyLock.unlock();
			}
		}
	}
	
	//The thread that handles requests from clients such as file downloads
	class RequestThread extends Thread {
		//Creating the connection variables
		private final Connection connection;
		
		//Creating the lock values
		private BlockingQueue<Object> databaseRequests = new LinkedBlockingQueue<>();
		
		private RequestThread(Connection connection) {
			this.connection = connection;
		}
		
		@Override
		public void run() {
			try {
				//Looping while the thread is alive
				while(!isInterrupted()) {
					//Taking the queue item
					Object request = databaseRequests.take();
					
					//Processing the request
					if(request instanceof ConversationInfoRequest) fulfillConversationRequest(connection, (ConversationInfoRequest) request);
					else if(request instanceof FileRequest) fulfillFileRequest(connection, (FileRequest) request);
					else if(request instanceof CustomRetrievalRequest) fulfillCustomRetrievalRequest(connection, (CustomRetrievalRequest) request);
					else if(request instanceof MassRetrievalRequest) fulfillMassRetrievalRequest(connection, (MassRetrievalRequest) request);
				}
			} catch(InterruptedException exception) {
				return;
			}
		}
		
		void addRequest(Object request) {
			databaseRequests.add(request);
			/* //Adding the data struct
			dbRequestsLock.lock();
			try {
				databaseRequests.add(request);
				dbRequestsCondition.signal();
			} finally {
				dbRequestsLock.unlock();
			} */
		}
	}
	
	void addClientRequest(Object request) {
		requestThread.addRequest(request);
	}
	
	private void fulfillConversationRequest(Connection connection, ConversationInfoRequest request) {
		//Creating the DSL context
		DSLContext create = DSL.using(connection, SQLDialect.SQLITE);
		
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
		
		//Checking if the connection is still open
		if(request.connection.isConnected()) {
			//Preparing to serialize the data
			try(ByteArrayOutputStream bos = new ByteArrayOutputStream(); ObjectOutputStream out = new ObjectOutputStream(bos)) {
				//Serializing the data
				out.writeInt(conversationInfoList.size());
				for(SharedValues.ConversationInfo item : conversationInfoList) out.writeObject(item);
				out.flush();
				
				//Sending the conversation info
				NetServerManager.sendPacket(request.connection, SharedValues.nhtConversationUpdate, bos.toByteArray());
			} catch(IOException exception) {
				Sentry.capture(exception);
				exception.printStackTrace();
			}
		}
	}
	
	private void fulfillFileRequest(Connection connection, FileRequest request) {
		//Creating the DSL context
		DSLContext create = DSL.using(connection, SQLDialect.SQLITE);
		
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
				byte[] compressedBuffer;
				int bytesRead;
				boolean moreDataRead;
				int requestIndex = 0;
				
				//Attempting to read the data
				if((bytesRead = inputStream.read(buffer)) != -1) {
					do {
						//Compressing the buffer
						compressedBuffer = Constants.compressGZIP(buffer, bytesRead);
						
						//Reading the next chunk
						moreDataRead = (bytesRead = inputStream.read(buffer)) != -1;
						
						//Preparing to serialize the data
						try(ByteArrayOutputStream bos = new ByteArrayOutputStream(); ObjectOutputStream out = new ObjectOutputStream(bos)) {
							out.writeShort(request.requestID); //Request ID
							out.writeUTF(request.fileGuid); //File GUID
							out.writeInt(requestIndex); //Request index
							out.writeInt(compressedBuffer.length); //Compressed chunk data
							out.write(compressedBuffer);
							out.reset();
							if(requestIndex == 0) out.writeLong(file.length()); //Total file length
							out.writeBoolean(!moreDataRead); //Is last
							out.flush();
							
							//Sending the data
							NetServerManager.sendPacket(request.connection, SharedValues.nhtAttachmentReq, bos.toByteArray());
							//if(request.connection.isOpen()) request.connection.send(bos.toByteArray());
						}
						
						//Adding to the request index
						requestIndex++;
					} while(moreDataRead);
				} else {
					//Setting the succeeded variable to false
					succeeded = false;
				}
			} catch(IOException exception) {
				Sentry.capture(exception);
				exception.printStackTrace();
			}
		}
		
		//Checking if the attempt was a failure
		if(!succeeded) {
			if(request.connection.isConnected()) {
				//Preparing to serialize the data
				try(ByteArrayOutputStream bos = new ByteArrayOutputStream();
					ObjectOutputStream out = new ObjectOutputStream(bos)) {
					out.writeShort(request.requestID); //Request ID
					out.writeUTF(request.fileGuid); //File GUID
					out.flush();
					
					//Sending the data
					NetServerManager.sendPacket(request.connection, SharedValues.nhtAttachmentReqFail, bos.toByteArray());
				} catch(IOException exception) {
					Sentry.capture(exception);
					exception.printStackTrace();
				}
			}
		}
	}
	
	private void fulfillCustomRetrievalRequest(Connection connection, CustomRetrievalRequest request) {
		try {
			//Returning their data
			DataFetchResult result = fetchData(connection, request.filter);
			if(request.connection.isConnected()) {
				//Serializing the data
				try(ByteArrayOutputStream bos = new ByteArrayOutputStream(); ObjectOutputStream out = new ObjectOutputStream(bos)) {
					out.writeInt(result.conversationItems.size());
					for(SharedValues.ConversationItem item : result.conversationItems) out.writeObject(item);
					out.flush();
					
					//Sending the data
					NetServerManager.sendPacket(request.connection, request.messageResponseType, bos.toByteArray());
				}
			}
		} catch(NoSuchAlgorithmException | IOException exception) {
			Sentry.capture(exception);
			exception.printStackTrace();
		}
	}
	
	private void fulfillMassRetrievalRequest(Connection connection, MassRetrievalRequest request) {
		try {
			//Reading the message data
			DataFetchResult messageResult = fetchData(connection, null);
			//if(messageResult == null) continue;
			
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
			
			//Checking if the connection is still open
			if(request.connection.isConnected()) {
				//Serializing the data
				try(ByteArrayOutputStream bos = new ByteArrayOutputStream(); ObjectOutputStream out = new ObjectOutputStream(bos)) {
					//Writing the message list
					out.writeInt(messageResult.conversationItems.size());
					for(SharedValues.ConversationItem item : messageResult.conversationItems) out.writeObject(item);
					
					//Writing the conversation list
					out.writeInt(conversationInfoList.size());
					for(SharedValues.ConversationInfo item : conversationInfoList) out.writeObject(item);
					
					out.flush();
					
					//Sending the data
					NetServerManager.sendPacket(request.connection, SharedValues.nhtMassRetrieval, bos.toByteArray());
				}
			}
		} catch(IOException | NoSuchAlgorithmException exception) {
			Sentry.capture(exception);
			exception.printStackTrace();
		}
	}
	
	private DataFetchResult fetchData(Connection connection, RetrievalFilter filter) throws IOException, NoSuchAlgorithmException {
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
							
							//Reading the file with GZIP compression
							byte[] fileBytes = Files.readAllBytes(file.toPath());
							fileBytes = Constants.compressGZIP(fileBytes, fileBytes.length);
							
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
	
	private void updateMessageStates(Connection connection, ArrayList<SharedValues.ModifierInfo> modifierList) {
		//Creating the DSL context
		DSLContext context = DSL.using(connection, SQLDialect.SQLITE);
		
		//Fetching the data
		/* Result<Record8<String, Long, Long, String, Boolean, Boolean, Boolean, Long>> results = context.select(DSL.field("message.text", String.class), DSL.field("message.date", Long.class), DSL.field("message.date", Long.class).max(), DSL.field("message.guid", String.class), DSL.field("message.is_sent", Boolean.class), DSL.field("message.is_delivered", Boolean.class), DSL.field("message.is_read", Boolean.class), DSL.field("message.date_read", Long.class))
				.from(DSL.table("chat"))
				.join(DSL.table("chat_message_join")).on(DSL.field("chat.ROWID").eq(DSL.field("chat_message_join.chat_id")))
				.join(DSL.table("message")).on(DSL.field("chat_message_join.message_id").eq(DSL.field("message.ROWID")))
				.where(DSL.field("message.is_from_me").isTrue())
				.groupBy(DSL.field("chat.ROWID")).fetch(); */
		
		Result<Record6<Long, String, Boolean, Boolean, Boolean, Long>> results = context.select(DSL.field("message.date", Long.class).max(), DSL.field("message.guid", String.class), DSL.field("message.is_sent", Boolean.class), DSL.field("message.is_delivered", Boolean.class), DSL.field("message.is_read", Boolean.class), DSL.field("message.date_read", Long.class))
				.from(DSL.table("message"))
				.join(DSL.table("chat_message_join")).on(DSL.field("message.ROWID").eq(DSL.field("chat_message_join.message_id")))
				.join(DSL.table("chat")).on(DSL.field("chat_message_join.chat_id").eq(DSL.field("chat.ROWID")))
				.where(DSL.field("message.is_from_me").isTrue())
				.groupBy(DSL.field("chat.ROWID")).fetch();
		
		/* Result<Record8<String, Long, Long, String, Boolean, Boolean, Boolean, Long>> results = context.select(DSL.field("message.text", String.class), DSL.field("message.date", Long.class), DSL.field("message.date", Long.class).max(), DSL.field("message.guid", String.class), DSL.field("message.is_sent", Boolean.class), DSL.field("message.is_delivered", Boolean.class), DSL.field("message.is_read", Boolean.class), DSL.field("message.date_read", Long.class))
				.from(DSL.table("message"))
				.join(DSL.table("chat_message_join")).on(DSL.field("message.ROWID").eq(DSL.field("chat_message_join.message_id")))
				.join(DSL.table("chat")).on(DSL.field("chat_message_join.chat_id").eq(DSL.field("chat.ROWID")))
				.join(DSL.select(DSL.field("chat.ROWID", Long.class), DSL.field("message.date", Long.class).max().as("maxDate")).from(DSL.table("message"))
						.join(DSL.table("chat_message_join")).on(DSL.field("message.ROWID").eq(DSL.field("chat_message_join.message_id")))
						.join(DSL.table("chat")).on(DSL.field("chat_message_join.chat_id").eq(DSL.field("chat.ROWID"))).groupBy(DSL.field("chat.ROWID")).asTable("grouped")).on(DSL.field("chat.ROWID").eq(DSL.field("grouped.ROWID"))).and(DSL.field("message.date").eq(DSL.field("grouped.maxDate")))
				//.where(DSL.field("message.is_from_me").isTrue())
				//.groupBy(DSL.field("chat.ROWID")).fetch();
				.fetch(); */
		
		//Creating the result list
		//ArrayList<Object> modifierList = new ArrayList<>();
		
		//Iterating over the results
		//HashMap<String, MessageState> messageStatesCache = messageStates;
		//messageStates = new HashMap<>();
		for(int i = 0; i < results.size(); i++) {
			//Getting the result information
			String resultGuid = results.getValue(i, DSL.field("message.guid", String.class));
			int resultState = determineMessageState(results.getValue(i, DSL.field("message.is_sent", Boolean.class)),
					results.getValue(i, DSL.field("message.is_delivered", Boolean.class)),
					results.getValue(i, DSL.field("message.is_read", Boolean.class)));
			
			//Getting the item
			MessageState messageState;
			if(messageStates.containsKey(resultGuid)) messageState = messageStates.get(resultGuid);
			else {
				messageStates.put(resultGuid, new MessageState(resultState));
				continue;
			}
			
			//Resetting the item's depth
			messageState.depth = 0;
			
			//Getting the message states
			int cacheState = messageStates.get(resultGuid).state;
			
			//Checking if the states don't match
			if(cacheState != resultState) {
				//Updating the state
				messageState.state = resultState;
				
				//Logging a debug message
				Main.getLogger().finest("New activity status for message " + resultGuid + ": " + cacheState + " -> " + resultState);
				//Main.getLogger().finest("New activity status for message " + results.getValue(i, DSL.field("message.text", String.class)) + ": " + cacheState + " -> " + resultState);
				
				//Adding the modifier to the list
				modifierList.add(new SharedValues.ActivityStatusModifierInfo(resultGuid, resultState, Main.getTimeHelper().toUnixTime(results.getValue(i, DSL.field("message.date_read", Long.class)))));
			}
		}
		
		//Increasing the depth of the elements in the list, and removing them if they are deeper than 5
		messageStates.entrySet().removeIf(stringMessageStateEntry -> ++stringMessageStateEntry.getValue().depth > 5);
		
		//Returning if there are no modifiers to send
		if(modifierList.isEmpty()) return;

		//Serializing the data
		try(ByteArrayOutputStream bos = new ByteArrayOutputStream(); ObjectOutputStream out = new ObjectOutputStream(bos)) {
			out.writeInt(modifierList.size());
			for(SharedValues.ModifierInfo item : modifierList) out.writeObject(item);
			out.flush();
			
			//Sending the data
			NetServerManager.sendPacket(null, SharedValues.nhtModifierUpdate, bos.toByteArray());
		} catch(IOException exception) {
			Sentry.capture(exception);
			exception.printStackTrace();
		}
	}
	
	private static class MessageState {
		private int state;
		private int depth = 0;
		
		MessageState(int state) {
			this.state = state;
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
		final NetServerManager.SocketManager connection;
		final List<String> conversationsGUIDs;
		
		ConversationInfoRequest(NetServerManager.SocketManager connection, List<String> conversationsGUIDs) {
			//Setting the values
			this.connection = connection;
			this.conversationsGUIDs = conversationsGUIDs;
		}
	}
	
	interface RetrievalFilter {
		Condition filter();
	}
	
	static class CustomRetrievalRequest {
		final NetServerManager.SocketManager connection;
		final RetrievalFilter filter;
		final int messageResponseType;
		
		CustomRetrievalRequest(NetServerManager.SocketManager connection, RetrievalFilter filter, int messageResponseType) {
			//Setting the values
			this.connection = connection;
			this.filter = filter;
			this.messageResponseType = messageResponseType;
		}
	}
	
	static class MassRetrievalRequest {
		final NetServerManager.SocketManager connection;
		
		MassRetrievalRequest(NetServerManager.SocketManager connection) {
			this.connection = connection;
		}
	}
	
	static class FileRequest {
		final NetServerManager.SocketManager connection;
		final String fileGuid;
		final short requestID;
		final int chunkSize;
		
		FileRequest(NetServerManager.SocketManager connection, String fileGuid, short requestID, int chunkSize) {
			this.connection = connection;
			this.fileGuid = fileGuid;
			this.requestID = requestID;
			this.chunkSize = chunkSize;
		}
	}
}