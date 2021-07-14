package me.tagavari.airmessageserver.server;

import io.sentry.Sentry;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

import java.util.logging.Level;

public class UIHelper {
	//Creating the reference values
	static final int windowMargin = 5;
	static final int sheetMargin = 20;
	static final int dialogButtonBarMargin = 8;
	static final int minButtonWidth = 100;
	static final int smallMinButtonWidth = 80;
	private static Display display;
	
	public static void initialize() {
		display = Display.getCurrent();
		if(display == null) display = Display.getDefault(); //The display may be null if outside the UI thread
	}
	
	public static boolean displayVersionWarning() {
		//Showing an alert dialog
		Shell shell = new Shell();
		MessageBox dialog = new MessageBox(shell, SWT.ICON_WARNING | SWT.YES | SWT.NO);
		dialog.setMessage(Main.resources().getString("message.os_unsupported"));
		int result = dialog.open();
		if(!shell.isDisposed()) shell.dispose();
		
		//Returning the result
		return result == SWT.YES;
	}
	
	public static int displaySchemaWarning() {
		//Showing an alert dialog
		Shell shell = new Shell();
		MessageBox dialog = new MessageBox(shell, SWT.ICON_WARNING | SWT.ABORT | SWT.RETRY | SWT.IGNORE);
		dialog.setMessage(Main.resources().getString("message.schema_upgrade_error"));
		int result = dialog.open();
		if(!shell.isDisposed()) shell.dispose();
		
		//Returning the result
		switch(result) {
			default:
			case SWT.ABORT:
				return 0;
			case SWT.RETRY:
				return 1;
			case SWT.IGNORE:
				return 2;
		}
	}
	
	public static void displayAutomationWarning() {
		AppleScriptManager.showAutomationWarning();
	}
	
	public static void displayDiskAccessWarning() {
		AppleScriptManager.showDiskAccessWarning();
	}
	
