package com.snapps.swt;

/*
 * Copyright (C) 2009-2010 Strategic Network Applications, Inc. <snapps.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.eclipse.jface.resource.ColorRegistry;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.widgets.*;

/**
 * The SquareButton class is a simple SWT widget that can be used in place of
 * a native SWT button. Unlike the native button, this should look exactly the
 * same on all platforms, which is very helpful when designing tightly spaced
 * multi-platform layouts (I'm looking at you, gigantic blue Mac OS X buttons).
 * <p>
 * Here's a simple example:
 * <p><code>
 * 		SquareButton myButton = new SquareButton(composite, SWT.NONE);<br>
 * 		myButton.setText("This is my button"); <br>
 * 		myButton.addSelectionListener(new SelectionAdapter() { <br>
 * 			public void widgetSelected(SelectionEvent event) { <br>
 * 				doSomethingAmazing();    // the click action <br>
 * 			} <br>
 * 		}); <br>
 * </code><p>
 * The default button style looks sort of similar to the buttons on the Gmail
 * web interface. You can very easily create your own styles that look different.
 * Colors can be specified for when the button is unselected, inactive, focused,
 * clicked, and hovered over. If two different background colors are specified
 * for a given button style, the button background will be a top-to-bottom
 * blended gradient of the two colors.
 * <p>
 * You can also specify a background image to be used, either cropped, centered,
 * stretched, tiled, or set such that the button is exactly the size of the
 * background image.
 * <p>
 * This code may be used under the terms of the Apache license, version 2.0.
 * Copyright 2009-2010 Strategic Network Applications, Inc. (http://www.snapps.com).
 * <p>
 *
 * @author Julian Robichaux
 * @version 1.0
 *
 */

public class SquareButton extends Canvas {
	protected Listener keyListener;
	protected Image image, backgroundImage;
	protected String text;
	protected Font font;
	protected Color fontColor, hoverFontColor, clickedFontColor, inactiveFontColor, selectedFontColor;
	protected Color borderColor, hoverBorderColor, clickedBorderColor, inactiveBorderColor, selectedBorderColor;
	protected Color currentColor, currentColor2, currentFontColor, currentBorderColor;
	protected Color backgroundColor, backgroundColor2;
	protected Color clickedColor, clickedColor2;
	protected Color hoverColor, hoverColor2;
	protected Color inactiveColor, inactiveColor2;
	protected Color selectedColor, selectedColor2;
	protected int innerMarginWidth = 8;
	protected int innerMarginHeight = 4;
	protected int borderWidth = 1;
	protected int imagePadding = 5;
	protected boolean enabled = true;
	protected boolean roundedCorners = true;
	protected boolean isFocused = false;
	protected boolean selectionBorder = false;
	private int lastWidth, lastHeight;
	
	public static int BG_IMAGE_CROP = 0;
	public static int BG_IMAGE_STRETCH = 1;
	public static int BG_IMAGE_TILE = 2;
	public static int BG_IMAGE_CENTER = 3;
	public static int BG_IMAGE_FIT = 4;
	protected int backgroundImageStyle = 0;
	
