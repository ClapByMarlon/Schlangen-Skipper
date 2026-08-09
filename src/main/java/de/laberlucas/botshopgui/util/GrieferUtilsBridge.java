package de.laberlucas.botshopgui.util;

import de.laberlucas.botshopgui.BotShopAddon;
import net.minecraft.client.Minecraft;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

public class GrieferUtilsBridge {

    private static final String BAB_BOT_CLASS = "dev.l3g7.griefer_utils.features.gui.integrations.byte_and_bit.data.BABBot";
    private static final String BOTSHOP_GUI_CLASS = "dev.l3g7.griefer_utils.features.gui.integrations.byte_and_bit.gui.BotshopGUI";
    private static final String GUI_BIG_CHEST_CLASS = "dev.l3g7.griefer_utils.core.misc.gui.guis.GuiBigChest";
    private static final String CHAT_QUEUE_CLASS = "dev.l3g7.griefer_utils.core.misc.ChatQueue";
    private static final String NAME_CACHE_CLASS = "dev.l3g7.griefer_utils.core.misc.NameCache";
    private static final int BUY_SLOT = 28;
    private static final long BOTSHOP_REQUEST_COOLDOWN_MS = 5000L;
    private static final long BOTSHOP_REQUEST_TIMEOUT_MS = 20000L;
    private static final long PROTECTION_INTERVAL_MS = 1000L;

    private final BotShopAddon addon;
    private Object protectedScreen;
    private long protectionUntilMs;
    private boolean botshopRequestInFlight;
    private long botshopRequestStartedMs;
    private long nextBotshopRequestMs;
    private long activeRequestId;
    private long nextProtectionCheckMs;

    public GrieferUtilsBridge(BotShopAddon addon) {
        this.addon = addon;
    }

