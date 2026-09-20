package com.sinosig.sluw.application.edd.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import java.time.OffsetDateTime;
import java.util.*;

/** Immutable server-side rule registry and three-valued evaluation. No side effects. */
public final class EddRuleService {
    public enum Mode { PRODUCTION, TEST }
    private static final JsonNodeFactory JSON=JsonNodeFactory.instance;
    private static final Set<String> GRADES=Set.of("low","medium","high","highest");
    public record Evaluation(EddHistoryService.History history,ObjectNode result) {
        public Evaluation { result=result.deepCopy(); }
        @Override public ObjectNode result(){return result.deepCopy();}
    }
    private record Fact(String value,List<String> refs,List<String> missing) {}
    private final Map<String,EddRulePackage> registry;
    private final Mode mode;
    public EddRuleService(){this(List.of(),Mode.PRODUCTION);}
    public EddRuleService(Collection<EddRulePackage> packages,Mode mode) {
        this.mode=Objects.requireNonNull(mode);
        Map<String,EddRulePackage> loaded=new HashMap<>();
        for(var p:packages)if(loaded.putIfAbsent(p.id()+"@"+p.version(),p)!=null)throw new IllegalArgumentException("Duplicate package version");
        registry=Map.copyOf(loaded);
    }
    public Evaluation evaluate(EddHistoryService.History history) {
        var prepared=history.prepared();ObjectNode request=prepared.input().request(),summary=history.summary();
        ObjectNode out=JSON.objectNode().put("test_only",mode==Mode.TEST).put("execution_permitted",false);
        Map<String,Fact> facts=new HashMap<>();
        String cashStatus=summary.at("/cash/status").asText("unknown");
        List<String> cashRefs=refs(summary.at("/cash/fact_refs"));
        if(prepared.fieldBlocked("cash_status"))cashStatus="unknown";
        facts.put("cash_status",new Fact(cashStatus.equals("unknown")?null:cashStatus,cashRefs,
                cashStatus.equals("unknown")?List.of("cash_evidence_or_coverage"):List.of()));
        extractRiskFacts(prepared,request,out,facts);
        ArrayNode evaluated=out.putArray("rule_evaluations"),missing=out.putArray("missing_inputs");
        String cashState=cashStatus.equals("found")?"hit":cashStatus.equals("not_observed_in_available_scope")?"not_hit":"unknown";
        ObjectNode baseline=evaluated.addObject().put("rule_ref","requirement.cash-risk-factor.v1")
                .put("basis_kind","confirmed_requirement").put("target","risk_factor").put("status",cashState)
                .put("value","risk_increasing").put("reason","Historical actual cash is a factor, never an automatic grade mapping");
        baseline.set("fact_refs",strings(cashRefs));baseline.set("missing_inputs",strings(facts.get("cash_status").missing()));
        String id=request.at("/rule_context/rule_package_id").asText(""),version=request.at("/rule_context/expected_version").asText("");
        EddRulePackage pack=registry.get(id+"@"+version);
        String unavailable=packageIssue(pack,id,version,request);
        ObjectNode packageInfo=out.putObject("rule_package").put("id",id.isEmpty()?null:id).put("version",version.isEmpty()?null:version);
        packageInfo.put("status",unavailable==null?"usable":unavailable);
        if(pack!=null) {
            packageInfo.put("approval",pack.approval()).put("approval_ref",pack.approvalRef()).put("approved_by",pack.approvedBy());
            packageInfo.put("approved_at",pack.approvedAt()==null?null:pack.approvedAt().toString());
            packageInfo.put("valid_from",pack.validFrom().toString()).put("valid_to",pack.validTo()==null?null:pack.validTo().toString());
            packageInfo.put("test_only",pack.testOnly());
        }
        if(unavailable!=null) {
            missing.add(unavailable);
            if(pack!=null)for(var rule:pack.rules()) {
                ObjectNode r=evaluated.addObject().put("rule_ref",pack.reference(rule)).put("target",rule.target())
                        .put("status","unknown").put("value",rule.value()).put("reason",unavailable).put("clause_ref",rule.clauseRef());
                r.putArray("fact_refs");r.putArray("missing_inputs").add(unavailable);
            }
            out.set("grade_recommendation",recommendation("insufficient_rules",null,unavailable,List.of(),List.of()));
            out.set("report_recommendation",recommendation("insufficient_rules",null,unavailable,List.of(),List.of()));
        } else {
            for(var rule:pack.rules()) {
                boolean failed=false,unknown=false;Set<String> evidence=new LinkedHashSet<>(),gaps=new LinkedHashSet<>();
                for(var condition:rule.conditions()) {
                    Fact fact=facts.get(condition.fact());evidence.addAll(fact.refs());
                    if(fact.value()==null){unknown=true;gaps.addAll(fact.missing());}
                    else if(!fact.value().equals(condition.expected()))failed=true;
                }
                String status=failed?"not_hit":unknown?"unknown":"hit";
                ObjectNode r=evaluated.addObject().put("rule_ref",pack.reference(rule)).put("target",rule.target())
                        .put("status",status).put("value",rule.value()).put("reason",status).put("clause_ref",rule.clauseRef());
                r.set("fact_refs",strings(evidence));r.set("missing_inputs",strings(gaps));gaps.forEach(missing::add);
            }
            out.set("grade_recommendation",resolve("grade",evaluated));
            out.set("report_recommendation",resolve("report",evaluated));
        }
        out.set("proposed_grade",out.at("/grade_recommendation/value").deepCopy());
        return new Evaluation(history,out);
    }

