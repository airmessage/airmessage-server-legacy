package me.tagavari.airmessage.server;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

public class UIHelper {
	//Creating the reference values
	static final int windowMargin = 5;
	static final int sheetMargin = 20;
	static final int dialogButtonBarMargin = 8;
	static final int minButtonWidth = 100;
	static final int smallMinButtonWidth = 80;
	private static final Display display = new Display();
	
	static boolean displayVersionWarning() {
		//Showing an alert dialog
		Shell shell = new Shell();
		MessageBox dialog = new MessageBox(shell, SWT.ICON_WARNING | SWT.YES | SWT.NO);
		dialog.setMessage(I18N.i.warning_osUnsupported());
		int result = dialog.open();
		if(!shell.isDisposed()) shell.dispose();
		
		//Returning the result
		return result == SWT.YES;
	}
	
	static void openIntroWindow() {
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
		gridLayout.marginLeft = windowMargin;
		gridLayout.marginTop = windowMargin;
		gridLayout.marginRight = windowMargin;
		gridLayout.marginBottom = windowMargin;
		gridLayout.verticalSpacing = 5;
		shell.setLayout(gridLayout);
		
		//Creating the UI components
		{
			Label titleLabel = new Label(shell, SWT.NONE);
			titleLabel.setText(I18N.i.intro_title());
			titleLabel.setFont(getFont(titleLabel.getFont(), 20, SWT.BOLD));
		}
		
		{
			Label subtitleLabel = new Label(shell, SWT.NONE);
			subtitleLabel.setText(I18N.i.intro_text());
		}
		
		{
			Button prefsButton = new Button(shell, SWT.PUSH);
			prefsButton.setText(I18N.i.button_openPrefs());
			prefsButton.addListener(SWT.Selection, event -> {
				shell.close();
				PreferencesManager.openWindow();
			});
			shell.setDefaultButton(prefsButton);
			
			GridData gridData = new GridData();
			gridData.horizontalAlignment = SWT.END;
			prefsButton.setLayoutData(gridData);
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
		
		//Registering a mouse tracker for the shell
		new MouseTracker().registerShell(shell, shell);
	}
	
	static void displayAlertDialog(String message) {
		//Showing an alert dialog
		Shell shell = new Shell();
		MessageBox dialog = new MessageBox(shell, SWT.ICON_WARNING | SWT.OK);
		dialog.setMessage(message);
		dialog.open();
		if(!shell.isDisposed()) shell.dispose();
	}
	
	static void startEventLoop() {
		while(!display.isDisposed()) if(!display.readAndDispatch()) display.sleep();
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
			
			if(control instanceof Composite) for(Control child : ((Composite) control).getChildren()) if((child.getStyle() & SWT.PUSH) != SWT.PUSH)registerShell(shell, child);
		}
	}
	
	static Font getFont(Font font, int fontSize, int style) {
		//Getting the font data
		FontData fontData = font.getFontData()[0];
		
		//Setting the font information
		if(fontSize != -1) fontData.setHeight(fontSize);
		if(style != -1) fontData.setStyle(style);
		
		//Returning the font
		return new Font(getDisplay(), fontData);
	}
	
	static void packControl(Control control, String text, int padding) {
		GC gc = new GC(control);
		Point textSize = gc.textExtent(text);
		gc.dispose();
		control.setSize(textSize.x + padding, control.getSize().y);
	}
	
	public static Display getDisplay() {
		return display;
	}
}