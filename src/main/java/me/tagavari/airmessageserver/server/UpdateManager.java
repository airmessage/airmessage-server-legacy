package me.tagavari.airmessageserver.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

class UpdateManager {
	//Creating the reference values
	private static final String updateBaseURL = "https://airmessage.org";
	private static final URL stableUpdateURL = makeURL(updateBaseURL + "/update/server/2.yaml");
	private static final URL betaUpdateURL = makeURL(updateBaseURL + "/update/server-beta/2.yaml");
	
	//Creating the state values
	private static final AtomicBoolean updateCheckInProgress = new AtomicBoolean(false);
	private static final AtomicBoolean updateInstallationInProgress = new AtomicBoolean(false);
	private static Shell manualUpdateShell = null;
	private static Shell updateResultShell = null;
	private static Shell updateInstallationShell = null;
	private static ProgressBar updateInstallationProgressBar = null;
	
	//Creating the timer values
	private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
	private static final Runnable runUpdateCheck = () -> {
		updateCheckInProgress.set(true);
		boolean shouldShowNoUpdateWindow = false;
		
		URL updateURL;
		if(PreferencesManager.getPrefGetBetaUpdates()) updateURL = betaUpdateURL;
		else updateURL = stableUpdateURL;
		
		try {
			//Getting the object
			UpdateRecord updateRecord;
			
			try {
				ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
				updateRecord = objectMapper.readValue(updateURL, UpdateRecord.class);
			} catch(IOException exception) {
				Main.getLogger().log(Level.WARNING, exception.getMessage(), exception);
				return;
			}
			
			try {
				//Returning if the release is incompatible, or no upgrade is needed
				if(Constants.compareVersions(Constants.getSystemVersion(), Constants.parseVersionString(updateRecord.osRequirement())) < 0 ||
				   updateRecord.versionCode() <= Constants.SERVER_VERSION_CODE) {
					shouldShowNoUpdateWindow = true;
					return;
				}
				
				//Fetching the locales of the release notes
				Map<Locale, String> releaseNotes = updateRecord.notes().stream()
					//Locale, message
					.collect(Collectors.toMap((notes) -> Locale.forLanguageTag(notes.lang()), UpdateRecord.Notes::message));
				
				//Compiling the system locales into a language range list
				List<Locale.LanguageRange> languageRangeList = new ArrayList<>();
				languageRangeList.add(new Locale.LanguageRange(Locale.getDefault().toLanguageTag()));
				languageRangeList.add(new Locale.LanguageRange(Locale.ENGLISH.toLanguageTag()));
				
				//Finding a target locale
				Locale targetLocale = Locale.lookup(languageRangeList, releaseNotes.keySet());
				if(targetLocale == null) throw new RuntimeException("Empty release notes language");
				
				//Getting the release information
				String messageMarkdown = releaseNotes.get(targetLocale);
				String messageHTML = HtmlRenderer.builder().build().render(Parser.builder().build().parse(messageMarkdown));
				
				String downloadURL;
				if(Main.isAppleSilicon()) {
					//Try to download an Apple Silicon-optimized version, fall back to default version
					if(updateRecord.urlAppleSilicon() != null) downloadURL = updateRecord.urlAppleSilicon();
					else downloadURL = updateRecord.urlIntel();
				} else {
					downloadURL = updateRecord.urlIntel();
				}
				
				//Showing the update window
				UIHelper.getDisplay().asyncExec(() -> openUpdateWindow(updateRecord.versionName(), messageHTML, downloadURL, updateRecord.externalDownload()));
			} catch(Exception exception) {
				Main.getLogger().log(Level.WARNING, exception.getMessage(), exception);
				Sentry.captureException(exception);
				
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
	private static boolean updateCheckerRunning = false;
	
	static void startUpdateChecker() {
		if(updateCheckerRunning) return;
		handleUpdateCheck = scheduler.scheduleAtFixedRate(runUpdateCheck, 0, 1, TimeUnit.DAYS);
		updateCheckerRunning = true;
	}
	
	static void stopUpdateChecker() {
		if(!updateCheckerRunning) return;
		if(handleUpdateCheck != null) handleUpdateCheck.cancel(false);
		updateCheckerRunning = false;
	}
	
	private static URL makeURL(String target) {
		try {
			return new URL(target);
		} catch(MalformedURLException exception) {
			Main.getLogger().log(Level.WARNING, exception.getMessage(), exception);
			Sentry.captureException(exception);
			
			throw new IllegalArgumentException(exception);
		}
	}
	
	private static URL makeURL(File file) {
		try {
			return file.toURI().toURL();
		} catch(MalformedURLException exception) {
			Main.getLogger().log(Level.WARNING, exception.getMessage(), exception);
			Sentry.captureException(exception);
			
			throw new IllegalArgumentException(exception);
		}
	}
	
	private static void openUpdateWindow(String newVer, String releaseNotes, String downloadURL, boolean external) {
		//Closing the current update result shell
		if(updateResultShell != null && !updateResultShell.isDisposed()) updateResultShell.close();
		
		//Creating the shell
		Shell shell = updateResultShell = new Shell(UIHelper.getDisplay(), SWT.TITLE | SWT.CLOSE | SWT.MIN);
		//Shell shell = new Shell(UIHelper.getDisplay());
		shell.setText(Main.resources().getString("label.update"));
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
			labelTitle.setText(Main.resources().getString("label.update.available.title"));
			labelTitle.setFont(UIHelper.getFont(labelTitle.getFont(), 14, SWT.BOLD));
			GridData labelTitleGD = new GridData();
			labelTitleGD.grabExcessHorizontalSpace = true;
			labelTitleGD.horizontalAlignment = GridData.FILL;
			labelTitle.setLayoutData(labelTitleGD);
			
			Label labelDescription = new Label(shell, SWT.WRAP);
			labelDescription.setText(MessageFormat.format(Main.resources().getString("label.update.available.body"), newVer, Constants.SERVER_VERSION));
			GridData labelDescriptionGD = new GridData();
			labelDescriptionGD.grabExcessHorizontalSpace = true;
			labelDescriptionGD.horizontalAlignment = GridData.FILL;
			labelDescription.setLayoutData(labelDescriptionGD);
			
			Label labelNotes = new Label(shell, SWT.WRAP);
			labelNotes.setText(Main.resources().getString("prefix.release_notes"));
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
			acceptButton.setText(Main.resources().getString(external ? "action.download_update" : "action.install_update"));
			FormData acceptButtonFD = new FormData();
			//if(acceptButton.computeSize(SWT.DEFAULT, SWT.DEFAULT).x < UIHelper.minButtonWidth) acceptButtonFD.width = UIHelper.minButtonWidth;
			acceptButtonFD.right = new FormAttachment(100);
			acceptButtonFD.top = new FormAttachment(50, -acceptButton.computeSize(SWT.DEFAULT, SWT.DEFAULT).y / 2);
			acceptButton.setLayoutData(acceptButtonFD);
			acceptButton.addListener(SWT.Selection, event -> {
				//Closing the window
				if(!shell.isDisposed()) shell.close();
				
				//Starting the update
				startUpdateInstallation(downloadURL, external);
			});
			shell.setDefaultButton(acceptButton);
			
			Button ignoreButton = new Button(buttonContainer, SWT.PUSH);
			ignoreButton.setText(Main.resources().getString("action.install_update.later"));
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
		manualUpdateShell.setText(Main.resources().getString("label.update.check.title"));
		manualUpdateShell.setMinimumSize(300, 0);
		
		GridLayout manualUpdateShellGL = new GridLayout();
		manualUpdateShellGL.numColumns = 1;
		manualUpdateShellGL.marginTop = manualUpdateShellGL.marginBottom = manualUpdateShellGL.marginLeft = manualUpdateShellGL.marginRight = UIHelper.windowMargin;
		manualUpdateShellGL.verticalSpacing = UIHelper.windowMargin;
		manualUpdateShell.setLayout(manualUpdateShellGL);
		
		//Adding the title
		Label title = new Label(manualUpdateShell, SWT.NONE);
		title.setText(Main.resources().getString("label.update.check.body"));
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
		//Closing the current update result shell
		if(updateResultShell != null && !updateResultShell.isDisposed()) updateResultShell.close();
		
		//Creating the shell
		Shell shell = updateResultShell = new Shell(SWT.TITLE);
		shell.setMinimumSize(300, 0);
		
		GridLayout shellGL = new GridLayout();
		shellGL.numColumns = 1;
		shellGL.marginTop = shellGL.marginBottom = shellGL.marginLeft = shellGL.marginRight = UIHelper.windowMargin;
		shellGL.verticalSpacing = UIHelper.windowMargin;
		shell.setLayout(shellGL);
		
		//Adding the title
		Label labelTitle = new Label(shell, SWT.WRAP);
		labelTitle.setText(Main.resources().getString("label.update.up_to_date.title"));
		labelTitle.setFont(UIHelper.getFont(labelTitle.getFont(), 14, SWT.BOLD));
		GridData labelTitleGD = new GridData();
		labelTitleGD.grabExcessHorizontalSpace = true;
		labelTitleGD.horizontalAlignment = GridData.FILL;
		labelTitle.setLayoutData(labelTitleGD);
		
		//Adding the description
		Label labelDescription = new Label(shell, SWT.WRAP);
		labelDescription.setText(MessageFormat.format(Main.resources().getString("label.update.up_to_date.body"), Constants.SERVER_VERSION));
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
		
		//Getting the bounds
		Rectangle screenBounds = UIHelper.getDisplay().getPrimaryMonitor().getBounds();
		Rectangle windowBounds = shell.getBounds();
		
		//Centering the window
		shell.setLocation(screenBounds.x + (screenBounds.width - windowBounds.width) / 2, screenBounds.y + (screenBounds.height - windowBounds.height) / 2);
		
		//Opening the shell
		shell.open();
		shell.forceActive();
	}
	
	private static void startUpdateInstallation(String downloadURL, boolean external) {
		//Returning if there is already an installation in progress
		if(updateInstallationInProgress.get()) return;
		
		if(external) {
			//Launch the link in the browser
			Program.launch(downloadURL);
		} else {
			//Showing the installation window
			showInstallationWindow();
			
			//Downloading and installing the update
			new Thread(() -> installUpdate(downloadURL)).start();
		}
	}
	
	private static void installUpdate(String downloadURL) {
		//Updating the state
		updateInstallationInProgress.set(true);
		
		//The top-level directory of the extracted update file (the AirMessage.app file)
		File topZipFile = null;
		try {
			//Connecting to the provided URL
			Main.getLogger().log(Level.INFO, "Downloading update URL: " + downloadURL);
			URL url = new URL(downloadURL);
			URLConnection conn = url.openConnection();
			conn.setConnectTimeout(10 * 1000);
			
			/* //Following redirects
			while(conn.getResponseCode() >= 300 && conn.getResponseCode() < 400) {
				String location = conn.getHeaderField("Location");
				if(location != null) {
					conn = (HttpURLConnection) new URL(location).openConnection();
				}
			} */
			
			//Getting the header data
			String contentType = conn.getHeaderField("content-type");
			int contentLength = conn.getHeaderFieldInt("content-length", -1);
			
			//Checking if this content type can't be handled by the app
			if(contentType == null || !(contentType.split(";")[0].equals("application/zip") || contentType.split(";")[0].equals("application/octet-stream"))) {
				Main.getLogger().log(Level.INFO, "Unknown update MIME type " + contentType + ", opening in browser");
				
				//Passing the request on to the system to handle
				Program.launch(downloadURL);
				
				//Updating the state
				updateInstallationInProgress.set(false);
				
				UIHelper.getDisplay().asyncExec(() -> {
					//Closing the installation progress window
					if(updateInstallationShell != null && !updateInstallationShell.isDisposed()) {
						updateInstallationShell.close();
					}
				});
				
				return;
			}
			
			//Preparing the update directory
			if(Constants.updateDir.isFile()) {
				boolean result = Constants.updateDir.delete();
				if(!result) throw new IOException("File at " + Constants.updateDir + " can't be removed");
			}
			if(Constants.updateDir.exists()) {
				File[] directoryContents = Constants.updateDir.listFiles();
				if(directoryContents == null)
					throw new IOException("Directory at " + Constants.updateDir + " can't be emptied");
				for(File file : directoryContents) Constants.recursiveDelete(file);
			} else {
				boolean result = Constants.updateDir.mkdir();
				if(!result) throw new IOException("Directory at " + Constants.updateDir + " can't be created");
			}
			
			//Downloading and extracting the file contents
			String canonicalDestDirPath = Constants.updateDir.getCanonicalPath();
			try(ZipInputStream zipInputStream = new ZipInputStream(new BufferedInputStream(conn.getInputStream()))) {
				byte[] buffer = new byte[32 * 1024]; //32 kb
				ZipEntry zipEntry = zipInputStream.getNextEntry();
				int bytesRead = 0;
				int lastProgress = 0; //0 to 100
				while(zipEntry != null) {
					File destFile = new File(Constants.updateDir, zipEntry.getName());
					
					if(Constants.updateDir.equals(destFile.getParentFile())) {
						topZipFile = destFile;
					}
					
					//Zip slip prevention
					if(!destFile.getCanonicalPath().startsWith(canonicalDestDirPath + File.separator)) {
						throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
					}
					
					//Writing the data
					if(zipEntry.isDirectory()) {
						destFile.mkdir();
					} else {
						try(FileOutputStream outputStream = new FileOutputStream(destFile)) {
							int len;
							while((len = zipInputStream.read(buffer)) > 0) {
								outputStream.write(buffer, 0, len);
							}
						}
						destFile.setExecutable(true);
					}
					
					if(contentLength > 0) {
						//Adding to the total read count
						bytesRead += zipEntry.getCompressedSize();
						
						//Updating the progress
						int newProgress = (int) (((float) bytesRead / contentLength) * 100);
						if(lastProgress != newProgress) {
							lastProgress = newProgress;
							
							//Updating the progress bar
							UIHelper.getDisplay().asyncExec(() -> {
								if(updateInstallationProgressBar != null && !updateInstallationProgressBar.isDisposed()) {
									updateInstallationProgressBar.setSelection(newProgress);
								}
							});
						}
					}
					
					//Reading the next entry
					zipEntry = zipInputStream.getNextEntry();
				}
			}
			
			//Closing the installation progress window
			UIHelper.getDisplay().asyncExec(() -> {
				if(updateInstallationShell != null && !updateInstallationShell.isDisposed()) {
					updateInstallationShell.close();
				}
			});
		} catch(IOException exception) {
			//Logging the error
			Main.getLogger().log(Level.INFO, "Couldn't download update file / " + exception.getMessage(), exception);
			
			//Updating the state
			updateInstallationInProgress.set(false);
			
			UIHelper.getDisplay().asyncExec(() -> {
				//Closing the installation progress window
				if(updateInstallationShell != null && !updateInstallationShell.isDisposed()) {
					updateInstallationShell.close();
				}
				
				//Displaying an error
				UIHelper.displayAlertDialog(Main.resources().getString("message.error.update.network"));
			});
			
			return;
		}
		
		//No update data downloaded?
		if(topZipFile == null) {
			//Updating the state
			updateInstallationInProgress.set(false);
			
			//Displaying an error
			UIHelper.getDisplay().asyncExec(() -> UIHelper.displayAlertDialog(Main.resources().getString("message.error.update.bad_update")));
			
			return;
		}
		
		//Kicking off the installer process
		try {
			final String pathUpdate = topZipFile.getCanonicalPath(); //The path of the copy of AirMessage to install
			
			Main.getLogger().log(Level.INFO, "Starting installer process");
			Main.getLogger().log(Level.INFO, "Update path: " + pathUpdate);
			
			String script = new String(Main.class.getClassLoader().getResourceAsStream("installUpdate.sh").readAllBytes());
			
			File outputFile = new File(Constants.applicationSupportDir, "update.log");
			if(outputFile.exists()) outputFile.delete();
			
			ProcessBuilder processBuilder = new ProcessBuilder();
			processBuilder.command("sh", "-c", script, "install", pathUpdate);
			processBuilder.redirectOutput(outputFile);
			processBuilder.start();
		} catch(IOException exception) {
			//Logging the error
			Main.getLogger().log(Level.INFO, "Couldn't initialize installation or update paths / " + exception.getMessage(), exception);
			
			//Updating the state
			updateInstallationInProgress.set(false);
			
			//Displaying an error
			UIHelper.getDisplay().asyncExec(() -> UIHelper.displayAlertDialog(Main.resources().getString("message.error.update.internal")));
			
			return;
		}
		
		//Ending this process
		System.exit(0);
	}
	
	private static void showInstallationWindow() {
		//Creating the shell
		updateInstallationShell = new Shell(SWT.TITLE);
		updateInstallationShell.setText(Main.resources().getString("label.update.check.title"));
		updateInstallationShell.setMinimumSize(300, 0);
		
		GridLayout updateDownloadShellGL = new GridLayout();
		updateDownloadShellGL.numColumns = 1;
		updateDownloadShellGL.marginTop = updateDownloadShellGL.marginBottom = updateDownloadShellGL.marginLeft = updateDownloadShellGL.marginRight = UIHelper.windowMargin;
		updateDownloadShellGL.verticalSpacing = UIHelper.windowMargin;
		updateInstallationShell.setLayout(updateDownloadShellGL);
		
		//Adding the title
		Label title = new Label(updateInstallationShell, SWT.NONE);
		title.setText(Main.resources().getString("label.update.process.download"));
		title.setFont(UIHelper.getFont(title.getFont(), -1, SWT.BOLD));
		GridData titleGD = new GridData();
		titleGD.horizontalAlignment = GridData.FILL;
		titleGD.grabExcessHorizontalSpace = true;
		title.setLayoutData(titleGD);
		
		//Adding the progress bar
		ProgressBar progressBar = updateInstallationProgressBar = new ProgressBar(updateInstallationShell, SWT.HORIZONTAL);
		GridData progressBarGD = new GridData();
		progressBarGD.horizontalAlignment = GridData.FILL;
		progressBarGD.grabExcessHorizontalSpace = true;
		progressBar.setLayoutData(progressBarGD);
		
		//Packing the shell
		updateInstallationShell.pack();
		
		//Centering the window
		Rectangle screenBounds = UIHelper.getDisplay().getPrimaryMonitor().getBounds();
		Rectangle windowBounds = updateInstallationShell.getBounds();
		updateInstallationShell.setLocation(screenBounds.x + (screenBounds.width - windowBounds.width) / 2, screenBounds.y + (screenBounds.height - windowBounds.height) / 2);
		
		//Opening the shell
		updateInstallationShell.open();
		updateInstallationShell.forceActive();
	}
}