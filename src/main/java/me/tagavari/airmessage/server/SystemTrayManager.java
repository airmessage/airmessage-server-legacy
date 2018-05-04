package me.tagavari.airmessage.server;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.*;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class SystemTrayManager {
	//Creating the images
	private static final Image iconDark;
	private static final Image iconLight;
	
	//Creating the menu items
	private static Shell shell;
	//private static TrayIcon trayIcon;
	private static MenuItem miStatus;
	private static MenuItem miStatusSub;
	
	static {
		//Creating the shell
		shell = new Shell(UIHelper.getDisplay());
		
		//Image.cocoa_new()
		//iconDark = Image.cocoa_new(shell.getDisplay(), SWT.ICON, NSImage.imageNamed(NSString.stringWith("statusicon_black.png")));
		iconDark = new Image(shell.getDisplay(), Main.class.getClassLoader().getResourceAsStream("statusicon_black.png"));
		iconLight = new Image(shell.getDisplay(), Main.class.getClassLoader().getResourceAsStream("statusicon_white.png"));
	}
	
	public static boolean setupSystemTray() {
		//Creating the menu
		Menu menu = new Menu(shell, SWT.POP_UP);
		
		//Server status
		miStatus = new MenuItem(menu, SWT.PUSH);
		miStatus.setText(I18N.i.menu_starting());
		miStatus.setEnabled(false);
		
		//Clients connected
		miStatusSub = new MenuItem(menu, SWT.PUSH);
		miStatusSub.setText(I18N.i.menu_clientsConnected(0));
		miStatusSub.setEnabled(false);
		miStatusSub.addListener(SWT.Selection, event -> Main.startServer());
		
		//Divider
		new MenuItem(menu, SWT.SEPARATOR);
		
		//Preferences
		MenuItem miPrefs = new MenuItem(menu, SWT.PUSH);
		miPrefs.setText(I18N.i.menu_preferences());
		miPrefs.addListener(SWT.Selection, event -> PreferencesManager.openWindow());
		
		//Check for updates
		MenuItem miUpdate = new MenuItem(menu, SWT.PUSH);
		miUpdate.setText(I18N.i.menu_checkForUpdates());
		miUpdate.addListener(SWT.Selection, event -> UpdateManager.requestManualUpdateCheck());
		
		//Divider
		new MenuItem(menu, SWT.SEPARATOR);
		
		//Quit
		MenuItem miQuit = new MenuItem(menu, SWT.PUSH);
		miQuit.setText(I18N.i.button_quitAirMessage());
		miQuit.addListener(SWT.Selection, event -> System.exit(0));;
		
		//Creating the tray item
		TrayItem trayItem = new TrayItem(shell.getDisplay().getSystemTray(), SWT.NONE);
		trayItem.addListener(SWT.MenuDetect, event -> {
			//Updating the images
			trayItem.setImage(getTrayIcon());
			trayItem.setHighlightImage(getHighlightTrayIcon());
			
			//Opening the menu
			menu.setVisible(true);
		});
		trayItem.setImage(getTrayIcon());
		trayItem.setHighlightImage(getHighlightTrayIcon());
		
		/*while (!shell.isDisposed()) {
			if (!shell.getDisplay().readAndDispatch())
				shell.getDisplay().sleep();
		}
		shell.dispose();
		iconLight.dispose();
		iconDark.dispose();*/
		/* trayIcon = new TrayIcon(getTrayIcon());
		trayIcon.setPopupMenu(popupMenu);
		
		try {
			SystemTray.getSystemTray().add(trayIcon);
		} catch(AWTException exception) {
			//Printing the stack trace
			Main.getLogger().log(Level.WARNING, exception.getMessage(), exception);
			
			//Returning false
			return false;
		}
		
		//Adding the update listener
		trayIcon.addMouseListener(new MouseListener() {
			@Override
			public void mouseClicked(MouseEvent e) {}
			
			@Override
			public void mousePressed(MouseEvent e) {
				//Updating the icon
				trayIcon.setImage(getTrayIcon());
			}
			
			@Override
			public void mouseReleased(MouseEvent e) {}
			
			@Override
			public void mouseEntered(MouseEvent e) {}
			
			@Override
			public void mouseExited(MouseEvent e) {}
		}); */
		
		//Returning true
		return true;
	}
	
	static void updateStatusMessage() {
		//Getting the message
		switch(Main.getServerState()) {
			case Main.serverStateStarting:
				miStatus.setText(I18N.i.menu_starting());
				miStatusSub.setText(I18N.i.menu_clientsConnected(0));
				miStatusSub.setEnabled(false);
				break;
			case Main.serverStateRunning:
				miStatus.setText(I18N.i.menu_running());
				miStatusSub.setText(I18N.i.menu_clientsConnected(NetServerManager.getConnectionCount()));
				miStatusSub.setEnabled(false);
				break;
			case Main.serverStateFailedDatabase:
				miStatus.setText(I18N.i.menu_err_database());
				miStatusSub.setText(I18N.i.button_retry());
				miStatusSub.setEnabled(true);
				break;
			case Main.serverStateFailedServerPort:
				miStatus.setText(I18N.i.menu_err_server_port());
				miStatusSub.setText(I18N.i.button_retry());
				miStatusSub.setEnabled(true);
				break;
			case Main.serverStateFailedServerInternal:
				miStatus.setText(I18N.i.menu_err_server_internal());
				miStatusSub.setText(I18N.i.button_retry());
				miStatusSub.setEnabled(true);
				break;
		}
	}
	
	static void updateConnectionsMessage() {
		//Returning if the state isn't connected
		if(Main.getServerState() != Main.serverStateRunning) return;
		
		//Updating the message
		miStatusSub.setText(I18N.i.menu_clientsConnected(NetServerManager.getConnectionCount()));
	}
	
	private static Image getTrayIcon() {
		if(checkDarkMode()) return iconLight;
		else return iconDark;
	}
	
	private static Image getHighlightTrayIcon() {
		return iconLight;
	}
	
	private static boolean checkDarkMode() {
		try {
			//Reading the system value
			Process process = Runtime.getRuntime().exec(new String[] {"defaults", "read", "-g", "AppleInterfaceStyle"});
			process.waitFor(100, TimeUnit.MILLISECONDS);
			return process.exitValue() == 0;
		} catch (IOException | InterruptedException | IllegalThreadStateException exception) {
			//Returning false if the value couldn't be read
			return false;
		}
	}
}