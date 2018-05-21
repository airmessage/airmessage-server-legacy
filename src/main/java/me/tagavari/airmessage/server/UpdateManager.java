package me.tagavari.airmessage.server;

import io.sentry.Sentry;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.ProgressEvent;
import org.eclipse.swt.browser.ProgressListener;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

class UpdateManager {
	//Creating the reference values
	private static final URL updateURL = makeURL("https://airmessage.org/update/server/1/");
	
	//Creating the state values
	private static final AtomicBoolean updateCheckInProgress = new AtomicBoolean(false);
	private static Shell manualUpdateShell = null;
	
	//Creating the timer values
	private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
	private static final Runnable runUpdateCheck = () -> {
		updateCheckInProgress.set(true);
		boolean shouldShowNoUpdateWindow = false;
		
		try {
			//Getting the object
			JSONObject jRoot;
			try(Scanner scanner = new Scanner(updateURL.openStream())) {
				scanner.useDelimiter("\\A");
				
				if(!scanner.hasNext()) return;
				String result = scanner.next();
				jRoot = new JSONObject(result);
			} catch(IOException exception) {
				Main.getLogger().log(Level.WARNING, exception.getMessage(), exception);
				return;
			}
			
			try {
				//Finding the first valid release
				JSONObject jLatestRelease = null;
				JSONArray jArrayReleases = jRoot.getJSONArray("releases");
				for(int i = 0; i < jArrayReleases.length(); i++) {
					JSONObject jRelease = jArrayReleases.getJSONObject(i);
					if(Constants.compareVersions(Constants.getSystemVersion(), Constants.parseVersionString(jRelease.getString("os_version_requirement"))) < 0) continue;
					jLatestRelease = jRelease;
					break;
				}
				
				//Returning if no release was found, or no upgrade is needed
				if(jLatestRelease == null || jLatestRelease.getInt("version_code") <= Constants.SERVER_VERSION_CODE) {
					shouldShowNoUpdateWindow = true;
					return;
				}
				
				//Fetching the locales of the release notes
				HashMap<Locale, String> releaseNotes = new HashMap<>(); //Locale, message
				JSONArray jArrayNotes = jLatestRelease.getJSONArray("notes");
				for(int j = 0; j < jArrayNotes.length(); j++) {
					JSONObject jNotes = jArrayNotes.getJSONObject(j);
					releaseNotes.put(Locale.forLanguageTag(jNotes.getString("lang")), jNotes.getString("message"));
				}
				
				//Compiling the system locales into a language range list
				List<Locale.LanguageRange> languageRangeList = new ArrayList<>();
				//for(Locale locale : Locale.getAvailableLocales()) languageRangeList.add(new Locale.LanguageRange(locale.toLanguageTag()));
				languageRangeList.add(new Locale.LanguageRange(Locale.getDefault().toLanguageTag()));
				languageRangeList.add(new Locale.LanguageRange(Locale.ENGLISH.toLanguageTag()));
				
				//Finding a target locale
				Locale targetLocale = null;
				List<Locale> filterResults = Locale.filter(languageRangeList, releaseNotes.keySet());
				if(!filterResults.isEmpty()) targetLocale = filterResults.get(0);
				else targetLocale = Locale.lookup(languageRangeList, releaseNotes.keySet());
				if(targetLocale == null) throw new JSONException("Empty release notes language");
				
				//Getting the release information
				final String relVerName = jLatestRelease.getString("version_name");
				final String relTargetLink = jLatestRelease.getString("download-url");
				String relMessage = new String(Base64.getDecoder().decode(releaseNotes.get(targetLocale)));
				final String relMessageHTML = HtmlRenderer.builder().build().render(Parser.builder().build().parse(relMessage));
				
				//Showing the update window
				UIHelper.getDisplay().asyncExec(() -> openUpdateWindow(relVerName, relMessageHTML, relTargetLink));
			} catch(Exception exception) {
				Main.getLogger().log(Level.WARNING, exception.getMessage(), exception);
				Sentry.capture(exception);
				
				return;
			}
		} finally {
			final boolean shouldShowNoUpdateWindowFinal = shouldShowNoUpdateWindow;
			updateCheckInProgress.set(false);
			
			UIHelper.getDisplay().asyncExec(() -> {
				//Closing the update window
				if(manualUpdateShell != null && !manualUpdateShell.isDisposed()) {
					manualUpdateShell.close();
					
					//Showing the "no updates available" window
					if(shouldShowNoUpdateWindowFinal) {
						showUpToDateWindow();
					}
				}
			});
		}
	};
	private static ScheduledFuture<?> handleUpdateCheck = null;
	