    private String packageIssue(EddRulePackage p,String id,String version,ObjectNode request) {
        if(id.isEmpty())return "RULE_PACKAGE_NOT_SELECTED";
        if(version.isEmpty())return "RULE_VERSION_REQUIRED";
        if(p==null)return "RULE_PACKAGE_VERSION_NOT_FOUND";
        if(p.testOnly()&&mode!=Mode.TEST)return "TEST_PACKAGE_FORBIDDEN";
        if(mode==Mode.TEST&&!request.path("synthetic").asBoolean())return "TEST_MODE_REQUIRES_SYNTHETIC_INPUT";
        if(!p.approval().equals("approved"))return "RULE_PACKAGE_NOT_APPROVED";
        OffsetDateTime cutoff=OffsetDateTime.parse(request.path("analysis_as_of").asText());
        if(p.approvedAt().isAfter(cutoff))return "RULE_APPROVED_AFTER_ANALYSIS_TIME";
        if(cutoff.isBefore(p.validFrom())||(p.validTo()!=null&&!cutoff.isBefore(p.validTo())))return "RULE_PACKAGE_NOT_EFFECTIVE";
        if(!p.scope().triggers().contains("*")&&!p.scope().triggers().contains(request.at("/context/trigger").asText()))return "RULE_SCOPE_MISMATCH";
        return null;
    }
    private static ObjectNode resolve(String target,ArrayNode evaluations) {
        Set<String> values=new HashSet<>(),evidence=new LinkedHashSet<>(),rules=new LinkedHashSet<>();
        boolean unknown=false,hasRule=false;
        for(JsonNode r:evaluations)if(r.path("target").asText().equals(target)) {
            hasRule=true;String state=r.path("status").asText();
            if(state.equals("hit")||state.equals("unknown")) {
                rules.add(r.path("rule_ref").asText());evidence.addAll(refs(r.path("fact_refs")));
            }
            if(state.equals("hit"))values.add(r.path("value").asText());
            if(state.equals("unknown"))unknown=true;
        }
        if(values.size()>1)return recommendation("requires_institution_review",null,"CONFLICTING_RULE_OUTCOMES",evidence,rules);
        if(unknown)return recommendation("insufficient_evidence",null,"RULE_DEPENDENCIES_UNRESOLVED",evidence,rules);
        if(values.size()==1)return recommendation("suggestion",values.iterator().next(),"Institution decides whether to adopt",evidence,rules);
        return recommendation("insufficient_rules",null,hasRule?"NO_APPLICABLE_MAPPING":"TARGET_MAPPING_MISSING",evidence,rules);
    }
    private static void extractRiskFacts(EddFactService.Prepared prepared,ObjectNode request,ObjectNode out,Map<String,Fact> facts) {
        ArrayNode grades=out.putArray("grade_history"),reports=out.putArray("historical_reports");
        List<JsonNode> current=new ArrayList<>();List<String> reportRefs=new ArrayList<>();
        boolean currentUncertain=false,reportUncertain=false;
        for(JsonNode record:request.path("risk_records")) {
            String id=record.path("fact_id").asText(),type=record.path("type").asText();
            var d=prepared.decisions().get(id);
            if(type.equals("risk_grade")||type.equals("risk_grade_change")) {
                grades.add(record.deepCopy());
                // No latest-record inference: current=true is an explicit upstream assertion.
                if(record.path("facts").path("current").asBoolean()) {
                    if(d.state()==EddFactService.State.EXCLUDED||discarded(prepared,id,Set.of("grade","current_grade","current")))continue;
                    if(d.state()!=EddFactService.State.INCLUDED||!usable(prepared,id,Set.of("grade","current_grade","current","event_time","subject_match")))
                        currentUncertain=true;
                    else current.add(record);
                }
            }
            if(type.equals("suspicious_report")) {
                reports.add(record.deepCopy());
                if(d.state()==EddFactService.State.EXCLUDED||discarded(prepared,id,Set.of("reported","historical_suspicious_report")))continue;
                if(d.state()==EddFactService.State.INCLUDED&&record.path("facts").path("reported").isBoolean()
                        &&record.path("facts").path("reported").asBoolean()&&usable(prepared,id,Set.of("reported","historical_suspicious_report","event_time","subject_match")))
                    reportRefs.add(id);
                else reportUncertain=true;
            }
        }
        Set<String> values=new HashSet<>();List<String> gradeRefs=new ArrayList<>();
        for(JsonNode r:current) {
            String grade=r.path("facts").path("grade").asText();gradeRefs.add(r.path("fact_id").asText());
            if(!GRADES.contains(grade))currentUncertain=true;else values.add(grade);
        }
        String grade=!currentUncertain&&values.size()==1?values.iterator().next():null;
        facts.put("current_grade",new Fact(grade,gradeRefs,grade==null?List.of("current_grade_missing_or_conflicting"):List.of()));
        ObjectNode currentOut=out.putObject("current_grade").put("value",grade).put("status",grade==null?"unknown":"confirmed");
        currentOut.set("fact_refs",strings(gradeRefs));
        JsonNode coverage=request.at("/coverage/historical_suspicious_reports");
        boolean complete=coverage.path("query_status").asText().equals("succeeded")&&coverage.path("complete").asBoolean()
                &&coverage.path("coverage_kind").asText().equals("risk_information")&&coverage.hasNonNull("from")&&coverage.hasNonNull("to");
        String report=!reportRefs.isEmpty()?"found":complete&&!reportUncertain?"not_observed_in_available_scope":null;
        facts.put("historical_suspicious_report",new Fact(report,reportRefs,report==null?List.of("historical_report_evidence_or_coverage"):List.of()));
        out.put("historical_report_status",report==null?"unknown":report);
    }
    private static boolean discarded(EddFactService.Prepared p,String id,Set<String> fields) {
        return p.fieldChoices().stream().anyMatch(c->c.candidates().contains(id)&&fields.contains(c.field())
                &&!c.blocked()&&!id.equals(c.adoptedFactId()));
    }
    private static boolean usable(EddFactService.Prepared p,String id,Set<String> fields) {
        for(var c:p.fieldChoices())if(c.candidates().contains(id)&&fields.contains(c.field()))
            if(c.blocked()||!id.equals(c.adoptedFactId()))return false;
        return true;
    }
    private static ObjectNode recommendation(String status,String value,String reason,Collection<String> facts,Collection<String> rules) {
        ObjectNode n=JSON.objectNode().put("status",status).put("value",value).put("reason",reason);
        n.set("fact_refs",strings(facts));n.set("rule_refs",strings(rules));return n;
    }
    private static List<String> refs(JsonNode n){List<String> r=new ArrayList<>();n.forEach(v->r.add(v.asText()));return r;}
    private static ArrayNode strings(Collection<String> v){ArrayNode n=JSON.arrayNode();v.forEach(n::add);return n;}
}
