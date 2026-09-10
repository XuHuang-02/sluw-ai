package com.sinosig.sluw.assessment;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.NoSuchElementException;

/** Single-instance pilot store. Immutable files, checked envelopes, no overwrite operation. */
public final class RuleStore {
    public record Envelope(String payload, String sha256) {}
    private final Path root;
    private final ObjectMapper mapper;
    public RuleStore(Path root, ObjectMapper mapper) { this.root = root.toAbsolutePath().normalize(); this.mapper = mapper; }
    private Path path(String area, String org, String id) {
        if (!java.util.Set.of("drafts", "confirmed", "versions").contains(area)) throw new IllegalArgumentException("未知存储区");
        return root.resolve(area).resolve(RuleSources.hash(org)).resolve(RuleSources.hash(id) + ".json");
    }
    public synchronized void put(String area, String org, String id, Object value) {
        Path target = path(area, org, id); Path temporary = null;
        try {
            Files.createDirectories(target.getParent());
            String payload = mapper.writeValueAsString(value);
            byte[] bytes = mapper.writeValueAsBytes(new Envelope(payload, RuleSources.hash(payload)));
            temporary = Files.createTempFile(target.getParent(), ".pending-", ".tmp");
            Files.write(temporary, bytes);
            Files.move(temporary, target); // No REPLACE_EXISTING: versions and confirmations are write-once.
        } catch (FileAlreadyExistsException e) { throw new IllegalStateException("记录已存在，不能覆盖，请使用新草稿或新版本");
        } catch (IOException e) { throw new IllegalStateException("规则记录保存失败");
        } finally { if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) { } }
    }
    public <T> T get(String area, String org, String id, Class<T> type) {
        try {
            Envelope e = mapper.readValue(Files.readString(path(area, org, id), StandardCharsets.UTF_8), Envelope.class);
            if (!RuleSources.hash(e.payload()).equals(e.sha256())) throw new IllegalStateException("规则记录完整性校验失败");
            return mapper.readValue(e.payload(), type);
        } catch (NoSuchFileException e) { throw new NoSuchElementException("记录不存在或不在授权范围内");
        } catch (IOException | NullPointerException e) { throw new IllegalStateException("规则记录无法读取或完整性校验失败"); }
    }
}