	static void startUpdateChecker() {
		handleUpdateCheck = scheduler.scheduleAtFixedRate(runUpdateCheck, 0, 1, TimeUnit.DAYS);
	}
	
	static void stopUpdateChecker() {
		if(handleUpdateCheck != null) handleUpdateCheck.cancel(false);
	}
	
	private static URL makeURL(String target) {
		try {
			return new URL(target);
		} catch(MalformedURLException exception) {
			Main.getLogger().log(Level.WARNING, exception.getMessage(), exception);
			Sentry.capture(exception);
			
			throw new IllegalArgumentException(exception);
		}
	}
	
	private static void openUpdateWindow(String newVer, String releaseNotes, String targetLink) {
		//Creating the shell
		Shell shell = new Shell(UIHelper.getDisplay(), SWT.TITLE | SWT.CLOSE | SWT.MIN);
		//Shell shell = new Shell(UIHelper.getDisplay());
		shell.setText(I18N.i.title_update());
		shell.setSize(550, 300);
		
		//Configuring the layout
		{
			GridLayout shellGL = new GridLayout();
			shellGL.numColumns = 1;
			shellGL.marginTop = UIHelper.windowMargin;
			shellGL.marginBottom = UIHelper.windowMargin;
			shellGL.marginLeft = UIHelper.windowMargin;
			shellGL.marginRight = UIHelper.windowMargin;
			shellGL.verticalSpacing = 8;
			shell.setLayout(shellGL);
			
			Label labelTitle = new Label(shell, SWT.WRAP);
			labelTitle.setText(I18N.i.message_updateAvailable_title(Constants.APP_NAME));
			labelTitle.setFont(UIHelper.getFont(labelTitle.getFont(), 14, SWT.BOLD));
			GridData labelTitleGD = new GridData();
			labelTitleGD.grabExcessHorizontalSpace = true;
			labelTitleGD.horizontalAlignment = GridData.FILL;
			labelTitle.setLayoutData(labelTitleGD);
			
			Label labelDescription = new Label(shell, SWT.WRAP);
			labelDescription.setText(I18N.i.message_updateAvailable_description(Constants.APP_NAME, Constants.SERVER_VERSION, newVer));
			GridData labelDescriptionGD = new GridData();
			labelDescriptionGD.grabExcessHorizontalSpace = true;
			labelDescriptionGD.horizontalAlignment = GridData.FILL;
			labelDescription.setLayoutData(labelDescriptionGD);
			
			Label labelNotes = new Label(shell, SWT.WRAP);
			labelNotes.setText(I18N.i.message_releaseNotes());
			labelNotes.setFont(UIHelper.getFont(labelNotes.getFont(), -1, SWT.BOLD));
			GridData labelNotesGD = new GridData();
			labelNotesGD.grabExcessHorizontalSpace = true;
			labelNotesGD.horizontalAlignment = GridData.FILL;
			labelNotes.setLayoutData(labelNotesGD);
			
			Browser browserNotes = new Browser(shell, SWT.BORDER);
			browserNotes.setText(releaseNotes);
			browserNotes.setMenu(new Menu(browserNotes));
			browserNotes.addProgressListener(new ProgressListener() {
				@Override
				public void changed(ProgressEvent event) {}
				
				@Override
				public void completed(ProgressEvent event) {
					browserNotes.execute("document.getElementsByTagName('body')[0].style.fontFamily = 'sans-serif';");
					browserNotes.execute("document.getElementsByTagName('body')[0].style.fontSize = " + UIHelper.getDisplay().getSystemFont().getFontData()[0].getHeight() + ";");
				}
			});
			GridData browserNotesGD = new GridData();
			browserNotesGD.grabExcessHorizontalSpace = browserNotesGD.grabExcessVerticalSpace = true;
			browserNotesGD.horizontalAlignment = browserNotesGD.verticalAlignment = GridData.FILL;
			browserNotesGD.minimumHeight = 100;
			browserNotesGD.heightHint = 100;
			browserNotes.setLayoutData(browserNotesGD);
		}
		
		//Configuring the buttons
		{
			Composite buttonContainer = new Composite(shell, SWT.NONE);
			GridData buttonContainerGD = new GridData();
			buttonContainerGD.grabExcessHorizontalSpace = true;
			buttonContainerGD.horizontalAlignment = GridData.FILL;
			buttonContainer.setLayoutData(buttonContainerGD);
			FormLayout buttonContainerFL = new FormLayout();
			buttonContainer.setLayout(buttonContainerFL);
			
			Button acceptButton = new Button(buttonContainer, SWT.PUSH);
			acceptButton.setText(I18N.i.button_installUpdate());
			FormData acceptButtonFD = new FormData();
			//if(acceptButton.computeSize(SWT.DEFAULT, SWT.DEFAULT).x < UIHelper.minButtonWidth) acceptButtonFD.width = UIHelper.minButtonWidth;
			acceptButtonFD.right = new FormAttachment(100);
			acceptButtonFD.top = new FormAttachment(50, -acceptButton.computeSize(SWT.DEFAULT, SWT.DEFAULT).y / 2);
			acceptButton.setLayoutData(acceptButtonFD);
			acceptButton.addListener(SWT.Selection, event -> Program.launch(targetLink));
			shell.setDefaultButton(acceptButton);
			
			Button ignoreButton = new Button(buttonContainer, SWT.PUSH);
			ignoreButton.setText(I18N.i.button_installUpdate_later());
			FormData ignoreButtonFD = new FormData();
			//if(ignoreButton.computeSize(SWT.DEFAULT, SWT.DEFAULT).x < UIHelper.minButtonWidth) ignoreButtonFD.width = UIHelper.minButtonWidth;
			ignoreButtonFD.right = new FormAttachment(acceptButton);
			ignoreButtonFD.top = new FormAttachment(acceptButton, 0, SWT.CENTER);
			ignoreButton.setLayoutData(ignoreButtonFD);
			ignoreButton.addListener(SWT.Selection, event -> shell.close());
			
			acceptButtonFD.width = ignoreButtonFD.width = UIHelper.computeMaxSize(new Button[]{acceptButton, ignoreButton});
		}
		
		//Packing the shell
		//shell.pack();
		
		//Getting the bounds
		Rectangle screenBounds = UIHelper.getDisplay().getPrimaryMonitor().getBounds();
		Rectangle windowBounds = shell.getBounds();
		
		//Centering the window
		shell.setLocation(screenBounds.x + (screenBounds.width - windowBounds.width) / 2, screenBounds.y + (screenBounds.height - windowBounds.height) / 2);
		
		//Opening the shell
		shell.open();
	}
	
