package me.tagavari.airmessage.connection.direct;

import io.sentry.Sentry;
import io.sentry.event.BreadcrumbBuilder;
import me.tagavari.airmessage.connection.CommConst;
import me.tagavari.airmessage.server.Constants;
import me.tagavari.airmessage.server.Main;

import javax.net.ssl.SSLException;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.SocketException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

class ReaderThread extends Thread {
	private final DataInputStream inputStream;
	private final ReaderThreadListener listener;
	
	ReaderThread(DataInputStream inputStream, ReaderThreadListener listener) {
		this.inputStream = inputStream;
		this.listener = listener;
	}
	
	@Override
	public void run() {
		while(!isInterrupted()) {
			try {
				//Reading the header data
				int messageType = inputStream.readInt();
				int contentLen = inputStream.readInt();
				
				//Adding a breadcrumb
				{
					Map<String, String> dataMap = new HashMap<>(2);
					dataMap.put("Message type", Integer.toString(messageType));
					dataMap.put("Content length", Integer.toString(contentLen));
					Sentry.getContext().recordBreadcrumb(new BreadcrumbBuilder().setCategory(Constants.sentryBCatPacket).setMessage("New packet received").setData(dataMap).build());
				}
				
				Main.getLogger().log(Level.FINEST, "New message received: " + messageType + " / " + contentLen);
				//Checking if the content length is greater than the maximum packet allocation
				if(contentLen > CommConst.maxPacketAllocation) {
					//Logging the error
					Main.getLogger().log(Level.WARNING, "Rejecting large packet (size " + contentLen + ")");
					Sentry.getContext().recordBreadcrumb(new BreadcrumbBuilder().setCategory(Constants.sentryBCatPacket).setMessage("Rejecting large packet (type: " + messageType + " - size: " + contentLen + ")").build());
					
					//Closing the connection
					listener.cancelConnection(true);
					return;
				}
				
				//Reading the content
				byte[] content = new byte[contentLen];
				inputStream.readFully(content);
				
				//Processing the data
				listener.processData(messageType, content);
			} catch(OutOfMemoryError exception) {
				//Logging the error
				Main.getLogger().log(Level.WARNING, exception.getMessage(), exception);
				Sentry.capture(exception);
				
				//Closing the connection
				listener.cancelConnection(true);
				
				//Breaking
				break;
			} catch(SocketException | SSLException | EOFException | RuntimeException exception) {
				Main.getLogger().log(Level.WARNING, exception.getMessage(), exception);
				
				//A low-level socket exception occurred, close forcefully
				listener.cancelConnection(false);
				
				//Breaking
				break;
			} catch(IOException exception) {
				//Logging the error
				Main.getLogger().log(Level.WARNING, exception.getMessage(), exception);
				
				//Closing the connection
				listener.cancelConnection(true);
				
				//Breaking
				break;
			}
		}
	}
}