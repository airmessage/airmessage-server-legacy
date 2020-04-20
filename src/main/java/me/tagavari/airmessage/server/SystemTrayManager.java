package me.tagavari.airmessage.server;

import me.tagavari.airmessage.connection.ConnectionManager;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.*;

import java.io.IOException;
import java.text.MessageFormat;
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
		miStatus.setText(Main.resources().getString("message.status.starting"));
		miStatus.setEnabled(false);
		
		//Clients connected
		miStatusSub = new MenuItem(menu, SWT.PUSH);
		miStatusSub.setText(MessageFormat.format(Main.resources().getString("message.status.connected_count"), 0));
		miStatusSub.setEnabled(false);
		miStatusSub.addListener(SWT.Selection, event -> Main.startServer());
		
		//Divider
		new MenuItem(menu, SWT.SEPARATOR);
		
		//Preferences
		MenuItem miPrefs = new MenuItem(menu, SWT.PUSH);
		miPrefs.setText(Main.resources().getString("action.preferences"));
		miPrefs.addListener(SWT.Selection, event -> PreferencesManager.openWindow());
		
		//Check for updates
		MenuItem miUpdate = new MenuItem(menu, SWT.PUSH);
		miUpdate.setText(Main.resources().getString("action.check_update"));
		miUpdate.addListener(SWT.Selection, event -> UpdateManager.requestManualUpdateCheck());
		
		//Divider
		new MenuItem(menu, SWT.SEPARATOR);
		
		//Quit
		MenuItem miQuit = new MenuItem(menu, SWT.PUSH);
		miQuit.setText(Main.resources().getString("action.quit_app"));
		miQuit.addListener(SWT.Selection, event -> System.exit(0));
		
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
	
	public static void updateStatusMessage() {
		//Getting the message
		switch(Main.getServerState()) {
			case Main.serverStateStarting:
				miStatus.setText(Main.resources().getString("message.status.starting"));
				miStatusSub.setText(MessageFormat.format(Main.resources().getString("message.status.connected_count"), 0));
				miStatusSub.setEnabled(false);
				break;
			case Main.serverStateRunning:
				miStatus.setText(Main.resources().getString("message.status.running"));
				miStatusSub.setText(MessageFormat.format(Main.resources().getString("message.status.connected_count"), ConnectionManager.getConnectionCount()));
				miStatusSub.setEnabled(false);
				break;
			case Main.serverStateFailedDatabase:
				miStatus.setText(Main.resources().getString("message.status.error.database"));
				miStatusSub.setText(Main.resources().getString("action.retry"));
				miStatusSub.setEnabled(true);
				break;
			case Main.serverStateFailedServerPort:
				miStatus.setText(Main.resources().getString("message.status.error.port"));
				miStatusSub.setText(Main.resources().getString("action.retry"));
				miStatusSub.setEnabled(true);
				break;
			case Main.serverStateFailedServerInternal:
				miStatus.setText(Main.resources().getString("message.status.error.internal"));
				miStatusSub.setText(Main.resources().getString("action.retry"));
				miStatusSub.setEnabled(true);
				break;
		}
	}
	
	public static void updateConnectionsMessage() {
		//Returning if the state isn't connected
		if(Main.getServerState() != Main.serverStateRunning) return;
		
		//Updating the message
		miStatusSub.setText(MessageFormat.format(Main.resources().getString("message.status.connected_count"), ConnectionManager.getConnectionCount()));
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