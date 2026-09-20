package com.sinosig.sluw.application.edd.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;

/** Deterministic context packing. No model, attachment reader or customer lookup. */
public final class EddDraftContext {
    private static final ObjectMapper JSON = new ObjectMapper();
    public record Packed(ObjectNode data, Map<String,List<String>> citations, Set<String> ruleRefs,
                         ObjectNode fixed, String inputHash) {
        public Packed {
            data=data.deepCopy(); fixed=fixed.deepCopy();
            Map<String,List<String>> copy=new LinkedHashMap<>();
            citations.forEach((k,v)->copy.put(k,List.copyOf(v)));
            citations=Collections.unmodifiableMap(copy); ruleRefs=Set.copyOf(ruleRefs);
        }
        @Override public ObjectNode data(){return data.deepCopy();}
        @Override public ObjectNode fixed(){return fixed.deepCopy();}
    }
    public Packed pack(EddRuleService.Evaluation evaluation,EddRuleRetrieval.Result retrieval) {
        Objects.requireNonNull(evaluation); Objects.requireNonNull(retrieval);
        var prepared=evaluation.history().prepared();
        ObjectNode request=prepared.input().request(), history=evaluation.history().summary(), rules=evaluation.result();
        if(!request.path("synthetic").asBoolean() && (retrieval.testOnly() || rules.path("test_only").asBoolean()))
            throw new IllegalArgumentException("Synthetic rule evidence cannot support a real input");
        ObjectNode data=JSON.createObjectNode(),fixed=JSON.createObjectNode();
        for(String key:List.of("snapshot_id","analysis_as_of","synthetic","context","coverage","conflicts"))
            data.set(key,request.path(key).deepCopy());
        // Identity has already been matched upstream; omit the redundant subject display/identifier fields.
        data.put("subject","selected_natural_person");
        fixed.set("grade_recommendation",rules.path("grade_recommendation").deepCopy());
        fixed.set("report_recommendation",rules.path("report_recommendation").deepCopy());
        fixed.set("proposed_grade",rules.path("proposed_grade").deepCopy());
        fixed.set("current_risk_grade",rules.at("/current_grade/value").deepCopy());
        fixed.set("conflicts",request.path("conflicts").deepCopy());
        fixed.set("coverage",request.path("coverage").deepCopy());
        fixed.put("execution_permitted",false);
        ArrayNode missing=fixed.putArray("missing_items");
        for(var issue:prepared.issues()) issue(missing,issue.code(),issue.path(),issue.message());
        for(JsonNode gap:history.path("gaps")) issue(missing,gap.path("code").asText("HISTORY_GAP"),"/history",gap.toString());
        for(JsonNode gap:rules.path("missing_inputs")) issue(missing,gap.asText(),"/rule_context","规则或依据待补充");
        request.path("coverage").fields().forEachRemaining(e->{
            if(!e.getValue().path("query_status").asText().equals("succeeded")||!e.getValue().path("complete").asBoolean())
                issue(missing,"COVERAGE_INCOMPLETE","/coverage/"+e.getKey(),"来源覆盖未完整确认，不等于查无记录");
        });
        for(var choice:prepared.fieldChoices()) if(choice.blocked())
            issue(missing,"CONFLICT_REQUIRES_CONFIRMATION","/conflicts","冲突字段待机构确认："+choice.field());

        ObjectNode summary=history.deepCopy();
        // Retain full history outside the prompt; group ALL events below rather than taking a head slice.
        summary.remove(List.of("event_history","observed_event_policy_ids"));
        data.set("business_summary",summary);
        ArrayNode grouped=data.putArray("event_groups");
        Map<String,ObjectNode> groups=new LinkedHashMap<>();
        for(JsonNode event:request.path("events")) {
            var decision=prepared.decisions().get(event.path("fact_id").asText());
            ObjectNode dimensions=JSON.createObjectNode();
            for(String key:List.of("business_type","status","payment_method","amount_kind","currency","direction"))
                dimensions.set(key,event.path(key).deepCopy());
            dimensions.put("scope",decision.state().name());
            String time=event.path("event_time").asText("");
            dimensions.put("period",time.length()>=7?time.substring(0,7):"unknown");
            ObjectNode group=groups.computeIfAbsent(dimensions.toString(),k->{
                ObjectNode g=dimensions.deepCopy();g.put("count",0);g.putArray("fact_refs");return g;
            });
            group.put("count",group.path("count").asInt()+1);
            ((ArrayNode)group.path("fact_refs")).add(event.path("fact_id").asText());
        }
        groups.values().forEach(grouped::add);
        ArrayNode records=data.putArray("fact_records");
        Set<String> visible=new LinkedHashSet<>();
        for(String type:List.of("core_snapshots","risk_records","manual_excerpts")) for(JsonNode fact:request.path(type)) {
            String id=fact.path("fact_id").asText();var d=prepared.decisions().get(id);
            if(d.state()==EddFactService.State.EXCLUDED||d.state()==EddFactService.State.DUPLICATE)continue;
            ObjectNode entry=records.addObject();entry.put("kind",type).put("scope",d.state().name());
            entry.set("scope_reasons",JSON.valueToTree(d.reasons()));entry.set("fact",fact.deepCopy());visible.add(id);
            if(d.state()==EddFactService.State.PENDING)
                issue(missing,"FACT_PENDING","/facts/"+id,"仅供待核实提示，不作为确定事实");
        }
        ArrayNode attachments=data.putArray("attachments");
        for(JsonNode attachment:request.path("attachments")) {
            String id=attachment.path("attachment_id").asText();
            boolean excerpt=false;
            for(JsonNode fact:request.path("manual_excerpts"))if(id.equals(fact.path("attachment_id").asText())&&visible.contains(fact.path("fact_id").asText()))excerpt=true;
            attachments.addObject().put("attachment_id",id).put("content_read",false).put("has_manual_excerpt",excerpt);
            if(!excerpt)issue(missing,"ATTACHMENT_NOT_EXCERPTED","/attachments/"+id,"附件仅保存，未读取内容；需机构手工摘录");
        }
        ObjectNode ruleContext=rules.deepCopy();
        ruleContext.remove(List.of("grade_history","historical_reports"));
        data.set("rules",ruleContext);
        // Reject unrelated explanation evidence instead of pairing another package/version with this evaluation.
        Set<String> allowedRules=new LinkedHashSet<>();
        for(JsonNode rule:rules.path("rule_evaluations")) {
            if(rule.path("basis_kind").asText().equals("confirmed_requirement")||rules.at("/rule_package/status").asText().equals("usable"))
                allowedRules.add(rule.path("rule_ref").asText());
        }
        for(var evidence:retrieval.evidence()) if(!allowedRules.contains(evidence.ruleRef()))
            throw new IllegalArgumentException("Retrieval does not match evaluated rules");
        data.set("rule_explanations",JSON.valueToTree(retrieval));
        if(retrieval.status()!=EddRuleRetrieval.Status.FOUND)
            issue(missing,"RULE_EXPLANATION_"+retrieval.status().name(),"/rule_explanations","规则解释未齐，不改变已有确定性规则结果");
        data.set("missing_items",missing.deepCopy());
        data.set("field_choices",JSON.valueToTree(prepared.fieldChoices()));
        // Each compact handle expands to the COMPLETE original fact list; no tail citations are lost.
        Map<String,List<String>> citations=new LinkedHashMap<>();
        Set<String> originalIds=prepared.decisions().keySet();
        compactRefs(data,citations,originalIds);
        for(String id:visible)citations.putIfAbsent(id,List.of(id));
        fixed.set("business_summary",history);
        fixed.set("rule_evaluation",rules);
        return new Packed(data,citations,allowedRules,fixed,EddRuleRetrieval.hash(request.toString()));
    }
    private static void compactRefs(JsonNode node,Map<String,List<String>> index,Set<String> originalIds) {
        if(node.isObject()) {
            ObjectNode object=(ObjectNode)node;
            JsonNode refs=object.get("fact_refs");
            if(refs!=null&&refs.isArray()&&!refs.isEmpty()) {
                LinkedHashSet<String> ids=new LinkedHashSet<>();
                for(JsonNode ref:refs) {
                    if(!ref.isTextual()||!originalIds.contains(ref.asText()))throw new IllegalArgumentException("Unknown prepared fact reference");
                    ids.add(ref.asText());
                }
                String handle="EDD-GROUP-"+index.size();
                while(originalIds.contains(handle)||index.containsKey(handle))handle+="X";
                index.put(handle,List.copyOf(ids));object.set("fact_refs",JSON.createArrayNode().add(handle));
            }
            object.fields().forEachRemaining(e->{if(!e.getKey().equals("fact_refs"))compactRefs(e.getValue(),index,originalIds);});
        } else if(node.isArray())for(JsonNode child:node)compactRefs(child,index,originalIds);
    }
    private static void issue(ArrayNode out,String code,String path,String message) {
        out.addObject().put("code",code).put("path",path).put("message",message);
    }
}
