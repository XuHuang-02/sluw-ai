package com.sinosig.sluw.application.service.routing;

import java.util.*;

/** Immutable contracts for the isolated dealIssue experiment. */
public final class RoutingTypes {
    private RoutingTypes() {}
    public enum Truth { TRUE, FALSE, UNKNOWN }
    public enum Status { SELECTED, INSUFFICIENT }
    public enum Route { EXIT_AUTO, RECOMMEND_PASS, INTERNAL, EXTERNAL1, NOTE_EXAM, COMBINED, NO_ACTION }
    public enum Kind { INTERNAL, EXTERNAL1, EXAM, INVESTIGATION, EXTERNAL2 }
    private static void require(boolean valid,String message) { if(!valid)throw new IllegalArgumentException(message); }
    private static <T> List<T> copy(List<T> source) {
        require(source!=null,"list required");require(source.stream().noneMatch(Objects::isNull),"null list element");return List.copyOf(source);
    }
    private static <T> Map<String,T> copyMap(Map<String,T> source) {
        require(source!=null,"map required");var result=new LinkedHashMap<String,T>();
        source.forEach((key,value)->{require(key!=null&&!key.isBlank()&&value!=null,"invalid map entry");result.put(key,value);});
        return Collections.unmodifiableMap(result);
    }
    private static void pathContract(Status status,Route route,String branchId,List<String> missing) {
        require(status!=null&&branchId!=null&&!branchId.isBlank(),"status and branch required");
        if(status==Status.SELECTED)require(route!=null&&missing.isEmpty(),"selected path requires route and no missing fields");
        else require(route==null&&!missing.isEmpty(),"insufficient path requires missing fields and no route");
    }
    public record Fact(Truth value,List<String> refs,List<String> missing) {
        public Fact { require(value!=null,"truth required");refs=copy(refs);missing=copy(missing); }
    }
    public record Item(Kind type,String subject,List<String> refs) {
        public Item { require(type!=null&&subject!=null&&!subject.isBlank(),"item type and subject required");refs=copy(refs); }
    }
    public record Facts(String version,Map<String,Fact> conditions,Map<String,List<Item>> items) {
        public Facts {
            require(version!=null&&!version.isBlank(),"version required");conditions=copyMap(conditions);
            var copied=new LinkedHashMap<String,List<Item>>();copyMap(items).forEach((key,list)->copied.put(key,copy(list)));
            items=Collections.unmodifiableMap(copied);
        }
    }
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    public record Selection(Status status,String branchId,String blockedAt) {}
    /** Pure path result; service items are deliberately absent. */
    public record ExpectedPath(Status status,Route route,String branchId,List<String> conditionIds,List<String> evidenceRefs,List<String> missingFields) {
        public ExpectedPath {
            conditionIds=copy(conditionIds);evidenceRefs=copy(evidenceRefs);missingFields=copy(missingFields);
            pathContract(status,route,branchId,missingFields);
        }
    }
    // Keep the full report schema stable, including explicit route:null for INSUFFICIENT.
    public record Decision(Status status,Route route,String branchId,List<String> conditionIds,List<String> evidenceRefs,List<String> missingFields,List<Item> items) {
        public Decision {
            conditionIds=copy(conditionIds);evidenceRefs=copy(evidenceRefs);missingFields=copy(missingFields);items=copy(items);
            pathContract(status,route,branchId,missingFields);
            require(status!=Status.INSUFFICIENT||items.isEmpty(),"insufficient decision cannot contain service items");
        }
    }
    public record Table(String entry,List<Node> nodes) {
        public Table { require(entry!=null&&!entry.isBlank(),"table entry required");nodes=copy(nodes); }
    }
    public record Node(String id,String condition,String yes,String no,Route route,String itemKey,String description) {}
}
