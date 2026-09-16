package com.sinosig.sluw.application.dto.routing;
import com.sinosig.sluw.application.service.routing.RoutingTypes.*;
/** One isolated call, including failures. Never contains raw model text or input records. */
public record RoutingEvaluation(String requestId, Mode mode, String version, String promptHash,
    String model, boolean modelCallStarted, String outcome, String reason, String stage,
    Selection selection, Decision decision, long elapsedMs, Long callMs,
    Integer inputTokens, Integer outputTokens, Integer totalTokens, String text) {
    public enum Mode { CONDITIONS, RECORDS }
}
