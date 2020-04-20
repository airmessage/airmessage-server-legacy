package me.tagavari.airmessage.connection.direct;

import io.sentry.Sentry;
import me.tagavari.airmessage.connection.CommConst;
import me.tagavari.airmessage.connection.ConnectionManager;
import me.tagavari.airmessage.connection.DataProxy;
import me.tagavari.airmessage.server.Constants;
import me.tagavari.airmessage.server.Main;

import java.net.ServerSocket;
import java.util.*;
import java.util.logging.Level;

public class DataProxyTCP extends DataProxy<ClientSocket> implements ListenerThreadListener {
	//Creating the state values
	private boolean serverRunning = false;
	private final List<ClientSocket> connectionList = Collections.synchronizedList(new ArrayList<>());
	
	private final int port; //The port to run the next server on
	private int launchedPort = -1; //The port the server is currently running on
	
	//Creating the thread values
	private ListenerThread listenerThread;
	private WriterThread writerThread;
	
	public DataProxyTCP(int port) {
		this.port = port;
	}
	
	@Override
	public void startServer() {
		//Returning if the server is already running
		if(serverRunning) return;
		
		//Returning if the requested port is already bound
		if(!Constants.checkPortAvailability(port)) {
			notifyStop(createServerResultPort);
			return;
		}
		
		try {
			//Creating the server socket
			ServerSocket serverSocket = new ServerSocket(port);
			
			//Starting the listener thread
			listenerThread = new ListenerThread(serverSocket, this);
			listenerThread.start();
		} catch(Exception exception) {
			Main.getLogger().log(Level.SEVERE, exception.getMessage(), exception);
			Sentry.capture(exception);
			notifyStop(createServerResultInternal);
			return;
		}
		
		//Starting the writer thread
		writerThread = new WriterThread(connectionList);
		writerThread.start();
		
		//Setting the port
		serverRunning = true;
		launchedPort = port;
		
		//Notifying the listeners
		notifyStart();
	}
	
	@Override
	public void acceptClient(ClientSocket client) {
		//Adding the client
		connectionList.add(client);
		
		//Notifying the communications manager
		notifyOpen(client);
	}
	
	@Override
	public void processData(ClientSocket client, int type, byte[] data) {
		//Notifying the communications manager
		notifyMessage(client, type, data);
	}
	
	@Override
	public void cancelConnection(ClientSocket client, boolean cleanup) {
		if(cleanup) ConnectionManager.getCommunicationsManager().initiateClose(client);
		else disconnectClient(client);
	}
	
	@Override
	public void stopServer() {
		//Returning if the server isn't running
		if(!serverRunning) return;
		
		//Stopping the threads
		if(listenerThread != null) listenerThread.closeAndInterrupt();
		if(writerThread != null) writerThread.interrupt();
		
		//Closing connected client connections
		for(ClientSocket client : new HashSet<>(connectionList)) client.disconnect();
		
		//Updating the server state
		serverRunning = false;
		
		//Notifying the listeners
		notifyStop(createServerResultOK);
	}
	
	@Override
	public void sendMessage(ClientSocket client, int type, byte[] content, Runnable sentRunnable) {
		writerThread.sendPacket(new WriterThread.PacketStruct(client, type, content, sentRunnable));
	}
	
	@Override
	public void disconnectClient(ClientSocket client) {
		//Disconnecting the client
		client.disconnect();
		
		//Unlisting the client's connection
		connectionList.remove(client);
		
		//Notifying the communications manager
		notifyClose(client);
	}
	
	@Override
	public Collection<ClientSocket> getConnections() {
		return connectionList;
	}
}