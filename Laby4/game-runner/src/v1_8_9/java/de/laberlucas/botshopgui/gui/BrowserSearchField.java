package de.laberlucas.botshopgui.gui;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiTextField;

final class BrowserSearchField extends GuiTextField {

    private final FontRenderer font;
    private final int fieldY;
    private final int fieldHeight;

    BrowserSearchField(FontRenderer font, int x, int y, int width, int height) {
        super(0, font, x, y, width, height);
        this.font = font;
        this.fieldY = y;
        this.fieldHeight = height;
        setEnableBackgroundDrawing(false);
    }

    @Override
    public void drawTextBox() {
        // Draw at the text baseline without shrinking or moving the mouse hitbox.
        yPosition = fieldY + Math.max(0, (fieldHeight - font.FONT_HEIGHT) / 2);
        try {
            super.drawTextBox();
        } finally {
            yPosition = fieldY;
        }
    }
}