	public static int IMAGE_LEFT = 0;
	public static int IMAGE_RIGHT = 1;
	protected int imageStyle = 0;
	
	
	public SquareButton(Composite parent, int style) {
		super(parent, style | SWT.NO_BACKGROUND);
		this.setBackgroundMode(SWT.INHERIT_DEFAULT);
		
		setDefaultColors();
		addListeners();
	}
	
	
	protected void widgetDisposed(DisposeEvent e) {
		// TODO clean up here (listeners?)
	}
	
	
	protected void addListeners() {
		addDisposeListener(new DisposeListener() {
			public void widgetDisposed(DisposeEvent e) {
				SquareButton.this.widgetDisposed(e);
			}
		});
		
		addPaintListener(new PaintListener() {
			public void paintControl(PaintEvent e) {
				SquareButton.this.paintControl(e);
			}
		});
		
		// MOUSE EVENTS
		this.addListener(SWT.MouseEnter, new Listener() {
			public void handleEvent(Event e) {
				SquareButton.this.setHoverColor(e);
			}
		});
		this.addListener(SWT.MouseExit, new Listener() {
			public void handleEvent(Event e) {
				if (isFocused)
					SquareButton.this.setSelectedColor(e);
				else
					SquareButton.this.setNormalColor(e);
			}
		});
		this.addListener(SWT.MouseUp, new Listener() {
			public void handleEvent(Event e) {
				if (e.button == 1) {
					SquareButton.this.setHoverColor(e);
					if ((e.count == 1) && enabled && (getClientArea().contains(e.x, e.y))) {
						doButtonClicked();
					}
				}
			}
		});
		this.addListener(SWT.MouseHover, new Listener() {
			public void handleEvent(Event e) {
				SquareButton.this.setHoverColor(e);
			}
		});
		this.addListener(SWT.MouseDown, new Listener() {
			public void handleEvent(Event e) {
				if (e.button == 1) {
					SquareButton.this.setClickedColor(e);
				}
			}
		});
		
		// TAB TRAVERSAL (a KeyDown listener is also required)
		this.addListener (SWT.Traverse, new Listener () {
			public void handleEvent (Event e) {
				switch (e.detail) {
					case SWT.TRAVERSE_ESCAPE:
					case SWT.TRAVERSE_RETURN:
					case SWT.TRAVERSE_TAB_NEXT:
					case SWT.TRAVERSE_TAB_PREVIOUS:
					case SWT.TRAVERSE_PAGE_NEXT:
					case SWT.TRAVERSE_PAGE_PREVIOUS:
						e.doit = true;
						break;
				}
			}
		});
		this.addListener (SWT.FocusIn, new Listener () {
			public void handleEvent (Event e) {
				isFocused = true;
				SquareButton.this.setSelectedColor(e);
				redraw();
			}
		});
		this.addListener (SWT.FocusOut, new Listener () {
			public void handleEvent (Event e) {
				isFocused = false;
				SquareButton.this.setNormalColor(e);
				redraw();
			}
		});
		
		this.addListener (SWT.KeyUp, new Listener () {
			public void handleEvent (Event e) {
				isFocused = true;
				SquareButton.this.setSelectedColor(e);
				redraw();
			}
		});
		keyListener = new Listener () {
			public void handleEvent (Event e) {
				// required for tab traversal to work
				switch (e.character) {
					case ' ':
					case '\r':
					case '\n':
						SquareButton.this.setClickedColor(e);
						redraw();
						doButtonClicked();
						break;
				}
			}
		};
		setTraversable(true);
	}
	
	
	/**
	 * SelectionListeners are notified when the button is clicked
	 *
	 * @param listener
	 */
	public void addSelectionListener (SelectionListener listener) {
		addListener(SWT.Selection, new TypedListener(listener));
	}
	public void removeSelectionListener (SelectionListener listener) {
		removeListener(SWT.Selection, listener);
	}
	
	
	protected void setTraversable (boolean canTraverse) {
		// TODO is there a better way to do this?
		try {
			if (canTraverse)
				this.addListener (SWT.KeyDown, keyListener);
			else if (!canTraverse)
				this.removeListener(SWT.KeyDown, keyListener);
		} catch (Exception e) {}
	}
	
	
	protected void doButtonClicked () {
		Event e = new Event();
		e.item = this;
		e.widget = this;
		e.type = SWT.Selection;
		notifyListeners(SWT.Selection, e);
	}
	
	
	protected void setDefaultColors () {
		fontColor = getSavedColor(0, 0, 0);
		hoverFontColor = getSavedColor(0, 0, 0);
		clickedFontColor = getSavedColor(255, 255, 255);
		inactiveFontColor = getSavedColor(187, 187, 187);
		selectedFontColor = getSavedColor(160, 107, 38);
		borderColor = getSavedColor(187, 187, 187);
		hoverBorderColor = getSavedColor(147, 147, 147);
		clickedBorderColor = getSavedColor(147, 147, 147);
		inactiveBorderColor = getSavedColor(200, 200, 200);
		selectedBorderColor = getSavedColor(160, 107, 38);
		backgroundColor = getSavedColor(248, 248, 248);
		backgroundColor2 = getSavedColor(228, 228, 228);
		clickedColor = getSavedColor(120, 120, 120);
		clickedColor2 = getSavedColor(150, 150, 150);
		hoverColor = getSavedColor(248, 248, 248);
		hoverColor2 = getSavedColor(228, 228, 228);
		inactiveColor = getSavedColor(248, 248, 248);
		inactiveColor2 = getSavedColor(228, 228, 228);
		selectedColor = getSavedColor(238, 238, 238);
		selectedColor2 = getSavedColor(218, 218, 218);
	}
	
	
	protected Color getSavedColor (int r, int g, int b) {
		String colorString = "SB_DEFAULT:" + r + "-" + g + "-" + b;
		ColorRegistry colorRegistry = JFaceResources.getColorRegistry();
		if (!colorRegistry.hasValueFor(colorString)) {
			colorRegistry.put(colorString, new RGB(r, g, b));
		}
		return colorRegistry.get(colorString);
	}
	
	
	protected void setNormalColor (Event e) {
		setMouseEventColor(backgroundColor, backgroundColor2, borderColor, fontColor);
	}
	protected void setHoverColor (Event e) {
		setMouseEventColor(hoverColor, hoverColor2, hoverBorderColor, hoverFontColor);
	}
	protected void setClickedColor (Event e) {
		setMouseEventColor(clickedColor, clickedColor2, clickedBorderColor, clickedFontColor);
	}
	protected void setInactiveColor (Event e) {
		setMouseEventColor(inactiveColor, inactiveColor2, inactiveBorderColor, inactiveFontColor);
	}
	protected void setSelectedColor (Event e) {
		setMouseEventColor(selectedColor, selectedColor2, selectedBorderColor, selectedFontColor);
	}
	protected void setMouseEventColor (Color bgColor1, Color bgColor2, Color bdrColor, Color fntColor) {
		if (!this.enabled)
			return;
		
		if (currentColor == null) {
			currentColor = backgroundColor;
			currentColor2 = backgroundColor2;
			currentBorderColor = borderColor;
			currentFontColor = fontColor;
		}
		
		boolean redrawFlag = false;
		if ((bgColor1 != null) && (!currentColor.equals(bgColor1))) {
			currentColor = currentColor2 = bgColor1;
			if (bgColor2 != null) {
				currentColor2 = bgColor2;
			}
			redrawFlag = true;
		}
		if ((bdrColor != null) && (!currentBorderColor.equals(bdrColor))) {
			currentBorderColor = bdrColor;
			redrawFlag = true;
		}
		if ((fntColor != null) && (!currentFontColor.equals(fntColor))) {
			currentFontColor = fntColor;
			redrawFlag = true;
		}
		if (redrawFlag) { redraw(); }
	}
	
	
	private void paintControl(PaintEvent e) {
		if (currentColor == null) {
			currentColor = backgroundColor;
			currentColor2 = backgroundColor2;
			currentBorderColor = borderColor;
			currentFontColor = fontColor;
		}
		
		int x = this.innerMarginWidth + 1;
		int y = this.innerMarginHeight;
		Point p = this.computeSize(SWT.DEFAULT, SWT.DEFAULT, false);
		// with certain layouts, the width is sometimes 1 pixel too wide
		if (p.x > getClientArea().width) { p.x = getClientArea().width; }
		Rectangle rect = new Rectangle(0, 0, p.x, p.y);
		
		GC gc = e.gc;
		gc.setAntialias(SWT.ON);
		gc.setAdvanced(true);
		
		
		// add transparency by making the canvas background the same as
		// the parent background (only needed for rounded corners)
		if (roundedCorners) {
			gc.setBackground(getParent().getBackground());
			gc.fillRectangle(rect);
		}
		
		
		// draw the background color of the inside of the button. There's no such
		// thing as a rounded gradient rectangle in SWT, so we need to draw a filled
		// rectangle that's just the right size to fit inside a rounded rectangle
		// without spilling out at the corners
		gc.setForeground(this.currentColor);
		gc.setBackground(this.currentColor2);
		Rectangle fill = new Rectangle(rect.x + 1, rect.y + 1, rect.width - 1, rect.height - 1);
		if (roundedCorners) {
			fill = new Rectangle(rect.x + 1, rect.y + 1, rect.width - 2, rect.height - 3);
		}
		gc.fillGradientRectangle(fill.x, fill.y, fill.width, fill.height, true);
		
		
		// if there's a background image, draw it on top of the interior color
		// so any image transparency allows the button colors to come through
		drawBackgroundImage(gc, fill);
		
		
		// draw the border of the button. If a zero-pixel border was specified,
		// draw a 1-pixel border the same color as the canvas background instead
		// so the rounded corners look right
		gc.setLineStyle(SWT.LINE_SOLID);
		int arcHeight = Math.max(5, (int)(p.y / 10));
		int arcWidth = arcHeight;
		int bw = borderWidth;
		if (borderWidth > 0) {
			gc.setLineWidth(borderWidth);
			gc.setForeground(this.currentBorderColor);
		} else {
			bw = 1;
			gc.setLineWidth(1);
			gc.setForeground(getParent().getBackground());
			arcWidth = arcHeight += 1;
		}
		if (roundedCorners) {
			gc.drawRoundRectangle(rect.x + (bw-1), rect.y + (bw-1), rect.width - bw, rect.height - 2, arcWidth, arcHeight);
		} else {
			gc.drawRectangle(rect.x, rect.y, rect.width - bw, rect.height - 1);
		}
		
		// dotted line selection border around the text, if any
		if (this.isFocused && this.selectionBorder) {
			gc.setForeground(currentFontColor);
			gc.setLineStyle(SWT.LINE_DASH);
			gc.setLineWidth(1);
			gc.drawRectangle(rect.x + (bw+1), rect.y + (bw+1), rect.width - (bw+5), rect.height - (bw+5));
		}
		
		// side image and/or button text, if any
		if (imageStyle == IMAGE_RIGHT) {
			drawText(gc, x, y);
			if (image != null) {
				x = rect.width - x - image.getBounds().width + imagePadding;
				drawImage(gc, x, y);
			}
		} else {
			x = drawImage(gc, x, y);
			drawText(gc, x, y);
		}
	}
	
	
	private void drawText (GC gc, int x, int y) {
		gc.setFont(font);
		gc.setForeground(currentFontColor);
		gc.drawText(text, x, y, SWT.DRAW_TRANSPARENT);
	}
	
	
	private int drawImage (GC gc, int x, int y) {
		if (image == null)
			return x;
		gc.drawImage(image, x, y);
		return x + image.getBounds().width + imagePadding;
	}
	
	
	private void drawBackgroundImage (GC gc, Rectangle rect) {
		if (backgroundImage == null)
			return;
		
		Rectangle imgBounds = backgroundImage.getBounds();
		
		if (backgroundImageStyle == BG_IMAGE_STRETCH) {
			gc.drawImage(backgroundImage, 0, 0, imgBounds.width, imgBounds.height, rect.x, rect.y, rect.width, rect.height);
			
		} else if (backgroundImageStyle == BG_IMAGE_CENTER) {
			int x = (imgBounds.width - rect.width) / 2;
			int y = (imgBounds.height - rect.height) / 2;
			Rectangle centerRect = new Rectangle(rect.x, rect.y, rect.width, rect.height);
			if (x < 0) {
				centerRect.x -= x;
				x = 0;
			}
			if (y < 0) {
				centerRect.y -= y;
				y = 0;
			}
			drawClippedImage(gc, backgroundImage, x, y, centerRect);
			
		} else if (backgroundImageStyle == BG_IMAGE_TILE) {
			for (int y = 0; y < rect.height; y += imgBounds.height) {
				Rectangle tileRect = new Rectangle(rect.x, y + rect.y, rect.width, rect.height-y);
				
				for (int x = 0; x < rect.width; x += imgBounds.width) {
					tileRect.x += drawClippedImage(gc, backgroundImage, 0, 0, tileRect);
					tileRect.width -= x;
				}
			}
			
		} else {
			// default is BG_IMAGE_CROP
			drawClippedImage(gc, backgroundImage, 0, 0, rect);
		}
	}
	
