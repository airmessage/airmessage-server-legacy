package me.tagavari.airmessage.server;

import me.tagavari.airmessage.common.SharedValues;
import org.java_websocket.WebSocket;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;
import java.util.zip.DataFormatException;

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
			"set AppleScript's text item delimiters to oldDelimiters", */
			"set recipientList to {%1$s}",
			
			//Converting the recipients to iMessage buddies
			"set buddyList to {}",
			"repeat with currentRecipient in recipientList",
			"set currentBuddy to buddy currentRecipient of targetService",
			"copy currentBuddy to the end of buddyList",
			"end repeat",
			
			//Creating the chat
			"set targetChat to make new text chat with properties {participants:buddyList}",
			
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
			"set AppleScript's text item delimiters to oldDelimiters", */
			"set recipientList to {%1$s}",
			
			//Converting the recipients to iMessage buddies
			"set buddyList to {}",
			"repeat with currentRecipient in recipientList",
			"set currentBuddy to buddy currentRecipient of targetService",
			"copy currentBuddy to the end of buddyList",
			"end repeat",
			
			//Creating the chat
			"set targetChat to make new text chat with properties {participants:buddyList}",
			
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
			exception.printStackTrace();
			
			//Returning false
			return false;
		}
		
		//Returning true
		return true;
	}
	
	static boolean sendNewMessage(String[] chatMembers, String message, String service) {
		//Returning false if there are no members
		if(chatMembers.length == 0) return false;
		
		//Formatting the chat members
		StringBuilder delimitedChatMembers = new StringBuilder('"' + escapeAppleScriptString(chatMembers[0]) + '"');
		
		//Adding the remaining members
		for(int i = 1; i < chatMembers.length; i++) delimitedChatMembers.append(',').append('"').append(escapeAppleScriptString(chatMembers[i])).append('"');
		Main.getLogger().fine("Making new conversation with targets: " + delimitedChatMembers.toString());
		
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
			exception.printStackTrace();
			
			//Returning false
			return false;
		}
		
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
			exception.printStackTrace();
			
			//Returning false
			return false;
		}
			
			//Returning true
		return true;
	}
	
	static boolean sendNewFile(String[] chatMembers, File file, String service) {
		//Returning false if there are no members
		if(chatMembers.length == 0) return false;
		
		//Formatting the chat members
		StringBuilder delimitedChatMembers = new StringBuilder(escapeAppleScriptString(chatMembers[0]));
		
		//Adding the remaining members
		for(int i = 1; i < chatMembers.length; i++) delimitedChatMembers.append(',').append('"').append(escapeAppleScriptString(chatMembers[i])).append('"');
		Main.getLogger().fine("New conversation targets: " + delimitedChatMembers.toString());
		
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
				Main.getLogger().severe(lsString);
				linesRead = true;
			}
			
			if(linesRead) return false;
		} catch(IOException exception) {
			//Printing the stack trace
			exception.printStackTrace();
			
			//Returning false
			return false;
		}
		
		//Returning true
		return true;
	}
	
	private static final ArrayList<FileUploadRequest> fileUploadRequests = new ArrayList<>();
	static void addFileFragment(WebSocket connection, short requestID, String chatGUID, String fileName, int index, byte[] compressedBytes, boolean isLast) {
		//Attempting to find a matching request
		FileUploadRequest request = null;
		for(FileUploadRequest allRequests : fileUploadRequests) {
			if(!allRequests.connection.equals(connection) || allRequests.requestID != requestID || allRequests.chatGUID == null || !allRequests.chatGUID.equals(chatGUID)) continue;
			request = allRequests;
			break;
		}
		
		//Checking if the request is invalid (there is no request currently in the list)
		if(request == null) {
			//Returning if this isn't the first request (meaning that the request failed, and shouldn't continue)
			if(index != 0) return;
			
			//Creating a new request
			request = new FileUploadRequest(connection, requestID, chatGUID, fileName);
			fileUploadRequests.add(request);
		}
		//Otherwise restarting the timer
		else request.stopTimer(true);
		
		//Adding the file fragment
		request.addFileFragment(new FileUploadRequest.FileFragment(index, compressedBytes, isLast));
	}
	
	static void addFileFragment(WebSocket connection, short requestID, String[] chatMembers, String service, String fileName, int index, byte[] compressedBytes, boolean isLast) {
		//Attempting to find a matching request
		FileUploadRequest request = null;
		for(FileUploadRequest allRequests : fileUploadRequests) {
			if(!allRequests.connection.equals(connection) || allRequests.requestID != requestID || allRequests.chatMembers == null || !Arrays.equals(allRequests.chatMembers, chatMembers)) continue;
			request = allRequests;
			break;
		}
		
		//Checking if the request is invalid (there is no request currently in the list)
		if(request == null) {
			//Returning if this isn't the first request (meaning that the request failed, and shouldn't continue)
			if(index != 0) return;
			
			//Creating a new request
			request = new FileUploadRequest(connection, requestID, chatMembers, service, fileName);
			fileUploadRequests.add(request);
		}
		//Otherwise restarting the timer
		else request.stopTimer(true);
		
		//Adding the file fragment
		request.addFileFragment(new FileUploadRequest.FileFragment(index, compressedBytes, isLast));
	}
	
	private static class FileUploadRequest {
		//Creating the request variables
		final WebSocket connection;
		final short requestID;
		final String chatGUID;
		final String[] chatMembers;
		final String service;
		final String fileName;
		File saveFile;
		
		//Creating the transfer variables
		private Timer timeoutTimer = null;
		private static final int timeout = 10 * 1000; //10 seconds
		
		private AttachmentWriter writerThread = null;
		private final Object queuedFileFragmentsLock = new Object();
		private ArrayList<FileFragment> queuedFileFragments = new ArrayList<>();
		private int requiredIndex = 0;
		
		FileUploadRequest(WebSocket connection, short requestID, String chatGUID, String fileName) {
			//Setting the variables
			this.connection = connection;
			this.requestID = requestID;
			this.chatGUID = chatGUID;
			this.chatMembers = null;
			this.service = null;
			this.fileName = fileName;
		}
		
		FileUploadRequest(WebSocket connection, short requestID, String[] chatMembers, String service, String fileName) {
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
			if(requiredIndex != fileFragment.index) {
				failRequest();
				return;
			}
			
			//Checking if this is the last fragment
			if(fileFragment.isLast) {
				//Stopping the timer
				stopTimer(false);
			}
			
			//Advancing the index
			requiredIndex++;
			
			//Checking if there is no save thread
			if(writerThread == null) {
				//Creating the attachment writer thread
				try {
					writerThread = new AttachmentWriter(fileName);
				} catch(IOException exception) {
					//Printing the stack trace
					exception.printStackTrace();
					
					//Failing the request
					failRequest();
					
					//Returning
					return;
				}
				
				//Starting the thread
				writerThread.start();
			}
			
			//Adding the file fragment
			synchronized(queuedFileFragmentsLock) {
				queuedFileFragments.add(fileFragment);
				queuedFileFragmentsLock.notifyAll();
			}
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
			
			//Deleting the file
			if(saveFile != null) {
				if(saveFile.exists()) saveFile.delete();
				saveFile.getParentFile().delete();
			}
			
			//Sending a negative response
			WSServerManager.sendMessageResponse(connection, requestID, false);
		}
		
		private void onDownloadSuccessful() {
			//Removing the request from the list
			fileUploadRequests.remove(this);
			
			//Sending the file
			boolean result = chatGUID != null ? sendExistingFile(chatGUID, saveFile) : sendNewFile(chatMembers, saveFile, service);
			
			//Sending the response
			WSServerManager.sendMessageResponse(connection, requestID, result);
		}
		
		private class AttachmentWriter extends Thread {
			//Creating the variables
			FileOutputStream outputStream = null;
			
			AttachmentWriter(String fileName) throws IOException {
				//Creating the upload directory if it doesn't exist
				if(Constants.uploadDir.isFile()) Constants.uploadDir.delete();
				if(!Constants.uploadDir.exists()) Constants.uploadDir.mkdir();
				
				//Finding the save file
				saveFile = Constants.findFreeFile(Constants.uploadDir, Long.toString(System.currentTimeMillis()));
				saveFile.mkdir();
				saveFile = new File(saveFile, fileName);
				outputStream = new FileOutputStream(saveFile);
			}
			
			@Override
			public void run() {
				while(true) {
					//Moving the file fragments (to be able to release the lock sooner)
					ArrayList<FileFragment> localQueuedFileFragments = new ArrayList<>();
					synchronized(queuedFileFragmentsLock) {
						if(!queuedFileFragments.isEmpty()) {
							localQueuedFileFragments = queuedFileFragments;
							queuedFileFragments = new ArrayList<>();
						}
					}
					
					
					//Iterating over the queued file fragments
					for(FileFragment fileFragment : localQueuedFileFragments) {
						//Writing the file to disk
						try {
							outputStream.write(SharedValues.decompress(fileFragment.compressedData));
						} catch(IOException | DataFormatException exception) {
							//Printing the stack trace
							exception.printStackTrace();
							
							//Deleting the file
							saveFile.delete();
							
							//Cleaning the thread
							cleanThread();
							
							//Failing the request
							failRequest();
							
							//Returning
							return;
						}
						
						//Checking if the file is the last one
						if(fileFragment.isLast) {
							//Cleaning the thread
							cleanThread();
							
							//Calling the download successful method
							onDownloadSuccessful();
							
							//Returning
							return;
						}
					}
					
					//Waiting for entries to appear
					try {
						synchronized(queuedFileFragmentsLock) {
							queuedFileFragmentsLock.wait(10 * 1000); //10-second timeout
						}
					} catch(InterruptedException exception) {
						//Cleaning up the thread
						cleanThread();
						
						//Failing the request
						failRequest();
						
						//Returning
						return;
					}
				}
			}
			
			void cleanThread() {
				if(outputStream == null) return;
				
				try {
					//Closing the output stream
					outputStream.close();
				} catch(IOException exception) {
					//Printing the exception's stack trace
					exception.printStackTrace();
				}
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
}