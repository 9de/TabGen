package com.turki.tabgen;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

import javax.imageio.ImageIO;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Mod(modid = TabGen.MODID, version = TabGen.VERSION, clientSideOnly = true)
@SideOnly(Side.CLIENT)
public class TabGen {
    public static final String MODID = "tabgen";
    public static final String VERSION = "1.1";
    private static final int BLOCK_WIDTH = 278;
    private static final int BLOCK_HEIGHT = 20;
    private static final int SPACING = 22;
    private static final int HEAD_SIZE = 16;
    private static final Color BLOCK_COLOR = new Color(211, 211, 211);
    
    // Optimized thread pool and cache configuration
    private static final ExecutorService executor = 
        new ThreadPoolExecutor(4, 8, 60L, TimeUnit.SECONDS, 
            new LinkedBlockingQueue<>(100),
            new ThreadPoolExecutor.CallerRunsPolicy());
    
    private static final Cache<String, BufferedImage> headCache = CacheBuilder.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(30, TimeUnit.MINUTES)
        .build();
        
    private static final Cache<String, Font> fontCache = CacheBuilder.newBuilder()
        .maximumSize(5)
        .expireAfterWrite(1, TimeUnit.HOURS)
        .build();
        
    private static Font loadMinecraftFont() {
        try {
            return fontCache.get("minecraft", () -> {
                InputStream is = TabGen.class.getResourceAsStream("/assets/tabgen/MinecraftFont.otf");
                return Font.createFont(Font.TRUETYPE_FONT, is).deriveFont(16f);
            });
        } catch (Exception e) {
            e.printStackTrace();
            return new Font("Arial", Font.PLAIN, 16);
        }
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        ClientCommandHandler.instance.registerCommand(new CommandTabGen());
    }

    @SideOnly(Side.CLIENT)
    public static class CommandTabGen extends CommandBase {
        private static final Font MINECRAFT_FONT = new Font("Minecraft", Font.PLAIN, 16);
        private static final ColorMapper COLOR_MAPPER = new ColorMapper();

        @Override
        public String getCommandName() {
            return "tabgen";
        }

        @Override
        public String getCommandUsage(ICommandSender sender) {
            return "/tabgen";
        }

        @Override
        public void processCommand(ICommandSender sender, String[] args) throws CommandException {
            sender.addChatMessage(new ChatComponentText("\u00A7aGenerating tab list image..."));
            CompletableFuture.runAsync(() -> {
                try {
                    generateTabList(sender);
                } catch (Exception e) {
                    sender.addChatMessage(new ChatComponentText("\u00A7cError: " + e.getMessage()));
                }
            }, executor);
        }

        private void generateTabList(ICommandSender sender) throws Exception {
            NetHandlerPlayClient netHandler = Minecraft.getMinecraft().getNetHandler();
            if (netHandler == null) {
                sender.addChatMessage(new ChatComponentText("\u00A7cError: Not connected to a server"));
                return;
            }

            List<NetworkPlayerInfo> players = netHandler.getPlayerInfoMap().stream()
                .sorted((p1, p2) -> {
                    String team1 = p1.getPlayerTeam() != null ? p1.getPlayerTeam().getRegisteredName() : "";
                    String team2 = p2.getPlayerTeam() != null ? p2.getPlayerTeam().getRegisteredName() : "";
                    int teamCompare = team1.compareTo(team2);
                    return teamCompare != 0 ? teamCompare : 
                           p1.getGameProfile().getName().compareTo(p2.getGameProfile().getName());
                })
                .collect(Collectors.toList());
            if (players.isEmpty()) {
                sender.addChatMessage(new ChatComponentText("\u00A7cNo players in tab list"));
                return;
            }

            generateAndSaveImage(players, sender);
        }

        private void generateAndSaveImage(List<NetworkPlayerInfo> players, ICommandSender sender) {
            int columns = (players.size() + 15) / 16;
            int width = columns * BLOCK_WIDTH + 2;
            
            BufferedImage image = new BufferedImage(width, 350, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = setupGraphics(image);

            List<CompletableFuture<Void>> futures = new ArrayList<>();
            int currentX = 0;
            int currentY = 0;

            for (NetworkPlayerInfo player : players) {
                if (currentY > 330) {
                    currentX += BLOCK_WIDTH;
                    currentY = 0;
                }

                final int x = currentX;
                final int y = currentY;
                
                futures.add(CompletableFuture.runAsync(() -> 
                    drawPlayerBlock(g2d, x, y, player), executor));

                currentY += SPACING;
            }

            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(15, TimeUnit.SECONDS);
            } catch (Exception e) {
                sender.addChatMessage(new ChatComponentText("\u00A7eWarning: Some elements may be incomplete"));
            }

            // Add watermark
            drawWatermark(g2d, image.getWidth());
            
            g2d.dispose();
            saveImage(image, sender);
        }

