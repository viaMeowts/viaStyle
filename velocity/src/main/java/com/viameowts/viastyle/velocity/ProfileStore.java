package com.viameowts.viastyle.velocity;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player viaStyle settings shared by all backends: {@code plugins/viastyle/profiles/<uuid>.json}.
 * The keys are owned by the backends (ignores, spy, pmSoundOff, channel, nickColor); the proxy
 * only merges and hands them out.
 */
final class ProfileStore {
    private final Path dir;
    private final Logger log;
    private final Map<UUID, JsonObject> cache = new ConcurrentHashMap<>();

    ProfileStore(Path dir, Logger log) {
        this.dir = dir;
        this.log = log;
    }

    boolean exists(UUID uuid) {
        return cache.containsKey(uuid) || Files.exists(file(uuid));
    }

    /** A copy of the profile ({} when the player has none yet). */
    synchronized JsonObject get(UUID uuid) {
        return load(uuid).deepCopy();
    }

    /** Creates an empty profile so the next join is no longer a first join. */
    synchronized void touch(UUID uuid) {
        if (!Files.exists(file(uuid))) save(uuid, load(uuid));
    }

    synchronized void set(UUID uuid, String key, JsonElement value) {
        JsonObject profile = load(uuid);
        if (value == null || value.isJsonNull()) profile.remove(key);
        else profile.add(key, value);
        save(uuid, profile);
    }

    void flush() {
        cache.clear();
    }

    private JsonObject load(UUID uuid) {
        return cache.computeIfAbsent(uuid, u -> {
            Path file = file(u);
            if (!Files.exists(file)) return new JsonObject();
            try {
                return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (IOException | RuntimeException e) {
                log.warn("Unreadable viaStyle profile {}: {}", file, e.getMessage());
                return new JsonObject();
            }
        });
    }

    private void save(UUID uuid, JsonObject profile) {
        try {
            Files.createDirectories(dir);
            Path tmp = dir.resolve(uuid + ".json.tmp");
            Files.writeString(tmp, profile.toString(), StandardCharsets.UTF_8);
            Files.move(tmp, file(uuid), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.warn("Could not save viaStyle profile {}: {}", uuid, e.getMessage());
        }
    }

    private Path file(UUID uuid) {
        return dir.resolve(uuid + ".json");
    }
}
