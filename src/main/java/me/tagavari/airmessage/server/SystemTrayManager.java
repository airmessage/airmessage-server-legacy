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
	private static MenuItem miConnected;
	
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
		
		//Client connected
		miConnected = new MenuItem(menu, SWT.PUSH);
		miConnected.setText(I18N.i.menu_clientsConnected(0));
		miConnected.setEnabled(false);
		
		//Divider
		new MenuItem(menu, SWT.SEPARATOR);
		
		//Preferences
		MenuItem miPrefs = new MenuItem(menu, SWT.PUSH);
		miPrefs.setText(I18N.i.menu_preferences());
		miPrefs.addListener(SWT.Selection, event -> openPreferencesWindow());
		
		//Divider
		new MenuItem(menu, SWT.SEPARATOR);
		
		//Quit
		MenuItem miQuit = new MenuItem(menu, SWT.PUSH);
		miQuit.setText(I18N.i.button_quitAirMessage());
		miQuit.addListener(SWT.Selection,event -> System.exit(0));;
		
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
			exception.printStackTrace();
			
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
	
	private static void openPreferencesWindow() {
	
	}
	
	private static void openIntroWindow() {
	
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