	private int drawClippedImage (GC gc, Image image, int x, int y, Rectangle rect) {
		if (image != null) {
			Rectangle imgBounds = image.getBounds();
			int width = Math.min(imgBounds.width-x, rect.width);
			int height = Math.min(imgBounds.height-y, rect.height);
			gc.drawImage(image, x, y, width, height, rect.x, rect.y, width, height);
			return width;
		}
		return 0;
	}
	
	
	public Point computeSize(int wHint, int hHint, boolean changed) {
		if ((wHint == SWT.DEFAULT) && (hHint == SWT.DEFAULT) && !changed &&
				(lastWidth > 0) && (lastHeight > 0)) {
			return new Point(lastWidth, lastHeight);
		}
		
		int width = 0, height = 0;
		if (image != null) {
			Rectangle bounds = image.getBounds();
			width = bounds.width + imagePadding;
			height = bounds.height + (this.innerMarginHeight*2);
		}
		if (text != null) {
			GC gc = new GC(this);
			gc.setFont(font);
			//Point extent = gc.stringExtent(text);  // stringExtent ignores linefeeds
			Point extent = gc.textExtent(text);
			gc.dispose();
			
			width += extent.x + (this.innerMarginWidth*2);
			height = Math.max(height, extent.y + (this.innerMarginHeight*2));
		}
		
		if (wHint != SWT.DEFAULT) width = wHint;
		if (hHint != SWT.DEFAULT) height = hHint;
		
		if ((backgroundImage != null) && (backgroundImageStyle == BG_IMAGE_FIT)) {
			width = backgroundImage.getBounds().width;
			height = backgroundImage.getBounds().height;
		}
		
		lastWidth = width + 2;
		lastHeight = height + 2;
		return new Point(lastWidth, lastHeight);
	}
	
	
	/**
	 * This is an image that will be displayed to the side of the
	 * text inside the button (if any). By default the image will be
	 * to the left of the text; however, setImageStyle can be used to
	 * specify that it's either to the right or left. If there is no
	 * text, the image will be centered inside the button.
	 *
	 * @param image
	 */
	public void setImage(Image image) {
		this.image = image;
		redraw();
	}
	public Image getImage() {
		return image;
	}
	
