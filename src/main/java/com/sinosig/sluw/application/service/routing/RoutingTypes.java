package com.sinosig.sluw.application.service.routing;

import java.util.*;

/** Strict contracts for the isolated dealIssue experiment. */
public final class RoutingTypes {
    private RoutingTypes() {}
    public enum Truth { TRUE, FALSE, UNKNOWN }
    public enum Status { SELECTED, INSUFFICIENT }
    public enum Route { EXIT_AUTO, RECOMMEND_PASS, INTERNAL, EXTERNAL1, NOTE_EXAM, COMBINED, NO_ACTION }
    public enum Kind { INTERNAL, EXTERNAL1, EXAM, INVESTIGATION, EXTERNAL2 }
    public record Fact(Truth value, List<String> refs, List<String> missing) {}
    public record Item(Kind type, String subject, List<String> refs) {}
    public record Facts(String version, Map<String, Fact> conditions, Map<String, List<Item>> items) {}
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    public record Selection(@com.fasterxml.jackson.annotation.JsonProperty(required=true) Status status,
                            String branchId, String blockedAt) {}
    public record Decision(Status status, Route route, String branchId,
                           List<String> conditionIds, List<String> evidenceRefs,
                           List<String> missingFields, List<Item> items) {}
    public record Table(String entry, Node[] nodes) {}
    public record Node(String id, String condition, String yes, String no,
                       Route route, String itemKey, String description) {}
}