	static void requestManualUpdateCheck() {
		//Returning if the shell is currently active (a request has already been made)
		if(manualUpdateShell != null && !manualUpdateShell.isDisposed()) return;
		
		//Creating the shell
		manualUpdateShell = new Shell(SWT.TITLE);
		manualUpdateShell.setText(I18N.i.title_updateCheck());
		manualUpdateShell.setMinimumSize(300, 0);
		
		GridLayout manualUpdateShellGL = new GridLayout();
		manualUpdateShellGL.numColumns = 1;
		manualUpdateShellGL.marginTop = manualUpdateShellGL.marginBottom = manualUpdateShellGL.marginLeft = manualUpdateShellGL.marginRight = UIHelper.windowMargin;
		manualUpdateShellGL.verticalSpacing = UIHelper.windowMargin;
		manualUpdateShell.setLayout(manualUpdateShellGL);
		
		//Adding the title
		Label title = new Label(manualUpdateShell, SWT.NONE);
		title.setText(I18N.i.message_checkingUpdates());
		title.setFont(UIHelper.getFont(title.getFont(), -1, SWT.BOLD));
		GridData titleGD = new GridData();
		titleGD.horizontalAlignment = GridData.FILL;
		titleGD.grabExcessHorizontalSpace = true;
		title.setLayoutData(titleGD);
		
		//Adding the progress bar
		ProgressBar progressBar = new ProgressBar(manualUpdateShell, SWT.HORIZONTAL | SWT.INDETERMINATE);
		GridData progressBarGD = new GridData();
		progressBarGD.horizontalAlignment = GridData.FILL;
		progressBarGD.grabExcessHorizontalSpace = true;
		progressBar.setLayoutData(progressBarGD);
		
		//Packing the shell
		manualUpdateShell.pack();
		
		//Centering the window
		Rectangle screenBounds = UIHelper.getDisplay().getPrimaryMonitor().getBounds();
		Rectangle windowBounds = manualUpdateShell.getBounds();
		manualUpdateShell.setLocation(screenBounds.x + (screenBounds.width - windowBounds.width) / 2, screenBounds.y + (screenBounds.height - windowBounds.height) / 2);
		
		//Opening the shell
		manualUpdateShell.open();
		manualUpdateShell.forceActive();
		
		//Checking if there is not currently an update check in progress
		if(!updateCheckInProgress.get()) {
			//Starting an update check
			if(PreferencesManager.getPrefAutoCheckUpdates()) {
				stopUpdateChecker();
				startUpdateChecker();
			} else {
				new Thread(runUpdateCheck).start();
			}
		}
	}
	