	/**
	 * Set the style with which the side image is drawn, either IMAGE_LEFT
	 * or IMAGE_RIGHT (default is IMAGE_LEFT).
	 *
	 * @param imageStyle
	 */
	public void setImageStyle(int imageStyle) {
		this.imageStyle = imageStyle;
	}
	public int getImageStyle() {
		return imageStyle;
	}
	
	
	
	/**
	 * This is an image that will be used as a background image for the
	 * button, drawn in the manner specified by the backgroundImageStyle
	 * setting. The order in which the button is drawn is: background
	 * color, then background image, then button image and text. So if the
	 * background image has transparency, the background color will show
	 * through the transparency.
	 */
	public void setBackgroundImage(Image backgroundImage) {
		this.backgroundImage = backgroundImage;
		redraw();
	}
	public Image getBackgroundImage() {
		return backgroundImage;
	}
	
	/**
	 * Set the style with which the background image is drawn (default is
	 * BG_IMAGE_CROP). The different styles are:
	 * <p><ul>
	 * <li>BG_IMAGE_CROP: the image is drawn once, with the top left corner
	 * of the image at the top left corner of the button. Any part of the image
	 * that is too wide or too tall to fit inside the button area is clipped
	 * (cropped) off.</li>
	 * <li>BG_IMAGE_STRETCH: the image is stretched (or squashed) to exactly
	 * fit the button area.</li>
	 * <li>BG_IMAGE_TILE: the image is tiled vertically and horizontally to
	 * cover the entire button area.</li>
	 * <li>BG_IMAGE_CENTER: the center of the image is placed inside the center
	 * of the button. Any part of the image that is too tall or too wide to fit
	 * will be clipped.</li>
	 * <li>BG_IMAGE_FIT: the button will be the exact size of the image. Note that
	 * this can sometimes truncate the text inside the button.</li>
	 * </ul>
	 *
	 * @param backgroundImageStyle
	 */
	public void setBackgroundImageStyle(int backgroundImageStyle) {
		this.backgroundImageStyle = backgroundImageStyle;
	}
	public int getBackgroundImageStyle() {
		return backgroundImageStyle;
	}
	
