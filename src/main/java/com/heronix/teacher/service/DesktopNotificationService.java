package com.heronix.teacher.service;

import javafx.application.Platform;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.awt.TrayIcon.MessageType;

/**
 * Service for showing desktop/system tray notifications.
 * Falls back to JavaFX alerts if system tray is not supported.
 */
@Slf4j
@Service
public class DesktopNotificationService {

    private TrayIcon trayIcon;
    private volatile boolean systemTraySupported = false;
    private volatile boolean enabled = true;
    private volatile boolean showMessageNotifications = true;
    private volatile boolean showAlertNotifications = true;
    private volatile boolean showNewsNotifications = true;

    public DesktopNotificationService() {
        initializeSystemTray();
    }

    private void initializeSystemTray() {
        if (!SystemTray.isSupported()) {
            log.info("System tray is not supported on this platform");
            return;
        }

        try {
            SystemTray tray = SystemTray.getSystemTray();

            // Create a simple icon (you can replace with actual icon)
            Image image = Toolkit.getDefaultToolkit().createImage(
                    getClass().getResource("/images/heronix-icon.png"));

            // Fallback to a generated icon if resource not found
            if (image == null) {
                image = createDefaultIcon();
            }

            trayIcon = new TrayIcon(image, "Heronix Teacher");
            trayIcon.setImageAutoSize(true);

            // Add popup menu
            PopupMenu popup = new PopupMenu();

            MenuItem openItem = new MenuItem("Open Heronix");
            openItem.addActionListener(e -> Platform.runLater(this::bringToFront));
            popup.add(openItem);

            popup.addSeparator();

            CheckboxMenuItem notifyItem = new CheckboxMenuItem("Enable Notifications", enabled);
            notifyItem.addItemListener(e -> enabled = notifyItem.getState());
            popup.add(notifyItem);

            popup.addSeparator();

            MenuItem exitItem = new MenuItem("Exit");
            exitItem.addActionListener(e -> System.exit(0));
            popup.add(exitItem);

            trayIcon.setPopupMenu(popup);

            // Double-click to open
            trayIcon.addActionListener(e -> Platform.runLater(this::bringToFront));

            tray.add(trayIcon);
            systemTraySupported = true;
            log.info("System tray initialized successfully");

        } catch (Exception e) {
            log.warn("Failed to initialize system tray: {}", e.getMessage());
        }
    }

    private Image createDefaultIcon() {
        // Create a simple 16x16 icon programmatically
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(
                16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(33, 150, 243)); // Material Blue
        g.fillOval(0, 0, 16, 16);
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 10));
        g.drawString("H", 4, 12);
        g.dispose();
        return image;
    }

    private void bringToFront() {
        // This would need access to the main stage
        // For now, just log
        log.debug("Bring to front requested");
    }

    /**
     * Show a notification for a new message
     */
    public void showMessageNotification(String senderName, String messagePreview, String channelName) {
        if (!enabled || !showMessageNotifications) return;

        String title = "New message from " + senderName;
        String message = channelName != null ? "[" + channelName + "] " + messagePreview : messagePreview;

        showNotification(title, truncate(message, 100), MessageType.INFO);
    }

    /**
     * Show a notification for a mention
     */
    public void showMentionNotification(String senderName, String messagePreview, String channelName) {
        if (!enabled || !showMessageNotifications) return;

        String title = senderName + " mentioned you";
        String message = channelName != null ? "[" + channelName + "] " + messagePreview : messagePreview;

        showNotification(title, truncate(message, 100), MessageType.INFO);
    }

    /**
     * Show a notification for an emergency alert
     */
    public void showAlertNotification(String alertLevel, String title, String message) {
        if (!enabled || !showAlertNotifications) return;

        MessageType type = switch (alertLevel.toUpperCase()) {
            case "EMERGENCY", "URGENT" -> MessageType.ERROR;
            case "HIGH" -> MessageType.WARNING;
            default -> MessageType.INFO;
        };

        showNotification("[" + alertLevel + "] " + title, truncate(message, 150), type);
    }

    /**
     * Show a notification for news
     */
    public void showNewsNotification(String headline, boolean isUrgent) {
        if (!enabled || !showNewsNotifications) return;

        String title = isUrgent ? "Urgent News" : "News Update";
        MessageType type = isUrgent ? MessageType.WARNING : MessageType.INFO;

        showNotification(title, truncate(headline, 100), type);
    }

    /**
     * Show a notification for channel invitation
     */
    public void showInvitationNotification(String inviterName, String channelName) {
        if (!enabled) return;

        String title = "Channel Invitation";
        String message = inviterName + " invited you to join " + channelName;

        showNotification(title, message, MessageType.INFO);
    }

    /**
     * Show a generic notification
     */
    public void showNotification(String title, String message, MessageType type) {
        if (!enabled) return;

        if (systemTraySupported && trayIcon != null) {
            try {
                trayIcon.displayMessage(title, message, type);
            } catch (Exception e) {
                log.debug("Failed to show system notification: {}", e.getMessage());
            }
        } else {
            // Fallback: log the notification
            log.info("Notification: {} - {}", title, message);
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }

    // Settings
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setShowMessageNotifications(boolean show) {
        this.showMessageNotifications = show;
    }

    public void setShowAlertNotifications(boolean show) {
        this.showAlertNotifications = show;
    }

    public void setShowNewsNotifications(boolean show) {
        this.showNewsNotifications = show;
    }

    public boolean isSystemTraySupported() {
        return systemTraySupported;
    }

    /**
     * Update the tray icon tooltip
     */
    public void updateTooltip(String text) {
        if (trayIcon != null) {
            trayIcon.setToolTip(text);
        }
    }

    /**
     * Set unread count badge (visual indicator)
     */
    public void setUnreadCount(int count) {
        if (trayIcon != null) {
            String tooltip = count > 0
                    ? "Heronix Teacher (" + count + " unread)"
                    : "Heronix Teacher";
            trayIcon.setToolTip(tooltip);
        }
    }

    /**
     * Clean up system tray on shutdown
     */
    public void shutdown() {
        if (systemTraySupported && trayIcon != null) {
            try {
                SystemTray.getSystemTray().remove(trayIcon);
            } catch (Exception e) {
                log.debug("Error removing tray icon: {}", e.getMessage());
            }
        }
    }
}
