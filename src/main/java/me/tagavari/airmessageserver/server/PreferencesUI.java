package me.tagavari.airmessageserver.server;

import me.tagavari.airmessageserver.connection.ConnectionManager;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

import java.text.MessageFormat;

public class PreferencesUI {
	//Creating the window values
	private static Shell windowShell = null;
	
	static void openWindow() {
		//Opening the window
		if(windowShell == null || windowShell.isDisposed()) openPrefsWindow();
		else windowShell.forceActive();
	}
	
	private static void openPrefsWindow() {
		int accountType = PreferencesManager.getPrefAccountType();
		
		//Getting the preferences
		int origPreferencePort = PreferencesManager.getPrefServerPort();
		boolean origPreferenceUpdateCheck = PreferencesManager.getPrefAutoCheckUpdates();
		
		//Creating the widget values
		Text pwTextPort;
		Button pwButtonAutoUpdate;
		
		//Creating the shell
		windowShell = new Shell(UIHelper.getDisplay(), SWT.TITLE);
		windowShell.setText(Main.resources().getString("label.preferences"));
		
		//Configuring the layouts
		GridLayout shellGL = new GridLayout(1, false);
		shellGL.marginLeft = shellGL.marginRight = shellGL.marginTop = shellGL.marginBottom = shellGL.marginWidth = shellGL.marginHeight = 0;
		shellGL.verticalSpacing = 5;
		windowShell.setLayout(shellGL);
		
		//Configuring the preferences
		Composite prefContainer = new Composite(windowShell, SWT.NONE);
		GridLayout prefContainerGL = new GridLayout(2, false);
		prefContainerGL.marginLeft = 100;
		prefContainerGL.marginRight = 50;
		prefContainerGL.marginTop = UIHelper.windowMargin;
		prefContainerGL.marginBottom = UIHelper.windowMargin;
		prefContainerGL.verticalSpacing = 5;
		prefContainer.setLayout(prefContainerGL);
		
		if(accountType == PreferencesManager.accountTypeDirect) {
			Label portLabel = new Label(prefContainer, SWT.NONE);
			pwTextPort = new Text(prefContainer, SWT.BORDER);
			
			portLabel.setText(Main.resources().getString("prefix.preference.port"));
			portLabel.setLayoutData(new GridData(GridData.END, GridData.CENTER, false, false));
			
			pwTextPort.setTextLimit(5);
			pwTextPort.addVerifyListener(event -> {
				//Returning if the text is invalid
				if(event.text.isEmpty()) return;
				
				//Trimming the text
				event.text = event.text.trim();
				
				//Iterating over the text's characters and rejecting the event if a non-numerical character was found
				for(char stringChar : event.text.toCharArray()) if(!('0' <= stringChar && stringChar <= '9')) {
					event.doit = false;
					return;
				}
				
				//Assembling the full string
				String originalString = ((Text) event.widget).getText();
				String fullString = originalString.substring(0, event.start) + event.text + originalString.substring(event.end);
				
				//Returning if the string is empty
				if(fullString.isEmpty()) return;
				
				//Rejecting the event if the value is not within the port range
				int portNum = Integer.parseInt(fullString);
				if(portNum < 1 || portNum > 65535) {
					event.doit = false;
					return;
				}
			});
			GridData textGD = new GridData();
			textGD.grabExcessHorizontalSpace = false;
			textGD.grabExcessVerticalSpace = false;
			textGD.widthHint = 43;
			pwTextPort.setLayoutData(textGD);
			pwTextPort.setText(Integer.toString(origPreferencePort));
			/* GridData textWarningGD = new GridData();
			textWarningGD.widthHint = 250;
			textWarning.setLayoutData(textWarningGD); */
		} else pwTextPort = null;
		
		if(accountType == PreferencesManager.accountTypeDirect) {
			Label securityLabel = new Label(prefContainer, SWT.NONE);
			Button prefsButton = new Button(prefContainer, SWT.PUSH);
			
			securityLabel.setText(Main.resources().getString("prefix.preference.security"));
			securityLabel.setLayoutData(new GridData(GridData.END, GridData.CENTER, false, false));
			
			prefsButton.setText(Main.resources().getString("action.edit_password"));
			GridData prefGB = new GridData(GridData.BEGINNING, GridData.CENTER, false, false);
			prefGB.horizontalIndent = -8;
			prefsButton.setLayoutData(prefGB);
			prefsButton.addListener(SWT.Selection, event -> openPrefsPasswordWindow(windowShell, null));
		}
		
		{
			Label updateLabel = new Label(prefContainer, SWT.NONE);
			pwButtonAutoUpdate = new Button(prefContainer, SWT.CHECK);
			
			updateLabel.setText(Main.resources().getString("prefix.preference.updates"));
			updateLabel.setLayoutData(new GridData(GridData.END, GridData.CENTER, false, false));
			
			pwButtonAutoUpdate.setText(Main.resources().getString("message.preference.auto_update"));
			GridData prefGB = new GridData(GridData.BEGINNING, GridData.CENTER, false, false);
			pwButtonAutoUpdate.setLayoutData(prefGB);
			pwButtonAutoUpdate.setSelection(origPreferenceUpdateCheck);
		}
		
		{
			Label accessLabel = new Label(prefContainer, SWT.NONE);
			Composite accessComposite = new Composite(prefContainer, SWT.NONE);
			Button accessButton = new Button(accessComposite, SWT.PUSH);
			Label accessDesc = new Label(accessComposite, SWT.WRAP);
			
			accessLabel.setText(Main.resources().getString("prefix.preference.account"));
			GridData accessLabelGD = new GridData(GridData.END, GridData.BEGINNING, false, false);
			accessLabelGD.verticalIndent = 10;
			accessLabel.setLayoutData(accessLabelGD);
			
			GridLayout compositeLayout = new GridLayout();
			compositeLayout.numColumns = 1;
			accessComposite.setLayout(compositeLayout);
			
			String stringAction, stringDescription;
			if(accountType == PreferencesManager.accountTypeDirect) {
				stringAction = Main.resources().getString("action.switch_to_account");
				stringDescription = Main.resources().getString("message.preference.account_manual");
			} else {
				stringAction = Main.resources().getString("action.sign_out");
				stringDescription = Main.resources().getString("message.preference.account_connect");
			}
			
			accessButton.setText(stringAction);
			GridData prefGD = new GridData();
			prefGD.horizontalIndent = -8;
			accessButton.setLayoutData(prefGD);
			accessButton.addListener(SWT.Selection, event -> {
				String stringDialogTitle = switch(accountType) {
					case PreferencesManager.accountTypeDirect -> Main.resources().getString("message.reset.title.direct");
					default -> Main.resources().getString("message.reset.title.connect");
				};
				
				//Displaying a confirmation dialog
				UIHelper.getMessageShellDual(windowShell, stringDialogTitle, Main.resources().getString("message.reset.description"), stringAction, () -> {
					//Closing the window
					windowShell.close();
					
					//Signing out
					signOutUser();
				}, Main.resources().getString("action.cancel"), null).open();
			});
			
			GridData accessDescRD = new GridData();
			accessDescRD.widthHint = 300;
			accessDesc.setLayoutData(accessDescRD);
			accessDesc.setText(stringDescription);
			accessDesc.setFont(UIHelper.getFont(accessDesc.getFont(), 10, -1));
		}
		
		//Adding the divider
		{
			Label divider = new Label(windowShell, SWT.HORIZONTAL);
			divider.setBackground(UIHelper.getDisplay().getSystemColor(SWT.COLOR_GRAY));
			GridData dividerGD = new GridData(GridData.FILL_HORIZONTAL);
			dividerGD.heightHint = 1;
			divider.setLayoutData(dividerGD);
		}
		
		Composite buttonContainer = new Composite(windowShell, SWT.NONE);
		buttonContainer.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		//Configuring the buttons
		{
			FormLayout buttonContainerFL = new FormLayout();
			//buttonContainerFL.marginLeft = buttonContainerFL.marginRight = buttonContainerFL.marginTop = buttonContainerFL.marginBottom = buttonContainerFL.marginWidth = buttonContainerFL.marginHeight = 0;
			buttonContainerFL.marginWidth = buttonContainerFL.marginHeight = UIHelper.dialogButtonBarMargin;
			buttonContainer.setLayout(buttonContainerFL);
			
			Button acceptButton = new Button(buttonContainer, SWT.PUSH);
			acceptButton.setText(Main.resources().getString("action.ok"));
			FormData acceptButtonFD = new FormData();
			if(acceptButton.computeSize(SWT.DEFAULT, SWT.DEFAULT).x < UIHelper.minButtonWidth) acceptButtonFD.width = UIHelper.minButtonWidth;
			acceptButtonFD.right = new FormAttachment(100);
			acceptButtonFD.top = new FormAttachment(50, -acceptButton.computeSize(SWT.DEFAULT, SWT.DEFAULT).y / 2);
			acceptButton.setLayoutData(acceptButtonFD);
			acceptButton.addListener(SWT.Selection, event -> {
				//Updating the port
				if(accountType == PreferencesManager.accountTypeDirect) {
					String portStr = pwTextPort.getText();
					int port = portStr.isEmpty() ? PreferencesManager.defaultPort : Integer.parseInt(portStr);
					
					if(origPreferencePort != port) {
						PreferencesManager.setPrefServerPort(port);
						Main.reinitializeServer();
					}
				}
				
				//Updating the update checker
				boolean updateCheck = pwButtonAutoUpdate.getSelection();
				if(origPreferenceUpdateCheck != updateCheck) {
					PreferencesManager.setPrefAutoCheckUpdates(updateCheck);
					
					if(updateCheck) UpdateManager.startUpdateChecker();
					else UpdateManager.stopUpdateChecker();
				}
				
				windowShell.close();
			});
			windowShell.setDefaultButton(acceptButton);
			
			Button discardButton = new Button(buttonContainer, SWT.PUSH);
			discardButton.setText(Main.resources().getString("action.cancel"));
			FormData discardButtonFD = new FormData();
			if(discardButton.computeSize(SWT.DEFAULT, SWT.DEFAULT).x < UIHelper.minButtonWidth) discardButtonFD.width = UIHelper.minButtonWidth;
			discardButtonFD.right = new FormAttachment(acceptButton);
			discardButtonFD.top = new FormAttachment(acceptButton, 0, SWT.CENTER);
			discardButton.setLayoutData(discardButtonFD);
			discardButton.addListener(SWT.Selection, event -> windowShell.close());
		}
		
		//Packing the shell
		windowShell.pack();
		windowShell.setMinimumSize(500, windowShell.getMinimumSize().y);
		
		//Centering the window
		Rectangle screenBounds = UIHelper.getDisplay().getPrimaryMonitor().getBounds();
		Rectangle windowBounds = windowShell.getBounds();
		windowShell.setLocation(screenBounds.x + (screenBounds.width - windowBounds.width) / 2, screenBounds.y + (screenBounds.height - windowBounds.height) / 2);
		
		windowShell.addListener(SWT.Close, event -> {
			//Invalidating the reference
			windowShell = null;
			
			//Updating the first run state
			/* if(isFirstRun) {
				clearFirstRun();
				Main.runPermissionCheck();
			} */
		});
		
		//Opening the shell
		windowShell.open();
		windowShell.forceActive();
	}
	