        private Graphics2D setupGraphics(BufferedImage image) {
            Graphics2D g2d = image.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, 
                               RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2d.setBackground(Color.BLACK);
            g2d.clearRect(0, 0, image.getWidth(), image.getHeight());
            g2d.setFont(MINECRAFT_FONT);
            return g2d;
        }

        private void drawPlayerBlock(Graphics2D g2d, int x, int y, NetworkPlayerInfo player) {
            synchronized(g2d) {
                g2d.setColor(BLOCK_COLOR);
                g2d.fillRect(x + 2, y, BLOCK_WIDTH - 2, BLOCK_HEIGHT);
            }

            String name = player.getGameProfile().getName();
            drawPlayerHead(g2d, x + 5, y + 2, name);
            
            String fullName = formatPlayerName(player);
            drawColoredString(g2d, fullName, x + 23, y + 16);
            drawPingIndicator(g2d, x + 259, y + 2, player.getResponseTime());
        }

        private void drawPlayerHead(Graphics2D g2d, int x, int y, String username) {
            try {
                BufferedImage head = headCache.get(username, () -> 
                    loadHeadImage("https://minotar.net/helm/" + username + "/" + HEAD_SIZE));
                
                synchronized(g2d) {
                    g2d.drawImage(head, x, y, HEAD_SIZE, HEAD_SIZE, null);
                }
            } catch (Exception e) {
                // Silently fail for head loading
            }
        }

        private BufferedImage loadHeadImage(String urlString) throws Exception {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            return ImageIO.read(conn.getInputStream());
        }

        private String formatPlayerName(NetworkPlayerInfo player) {
            String name = player.getGameProfile().getName();
            if (player.getPlayerTeam() != null) {
                return player.getPlayerTeam().getColorPrefix() + 
                       name + 
                       player.getPlayerTeam().getColorSuffix();
            }
            return name;
        }

        private void drawColoredString(Graphics2D g2d, String text, int x, int y) {
            int currentX = x;
            Color currentColor = Color.BLACK;

            for (int i = 0; i < text.length(); i++) {
                if (text.charAt(i) == '\u00A7' && i + 1 < text.length()) {
                    currentColor = COLOR_MAPPER.getColor(text.charAt(i + 1));
                    i++;
                    continue;
                }
                
                String c = String.valueOf(text.charAt(i));
                synchronized(g2d) {
                    g2d.setColor(currentColor);
                    g2d.drawString(c, currentX, y);
                }
                currentX += g2d.getFontMetrics().stringWidth(c);
            }
        }

        private static final Map<String, BufferedImage> pingImages = new ConcurrentHashMap<>();
        private static final String[] SIGNAL_IMAGES = {
            "signal_0.png", "signal_2.png", "signal_3.png", "signal_4.png", "signal_5.png"
        };

