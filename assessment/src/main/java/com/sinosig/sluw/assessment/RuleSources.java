package com.sinosig.sluw.assessment;

import static com.sinosig.sluw.assessment.Model.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;

/** Exact chunk lookup; similarity results cannot establish checklist completeness. */
public interface RuleSources {
    record Ref(String datasetId, String documentId, String chunkId) {}
    Source fetch(Ref ref);
    static String hash(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    static boolean valid(Source s) {
        return s != null && !RuleEngine.blank(s.datasetId()) && !RuleEngine.blank(s.documentId())
                && !RuleEngine.blank(s.chunkId()) && !RuleEngine.blank(s.text()) && hash(s.text()).equals(s.sha256());
    }
    enum Failure { NOT_FOUND, UNAVAILABLE, INVALID_RESPONSE }
    final class SourceException extends RuntimeException {
        private final Failure failure;
        public SourceException(Failure failure) { super("规则来源获取失败：" + failure); this.failure = failure; }
        public Failure failure() { return failure; }
    }
    final class RagFlow implements RuleSources {
        private final String baseUrl, key;
        private final ObjectMapper mapper;
        private final java.util.Set<String> datasets;
        private HttpClient client;
        private synchronized HttpClient client() {
            if (client == null) client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                    .followRedirects(HttpClient.Redirect.NEVER).build();
            return client;
        }
        public RagFlow(String baseUrl, String key, ObjectMapper mapper, java.util.Set<String> datasets) {
            this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", ""); this.key = key; this.mapper = mapper;
            this.datasets = java.util.Set.copyOf(datasets);
        }
        public Source fetch(Ref ref) {
            if (ref == null) throw new IllegalArgumentException("来源标识不能为空");
            for (String id : new String[]{ref.datasetId(), ref.documentId(), ref.chunkId()})
                if (id == null || !id.matches("[A-Za-z0-9_-]{1,200}")) throw new IllegalArgumentException("来源标识格式无效");
            if (!datasets.contains(ref.datasetId())) throw new SecurityException("数据集不在当前机构授权范围内");
            if (baseUrl.isBlank() || RuleEngine.blank(key)) throw new SourceException(Failure.UNAVAILABLE);
            try {
                URI uri = URI.create(baseUrl + "/api/v1/datasets/" + ref.datasetId() + "/documents/" + ref.documentId()
                        + "/chunks?id=" + ref.chunkId() + "&page=1&page_size=2");
                HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15))
                        .header("Authorization", "Bearer " + key).GET().build();
                HttpResponse<String> response = client().send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() == 404) throw new SourceException(Failure.NOT_FOUND);
                if (response.statusCode() != 200) throw new SourceException(Failure.UNAVAILABLE);
                JsonNode body;
                try { body = mapper.readTree(response.body()); }
                catch (Exception e) { throw new SourceException(Failure.INVALID_RESPONSE); }
                if (body == null || !body.path("code").isIntegralNumber() || body.path("code").asInt() != 0)
                    throw new SourceException(Failure.INVALID_RESPONSE);
                JsonNode chunks = body.path("data").path("chunks");
                if (!chunks.isArray()) throw new SourceException(Failure.INVALID_RESPONSE);
                if (chunks.isEmpty()) throw new SourceException(Failure.NOT_FOUND);
                if (chunks.size() != 1 || !ref.chunkId().equals(chunks.get(0).path("id").asText())
                        || !chunks.get(0).path("content").isTextual() || chunks.get(0).path("content").asText().isBlank())
                    throw new SourceException(Failure.INVALID_RESPONSE);
                String text = chunks.get(0).path("content").asText();
                return new Source(ref.datasetId(), ref.documentId(), ref.chunkId(), text, hash(text));
            } catch (SourceException e) { throw e;
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new SourceException(Failure.UNAVAILABLE);
            } catch (Exception e) { throw new SourceException(Failure.UNAVAILABLE); }
        }
    }
}