    public synchronized boolean openBotshop(final BotRepository.BotInfo botInfo) {
        if (botInfo == null) {
            return false;
        }

        long now = System.currentTimeMillis();
        if (botshopRequestInFlight && now - botshopRequestStartedMs < BOTSHOP_REQUEST_TIMEOUT_MS) {
            return false;
        }
        if (now < nextBotshopRequestMs) {
            return false;
        }

        final Minecraft minecraft = Minecraft.getMinecraft();
        final Object sourceScreen = minecraft == null ? null : minecraft.currentScreen;
        final long requestId = ++activeRequestId;
        botshopRequestInFlight = true;
        botshopRequestStartedMs = now;
        nextBotshopRequestMs = now + BOTSHOP_REQUEST_COOLDOWN_MS;

        try {
            final Object bot = createPrivateBot(botInfo.getUuid(), botInfo.getApiUrl());
            Object sync = findMethod(bot.getClass(), "sync").invoke(bot);
            if (!(sync instanceof CompletableFuture)) {
                finishBotshopRequest(requestId);
                return false;
            }

            ((CompletableFuture<?>) sync).whenComplete(new BiConsumer<Object, Throwable>() {
                @Override
                public void accept(final Object result, final Throwable throwable) {
                    Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                        @Override
                        public void run() {
                            if (!finishBotshopRequest(requestId) || Minecraft.getMinecraft().currentScreen != sourceScreen) {
                                return;
                            }
                            if (throwable != null || !(result instanceof Boolean) || !((Boolean) result).booleanValue() || !hasItems(bot)) {
                                return;
                            }

                            openGui(bot, true);
                        }
                    });
                }
            });
            return true;
        } catch (Throwable throwable) {
            finishBotshopRequest(requestId);
            return false;
        }
    }

    private synchronized boolean finishBotshopRequest(long requestId) {
        if (requestId != activeRequestId) {
            return false;
        }

        botshopRequestInFlight = false;
        nextBotshopRequestMs = Math.max(nextBotshopRequestMs, System.currentTimeMillis() + BOTSHOP_REQUEST_COOLDOWN_MS);
        return true;
    }

    public String getBotDisplayName(String uuid) {
        try {
            Class<?> nameCacheClass = Class.forName(NAME_CACHE_CLASS);
            Method getName = nameCacheClass.getMethod("getName", UUID.class);
            Object value = getName.invoke(null, toUuid(uuid));
            if (value instanceof String && !((String) value).trim().isEmpty()) {
                return (String) value;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    public void protectActiveBotshop() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) {
            return;
        }

        Object currentScreen = mc.currentScreen;
        if (protectedScreen != null && currentScreen != protectedScreen) {
            protectedScreen = null;
            protectionUntilMs = 0L;
            nextProtectionCheckMs = 0L;
        }

        if (currentScreen == null || protectedScreen == null || currentScreen != protectedScreen || !isGrieferUtilsBotshop(currentScreen)) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now < nextProtectionCheckMs) {
            return;
        }

        nextProtectionCheckMs = now + PROTECTION_INTERVAL_MS;
        protectionUntilMs = now + 30000L;
        patchBuySlot(currentScreen);
        removeQueuedPayCommands();
    }

    private Object createPrivateBot(String rawUuid, String apiUrl) throws Exception {
        String uuid = BotRepository.normalizeUuid(rawUuid);
        Class<?> botClass = Class.forName(BAB_BOT_CLASS);
        Constructor<?> constructor = botClass.getConstructor(String.class, String.class);
        return constructor.newInstance(normalizeApiUrl(apiUrl), uuid);
    }

    private String normalizeApiUrl(String value) {
        String url = value == null ? "" : value.trim();
        return url.endsWith("/") ? url : url + "/";
    }

    private UUID toUuid(String uuid) {
        String value = BotRepository.normalizeUuid(uuid);
        if (value.length() != 32) {
            return UUID.fromString(uuid);
        }
        return UUID.fromString(value.substring(0, 8) + "-" + value.substring(8, 12) + "-" + value.substring(12, 16) + "-" + value.substring(16, 20) + "-" + value.substring(20));
    }

    private boolean hasItems(Object bot) {
        try {
            Field itemsField = bot.getClass().getDeclaredField("items");
            itemsField.setAccessible(true);
            Object items = itemsField.get(bot);
            return items instanceof List && !((List<?>) items).isEmpty();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void openGui(Object bot, boolean lockBuying) {
        try {
            Class<?> guiClass = Class.forName(BOTSHOP_GUI_CLASS);
            Constructor<?> constructor = guiClass.getConstructor(Class.forName(BAB_BOT_CLASS));
            Object gui = constructor.newInstance(bot);
            if (lockBuying) {
                protectedScreen = gui;
                protectionUntilMs = System.currentTimeMillis() + 30000L;
                nextProtectionCheckMs = System.currentTimeMillis() + PROTECTION_INTERVAL_MS;
                patchBuySlot(gui);
            } else {
                protectedScreen = null;
                protectionUntilMs = 0L;
            }
        } catch (Throwable throwable) {
            addon.notify("\u00a7cGrieferUtils-GUI konnte nicht erstellt werden.");
        }
    }

    @SuppressWarnings("unchecked")
    private void patchBuySlot(Object gui) {
        try {
            Class<?> guiBigChest = Class.forName(GUI_BIG_CHEST_CLASS);
            Runnable blocker = new Runnable() {
                @Override
                public void run() {
                    protectionUntilMs = System.currentTimeMillis() + 30000L;
                    removeQueuedPayCommands();
                }
            };

            for (String fieldName : new String[]{"leftClickEvents", "rightClickEvents", "midClickEvents"}) {
                Field field = guiBigChest.getDeclaredField(fieldName);
                field.setAccessible(true);
                Object value = field.get(gui);
                if (value instanceof List) {
                    List<Runnable> events = (List<Runnable>) value;
                    if (events.size() > BUY_SLOT) {
                        events.set(BUY_SLOT, blocker);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void removeQueuedPayCommands() {
        try {
            Class<?> chatQueue = Class.forName(CHAT_QUEUE_CLASS);
            removePayCommandsFromList(getListField(chatQueue, "queuedMessages"));
            removePayCommandsFromList(getListField(chatQueue, "queuedSlowMessages"));
        } catch (Throwable ignored) {
        }
    }

    private List<?> getListField(Class<?> owner, String name) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        Object value = field.get(null);
        return value instanceof List ? (List<?>) value : null;
    }

    private void removePayCommandsFromList(List<?> list) {
        if (list == null) {
            return;
        }

        Iterator<?> iterator = list.iterator();
        while (iterator.hasNext()) {
            Object entry = iterator.next();
            if (entry instanceof String && ((String) entry).trim().toLowerCase().startsWith("/pay ")) {
                iterator.remove();
            }
        }
    }

    private Method findMethod(Class<?> owner, String name) throws NoSuchMethodException {
        Method method = owner.getMethod(name);
        method.setAccessible(true);
        return method;
    }

    private boolean isGrieferUtilsBotshop(Object screen) {
        return screen != null && BOTSHOP_GUI_CLASS.equals(screen.getClass().getName());
    }
}