	public static void openIntroWindow() {
		//Creating the shell
		Shell shell = new Shell(display, SWT.NONE);
		
		//Configuring the layout
		/* RowLayout rowLayout = new RowLayout();
		rowLayout.wrap = false;
		rowLayout.pack = true;
		rowLayout.justify = false;
		rowLayout.type = SWT.VERTICAL;
		rowLayout.marginLeft = windowMargin;
		rowLayout.marginTop = windowMargin;
		rowLayout.marginRight = windowMargin;
		rowLayout.marginBottom = windowMargin;
		rowLayout.spacing = 5; */
		GridLayout gridLayout = new GridLayout();
		gridLayout.numColumns = 1;
		gridLayout.marginLeft = 10;
		gridLayout.marginTop = 10;
		gridLayout.marginRight = 10;
		gridLayout.marginBottom = 10;
		gridLayout.verticalSpacing = 10;
		shell.setLayout(gridLayout);
		
		//Creating the UI components
		{
			Label titleLabel = new Label(shell, SWT.NONE);
			titleLabel.setText(Main.resources().getString("message.intro.title"));
			titleLabel.setFont(getFont(titleLabel.getFont(), 20, SWT.BOLD));
		}
		
		{
			Label subtitleLabel = new Label(shell, SWT.NONE);
			subtitleLabel.setText(Main.resources().getString("message.intro.body"));
		}
		
		{
			Composite buttonContainer = new Composite(shell, SWT.NONE);
			buttonContainer.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_END));
			//Configuring the buttons
			{
				FormLayout buttonContainerFL = new FormLayout();
				//buttonContainerFL.marginLeft = buttonContainerFL.marginRight = buttonContainerFL.marginTop = buttonContainerFL.marginBottom = buttonContainerFL.marginWidth = buttonContainerFL.marginHeight = 0;
				buttonContainer.setLayout(buttonContainerFL);
				
				Button accountButton = new Button(buttonContainer, SWT.PUSH);
				accountButton.setText(Main.resources().getString("action.connect_account"));
				FormData accountButtonFD = new FormData();
				if(accountButton.computeSize(SWT.DEFAULT, SWT.DEFAULT).x < UIHelper.minButtonWidth) accountButtonFD.width = UIHelper.minButtonWidth;
				accountButtonFD.right = new FormAttachment(100);
				accountButtonFD.top = new FormAttachment(50, -accountButton.computeSize(SWT.DEFAULT, SWT.DEFAULT).y / 2);
				accountButton.setLayoutData(accountButtonFD);
				accountButton.addListener(SWT.Selection, event -> {
					ConnectAccountManager.openAccountWindow(shell);
				});
				shell.setDefaultButton(accountButton);
				
				Button manualButton = new Button(buttonContainer, SWT.PUSH);
				manualButton.setText(Main.resources().getString("action.setup_manual"));
				FormData manualButtonFD = new FormData();
				if(manualButton.computeSize(SWT.DEFAULT, SWT.DEFAULT).x < UIHelper.minButtonWidth) manualButtonFD.width = UIHelper.minButtonWidth;
				manualButtonFD.right = new FormAttachment(accountButton);
				manualButtonFD.top = new FormAttachment(accountButton, 0, SWT.CENTER);
				manualButton.setLayoutData(manualButtonFD);
				manualButton.addListener(SWT.Selection, event -> {
					PreferencesUI.openPrefsPasswordWindow(shell, false, () -> {
						//Closing the shell
						shell.close();
						
						//Saving the new initialized state
						PreferencesManager.setPrefAccountType(PreferencesManager.accountTypeDirect);
						PreferencesManager.setPrefAccountConfirmed(true);
						PreferencesManager.setPrefServerPort(PreferencesManager.defaultPort);
						
						//Running the permission check
						Main.runPermissionCheck();
						
						//Disabling setup mode
						Main.setSetupMode(false);
						
						//Starting the server
						Main.startServer();
					});
				});
			}
		}
		
		//Packing the shell
		shell.pack();
		
		//Getting the bounds
		Rectangle screenBounds = display.getPrimaryMonitor().getBounds();
		Rectangle windowBounds = shell.getBounds();
		
		//Centering the window
		shell.setLocation(screenBounds.x + (screenBounds.width - windowBounds.width) / 2, screenBounds.y + (screenBounds.height - windowBounds.height) / 2);
		
		//Opening the shell
		shell.open();
		shell.forceActive();
		
		//Registering a mouse tracker for the shell
		new MouseTracker().registerShell(shell, shell);
	}
	
	public static void displayAlertDialog(String message) {
		//Showing an alert dialog
		Shell shell = new Shell();
		MessageBox dialog = new MessageBox(shell, SWT.ICON_WARNING | SWT.OK);
		dialog.setMessage(message);
		dialog.open();
		if(!shell.isDisposed()) shell.dispose();
	}
	
	public static void startEventLoop() {
		try {
			while(!display.isDisposed()) if(!display.readAndDispatch()) display.sleep();
		} catch(Exception exception) {
			Sentry.captureException(exception);
			Main.getLogger().log(Level.SEVERE, exception.getMessage(), exception);
		}
		System.exit(0);
	}
	
	/* private static Region createRoundedRectangle(int x, int y, int W, int H, int r) {
		Region region = new Region();
		int d = (2 * r); // diameter
		
		region.add(circle(r, (x + r), (y + r)));
		region.add(circle(r, (x + W - r), (y + r)));
		region.add(circle(r, (x + W - r), (y + H - r)));
		region.add(circle(r, (x + r), (y + H - r)));
		
		region.add((x + r), y, (W - d), H);
		region.add(x, (y + r), W, (H - d));
		
		return region;
	}
	
	private static int[] circle(int r, int offsetX, int offsetY) {
		int[] polygon = new int[8 * r + 4];
		// x^2 + y^2 = r^2
		for (int i = 0; i < 2 * r + 1; i++) {
			int x = i - r;
			int y = (int) Math.sqrt(r * r - x * x);
			polygon[2 * i] = offsetX + x;
			polygon[2 * i + 1] = offsetY + y;
			polygon[8 * r - 2 * i - 2] = offsetX + x;
			polygon[8 * r - 2 * i - 1] = offsetY - y;
		}
		return polygon;
	} */
	
	public static Font getFont(Font font, int fontSize, int style) {
		//Getting the font data
		FontData fontData = font.getFontData()[0];
		
		//Setting the font information
		if(fontSize != -1) fontData.setHeight(fontSize);
		if(style != -1) fontData.setStyle(style);
		
		//Returning the font
		return new Font(getDisplay(), fontData);
	}
	
	public static void packControl(Control control, String text, int padding) {
		GC gc = new GC(control);
		Point textSize = gc.textExtent(text);
		gc.dispose();
		control.setSize(textSize.x + padding, control.getSize().y);
	}
	
	public static int computeMaxSize(Button[] buttons) {
		int maxSize = 0;
		for(Button button : buttons) {
			int buttonSize = button.computeSize(SWT.DEFAULT, SWT.DEFAULT).x;
			if(buttonSize > maxSize) maxSize = buttonSize;
		}
		return maxSize;
	}
	
	public static Shell getMessageShell(Shell parentShell, String titleText, String bodyText) {
		//Creating the shell
		Shell shell = new Shell(parentShell, SWT.SHEET);
		shell.setMinimumSize(300, 0);
		
		GridLayout shellGL = new GridLayout();
		shellGL.numColumns = 1;
		shellGL.marginTop = shellGL.marginBottom = shellGL.marginLeft = shellGL.marginRight = UIHelper.sheetMargin;
		shellGL.verticalSpacing = UIHelper.windowMargin;
		shell.setLayout(shellGL);
		
		//Adding the title
		Label labelTitle = new Label(shell, SWT.WRAP);
		labelTitle.setText(titleText);
		labelTitle.setFont(UIHelper.getFont(labelTitle.getFont(), 14, SWT.BOLD));
		GridData labelTitleGD = new GridData();
		labelTitleGD.grabExcessHorizontalSpace = true;
		labelTitleGD.horizontalAlignment = GridData.FILL;
		labelTitle.setLayoutData(labelTitleGD);
		
		//Adding the description
		Label labelDescription = new Label(shell, SWT.WRAP);
		labelDescription.setText(bodyText);
		GridData labelDescriptionGD = new GridData();
		labelDescriptionGD.grabExcessHorizontalSpace = true;
		labelDescriptionGD.horizontalAlignment = GridData.FILL;
		labelDescription.setLayoutData(labelDescriptionGD);
		
		//Adding the button
		Button closeButton = new Button(shell, SWT.PUSH);
		closeButton.setText(Main.resources().getString("action.ok"));
		GridData closeButtonGD = new GridData();
		closeButtonGD.horizontalAlignment = GridData.END;
		closeButtonGD.widthHint = UIHelper.minButtonWidth;
		closeButton.setLayoutData(closeButtonGD);
		closeButton.addListener(SWT.Selection, event -> shell.close());
		shell.setDefaultButton(closeButton);
		
		//Packing the shell
		shell.pack();
		if(shell.getSize().x > 500) shell.setSize(500, shell.getSize().y);
		
		//Returning the shell
		return shell;
	}
	
	public static Shell getMessageShellDual(Shell parentShell, String titleText, String bodyText, String buttonMainText, Runnable buttonMainCallback, String buttonSecondaryText, Runnable buttonSecondaryCallback) {
		//Creating the shell
		Shell shell = new Shell(parentShell, SWT.SHEET);
		shell.setMinimumSize(300, 0);
		
		GridLayout shellGL = new GridLayout();
		shellGL.numColumns = 1;
		shellGL.marginTop = shellGL.marginBottom = shellGL.marginLeft = shellGL.marginRight = UIHelper.sheetMargin;
		shellGL.verticalSpacing = UIHelper.windowMargin;
		shell.setLayout(shellGL);
		
		//Adding the title
		Label labelTitle = new Label(shell, SWT.WRAP);
		labelTitle.setText(titleText);
		labelTitle.setFont(UIHelper.getFont(labelTitle.getFont(), 14, SWT.BOLD));
		GridData labelTitleGD = new GridData();
		labelTitleGD.grabExcessHorizontalSpace = true;
		labelTitleGD.horizontalAlignment = GridData.FILL;
		labelTitle.setLayoutData(labelTitleGD);
		
		//Adding the description
		Label labelDescription = new Label(shell, SWT.WRAP);
		labelDescription.setText(bodyText);
		GridData labelDescriptionGD = new GridData();
		labelDescriptionGD.grabExcessHorizontalSpace = true;
		labelDescriptionGD.horizontalAlignment = GridData.FILL;
		labelDescription.setLayoutData(labelDescriptionGD);
		
		//Configuring the buttons
		{
			Composite buttonContainer = new Composite(shell, SWT.NONE);
			buttonContainer.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
			
			FormLayout buttonContainerFL = new FormLayout();
			buttonContainer.setLayout(buttonContainerFL);
			
			Button mainButton = new Button(buttonContainer, SWT.PUSH);
			mainButton.setText(buttonMainText);
			FormData acceptButtonFD = new FormData();
			if(mainButton.computeSize(SWT.DEFAULT, SWT.DEFAULT).x < UIHelper.minButtonWidth) acceptButtonFD.width = UIHelper.minButtonWidth;
			acceptButtonFD.right = new FormAttachment(100);
			acceptButtonFD.top = new FormAttachment(50, -mainButton.computeSize(SWT.DEFAULT, SWT.DEFAULT).y / 2);
			mainButton.setLayoutData(acceptButtonFD);
			mainButton.addListener(SWT.Selection, event -> {
				shell.close();
				if(buttonMainCallback != null) buttonMainCallback.run();
			});
			shell.setDefaultButton(mainButton);
			
			Button secondaryButton = new Button(buttonContainer, SWT.PUSH);
			secondaryButton.setText(buttonSecondaryText);
			FormData discardButtonFD = new FormData();
			if(secondaryButton.computeSize(SWT.DEFAULT, SWT.DEFAULT).x < UIHelper.minButtonWidth) discardButtonFD.width = UIHelper.minButtonWidth;
			discardButtonFD.right = new FormAttachment(mainButton);
			discardButtonFD.top = new FormAttachment(mainButton, 0, SWT.CENTER);
			secondaryButton.setLayoutData(discardButtonFD);
			secondaryButton.addListener(SWT.Selection, event -> {
				shell.close();
				if(buttonSecondaryCallback != null) buttonSecondaryCallback.run();
			});
		}
		
		//Packing the shell
		shell.pack();
		if(shell.getSize().x > 500) shell.setSize(500, shell.getSize().y);
		
		//Returning the shell
		return shell;
	}
	
	private static class MouseTracker {
		boolean mouseDown = false;
		int mouseX = 0;
		int mouseY = 0;
		
		void registerShell(Shell shell, Control control) {
			control.addMouseListener(new MouseListener() {
				@Override
				public void mouseDoubleClick(MouseEvent mouseEvent) {
				
				}
				
				@Override
				public void mouseDown(MouseEvent mouseEvent) {
					mouseDown = true;
					mouseX = mouseEvent.x;
					mouseY = mouseEvent.y;
				}
				
				@Override
				public void mouseUp(MouseEvent mouseEvent) {
					mouseDown = false;
				}
			});
			
			control.addMouseMoveListener(mouseEvent -> {
				if(mouseDown) shell.setLocation(shell.getLocation().x + (mouseEvent.x - mouseX), shell.getLocation().y + (mouseEvent.y - mouseY));
			});
			
			if(control instanceof Composite) {
				for(Control child : ((Composite) control).getChildren()) {
					if((child.getStyle() & SWT.PUSH) == SWT.PUSH) continue;
					
					registerShell(shell, child);
				}
			}
		}
	}
	
	public static Display getDisplay() {
		return display;
	}
}