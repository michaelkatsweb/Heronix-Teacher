package com.heronix.teacher.service;

import javafx.scene.media.AudioClip;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.net.URL;
import java.util.EnumMap;
import java.util.Map;

/**
 * Service for playing notification sounds in the application.
 * Uses JavaFX AudioClip for low-latency playback.
 */
@Slf4j
@Service
public class NotificationSoundService {

    /**
     * Types of notification sounds
     */
    public enum SoundType {
        MESSAGE_RECEIVED("message_received.wav"),
        NOTIFICATION("notification.wav"),
        MENTION("mention.wav"),
        INVITE_RECEIVED("invite_received.wav"),
        EMERGENCY("emergency.wav"),
        ERROR("error.wav"),
        SUCCESS("success.wav");

        private final String filename;

        SoundType(String filename) {
            this.filename = filename;
        }

        public String getFilename() {
            return filename;
        }
    }

    private final Map<SoundType, AudioClip> audioClips = new EnumMap<>(SoundType.class);

    @Getter
    private boolean soundEnabled = true;

    @Getter
    private double volume = 0.7; // 0.0 to 1.0

    @PostConstruct
    public void init() {
        log.info("Initializing NotificationSoundService");
        loadSounds();
    }

    /**
     * Load all sound files
     */
    private void loadSounds() {
        for (SoundType type : SoundType.values()) {
            try {
                String resourcePath = "/sounds/" + type.getFilename();
                URL soundUrl = getClass().getResource(resourcePath);

                if (soundUrl != null) {
                    AudioClip clip = new AudioClip(soundUrl.toExternalForm());
                    clip.setVolume(volume);
                    audioClips.put(type, clip);
                    log.debug("Loaded sound: {}", type.getFilename());
                } else {
                    log.warn("Sound file not found: {}", resourcePath);
                }
            } catch (Exception e) {
                log.warn("Failed to load sound {}: {}", type.getFilename(), e.getMessage());
            }
        }
        log.info("Loaded {} sound files", audioClips.size());
    }

    /**
     * Play a notification sound
     */
    public void playSound(SoundType type) {
        if (!soundEnabled) {
            log.debug("Sound disabled, skipping: {}", type);
            return;
        }

        AudioClip clip = audioClips.get(type);
        if (clip != null) {
            try {
                clip.play(volume);
                log.debug("Playing sound: {}", type);
            } catch (Exception e) {
                log.warn("Failed to play sound {}: {}", type, e.getMessage());
            }
        } else {
            // Play a fallback beep if the sound file is not available
            playFallbackBeep();
        }
    }

    /**
     * Play message received sound
     */
    public void playMessageReceived() {
        playSound(SoundType.MESSAGE_RECEIVED);
    }

    /**
     * Play notification sound
     */
    public void playNotification() {
        playSound(SoundType.NOTIFICATION);
    }

    /**
     * Play mention sound
     */
    public void playMention() {
        playSound(SoundType.MENTION);
    }

    /**
     * Play invite received sound
     */
    public void playInviteReceived() {
        playSound(SoundType.INVITE_RECEIVED);
    }

    /**
     * Play emergency alert sound (for critical alerts)
     */
    public void playEmergency() {
        // Play multiple times for attention-grabbing
        playSound(SoundType.EMERGENCY);
    }

    /**
     * Play error sound
     */
    public void playError() {
        playSound(SoundType.ERROR);
    }

    /**
     * Play success sound
     */
    public void playSuccess() {
        playSound(SoundType.SUCCESS);
    }

    /**
     * Enable or disable sounds
     */
    public void setSoundEnabled(boolean enabled) {
        this.soundEnabled = enabled;
        log.info("Sound notifications {}", enabled ? "enabled" : "disabled");
    }

    /**
     * Toggle sound on/off
     */
    public boolean toggleSound() {
        soundEnabled = !soundEnabled;
        log.info("Sound notifications {}", soundEnabled ? "enabled" : "disabled");
        return soundEnabled;
    }

    /**
     * Set volume (0.0 to 1.0)
     */
    public void setVolume(double volume) {
        this.volume = Math.max(0.0, Math.min(1.0, volume));
        // Update all loaded clips
        for (AudioClip clip : audioClips.values()) {
            clip.setVolume(this.volume);
        }
        log.debug("Volume set to: {}", this.volume);
    }

    /**
     * Increase volume by 10%
     */
    public void increaseVolume() {
        setVolume(volume + 0.1);
    }

    /**
     * Decrease volume by 10%
     */
    public void decreaseVolume() {
        setVolume(volume - 0.1);
    }

    /**
     * Play a fallback system beep if audio files are not available
     */
    private void playFallbackBeep() {
        try {
            java.awt.Toolkit.getDefaultToolkit().beep();
        } catch (Exception e) {
            log.debug("Fallback beep not available");
        }
    }

    /**
     * Check if a specific sound type is loaded
     */
    public boolean isSoundLoaded(SoundType type) {
        return audioClips.containsKey(type);
    }

    /**
     * Get count of loaded sounds
     */
    public int getLoadedSoundCount() {
        return audioClips.size();
    }
}
