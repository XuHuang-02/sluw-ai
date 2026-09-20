package com.sinosig.sluw.application.edd.service;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.*;

/** Server-owned declarative package. Approval metadata is supplied by a trusted deployment process. */
public final class EddRulePackage {
    public record Condition(String fact,String expected) {}
    public record Rule(String id,String target,String value,String clauseRef,List<Condition> conditions) {
        public Rule { conditions=List.copyOf(conditions); }
    }
    public record Scope(String subjectType,Set<String> triggers) {
        public Scope { triggers=Set.copyOf(triggers); }
    }
    private final String id,version,approval,approvalRef,approvedBy;
    private final OffsetDateTime approvedAt,validFrom,validTo;
    private final boolean testOnly;
    private final Scope scope;
    private final List<Rule> rules;
    private EddRulePackage(JsonNode n) {
        keys(n,Set.of("id","version","approval","approval_ref","approved_by","approved_at","valid_from","valid_to","test_only","scope","rules"));
        id=token(n,"id");version=token(n,"version");approval=choice(n,"approval",Set.of("approved","draft","rejected"));
        approvalRef=optional(n,"approval_ref");approvedBy=optional(n,"approved_by");
        approvedAt=time(n,"approved_at",true);validFrom=time(n,"valid_from",false);validTo=time(n,"valid_to",true);
        if(validTo!=null&&!validTo.isAfter(validFrom))throw invalid("Invalid effective interval");
        if(approval.equals("approved")&&(approvalRef==null||approvedBy==null||approvedAt==null))throw invalid("Approval evidence required");
        if(!n.path("test_only").isBoolean())throw invalid("test_only must be explicit");
        testOnly=n.path("test_only").asBoolean();
        JsonNode s=n.path("scope");keys(s,Set.of("subject_type","triggers"));
        String subject=choice(s,"subject_type",Set.of("natural_person"));
        Set<String> triggers=new LinkedHashSet<>();
        if(!s.path("triggers").isArray()||s.path("triggers").isEmpty())throw invalid("Trigger scope required");
        for(JsonNode t:s.path("triggers"))if(!t.isTextual()||t.asText().isBlank()||!triggers.add(t.asText()))throw invalid("Invalid triggers");
        scope=new Scope(subject,triggers);
        if(!n.path("rules").isArray()||n.path("rules").size()>1000)throw invalid("Invalid rule list");
        List<Rule> loaded=new ArrayList<>();Set<String> ids=new HashSet<>();
        for(JsonNode r:n.path("rules")) {
            keys(r,Set.of("id","target","value","clause_ref","conditions"));
            String rid=token(r,"id");if(!ids.add(rid))throw invalid("Duplicate rule ID");
            String target=choice(r,"target",Set.of("grade","report","risk_factor"));
            Set<String> values=switch(target) {
                case "grade" -> Set.of("low","medium","high","highest");
                case "report" -> Set.of("suggest_report","suggest_not_report");
                default -> Set.of("risk_increasing");
            };
            String value=choice(r,"value",values),clause=required(r,"clause_ref");
            if(!r.path("conditions").isArray()||r.path("conditions").isEmpty()||r.path("conditions").size()>32)throw invalid("Explicit conditions required");
            List<Condition> conditions=new ArrayList<>();
            for(JsonNode c:r.path("conditions")) {
                keys(c,Set.of("fact","operator","expected"));
                String fact=choice(c,"fact",Set.of("cash_status","current_grade","historical_suspicious_report"));
                choice(c,"operator",Set.of("eq"));
                Set<String> allowed=fact.equals("current_grade")?Set.of("low","medium","high","highest"):
                        Set.of("found","not_observed_in_available_scope");
                conditions.add(new Condition(fact,choice(c,"expected",allowed)));
            }
            if((id+"@"+version+":"+rid).length()>128)throw invalid("Rule reference too long");
            loaded.add(new Rule(rid,target,value,clause,conditions));
        }
        rules=List.copyOf(loaded);
    }
    public static EddRulePackage load(String json) {
        if(json==null||json.getBytes(StandardCharsets.UTF_8).length>1024*1024)throw invalid("Package exceeds limit or is missing");
        try {
            ObjectMapper mapper=new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
            return new EddRulePackage(mapper.readTree(json));
        } catch(java.io.IOException e){throw invalid("Invalid package JSON");}
    }
    public String id(){return id;} public String version(){return version;}
    public String approval(){return approval;} public String approvalRef(){return approvalRef;}
    public String approvedBy(){return approvedBy;} public OffsetDateTime approvedAt(){return approvedAt;}
    public OffsetDateTime validFrom(){return validFrom;} public OffsetDateTime validTo(){return validTo;}
    public boolean testOnly(){return testOnly;} public Scope scope(){return scope;}
    public List<Rule> rules(){return rules;}
    public String reference(Rule r){return id+"@"+version+":"+r.id();}
    private static void keys(JsonNode n,Set<String> allowed) {
        if(n==null||!n.isObject())throw invalid("Object required");
        n.fieldNames().forEachRemaining(k->{if(!allowed.contains(k))throw invalid("Unknown package field: "+k);});
        for(String k:allowed)if(!n.has(k))throw invalid("Missing package field: "+k);
    }
    private static String required(JsonNode n,String key){JsonNode v=n.get(key);if(v==null||!v.isTextual()||v.asText().isBlank())throw invalid("Text required: "+key);return v.asText();}
    private static String token(JsonNode n,String key){String v=required(n,key);if(!v.matches("[A-Za-z0-9_.-]{1,64}"))throw invalid("Invalid identifier");return v;}
    private static String optional(JsonNode n,String key){return n.path(key).isNull()?null:required(n,key);}
    private static String choice(JsonNode n,String key,Set<String> values){String v=required(n,key);if(!values.contains(v))throw invalid("Unsupported "+key);return v;}
    private static OffsetDateTime time(JsonNode n,String key,boolean optional){
        if(optional&&n.path(key).isNull())return null;
        try{return OffsetDateTime.parse(required(n,key));}catch(java.time.DateTimeException e){throw invalid("Invalid timestamp: "+key);}
    }
    private static IllegalArgumentException invalid(String message){return new IllegalArgumentException(message);}
}