        private void loadPingImages() {
            if (pingImages.isEmpty()) {
                try {
                    for (String imageName : SIGNAL_IMAGES) {
                        BufferedImage img = ImageIO.read(TabGen.class.getResourceAsStream("/assets/" + TabGen.MODID + "/" + imageName));
                        pingImages.put(imageName, img);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        private void drawPingIndicator(Graphics2D g2d, int x, int y, int ping) {
            loadPingImages();
            String imageName = getPingImageName(ping);
            BufferedImage signalImage = pingImages.get(imageName);
            
            if (signalImage != null) {
                synchronized(g2d) {
                    g2d.drawImage(signalImage, x, y, HEAD_SIZE, HEAD_SIZE, null);
                }
            }
        }

        private String getPingImageName(int ping) {
            if (ping < 0) return "signal_0.png";
            if (ping <= 150) return "signal_5.png";
            if (ping <= 300) return "signal_4.png";
            if (ping <= 600) return "signal_3.png";
            if (ping <= 1000) return "signal_2.png";
            return "signal_1.png";
        }

        private void saveImage(BufferedImage image, ICommandSender sender) {
            try {
                String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss")
                    .format(new Date());
                    
                // Get clean path to screenshots directory
                File mcDir = Minecraft.getMinecraft().mcDataDir.getCanonicalFile();
                File screenshotsDir = new File(mcDir, "screenshots");
                if (!screenshotsDir.exists()) {
                    screenshotsDir.mkdirs();
                }
                
                // Create file with clean path
                File outputFile = new File(screenshotsDir, "tablist_" + timestamp + ".png").getCanonicalFile();
                ImageIO.write(image, "PNG", outputFile);
                
                // Create clickable messages
                ChatComponentText message = new ChatComponentText("\u00A7aTab list saved! ");
                
                // File open button with clean path
                ChatComponentText fileComponent = new ChatComponentText("\u00A7e[Open Image]");
                fileComponent.setChatStyle(
                    new ChatStyle()
                        .setChatClickEvent(new ClickEvent(
                            ClickEvent.Action.OPEN_FILE, 
                            outputFile.getAbsolutePath()))
                        .setChatHoverEvent(new HoverEvent(
                            HoverEvent.Action.SHOW_TEXT,
                            new ChatComponentText("\u00A7eClick to view the image")))
                );
                
                // Folder open button with clean path
                ChatComponentText folderComponent = new ChatComponentText(" \u00A7b[Open Folder]");
                folderComponent.setChatStyle(
                    new ChatStyle()
                        .setChatClickEvent(new ClickEvent(
                            ClickEvent.Action.OPEN_FILE, 
                            screenshotsDir.getAbsolutePath()))
                        .setChatHoverEvent(new HoverEvent(
                            HoverEvent.Action.SHOW_TEXT,
                            new ChatComponentText("\u00A7eClick to open screenshots folder")))
                );
                
                // Combine all components
                message.appendSibling(fileComponent);
                message.appendSibling(folderComponent);
                message.appendSibling(new ChatComponentText(" \u00A77(" + outputFile.getName() + ")"));
                
                // Send message
                sender.addChatMessage(message);
                
                // Play screenshot sound
                Minecraft.getMinecraft().thePlayer.playSound("random.screenshot", 1.0F, 1.0F);
                    
            } catch (Exception e) {
                sender.addChatMessage(new ChatComponentText(
                    "\u00A7cError saving image: " + e.getMessage()));
                e.printStackTrace();
            }
        }
 
        @Override
        public int getRequiredPermissionLevel() {
            return 0;
        }
    }

    private static void drawWatermark(Graphics2D g2d, int imageWidth) {
        String watermark = "TabGen v" + VERSION;  // More professional versioned watermark
        
        FontMetrics metrics = g2d.getFontMetrics();
        int watermarkWidth = metrics.stringWidth(watermark);
        
        // Position at bottom right corner with padding
        int padding = 10;
        int x = imageWidth - watermarkWidth - padding;
        int y = 345;  // Moved slightly lower

        // Draw semi-transparent background for better readability
        int bgPadding = 4;
        g2d.setColor(new Color(0, 0, 0, 100));
        g2d.fillRect(x - bgPadding, 
                    y - metrics.getAscent() - bgPadding/2, 
                    watermarkWidth + bgPadding * 2, 
                    metrics.getHeight() + bgPadding);
        
        // Draw text with shadow effect
        g2d.setColor(new Color(64, 64, 64, 180));
        g2d.drawString(watermark, x + 1, y + 1);  // Shadow
        
        // Main text in a subtle gold color
        g2d.setColor(new Color(255, 215, 0, 220));
        g2d.drawString(watermark, x, y);
    }

    private static class ColorMapper {
        private final Map<Character, Color> colorMap;

        ColorMapper() {
            colorMap = new HashMap<>();
            colorMap.put('0', new Color(0, 0, 0));
            colorMap.put('1', new Color(0, 0, 170));
            colorMap.put('2', new Color(0, 170, 0));
            colorMap.put('3', new Color(0, 170, 170));
            colorMap.put('4', new Color(170, 0, 0));
            colorMap.put('5', new Color(170, 0, 170));
            colorMap.put('6', new Color(255, 170, 0));
            colorMap.put('7', new Color(170, 170, 170));
            colorMap.put('8', new Color(85, 85, 85));
            colorMap.put('9', new Color(85, 85, 255));
            colorMap.put('a', new Color(85, 255, 85));
            colorMap.put('b', new Color(85, 255, 255));
            colorMap.put('c', new Color(255, 85, 85));
            colorMap.put('d', new Color(255, 85, 255));
            colorMap.put('e', new Color(255, 255, 85));
            colorMap.put('f', new Color(255, 255, 255));
        }

        Color getColor(char code) {
            return colorMap.getOrDefault(code, new Color(255, 255, 255));
        }
    }
}