	public String getText() {
		return text;
	}
	public void setText(String text) {
		this.text = text;
		redraw();
	}
	public Font getFont() {
		return font;
	}
	public void setFont(Font font) {
		if (font != null)
			this.font = font;
	}
	
	/**
	 * Set whether or not this button is enabled (active) or
	 * not (inactive). This setting can be changed dynamically
	 * after the button has been drawn.
	 * <p>
	 * An inactive button does not change color
	 * when it is hovered over or clicked, does not receive focus
	 * or participate in the tab order of the widget container,
	 * and does not notify listeners when clicked.
	 */
	public void setEnabled(boolean enabled) {
		boolean oldSetting = this.enabled;
		this.enabled = enabled;
		if (oldSetting != enabled) {
			if (!enabled) {
				this.enabled = true;
				this.setInactiveColor(null);
				this.setTraversable(false);
				this.enabled = false;
			} else {
				this.setNormalColor(null);
				this.setTraversable(true);
			}
		}
	}
	public boolean getEnabled() {
		return enabled;
	}
	public boolean isEnabled() {
		return enabled;
	}
	
	/**
	 * Set the inner margin between the left and right of the text
	 * inside the button and the button borders, in pixels. Like the
	 * left and right padding for the text. Default is 8 pixels.
	 *
	 * @param innerMarginWidth
	 */
	public void setInnerMarginWidth(int innerMarginWidth) {
		if (innerMarginWidth >= 0)
			this.innerMarginWidth = innerMarginWidth;
	}
	public int getInnerMarginWidth() {
		return innerMarginWidth;
	}
	
