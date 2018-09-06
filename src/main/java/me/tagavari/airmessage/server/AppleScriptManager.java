package me.tagavari.airmessage.server;

import io.sentry.Sentry;

import java.io.*;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

class AppleScriptManager {
	//Creating the AppleScript commands
	private static final String[] ASTextExisting = { //ARGS: Chat GUID / Message
			"tell application \"Messages\"",
			
			//Getting target chat
			"set targetChat to text chat id \"%1$s\"",
			
			//Sending the message
			"send \"%2$s\" to targetChat",
			
			"end tell"
	};
	private static final String[] ASTextNew = { //ARGS: Recipients / Message / Service
			"tell application \"Messages\"",
			
			//Getting the iMessage service
			"if \"%3$s\" is \"iMessage\" then",
			"set targetService to 1st service whose service type = iMessage",
			"else",
			"set targetService to service \"%3$s\"",
			"end if",
			
			//Splitting the recipient list
			/* "set oldDelimiters to AppleScript's text item delimiters",
			"set AppleScript's text item delimiters to \"" + appleScriptDelimiter + "\"",
			"set recipientList to every text item of \"%1$s\"",
			"set AppleScript's text item delimiters to oldDelimiters",
			"set recipientList to {%1$s}", */
			
			//Converting the recipients to iMessage buddies
			/* "set buddyList to {}",
			"repeat with currentRecipient in recipientList",
			"set currentBuddy to buddy currentRecipient of targetService",
			"copy currentBuddy to the end of buddyList",
			"end repeat", */
			
			//Creating the chat
			"set targetChat to make new text chat with properties {participants:{%1$s}}",
			
			//Sending the messages
			"send \"%2$s\" to targetChat",
			
			//Getting the chat info
			//"get targetChat",
			
			"end tell"
	};
	private static final String[] ASFileExisting = { //ARGS: Chat GUID / File
			//Getting the file
			"set message to POSIX file \"%2$s\"",
			
			"tell application \"Messages\"",
			
			//Getting target chat
			"set targetChat to text chat id \"%1$s\"",
			
			//Sending the message
			"send message to targetChat",
			
			"end tell"
	};
	private static final String[] ASFileNew = { //ARGS: Recipients / File / Service
			//Getting the file
			"set message to POSIX file \"%2$s\"",
			
			"tell application \"Messages\"",
			
			//Getting the iMessage service
			"if \"%3$s\" is \"iMessage\" then",
			"set targetService to 1st service whose service type = iMessage",
			"else",
			"set targetService to service \"%3$s\"",
			"end if",
			
			//Splitting the recipient list
			/* "set oldDelimiters to AppleScript's text item delimiters",
			"set AppleScript's text item delimiters to \"" + appleScriptDelimiter + "\"",
			"set recipientList to every text item of \"%1$s\"",
			"set AppleScript's text item delimiters to oldDelimiters",
			"set recipientList to {%1$s}", */
			
			//Converting the recipients to iMessage buddies
			/* "set buddyList to {}",
			"repeat with currentRecipient in recipientList",
			"set currentBuddy to buddy currentRecipient of targetService",
			"copy currentBuddy to the end of buddyList",
			"end repeat", */
			
			//Creating the chat
			"set targetChat to make new text chat with properties {participants:{%1$s}}",
			
			//Sending the messages
			"send message to targetChat",
			
			//Getting the chat info
			//"get targetChat",
			
			"end tell"
	};
	
	static boolean sendExistingMessage(String chatGUID, String message) {
		//Building the command
		ArrayList<String> command = new ArrayList<>();
		command.add("osascript");
		for(String line : ASTextExisting) {
			command.add("-e");
			command.add(String.format(line, chatGUID, escapeAppleScriptString(message)));
		}
		
		try {
			//Running the command
			Process process = Runtime.getRuntime().exec(command.toArray(new String[0]));
			
			//Reading from the error stream
			BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
			boolean linesRead = false;
			String lsString;
			while ((lsString = errorReader.readLine()) != null) {
				if(!isFatalResponse(lsString)) continue;
				Main.getLogger().severe(lsString);
				linesRead = true;
			}
			
			//Checking if there were any lines read
			if(linesRead) {
				//Checking if the conversation has been indexed as a one-on-one chat
				DatabaseManager.CreationTargetingChat targetChat = DatabaseManager.getInstance().getCreationTargetingAvailabilityList().get(chatGUID);
				if(targetChat != null) {
					//Attempting to send the message as a new conversation
					return sendNewMessage(new String[]{targetChat.getAddress()}, message, targetChat.getService(), true);
				}
				
				//Returning false
				return false;
			}
		} catch(IOException exception) {
			//Printing the stack trace
			Main.getLogger().log(Level.WARNING, exception.getMessage(), exception);
			
			//Returning false
			return false;
		}
		
		//Returning true
		return true;
	}
	
