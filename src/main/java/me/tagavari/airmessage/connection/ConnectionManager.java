package me.tagavari.airmessage.connection;

import me.tagavari.airmessage.connection.direct.DataProxyTCP;
import me.tagavari.airmessage.server.Main;
import me.tagavari.airmessage.server.PreferencesManager;

/**
 * Controls the server connection
 * Handles interfacing between the data proxy and the data handler
 */
public class ConnectionManager {
	private static DataProxy dataProxy;
	private static CommunicationsManager communicationsManager;
	
	public static void setDataProxy(DataProxy dataProxy) {
		ConnectionManager.dataProxy = dataProxy;
	}
	
	public static void assignDataProxy() {
		DataProxyTCP dataProxy = new DataProxyTCP(PreferencesManager.getPrefServerPort());
		setDataProxy(dataProxy);
	}
	
	public static void start() {
		//Returning if there is already an active process
		if(communicationsManager != null && communicationsManager.isRunning()) return;
		
		//Starting the communications manager
		communicationsManager = new CommunicationsManager(dataProxy);
		communicationsManager.start();
	}
	
	public static void stop() {
		//Returning if there is no active process
		if(communicationsManager == null || !communicationsManager.isRunning()) return;
		
		//Stopping the communications manager
		communicationsManager.stop();
	}
	
	public static CommunicationsManager getCommunicationsManager() {
		return communicationsManager;
	}
	
	public static DataProxy<ClientRegistration> activeProxy() {
		return communicationsManager.getDataProxy();
	}
	
	public static int getConnectionCount() {
		if(communicationsManager == null) return 0;
		return communicationsManager.getDataProxy().getConnections().size();
	}
	
	static int proxyErrorToServerState(int value) {
		switch(value) {
			default:
				throw new IllegalArgumentException("Expected a create server result error; instead got " + value);
			case DataProxy.createServerResultOK:
				return Main.serverStateStarting;
			case DataProxy.createServerResultPort:
				return Main.serverStateFailedServerPort;
			case DataProxy.createServerResultInternal:
				return Main.serverStateFailedServerInternal;
		}
	}
}