	private static void showUpToDateWindow() {
		//Creating the shell
		Shell shell = new Shell(SWT.TITLE);
		shell.setMinimumSize(300, 0);
		
		GridLayout shellGL = new GridLayout();
		shellGL.numColumns = 1;
		shellGL.marginTop = shellGL.marginBottom = shellGL.marginLeft = shellGL.marginRight = UIHelper.windowMargin;
		shellGL.verticalSpacing = UIHelper.windowMargin;
		shell.setLayout(shellGL);
		
		//Adding the title
		Label labelTitle = new Label(shell, SWT.WRAP);
		labelTitle.setText(I18N.i.message_upToDate());
		labelTitle.setFont(UIHelper.getFont(labelTitle.getFont(), 14, SWT.BOLD));
		GridData labelTitleGD = new GridData();
		labelTitleGD.grabExcessHorizontalSpace = true;
		labelTitleGD.horizontalAlignment = GridData.FILL;
		labelTitle.setLayoutData(labelTitleGD);
		
		//Adding the description
		Label labelDescription = new Label(shell, SWT.WRAP);
		labelDescription.setText(I18N.i.message_upToDate_desc(Constants.SERVER_VERSION));
		GridData labelDescriptionGD = new GridData();
		labelDescriptionGD.grabExcessHorizontalSpace = true;
		labelDescriptionGD.horizontalAlignment = GridData.FILL;
		labelDescription.setLayoutData(labelDescriptionGD);
		
		//Adding the button
		Button closeButton = new Button(shell, SWT.PUSH);
		closeButton.setText(I18N.i.button_ok());
		GridData closeButtonGD = new GridData();
		closeButtonGD.horizontalAlignment = GridData.END;
		closeButtonGD.widthHint = UIHelper.minButtonWidth;
		closeButton.setLayoutData(closeButtonGD);
		closeButton.addListener(SWT.Selection, event -> shell.close());
		shell.setDefaultButton(closeButton);
		
		//Packing the shell
		shell.pack();
		if(shell.getSize().x > 500) shell.setSize(500, shell.getSize().y);
		
		//Getting the bounds
		Rectangle screenBounds = UIHelper.getDisplay().getPrimaryMonitor().getBounds();
		Rectangle windowBounds = shell.getBounds();
		
		//Centering the window
		shell.setLocation(screenBounds.x + (screenBounds.width - windowBounds.width) / 2, screenBounds.y + (screenBounds.height - windowBounds.height) / 2);
		
		//Opening the shell
		shell.open();
		shell.forceActive();
	}
}