	static boolean sendNewMessage(String[] chatMembers, String message, String service, boolean isFallback) {
		//Returning false if there are no members
		if(chatMembers.length == 0) return false;
		
		//Formatting the chat members
		StringBuilder delimitedChatMembers = new StringBuilder("buddy \"" + escapeAppleScriptString(chatMembers[0]) + "\" of targetService");
		
		//Adding the remaining members
		for(int i = 1; i < chatMembers.length; i++) delimitedChatMembers.append(',').append("buddy \"").append(escapeAppleScriptString(chatMembers[i])).append("\" of targetService");
		
		//Building the command
		ArrayList<String> command = new ArrayList<>();
		command.add("osascript");
		for(String line : ASTextNew) {
			command.add("-e");
			command.add(String.format(line, delimitedChatMembers.toString(), escapeAppleScriptString(message), escapeAppleScriptString(service)));
		}
		
		//Running the command
		try {
			Process process = Runtime.getRuntime().exec(command.toArray(new String[0]));
			
			//Returning false if there was any error
			BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
			boolean linesRead = false;
			String lsString;
			while ((lsString = errorReader.readLine()) != null) {
				Main.getLogger().severe(lsString);
				linesRead = true;
			}
			
			if(linesRead) return false;
		} catch(IOException exception) {
			//Printing the stack trace
			Main.getLogger().log(Level.WARNING, exception.getMessage(), exception);
			
			//Returning false
			return false;
		}
		
		//Reindexing the creation targeting index
		if(!isFallback) DatabaseManager.getInstance().requestCreationTargetingAvailabilityUpdate();
		
		//Returning true
		return true;
	}
	
	static boolean sendExistingFile(String chatGUID, File file) {
		//Building the command
		ArrayList<String> command = new ArrayList<>();
		command.add("osascript");
		for(String line : ASFileExisting) {
			command.add("-e");
			command.add(String.format(line, chatGUID, escapeAppleScriptString(file.getAbsolutePath())));
		}
		
		try {
			//Running the command
			Process process = Runtime.getRuntime().exec(command.toArray(new String[0]));
			
			//Reading from the error stream
			BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
			boolean linesRead = false;
			String lsString;
			while ((lsString = errorReader.readLine()) != null) {
				Main.getLogger().severe(lsString);
				linesRead = true;
			}
			
			//Checking if there were any lines read
			if(linesRead) {
				//Checking if the conversation has been indexed as a one-on-one chat
				DatabaseManager.CreationTargetingChat targetChat = DatabaseManager.getInstance().getCreationTargetingAvailabilityList().get(chatGUID);
				if(targetChat != null) {
					//Attempting to send the message as a new conversation
					return sendNewFile(new String[]{targetChat.getAddress()}, file, targetChat.getService(), true);
				}
				
				//Returning false
				return false;
			}
		} catch(IOException exception) {
			//Printing the stack trace
			Main.getLogger().log(Level.WARNING, exception.getMessage(), exception);
			
			//Returning false
			return false;
		}
			
			//Returning true
		return true;
	}
	
	static boolean sendNewFile(String[] chatMembers, File file, String service, boolean isFallback) {
		//Returning false if there are no members
		if(chatMembers.length == 0) return false;
		
		//Formatting the chat members
		StringBuilder delimitedChatMembers = new StringBuilder("buddy \"" + escapeAppleScriptString(chatMembers[0]) + "\" of targetService");
		
		//Adding the remaining members
		for(int i = 1; i < chatMembers.length; i++) delimitedChatMembers.append(',').append("buddy \"").append(escapeAppleScriptString(chatMembers[i])).append("\" of targetService");
		
		//Building the command
		ArrayList<String> command = new ArrayList<>();
		command.add("osascript");
		for(String line : ASFileNew) {
			command.add("-e");
			command.add(String.format(line, delimitedChatMembers.toString(), escapeAppleScriptString(file.getAbsolutePath()), escapeAppleScriptString(service)));
		}
		
		//Running the command
		try {
			Process process = Runtime.getRuntime().exec(command.toArray(new String[0]));
			
			//Returning false if there was any error
			BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
			boolean linesRead = false;
			String lsString;
			while ((lsString = errorReader.readLine()) != null) {
				if(!isFatalResponse(lsString)) continue;
				Main.getLogger().severe(lsString);
				linesRead = true;
			}
			
			if(linesRead) return false;
		} catch(IOException exception) {
			//Printing the stack trace
			Main.getLogger().log(Level.WARNING, exception.getMessage(), exception);
			
			//Returning false
			return false;
		}
		
		//Reindexing the creation targeting index
		if(!isFallback) DatabaseManager.getInstance().requestCreationTargetingAvailabilityUpdate();
		
		//Returning true
		return true;
	}
	