	/**
	 * Set the inner margin between the top and bottom of the text
	 * inside the button and the button borders, in pixels. Like the
	 * top and bottom padding for the text. Default is 4 pixels.
	 *
	 * @param innerMarginHeight
	 */
	public void setInnerMarginHeight(int innerMarginHeight) {
		if (innerMarginHeight >= 0)
			this.innerMarginHeight = innerMarginHeight;
	}
	public int getInnerMarginHeight() {
		return innerMarginHeight;
	}
	
	/**
	 * Set whether or not the button should have rounded corners
	 * (default is true).
	 *
	 * @param roundedCorners
	 */
	public void setRoundedCorners(boolean roundedCorners) {
		this.roundedCorners = roundedCorners;
	}
	public boolean hasRoundedCorners() {
		return roundedCorners;
	}
	
	/**
	 * Set whether or not a dotted-line border should be drawn around
	 * the text inside the button when the button has tab focus. Default is
	 * false (no selection border). If a selection border is used, it will
	 * be the same color as the font color. Note that you can also use
	 * setSelectedColors() to change the look of the button when it has
	 * focus.
	 *
	 * @param selectionBorder
	 */
	public void setSelectionBorder(boolean selectionBorder) {
		this.selectionBorder = selectionBorder;
	}
	public boolean hasSelectionBorder() {
		return selectionBorder;
	}
	
	/**
	 * Set the width of the button border, in pixels (default is 1).
	 *
	 * @param borderWidth
	 */
	public void setBorderWidth(int borderWidth) {
		this.borderWidth = borderWidth;
	}
	public int getBorderWidth() {
		return borderWidth;
	}
	