	public static void openPrefsPasswordWindow(Shell parentShell, Runnable completionListener) {
		//Creating the shell flags
		Constants.ValueWrapper<Boolean> textEditorOpen = new Constants.ValueWrapper<>(Boolean.FALSE);
		
		//Creating the shell
		Shell shell = new Shell(parentShell, SWT.SHEET);
		
		//Configuring the shell
		shell.addListener(SWT.Traverse, event -> {
			//Cancelling the event if the editor is open and the button is "escape" or "return" (to exit the dialog)
			if(textEditorOpen.value && (event.detail == SWT.TRAVERSE_ESCAPE || event.detail == SWT.TRAVERSE_RETURN)) event.doit = false;
		});
		
		//Configuring the layout
		GridLayout shellGL = new GridLayout(1, false);
		shellGL.marginWidth = shellGL.marginHeight = UIHelper.sheetMargin;
		shellGL.verticalSpacing = 10;
		shell.setLayout(shellGL);
		
		//Creating the relevant widget values
		Text passTextHidden;
		Text passTextVisible;
		Constants.ValueWrapper<Text> passTextCurrent;
		Label strengthLabel;
		
		{
			//Getting the password
			String currentPassword = PreferencesManager.getPrefPassword();
			int passwordStrength = calculatePasswordStrength(currentPassword);
			
			//Creating the list label
			Label listLabel = new Label(shell, SWT.NONE);
			listLabel.setText(Main.resources().getString("prefix.preference.password"));
			
			//Creating the text
			passTextHidden = new Text(shell, SWT.BORDER | SWT.SINGLE | SWT.PASSWORD);
			passTextHidden.setText(currentPassword);
			GridData passTextHiddenGD = new GridData();
			passTextHiddenGD.horizontalAlignment = GridData.FILL;
			passTextHiddenGD.grabExcessHorizontalSpace = true;
			passTextHidden.setLayoutData(passTextHiddenGD);
			
			passTextVisible = new Text(shell, SWT.BORDER | SWT.SINGLE);
			GridData passTextVisibleGD = new GridData();
			passTextVisibleGD.horizontalAlignment = GridData.FILL;
			passTextVisibleGD.grabExcessHorizontalSpace = true;
			passTextVisibleGD.exclude = true;
			passTextVisible.setLayoutData(passTextVisibleGD);
			passTextVisible.setVisible(false);
			
			passTextCurrent = new Constants.ValueWrapper<>(passTextHidden);
			
			VerifyListener verifyListener = event -> event.doit = !stringContainsControlChar(event.text);
			passTextHidden.addVerifyListener(verifyListener);
			passTextVisible.addVerifyListener(verifyListener);
			
			//Creating the strength label
			strengthLabel = new Label(shell, SWT.NONE);
			strengthLabel.setText(getPasswordStrengthLabel(passwordStrength));
			
			//Creating the password display toggle
			Button buttonShowPassword = new Button(shell, SWT.CHECK);
			buttonShowPassword.setSelection(false);
			buttonShowPassword.setText(Main.resources().getString("action.show_password"));
			buttonShowPassword.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent event) {
					//Swapping and updating the password widget
					Button button = (Button) event.widget;
					if(button.getSelection()) {
						passTextVisible.setText(passTextHidden.getText());
						((GridData) passTextHidden.getLayoutData()).exclude = true;
						passTextHidden.setVisible(false);
						((GridData) passTextVisible.getLayoutData()).exclude = false;
						passTextVisible.setVisible(true);
						passTextCurrent.value = passTextVisible;
					} else {
						passTextHidden.setText(passTextVisible.getText());
						((GridData) passTextHidden.getLayoutData()).exclude = false;
						passTextHidden.setVisible(true);
						((GridData) passTextVisible.getLayoutData()).exclude = true;
						passTextVisible.setVisible(false);
						
						passTextCurrent.value = passTextHidden;
					}
					
					//Requesting focus
					passTextCurrent.value.setFocus();
					
					//Requesting a refresh
					shell.layout(true);
				}
			});
			GridData gridData = new GridData();
			gridData.horizontalAlignment = GridData.FILL;
			gridData.grabExcessHorizontalSpace = true;
			buttonShowPassword.setLayoutData(gridData);
		}
		
		{
			//Creating the button composite
			Composite buttonContainer = new Composite(shell, SWT.NONE);
			FormLayout buttonCompositeFL = new FormLayout();
			buttonCompositeFL.marginWidth = buttonCompositeFL.marginHeight = 0;
			buttonContainer.setLayout(buttonCompositeFL);
			GridData buttonCompositeGD = new GridData();
			buttonCompositeGD.horizontalAlignment = GridData.FILL;
			buttonCompositeGD.grabExcessHorizontalSpace = true;
			buttonContainer.setLayoutData(buttonCompositeGD);
			
			//Adding the apply / discard buttons
			Button acceptButton = new Button(buttonContainer, SWT.PUSH);
			acceptButton.setText(Main.resources().getString("action.ok"));
			FormData acceptButtonFD = new FormData();
			if(acceptButton.computeSize(SWT.DEFAULT, SWT.DEFAULT).x < UIHelper.smallMinButtonWidth) acceptButtonFD.width = UIHelper.smallMinButtonWidth;
			acceptButtonFD.right = new FormAttachment(100);
			acceptButtonFD.top = new FormAttachment(50, -acceptButton.computeSize(SWT.DEFAULT, SWT.DEFAULT).y / 2);
			acceptButton.setLayoutData(acceptButtonFD);
			ModifyListener modifyListener = event -> {
				Text text = (Text) event.widget;
				String currentText = text.getText();
				
				//Updating the strength label
				int passwordStrength = calculatePasswordStrength(currentText);
				strengthLabel.setText(getPasswordStrengthLabel(passwordStrength));
				strengthLabel.requestLayout();
				
				//Enabling the accept button if the text isn't empty and has no control characters
				acceptButton.setEnabled(!currentText.isEmpty());
			};
			passTextHidden.addModifyListener(modifyListener);
			passTextVisible.addModifyListener(modifyListener);
			acceptButton.addListener(SWT.Selection, event -> {
				//Setting the password
				boolean result = PreferencesManager.setPrefPassword(passTextCurrent.value.getText());
				
				if(result) {
					//Closing the shell
					shell.close();
					
					//Calling the completion listener
					if(completionListener != null) completionListener.run();
				}
			});
			shell.setDefaultButton(acceptButton);
			
			Button discardButton = new Button(buttonContainer, SWT.PUSH);
			discardButton.setText(Main.resources().getString("action.cancel"));
			FormData discardButtonFD = new FormData();
			if(discardButton.computeSize(SWT.DEFAULT, SWT.DEFAULT).x < UIHelper.smallMinButtonWidth) discardButtonFD.width = UIHelper.smallMinButtonWidth;
			discardButtonFD.right = new FormAttachment(acceptButton);
			discardButtonFD.top = new FormAttachment(acceptButton, 0, SWT.CENTER);
			discardButton.setLayoutData(discardButtonFD);
			discardButton.addListener(SWT.Selection, event -> shell.close());
		}
		
		//Opening the dialog
		shell.pack();
		shell.setSize(300, shell.getSize().y);
		shell.open();
	}
	
	private static int calculatePasswordStrength(String password) {
		int score = 0;
		
		//Up to 6 points for a length of 10
		score += Math.min((int) ((float) password.length() / 10 * 6), 6);
		
		//1 point for a digit
		if(password.matches("(?=.*[0-9]).*")) score += 1;
		
		//1 point for lowercase letter
		if(password.matches("(?=.*[a-z]).*")) score += 1;
		
		//1 point for uppercase letter
		if(password.matches("(?=.*[A-Z]).*")) score += 1;
		
		//1 point for special character
		if(password.matches("(?=.*[~!@#$%^&*()_-]).*")) score += 1;
		
		return score;
	}
	
	private static String getPasswordStrengthLabel(int level) {
		String strength;
		if(level <= 4) strength = Main.resources().getString("message.password.strength.level1");
		else if(level <= 6) strength = Main.resources().getString("message.password.strength.level2");
		else if(level <= 8) strength = Main.resources().getString("message.password.strength.level3");
		else strength = Main.resources().getString("message.password.strength.level4");
		
		return MessageFormat.format(Main.resources().getString("message.password.strength"), strength);
	}
	
	private static boolean stringContainsControlChar(String string) {
		for(char c : string.toCharArray()) {
			if(Character.isISOControl(c)) return true;
		}
		
		return false;
	}
	
	public static void signOutUser() {
		//Setting the user's account as unconfirmed
		PreferencesManager.setPrefAccountConfirmed(false);
		
		//Enabling setup mode
		Main.setSetupMode(true);
		
		//Disconnecting
		ConnectionManager.stop();
		
		//Showing the intro UI
		UIHelper.openIntroWindow();
		
		//Updating the state
		Main.setServerState(ServerState.SETUP);
		
		//Updating the UI
		SystemTrayManager.updateStatusMessage();
	}
}