	private static final List<FileUploadRequest> fileUploadRequests = Collections.synchronizedList(new ArrayList<>());
	static void addFileFragment(NetServerManager.SocketManager connection, short requestID, String chatGUID, String fileName, int index, byte[] compressedBytes, boolean isLast) {
		//Attempting to find a matching request
		FileUploadRequest request = null;
		for(FileUploadRequest allRequests : fileUploadRequests) {
			if(allRequests.connection != connection || allRequests.requestID != requestID || allRequests.chatGUID == null || !allRequests.chatGUID.equals(chatGUID)) continue;
			request = allRequests;
			break;
		}
		
		//Checking if the request is invalid (there is no request currently in the list)
		if(request == null) {
			//Returning if this isn't the first request (meaning that the request failed, and shouldn't continue)
			if(index != 0) return;
			
			//Creating and adding a new request
			request = new FileUploadRequest(connection, requestID, chatGUID, fileName);
			fileUploadRequests.add(request);
			
			//Starting the timer
			request.startTimer();
		}
		//Otherwise restarting the timer
		else request.stopTimer(true);
		
		//Adding the file fragment
		request.addFileFragment(new FileUploadRequest.FileFragment(index, compressedBytes, isLast));
	}
	
	static void addFileFragment(NetServerManager.SocketManager connection, short requestID, String[] chatMembers, String service, String fileName, int index, byte[] compressedBytes, boolean isLast) {
		//Attempting to find a matching request
		FileUploadRequest request = null;
		for(FileUploadRequest allRequests : fileUploadRequests) {
			if(allRequests.connection != connection || allRequests.requestID != requestID || allRequests.chatMembers == null || !Arrays.equals(allRequests.chatMembers, chatMembers)) continue;
			request = allRequests;
			break;
		}
		
		//Checking if the request is invalid (there is no request currently in the list)
		if(request == null) {
			//Returning if this isn't the first request (meaning that the request failed, and shouldn't continue)
			if(index != 0) return;
			
			//Creating and adding a new request
			request = new FileUploadRequest(connection, requestID, chatMembers, service, fileName);
			fileUploadRequests.add(request);
			
			//Starting the timer
			request.startTimer();
		}
		//Otherwise restarting the timer
		else request.stopTimer(true);
		
		//Adding the file fragment
		request.addFileFragment(new FileUploadRequest.FileFragment(index, compressedBytes, isLast));
	}
	
	private static class FileUploadRequest {
		//Creating the request variables
		final NetServerManager.SocketManager connection;
		final short requestID;
		final String chatGUID;
		final String[] chatMembers;
		final String service;
		final String fileName;
		
		//Creating the transfer variables
		private Timer timeoutTimer = null;
		private static final int timeout = 10 * 1000; //10 seconds
		
		private AttachmentWriter writerThread = null;
		private int lastIndex = -1;
		
		FileUploadRequest(NetServerManager.SocketManager connection, short requestID, String chatGUID, String fileName) {
			//Setting the variables
			this.connection = connection;
			this.requestID = requestID;
			this.chatGUID = chatGUID;
			this.chatMembers = null;
			this.service = null;
			this.fileName = fileName;
		}
		
		FileUploadRequest(NetServerManager.SocketManager connection, short requestID, String[] chatMembers, String service, String fileName) {
			//Setting the variables
			this.connection = connection;
			this.requestID = requestID;
			this.chatGUID = null;
			this.chatMembers = chatMembers;
			this.service = service;
			this.fileName = fileName;
		}
		
