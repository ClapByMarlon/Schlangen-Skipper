package de.laberlucas.botshopgui.gui;

import de.laberlucas.botshopgui.BotShopAddon;
import de.laberlucas.botshopgui.util.BotRepository;
import de.laberlucas.botshopgui.util.BotRepository.BotInfo;
import de.laberlucas.botshopgui.util.BotRepository.SourceInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.ShaderGroup;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class BotSelectionGui extends GuiScreen {

    private static final int BUTTON_REFRESH = 1;
    private static final int BUTTON_CLOSE = 2;
    private static final int BUTTON_NAV_API = 100;

    private static final int COLOR_ACCENT = 0xFF6E294E;
    private static final int COLOR_ACCENT_HOVER = 0xFF84365C;
    private static final int COLOR_PANEL = 0xFF141B26;
    private static final int COLOR_CONTENT = 0xFF161D28;
    private static final int COLOR_INPUT = 0xFF0D1219;
    private static final int COLOR_BORDER = 0xFF06090E;
    private static final int COLOR_BUTTON_BORDER = 0xFF3C4553;
    private static final int COLOR_BUTTON_BORDER_HOVER = 0xFF66505D;
    private static final int COLOR_BUTTON_HIGHLIGHT = 0xFF874260;
    private static final int COLOR_BUTTON_HOVER_LINE = 0xFFA15A78;
    private static final int COLOR_SURFACE = 0xFF171E29;
    private static final int COLOR_SURFACE_HOVER = 0xFF202A39;

    private final BotShopAddon addon;
    private final List<UiButton> uiButtons = new ArrayList<UiButton>();
    private GuiTextField searchField;
    private List<BotInfo> filteredBots = Collections.emptyList();
    private String selectedApiUrl;
    private int scrollOffset;
    private long lastRefreshMs;
    private int apiTabSourceCount = -1;
    private int searchX;
    private int searchY;
    private int searchWidth;
    private boolean draggingScrollBar;
    private int scrollDragOffset;
    private ShaderGroup blurShader;

    public BotSelectionGui(BotShopAddon addon) {
        this.addon = addon;
    }

    @Override
    public void initGui() {
        String search = searchField == null ? "" : searchField.getText();
        Keyboard.enableRepeatEvents(true);
        searchX = getScreenMargin() + getScaledX(18);
        searchY = getToolbarTop() + Math.max(4, (getToolbarHeight() - getControlHeight()) / 2);
        searchWidth = Math.max(150, getScaledX(637));

        buttonList.clear();
        searchField = createSearchField(fontRendererObj, searchX + 8, searchY, searchWidth - 16, getControlHeight());
        searchField.setEnableBackgroundDrawing(false);
        searchField.setMaxStringLength(64);
        searchField.setText(search);
        searchField.setFocused(true);
        rebuildButtons();
        refreshFilteredBots();
        setupBlurShader();
    }

    protected GuiTextField createSearchField(FontRenderer font, int x, int y, int fieldWidth, int fieldHeight) {
        return new GuiTextField(0, font, x, y + Math.max(0, (fieldHeight - font.FONT_HEIGHT) / 2), fieldWidth, 18);
    }

    @Override
    public void updateScreen() {
        if (searchField != null) {
            searchField.updateCursorCounter();
        }
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        draggingScrollBar = false;
        destroyBlurShader();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (System.currentTimeMillis() - lastRefreshMs > 1000L) {
            refreshFilteredBots();
        }
        if (getApiOptions().size() != apiTabSourceCount) {
            rebuildButtons();
        }

        renderBlur(partialTicks);
        if (mc.theWorld == null) {
            drawDefaultBackground();
        }
        drawRect(0, 0, width, height, 0x66070B11);

        int margin = getScreenMargin();
        int titleTop = getTitleTop();
        int navTop = getNavTop();
        int toolbarTop = getToolbarTop();
        int contentTop = getContentTop();
        int bottom = getContentBottom();

        drawPanelFrame(margin, titleTop, width - margin, titleTop + getTitleHeight(), 5, COLOR_PANEL);
        drawPanelFrame(margin, navTop, width - margin, navTop + getNavHeight(), 5, COLOR_PANEL);
        drawPanelFrame(margin, toolbarTop, width - margin, toolbarTop + getToolbarHeight(), 5, COLOR_PANEL);
        drawPanelFrame(margin, contentTop, width - margin, bottom, 5, COLOR_CONTENT);

        int titleTextY = titleTop + (getTitleHeight() - Math.round(9 * getTitleScale())) / 2;
        drawScaledCenteredString("\u00a7fSchlangen Skipper", titleTextY, getTitleScale());

        drawInsetField(searchX, searchY, searchX + searchWidth, searchY + getControlHeight(),
            searchField.isFocused() ? COLOR_ACCENT : 0xFF303946, COLOR_INPUT);
        searchField.drawTextBox();
        if (searchField.getText().isEmpty()) {
            drawString(fontRendererObj, "\u00a77Suche", searchX + 8, getSearchTextY(), 0xFFFFFF);
        }
        drawStatus();
        drawRows(mouseX, mouseY);
        for (UiButton button : uiButtons) {
            button.draw(mc, mouseX, mouseY);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(null);
            return;
        }

        if (searchField.textboxKeyTyped(typedChar, keyCode)) {
            scrollOffset = 0;
            refreshFilteredBots();
            return;
        }

        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        searchField.mouseClicked(mouseX, mouseY, mouseButton);

        if (mouseButton != 0) {
            return;
        }

        if (handleScrollBarClick(mouseX, mouseY)) {
            return;
        }

        for (UiButton button : uiButtons) {
            if (button.contains(mouseX, mouseY)) {
                handleButton(button.id);
                return;
            }
        }

        int index = getHoveredIndex(mouseX, mouseY);
        if (index >= 0 && index < filteredBots.size()) {
            addon.openBotshop(filteredBots.get(index));
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
        if (clickedMouseButton == 0 && draggingScrollBar) {
            updateScrollFromMouse(mouseY);
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        super.mouseReleased(mouseX, mouseY, state);
        if (state == 0) {
            draggingScrollBar = false;
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) {
            return;
        }

        int maxScroll = Math.max(0, filteredBots.size() - getVisibleRows());
        if (wheel < 0) {
            scrollOffset = Math.min(maxScroll, scrollOffset + 1);
        } else {
            scrollOffset = Math.max(0, scrollOffset - 1);
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void drawStatus() {
        BotRepository repository = addon.getBotRepository();
        int total = repository == null ? 0 : repository.size();
        String status = "\u00a7f" + filteredBots.size() + "\u00a77/" + total + " Bots";
        if (repository != null && repository.isLoading()) {
            status += " \u00a7e(lade...)";
        }

        if (repository != null && repository.getSourceCount() > 0) {
            status += " \u00a78\u2022 \u00a7f" + repository.getLoadedSourceCount() + "\u00a77/" + repository.getSourceCount() + " APIs";
        }
        if (repository != null && repository.getLastError() != null) {
            status += " \u00a78\u2022 \u00a7c" + repository.getLastError();
        }

        String botshopStatus = addon.getBotshopStatus();
        if (botshopStatus != null) {
            status = botshopStatus;
        } else if (addon.getUpdateStatus() != null) {
            status = addon.getUpdateStatus();
        }
        int margin = getScreenMargin();
        int actionWidth = getToolbarActionWidth();
        int actionGap = getToolbarActionGap();
        int actionsRight = searchX + searchWidth + actionGap * 2 + actionWidth * 2;
        int availableWidth = Math.max(80, width - margin - actionsRight - 20);
        status = trimToWidth(status, availableWidth);
        int statusY = searchY + (getControlHeight() - 8) / 2;
        drawString(fontRendererObj, status, width - margin - 12 - fontRendererObj.getStringWidth(status), statusY, 0xFFFFFF);
    }

    private void drawRows(int mouseX, int mouseY) {
        int margin = getScreenMargin();
        int rowHeight = getRowHeight();
        int cardHeight = rowHeight - Math.max(4, getScaledY(5));
        int listX = margin + getScaledX(25);
        int listY = getListTop();
        int listRight = width - margin - getScaledX(71);
        int listWidth = listRight - listX;
        int visibleRows = getVisibleRows();

        if (filteredBots.isEmpty()) {
            drawListCardFrame(listX, listY, listX + listWidth, listY + cardHeight, COLOR_SURFACE, false);
            drawCenteredString(fontRendererObj, "\u00a77Keine Bots gefunden.", listX + listWidth / 2, listY + (cardHeight - 8) / 2, 0xFFFFFF);
            drawScrollBar(visibleRows, mouseX, mouseY);
            return;
        }

        int max = Math.min(filteredBots.size(), scrollOffset + visibleRows);
        for (int index = scrollOffset; index < max; index++) {
            BotInfo bot = filteredBots.get(index);
            String uuid = bot.getUuid();
            String name = getName(uuid);
            int row = index - scrollOffset;
            int y = listY + row * rowHeight;
            int cardRight = listX + listWidth;
            boolean hovered = mouseX >= listX && mouseX <= cardRight && mouseY >= y && mouseY <= y + cardHeight;

            drawListCardFrame(listX, y, cardRight, y + cardHeight,
                hovered ? COLOR_SURFACE_HOVER : COLOR_SURFACE, hovered);

            int nameY = y + Math.max(5, getScaledY(26)) + (getRowControlHeight() - 8) / 2;
            int nameWidth = Math.max(60, listWidth - getScaledX(30));
            drawString(fontRendererObj, "\u00a7f" + trimToWidth(name, nameWidth), listX + getScaledX(10),
                nameY, 0xFFFFFF);

            int detailsTop = y + cardHeight - Math.max(16, getScaledY(27));
            int detailsBottom = y + cardHeight - Math.max(5, getScaledY(8));
            drawInsetField(listX + getScaledX(8), detailsTop, cardRight - getScaledX(8), detailsBottom, COLOR_BORDER, COLOR_INPUT);
            String details = "UUID: " + BotRepository.normalizeUuid(uuid) + "    \u00a78\u2022\u00a77    Quelle: " + bot.getApiUrl();
            drawString(fontRendererObj, "\u00a77" + trimToWidth(details, listWidth - getScaledX(30)),
                listX + getScaledX(13), detailsTop + Math.max(3, (detailsBottom - detailsTop - 8) / 2), 0xFFFFFF);
        }

        drawScrollBar(visibleRows, mouseX, mouseY);
    }

    private void refreshFilteredBots() {
        BotRepository repository = addon.getBotRepository();
        if (repository == null) {
            filteredBots = Collections.emptyList();
            return;
        }

        ensureSelectedApiExists();
        String search = searchField == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        String normalizedSearch = search.replace("-", "");
        List<BotInfo> bots = repository.getBots();

        List<BotInfo> result = new ArrayList<BotInfo>();
        for (BotInfo bot : bots) {
            String uuid = bot.getUuid();
            String name = addon.getBotDisplayName(uuid);
            if (name == null) {
                continue;
            }
            boolean matchesApiFilter = selectedApiUrl == null || sameApi(selectedApiUrl, bot.getApiUrl());
            boolean matchesUuid = BotRepository.normalizeUuid(uuid).contains(normalizedSearch);
            boolean matchesName = name != null && name.toLowerCase(Locale.ROOT).contains(search);
            boolean matchesApiSearch = bot.getApiName().toLowerCase(Locale.ROOT).contains(search) || bot.getApiUrl().toLowerCase(Locale.ROOT).contains(search);
            if (matchesApiFilter && (search.isEmpty() || matchesUuid || matchesName || matchesApiSearch)) {
                result.add(bot);
            }
        }

        Collections.sort(result, new Comparator<BotInfo>() {
            @Override
            public int compare(BotInfo first, BotInfo second) {
                String firstName = addon.getBotDisplayName(first.getUuid());
                String secondName = addon.getBotDisplayName(second.getUuid());
                if (firstName == null && secondName == null) {
                    int byUuid = first.getUuid().compareTo(second.getUuid());
                    return byUuid != 0 ? byUuid : first.getApiName().compareToIgnoreCase(second.getApiName());
                }
                if (firstName == null) {
                    return 1;
                }
                if (secondName == null) {
                    return -1;
                }
                int byName = firstName.compareToIgnoreCase(secondName);
                return byName != 0 ? byName : first.getApiName().compareToIgnoreCase(second.getApiName());
            }
        });
        filteredBots = result;
        scrollOffset = Math.min(scrollOffset, Math.max(0, filteredBots.size() - getVisibleRows()));
        lastRefreshMs = System.currentTimeMillis();
    }

    private void rebuildButtons() {
        buttonList.clear();
        uiButtons.clear();
        int controlHeight = getControlHeight();
        int actionWidth = getToolbarActionWidth();
        int actionGap = getToolbarActionGap();

        uiButtons.add(new UiButton(BUTTON_REFRESH, searchX + searchWidth + actionGap, searchY, actionWidth, controlHeight,
            "Aktualisieren", COLOR_ACCENT, COLOR_ACCENT_HOVER));
        uiButtons.add(new UiButton(BUTTON_CLOSE, searchX + searchWidth + actionGap * 2 + actionWidth, searchY, actionWidth, controlHeight,
            "Schlie\u00dfen", COLOR_ACCENT, COLOR_ACCENT_HOVER));

        List<SourceInfo> sources = getApiOptions();
        int margin = getScreenMargin();
        int tabsLeft = margin + getScaledX(17);
        int tabsRight = width - margin - getScaledX(17);
        int navButtonHeight = getNavButtonHeight();
        int tabY = getNavTop() + Math.max(4, (getNavHeight() - navButtonHeight) / 2);
        String apiLabel = "API: " + getSelectedApiLabel();
        uiButtons.add(new UiButton(
            BUTTON_NAV_API,
            tabsLeft,
            tabY,
            tabsRight - tabsLeft,
            navButtonHeight,
            trimToWidth(apiLabel, tabsRight - tabsLeft - 20),
            COLOR_ACCENT,
            COLOR_ACCENT_HOVER
        ));
        apiTabSourceCount = sources.size();
    }

    private void handleButton(int buttonId) {
        if (buttonId == BUTTON_REFRESH) {
            if (addon.getBotRepository() != null) {
                addon.getBotRepository().refreshNow();
            }
            refreshFilteredBots();
            return;
        }
        if (buttonId == BUTTON_CLOSE) {
            mc.displayGuiScreen(null);
            return;
        }
        if (buttonId == BUTTON_NAV_API) {
            cycleApiFilter();
            refreshFilteredBots();
            rebuildButtons();
        }
    }

    private List<SourceInfo> getApiOptions() {
        BotRepository repository = addon.getBotRepository();
        return repository == null ? Collections.<SourceInfo>emptyList() : repository.getSources();
    }

    private void ensureSelectedApiExists() {
        if (selectedApiUrl == null) {
            return;
        }

        for (SourceInfo source : getApiOptions()) {
            if (sameApi(selectedApiUrl, source.getApiUrl())) {
                return;
            }
        }
        selectedApiUrl = null;
    }

    private String getSelectedApiLabel() {
        if (selectedApiUrl == null) {
            return "Alle Bots";
        }

        for (SourceInfo source : getApiOptions()) {
            if (sameApi(selectedApiUrl, source.getApiUrl())) {
                return source.getApiUrl();
            }
        }
        return selectedApiUrl;
    }

    private int getHoveredIndex(int mouseX, int mouseY) {
        int margin = getScreenMargin();
        int listX = margin + getScaledX(25);
        int listY = getListTop();
        int listWidth = width - margin - getScaledX(71) - listX;
        int rowHeight = getRowHeight();
        int cardHeight = rowHeight - Math.max(4, getScaledY(5));
        int visibleRows = getVisibleRows();

        if (mouseX < listX || mouseX > listX + listWidth || mouseY < listY || mouseY > listY + rowHeight * visibleRows) {
            return -1;
        }
        if ((mouseY - listY) % rowHeight > cardHeight) {
            return -1;
        }

        return scrollOffset + (mouseY - listY) / rowHeight;
    }

    private String getName(String uuid) {
        String name = addon.getBotDisplayName(uuid);
        return name == null ? "Name wird geladen..." : name;
    }

    private int getVisibleRows() {
        return Math.max(1, (getContentBottom() - getListTop() - Math.max(4, getScaledY(8))) / getRowHeight());
    }

    private void cycleApiFilter() {
        List<SourceInfo> sources = getApiOptions();
        if (sources.isEmpty()) {
            return;
        }

        int current = -1;
        if (selectedApiUrl != null) {
            for (int index = 0; index < sources.size(); index++) {
                if (sameApi(selectedApiUrl, sources.get(index).getApiUrl())) {
                    current = index;
                    break;
                }
            }
        }

        int next = current + 1;
        selectedApiUrl = next >= sources.size() ? null : sources.get(next).getApiUrl();
        scrollOffset = 0;
    }

    private int getScaledX(int referencePixels) {
        return Math.max(1, Math.round(width * referencePixels / 1727.0F));
    }

    private int getScaledY(int referencePixels) {
        return Math.max(1, Math.round(height * referencePixels / 910.0F));
    }

    private int getScreenMargin() {
        return Math.max(40, getScaledX(151));
    }

    private int getTitleTop() {
        return Math.max(18, getScaledY(51));
    }

    private int getTitleHeight() {
        return Math.max(34, getScaledY(111));
    }

    private float getTitleScale() {
        return Math.max(1.55F, Math.min(1.9F, width / 500.0F));
    }

    private int getNavTop() {
        return Math.max(getTitleTop() + getTitleHeight() + 6, getScaledY(173));
    }

    private int getNavHeight() {
        return Math.max(30, getScaledY(98));
    }

    private int getToolbarTop() {
        return Math.max(getNavTop() + getNavHeight() + 6, getScaledY(278));
    }

    private int getToolbarHeight() {
        return Math.max(30, getScaledY(94));
    }

    private int getContentTop() {
        return Math.max(getToolbarTop() + getToolbarHeight(), getScaledY(370));
    }

    private int getContentBottom() {
        return height - Math.max(12, getScaledY(55));
    }

    private int getListTop() {
        return getContentTop() + Math.max(6, getScaledY(13));
    }

    private int getRowHeight() {
        return Math.max(48, getScaledY(127));
    }

    private int getControlHeight() {
        return Math.max(22, getScaledY(58));
    }

    private int getToolbarActionWidth() {
        int labelWidth = Math.max(
            fontRendererObj.getStringWidth("Aktualisieren"),
            fontRendererObj.getStringWidth("Schlie\u00dfen")
        );
        return Math.max(getScaledX(130), labelWidth + 20);
    }

    private int getToolbarActionGap() {
        return Math.max(5, getScaledX(14));
    }

    private int getNavButtonHeight() {
        return Math.max(24, getScaledY(68));
    }

    private int getRowControlHeight() {
        return Math.max(20, getScaledY(57));
    }

    private int getSearchTextY() {
        return searchY + Math.max(0, (getControlHeight() - fontRendererObj.FONT_HEIGHT) / 2);
    }

    private void drawScaledCenteredString(String text, int y, float scale) {
        GlStateManager.pushMatrix();
        GlStateManager.scale(scale, scale, 1.0F);
        drawCenteredString(fontRendererObj, text, (int) (width / (2.0F * scale)), (int) (y / scale), 0xFFFFFF);
        GlStateManager.popMatrix();
    }

    private void drawScrollBar(int visibleRows, int mouseX, int mouseY) {
        ScrollBarMetrics scrollBar = getScrollBarMetrics(visibleRows);
        drawInsetField(
            scrollBar.x,
            scrollBar.y,
            scrollBar.x + scrollBar.width,
            scrollBar.y + scrollBar.height,
            COLOR_BORDER,
            COLOR_INPUT
        );

        if (filteredBots.isEmpty()) {
            return;
        }

        boolean hovered = draggingScrollBar || scrollBar.containsThumb(mouseX, mouseY);
        drawRoundedBorder(
            scrollBar.x + scrollBar.inset,
            scrollBar.thumbY,
            scrollBar.x + scrollBar.width - scrollBar.inset,
            scrollBar.thumbY + scrollBar.thumbHeight,
            2,
            hovered ? COLOR_BUTTON_BORDER_HOVER : COLOR_BUTTON_BORDER,
            hovered ? COLOR_ACCENT_HOVER : COLOR_ACCENT
        );
    }

    private ScrollBarMetrics getScrollBarMetrics(int visibleRows) {
        int scrollWidth = Math.max(10, getScaledX(34));
        int x = width - getScreenMargin() - getScaledX(24) - scrollWidth;
        int y = getContentTop() + Math.max(6, getScaledY(12));
        int trackHeight = Math.max(1, getContentBottom() - Math.max(8, getScaledY(24)) - y);
        int inset = Math.max(3, getScaledX(7));
        int innerHeight = Math.max(1, trackHeight - inset * 2);
        int botCount = filteredBots.size();
        int maxScroll = Math.max(0, botCount - visibleRows);
        int thumbHeight = botCount == 0
            ? innerHeight
            : Math.max(20, innerHeight * Math.min(visibleRows, botCount) / botCount);
        thumbHeight = Math.min(innerHeight, thumbHeight);
        int thumbTravel = Math.max(0, innerHeight - thumbHeight);
        int thumbY = y + inset;
        if (maxScroll > 0 && thumbTravel > 0) {
            thumbY += Math.round(thumbTravel * scrollOffset / (float) maxScroll);
        }
        return new ScrollBarMetrics(x, y, scrollWidth, trackHeight, inset, thumbY, thumbHeight, thumbTravel, maxScroll);
    }

    private boolean handleScrollBarClick(int mouseX, int mouseY) {
        ScrollBarMetrics scrollBar = getScrollBarMetrics(getVisibleRows());
        if (!scrollBar.containsTrack(mouseX, mouseY)) {
            return false;
        }
        if (scrollBar.maxScroll <= 0) {
            return true;
        }

        draggingScrollBar = true;
        if (scrollBar.containsThumb(mouseX, mouseY)) {
            scrollDragOffset = mouseY - scrollBar.thumbY;
        } else {
            scrollDragOffset = scrollBar.thumbHeight / 2;
            updateScrollFromMouse(mouseY);
        }
        return true;
    }

    private void updateScrollFromMouse(int mouseY) {
        ScrollBarMetrics scrollBar = getScrollBarMetrics(getVisibleRows());
        if (scrollBar.maxScroll <= 0 || scrollBar.thumbTravel <= 0) {
            scrollOffset = 0;
            return;
        }

        int minThumbY = scrollBar.y + scrollBar.inset;
        int requestedThumbY = mouseY - scrollDragOffset;
        int clampedThumbY = Math.max(minThumbY, Math.min(minThumbY + scrollBar.thumbTravel, requestedThumbY));
        scrollOffset = Math.round((clampedThumbY - minThumbY) * scrollBar.maxScroll / (float) scrollBar.thumbTravel);
    }

    private boolean sameApi(String first, String second) {
        return normalizeApiUrl(first).equals(normalizeApiUrl(second));
    }

    private String normalizeApiUrl(String value) {
        String url = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return url.endsWith("/") ? url : url + "/";
    }

    private String trimToWidth(String value, int maxWidth) {
        if (value == null) {
            return "";
        }
        if (maxWidth <= 0 || fontRendererObj.getStringWidth(value) <= maxWidth) {
            return value;
        }

        String suffix = "...";
        int allowedWidth = Math.max(0, maxWidth - fontRendererObj.getStringWidth(suffix));
        String trimmed = value;
        while (!trimmed.isEmpty() && fontRendererObj.getStringWidth(trimmed) > allowedWidth) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + suffix;
    }

    private void setupBlurShader() {
        destroyBlurShader();
        if (!OpenGlHelper.shadersSupported) {
            return;
        }

        try {
            blurShader = new ShaderGroup(
                mc.getTextureManager(),
                mc.getResourceManager(),
                mc.getFramebuffer(),
                new ResourceLocation("shaders/post/blur.json")
            );
            blurShader.createBindFramebuffers(mc.displayWidth, mc.displayHeight);
        } catch (Exception ignored) {
            destroyBlurShader();
        }
    }

    private void renderBlur(float partialTicks) {
        if (blurShader == null) {
            return;
        }

        try {
            blurShader.loadShaderGroup(partialTicks);
            mc.getFramebuffer().bindFramebuffer(true);
        } catch (Exception ignored) {
            destroyBlurShader();
        }
    }

    private void destroyBlurShader() {
        if (blurShader != null) {
            blurShader.deleteShaderGroup();
            blurShader = null;
        }
    }

    private static void drawPanelFrame(int left, int top, int right, int bottom, int radius, int fillColor) {
        int frame = Math.max(3, Math.min(7, ((right - left) + 90) / 180));
        int shadow = Math.max(1, frame / 2);

        drawRoundedRect(left + shadow, top + shadow + 1, right + shadow, bottom + shadow + 1, radius, 0xA0000000);
        drawRoundedRect(left, top, right, bottom, radius, 0xFF05070B);
        drawRoundedRect(left + 1, top + 1, right - 1, bottom - 1, Math.max(2, radius - 1), 0xFF343A45);
        drawRoundedRect(left + Math.max(2, frame / 2), top + Math.max(2, frame / 2),
            right - Math.max(2, frame / 2), bottom - Math.max(2, frame / 2),
            Math.max(2, radius - 2), 0xFF080B10);
        drawRoundedRect(left + frame, top + frame, right - frame, bottom - frame,
            Math.max(2, radius - 3), fillColor);

        drawRoundedRect(left + frame + 2, top + frame, right - frame - 2, top + frame + 1, 0, 0xFF2B323D);
        drawRoundedRect(left + frame, top + frame + 2, left + frame + 1, bottom - frame - 2, 0, 0xFF242B35);
        drawRoundedRect(left + frame + 2, bottom - frame - 1, right - frame - 2, bottom - frame, 0, 0xFF090C12);
        drawPixelCorner(left + frame + 2, top + frame + 2, true, true);
        drawPixelCorner(right - frame - 3, top + frame + 2, false, true);
        drawPixelCorner(left + frame + 2, bottom - frame - 3, true, false);
        drawPixelCorner(right - frame - 3, bottom - frame - 3, false, false);
    }

    private static void drawListCardFrame(int left, int top, int right, int bottom, int fillColor, boolean hovered) {
        int borderColor = hovered ? 0xFF516177 : 0xFF353C47;
        drawRoundedRect(left + 2, top + 3, right + 2, bottom + 3, 4, 0x90000000);
        drawRoundedRect(left, top, right, bottom, 4, 0xFF06080C);
        drawRoundedRect(left + 2, top + 2, right - 2, bottom - 2, 3, borderColor);
        drawRoundedRect(left + 4, top + 4, right - 4, bottom - 4, 2, 0xFF090C12);
        drawRoundedRect(left + 6, top + 6, right - 6, bottom - 6, 2, fillColor);
        drawRoundedRect(left + 8, top + 6, right - 8, top + 7, 0, hovered ? 0xFF364C69 : 0xFF303743);
    }

    private static void drawInsetField(int left, int top, int right, int bottom, int borderColor, int fillColor) {
        int layer = Math.max(1, Math.min(3, (bottom - top) / 12));
        drawRoundedRect(left + 1, top + 2, right + 1, bottom + 2, 4, 0x80000000);
        drawRoundedRect(left, top, right, bottom, 4, 0xFF05070A);
        drawRoundedRect(left + layer, top + layer, right - layer, bottom - layer, 3, borderColor);
        drawRoundedRect(left + layer + 1, top + layer + 1, right - layer - 1, bottom - layer - 1, 2, fillColor);
        drawRoundedRect(left + layer + 3, top + layer + 1, right - layer - 3, top + layer + 2, 0, 0xFF252C36);
    }

    private static void drawPixelCorner(int x, int y, boolean leftSide, boolean topSide) {
        int horizontalStart = leftSide ? x : x - 2;
        int verticalStart = topSide ? y : y - 2;
        drawRoundedRect(horizontalStart, y, horizontalStart + 3, y + 1, 0, 0xFF3A414D);
        drawRoundedRect(x, verticalStart, x + 1, verticalStart + 3, 0, 0xFF3A414D);
    }

    private static void drawButtonFrame(int left, int top, int right, int bottom, int radius, int fillColor, boolean hovered) {
        int borderColor = hovered ? COLOR_BUTTON_BORDER_HOVER : COLOR_BUTTON_BORDER;
        drawRoundedRect(left + 2, top + 3, right + 2, bottom + 3, radius, 0x90000000);
        drawRoundedRect(left, top, right, bottom, radius, 0xFF05070B);
        drawRoundedRect(left + 1, top + 1, right - 1, bottom - 1, Math.max(2, radius - 1), borderColor);
        drawRoundedRect(left + 3, top + 3, right - 3, bottom - 3, Math.max(2, radius - 2), 0xFF0A0E15);
        drawRoundedRect(left + 5, top + 5, right - 5, bottom - 5, Math.max(1, radius - 3), fillColor);
        drawRoundedRect(left + 7, top + 5, right - 7, top + 6, 0,
            hovered ? COLOR_BUTTON_HOVER_LINE : COLOR_BUTTON_HIGHLIGHT);
    }

    private static void drawRoundedBorder(int left, int top, int right, int bottom, int radius, int borderColor, int fillColor) {
        drawRoundedRect(left, top, right, bottom, radius, borderColor);
        drawRoundedRect(left + 1, top + 1, right - 1, bottom - 1, Math.max(1, radius - 1), fillColor);
    }

    private static void drawRoundedRect(int left, int top, int right, int bottom, int radius, int color) {
        if (left >= right || top >= bottom) {
            return;
        }

        int safeRadius = Math.max(0, Math.min(radius, Math.min((right - left) / 2, (bottom - top) / 2)));
        if (safeRadius == 0) {
            drawRect(left, top, right, bottom, color);
            return;
        }

        drawRect(left, top + safeRadius, right, bottom - safeRadius, color);
        for (int row = 0; row < safeRadius; row++) {
            double distanceFromCenter = safeRadius - row - 0.5D;
            int inset = (int) Math.ceil(safeRadius - Math.sqrt(safeRadius * safeRadius - distanceFromCenter * distanceFromCenter));
            drawRect(left + inset, top + row, right - inset, top + row + 1, color);
            drawRect(left + inset, bottom - row - 1, right - inset, bottom - row, color);
        }
    }

    private static class ScrollBarMetrics {

        private final int x;
        private final int y;
        private final int width;
        private final int height;
        private final int inset;
        private final int thumbY;
        private final int thumbHeight;
        private final int thumbTravel;
        private final int maxScroll;

        private ScrollBarMetrics(
            int x,
            int y,
            int width,
            int height,
            int inset,
            int thumbY,
            int thumbHeight,
            int thumbTravel,
            int maxScroll
        ) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.inset = inset;
            this.thumbY = thumbY;
            this.thumbHeight = thumbHeight;
            this.thumbTravel = thumbTravel;
            this.maxScroll = maxScroll;
        }

        private boolean containsTrack(int mouseX, int mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }

        private boolean containsThumb(int mouseX, int mouseY) {
            return mouseX >= x + inset
                && mouseX < x + width - inset
                && mouseY >= thumbY
                && mouseY < thumbY + thumbHeight;
        }
    }

    private static class UiButton {

        private final int id;
        private final int x;
        private final int y;
        private final int width;
        private final int height;
        private final String label;
        private final int normalColor;
        private final int hoverColor;

        private UiButton(int id, int x, int y, int width, int height, String label, int normalColor, int hoverColor) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.label = label;
            this.normalColor = normalColor;
            this.hoverColor = hoverColor;
        }

        private void draw(Minecraft minecraft, int mouseX, int mouseY) {
            boolean hovered = contains(mouseX, mouseY);
            int backgroundColor = hovered ? hoverColor : normalColor;
            drawButtonFrame(x, y, x + width, y + height, 5, backgroundColor, hovered);
            if (hovered) {
                drawRoundedRect(x + 4, y + height - 2, x + width - 4, y + height - 1, 0, COLOR_BUTTON_HOVER_LINE);
            }
            int textX = x + (width - minecraft.fontRendererObj.getStringWidth(label)) / 2;
            minecraft.fontRendererObj.drawStringWithShadow(label, textX, y + (height - 8) / 2, 0xFFFFFFFF);
        }

        private boolean contains(int mouseX, int mouseY) {
            return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
        }
    }
}