	/**
	 * The colors of the button in its "default" state (not clicked, selected, etc.)
	 *
	 * @param bgColor1 the gradient color at the top of the button
	 * @param bgColor2 the gradient color at the bottom of the button (if you don't want a gradient, set this to bgColor1)
	 * @param bdrColor the color of the border around the button (if you don't want a border, use getBackground())
	 * @param fntColor the color of the font inside the button
	 */
	public void setDefaultColors (Color bgColor1, Color bgColor2, Color bdrColor, Color fntColor) {
		if (bgColor1 != null) { this.backgroundColor = bgColor1; }
		if (bgColor2 != null) { this.backgroundColor2 = bgColor2; }
		if (bdrColor != null) { this.borderColor = bdrColor; }
		if (fntColor != null) { this.fontColor = fntColor; }
	}
	
	/**
	 * The colors of the button when the mouse is hovering over it
	 *
	 * @param bgColor1 the gradient color at the top of the button
	 * @param bgColor2 the gradient color at the bottom of the button (if you don't want a gradient, set this to bgColor1)
	 * @param bdrColor the color of the border around the button (if you don't want a border, use getBackground())
	 * @param fntColor the color of the font inside the button
	 */
	public void setHoverColors (Color bgColor1, Color bgColor2, Color bdrColor, Color fntColor) {
		if (bgColor1 != null) { this.hoverColor = bgColor1; }
		if (bgColor2 != null) { this.hoverColor2 = bgColor2; }
		if (bdrColor != null) { this.hoverBorderColor = bdrColor; }
		if (fntColor != null) { this.hoverFontColor = fntColor; }
	}
	
	/**
	 * The colors of the button when it is being clicked (MouseDown)
	 *
	 * @param bgColor1 the gradient color at the top of the button
	 * @param bgColor2 the gradient color at the bottom of the button (if you don't want a gradient, set this to bgColor1)
	 * @param bdrColor the color of the border around the button (if you don't want a border, use getBackground())
	 * @param fntColor the color of the font inside the button
	 */
	public void setClickedColors (Color bgColor1, Color bgColor2, Color bdrColor, Color fntColor) {
		if (bgColor1 != null) { this.clickedColor = bgColor1; }
		if (bgColor2 != null) { this.clickedColor2 = bgColor2; }
		if (bdrColor != null) { this.clickedBorderColor = bdrColor; }
		if (fntColor != null) { this.clickedFontColor = fntColor; }
	}
	
	/**
	 * The colors of the button when it has focus
	 *
	 * @param bgColor1 the gradient color at the top of the button
	 * @param bgColor2 the gradient color at the bottom of the button (if you don't want a gradient, set this to bgColor1)
	 * @param bdrColor the color of the border around the button (if you don't want a border, use getBackground())
	 * @param fntColor the color of the font inside the button
	 */
	public void setSelectedColors (Color bgColor1, Color bgColor2, Color bdrColor, Color fntColor) {
		if (bgColor1 != null) { this.selectedColor = bgColor1; }
		if (bgColor2 != null) { this.selectedColor2 = bgColor2; }
		if (bdrColor != null) { this.selectedBorderColor = bdrColor; }
		if (fntColor != null) { this.selectedFontColor = fntColor; }
	}
	
	/**
	 * The colors of the button when it is in an inactive (not enabled) state
	 *
	 * @param bgColor1 the gradient color at the top of the button
	 * @param bgColor2 the gradient color at the bottom of the button (if you don't want a gradient, set this to bgColor1)
	 * @param bdrColor the color of the border around the button (if you don't want a border, use getBackground())
	 * @param fntColor the color of the font inside the button
	 */
	public void setInactiveColors (Color bgColor1, Color bgColor2, Color bdrColor, Color fntColor) {
		if (bgColor1 != null) { this.inactiveColor = bgColor1; }
		if (bgColor2 != null) { this.inactiveColor2 = bgColor2; }
		if (bdrColor != null) { this.inactiveBorderColor = bdrColor; }
		if (fntColor != null) { this.inactiveFontColor = fntColor; }
	}
	
}