		void addFileFragment(FileFragment fileFragment) {
			//Failing the request if the index doesn't line up
			if(lastIndex + 1 != fileFragment.index) {
				failRequest();
				return;
			}
			
			//Checking if this is the last fragment
			if(fileFragment.isLast) {
				//Stopping the timer
				stopTimer(false);
			}
			
			//Updating the index
			lastIndex = fileFragment.index;
			
			//Checking if there is no writer thread
			if(writerThread == null) {
				//Creating the attachment writer thread
				writerThread = new AttachmentWriter(fileName);
				
				//Starting the thread
				writerThread.start();
			}
			
			//Adding the file fragment
			writerThread.dataQueue.add(fileFragment);
		}
		
		void startTimer() {
			timeoutTimer = new Timer();
			timeoutTimer.schedule(new TimerTask() {
				@Override
				public void run() {
					//Failing the request
					failRequest();
				}
			}, timeout);
		}
		
		void stopTimer(boolean restart) {
			if(timeoutTimer != null) timeoutTimer.cancel();
			if(restart) startTimer();
			else timeoutTimer = null;
		}
		
		private void failRequest() {
			//Removing the request from the list
			fileUploadRequests.remove(this);
			
			//Stopping the timer
			stopTimer(false);
			
			//Stopping the thread
			if(writerThread != null) writerThread.stopThread();
			
			//Sending a negative response
			NetServerManager.sendMessageRequestResponse(connection, requestID, false);
		}
		
		private void onDownloadSuccessful(File file) {
			//Removing the request from the list
			fileUploadRequests.remove(this);
			
			//Sending the file
			boolean result = chatGUID != null ? sendExistingFile(chatGUID, file) : sendNewFile(chatMembers, file, service, false);
			
			//Sending the response
			NetServerManager.sendMessageRequestResponse(connection, requestID, result);
		}
		
		private class AttachmentWriter extends Thread {
			//Creating the queue
			private final BlockingQueue<FileFragment> dataQueue = new LinkedBlockingQueue<>();
			
			//Creating the request values
			private final String fileName;
			
			//Creating the process values
			private File targetDir;
			private File targetFile;
			private final AtomicBoolean requestKill = new AtomicBoolean(false);
			
			AttachmentWriter(String fileName) {
				this.fileName = fileName;
			}
			
			@Override
			public void run() {
				//Creating the upload directory if it doesn't exist
				if(Constants.uploadDir.isFile()) Constants.uploadDir.delete();
				if(!Constants.uploadDir.exists()) Constants.uploadDir.mkdir();
				
				//Finding the save file
				targetDir = Constants.findFreeFile(Constants.uploadDir, Long.toString(System.currentTimeMillis()));
				targetDir.mkdir();
				targetFile = new File(targetDir, fileName);
				
				try(FileOutputStream out = new FileOutputStream(targetFile)) {
					while(!requestKill.get()) {
						//Getting the data struct
						FileFragment fileFragment = dataQueue.poll(timeout, TimeUnit.MILLISECONDS);
						
						//Skipping the remainder of the iteration if the file fragment is invalid
						if(fileFragment == null) continue;
						
						//Writing the file to disk
						out.write(Constants.decompressGZIP(fileFragment.compressedData));
						
						//Checking if the file is the last one
						if(fileFragment.isLast) {
							//Calling the download successful method
							onDownloadSuccessful(targetFile);
							
							//Returning
							return;
						}
					}
				} catch(IOException | InterruptedException | OutOfMemoryError exception) {
					//Printing the stack trace
					Main.getLogger().log(Level.WARNING, exception.getMessage(), exception);
					Sentry.capture(exception);
					
					//Failing the download
					failRequest();
					
					//Terminating the thread
					requestKill.set(true);
				}
				
				//Checking if the thread was stopped
				if(requestKill.get()) {
					//Cleaning up
					Constants.recursiveDelete(targetDir);
				}
			}
			
			void stopThread() {
				requestKill.set(true);
			}
		}
		
		static class FileFragment {
			int index;
			byte[] compressedData;
			boolean isLast;
			
			FileFragment(int index, byte[] compressedData, boolean isLast) {
				this.index = index;
				this.compressedData = compressedData;
				this.isLast = isLast;
			}
		}
	}
	
	private static String escapeAppleScriptString(String string) {
		return string.replace("\\", "\\\\").replace("\"", "\\\"");
	}
	
	private static boolean isFatalResponse(String line) {
		return line.startsWith("execution error");
	}
}