package com.sinosig.sluw.application.edd.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;
import java.util.regex.*;

/** Deterministic publication gate. No LLM judging and no claim of full textual entailment. */
public final class EddResultValidator {
    public static final String VERSION="edd-validation-v1";
    private static final JsonNodeFactory JSON=JsonNodeFactory.instance;
    private static final Pattern TOKEN=Pattern.compile("\\{\\{(metric|source_metric|event|count|risk|coverage):([^{}]+)}}");
    private static final Pattern NUMBER=Pattern.compile("[0-9０-９一二三四五六七八九十百千万亿两]+(?:[.,，．][0-9]+)*(?:\\s*(?:万元|亿元|元|笔|份|次|年|月|日|%|％))|[0-9]{4}[-/][0-9]{1,2}[-/][0-9]{1,2}|(?:CNY|USD|HKD|人民币|美元|港币)\\s*[0-9]+",Pattern.CASE_INSENSITIVE);
    public record Report(ObjectNode draft,ArrayNode errors,ArrayNode reviews) {
        public Report {draft=draft.deepCopy();errors=errors.deepCopy();reviews=reviews.deepCopy();}
        @Override public ObjectNode draft(){return draft.deepCopy();}
        @Override public ArrayNode errors(){return errors.deepCopy();}
        @Override public ArrayNode reviews(){return reviews.deepCopy();}
        public boolean valid(){return errors.isEmpty();}
        public boolean hasGaps(){return !reviews.isEmpty()||!draft.path("missing_items").isEmpty();}
    }
    public Report validate(ObjectNode candidate,EddRuleService.Evaluation evaluation) {
        ObjectNode draft=candidate.deepCopy();ArrayNode errors=JSON.arrayNode(),reviews=JSON.arrayNode();
        var prepared=evaluation.history().prepared();ObjectNode summary=evaluation.history().summary(),rules=evaluation.result();
        ObjectNode request=prepared.input().request();Map<String,JsonNode> events=new HashMap<>();
        for(JsonNode event:request.path("events"))events.put(event.path("fact_id").asText(),event);
        Set<String> allowedRules=new HashSet<>();
        for(JsonNode rule:rules.path("rule_evaluations"))if(rule.path("basis_kind").asText().equals("confirmed_requirement")||rules.at("/rule_package/status").asText().equals("usable"))allowedRules.add(rule.path("rule_ref").asText());
        StringBuilder recommendations=new StringBuilder();
        for(String collection:List.of("overview_sections","disposition_recommendations")) {
            int index=0;
            for(JsonNode item:draft.path(collection)) {
                String path="/"+collection+"/"+index++;Set<String> refs=new LinkedHashSet<>();
                for(JsonNode ref:item.path("fact_refs")) {
                    String id=ref.asText();refs.add(id);var decision=prepared.decisions().get(id);
                    if(decision==null)add(errors,"INVALID_FACT_REFERENCE",path+"/fact_refs","/facts");
                    else if(decision.state()!=EddFactService.State.INCLUDED) {
                        String text=item.path("text").asText()+item.path("reason").asText();
                        if(!uncertain(text))add(errors,"UNCONFIRMED_FACT_ASSERTION",path,"/facts/"+id);
                        else add(reviews,"FACT_REQUIRES_REVIEW",path,"/facts/"+id);
                    }
                }
                for(JsonNode ref:item.path("rule_refs"))if(!allowedRules.contains(ref.asText()))add(errors,"INVALID_RULE_REFERENCE",path+"/rule_refs","/rule_evaluations");
                for(String field:List.of("text","value","reason"))if(item.hasNonNull(field)) {
                    String text=item.path(field).asText();String fieldPath=path+"/"+field;
                    // Inspect model prose before inserting trusted, fully labelled factual values.
                    String prose=TOKEN.matcher(text).replaceAll("");
                    if(NUMBER.matcher(prose).find()||matches(prose,"[0-9０-９]"))add(errors,"UNBOUND_QUANTITATIVE_CLAIM",fieldPath,"/business_summary");
                    checkBoundaries(prose,fieldPath,rules,summary,request,errors);
                    ((ObjectNode)item).put(field,render(text,fieldPath,refs,events,summary,request,prepared,errors));
                    if(collection.equals("disposition_recommendations"))recommendations.append(prose).append('。');
                }
                String text=item.path("text").asText()+item.path("reason").asText();
                if(refs.isEmpty()&&!uncertain(text))add(reviews,"TEXT_REQUIRES_EVIDENCE_REVIEW",path,"/facts");
                for(var choice:prepared.fieldChoices())if(choice.candidates().stream().anyMatch(id->refs.contains(id)&&(choice.blocked()||!id.equals(choice.adoptedFactId())))) {
                    if(!uncertain(text))add(errors,"UNRESOLVED_CONFLICT",path,"/conflicts");
                    else add(reviews,"CONFLICT_REQUIRES_REVIEW",path,"/conflicts");
                }
            }
        }
        String advice=recommendations.toString();
        if(matches(advice,"建议(?:提交|上报)")&&matches(advice,"建议(?:不予上报|不上报|无需上报)"))add(errors,"CONTRADICTORY_REPORT_ADVICE","/disposition_recommendations","/report_recommendation");
        if(matches(advice,"建议(?:调高|提高|上调)")&&matches(advice,"建议(?:调低|降低|下调)"))add(errors,"CONTRADICTORY_GRADE_ADVICE","/disposition_recommendations","/grade_recommendation");
        if(summary.at("/cash/status").asText().equals("found")) {
            JsonNode cashSection=null;for(JsonNode s:draft.path("overview_sections"))if(s.path("section").asText().equals("historical_cash"))cashSection=s;
            if(cashSection==null||!cashSection.path("text").asText().contains("现金")||!cashSection.path("text").asText().contains("风险")||!containsAll(cashSection.path("fact_refs"),summary.at("/cash/fact_refs")))
                add(errors,"CASH_SIGNAL_OMITTED","/overview_sections","/business_summary/cash");
        }
        // Fixed decisions must remain exactly those of the deterministic rule evaluator.
        for(String key:List.of("grade_recommendation","report_recommendation","proposed_grade"))if(!draft.path(key).equals(rules.path(key)))add(errors,"FIXED_DECISION_CHANGED","/"+key,"/rule_evaluation/"+key);
        if(!draft.path("current_risk_grade").equals(rules.at("/current_grade/value")))add(errors,"FIXED_DECISION_CHANGED","/current_risk_grade","/current_grade/value");
        return new Report(draft,errors,reviews);
    }
    private static String render(String text,String path,Set<String> refs,Map<String,JsonNode> events,JsonNode summary,JsonNode request,EddFactService.Prepared prepared,ArrayNode errors) {
        Matcher matcher=TOKEN.matcher(text);StringBuffer out=new StringBuffer();
        while(matcher.find()) {
            String kind=matcher.group(1),key=matcher.group(2),replacement="";JsonNode evidence=null;
            if(kind.equals("metric")||kind.equals("source_metric")) {
                String collection=kind.equals("metric")?"transaction_metrics":"source_metrics";
                if(key.matches("[0-9]{1,6}"))evidence=summary.path(collection).path(Integer.parseInt(key));
                if(evidence!=null&&!evidence.isMissingNode()&&containsAll(refs,evidence.path("fact_refs"))&&included(evidence.path("fact_refs"),prepared))
                    replacement=(kind.equals("metric")?"交易汇总":"原始快照值（不代表实际交易）")+"[口径="+evidence.path("name").asText()+"；值="+evidence.path("value").asText()+"；币种="+evidence.path("currency_code").asText("未确认")+"；单位="+evidence.path("unit").asText()+"；粒度="+evidence.path("grain").asText()+"]";
            } else if(kind.equals("event")) {
                evidence=events.get(key);var d=prepared.decisions().get(key);
                if(evidence!=null&&refs.contains(key)&&d!=null&&d.state()==EddFactService.State.INCLUDED&&!blocked(key,prepared))
                    replacement="业务记录[类型="+evidence.path("business_type").asText()+"；状态="+evidence.path("status").asText()+"；时间="+evidence.path("event_time").asText()+"；金额="+evidence.path("amount").asText("未确认")+"；金额含义="+evidence.path("amount_kind").asText("未确认")+"；币种="+evidence.path("currency").asText("未确认")+"；资金方向="+evidence.path("direction").asText("未确认")+"]";
            } else if(kind.equals("risk")) {
                for(JsonNode risk:request.path("risk_records"))if(risk.path("fact_id").asText().equals(key))evidence=risk;
                var d=prepared.decisions().get(key);
                if(evidence!=null&&refs.contains(key)&&d!=null&&d.state()==EddFactService.State.INCLUDED&&!blocked(key,prepared))
                    replacement="风险记录[类型="+evidence.path("type").asText()+"；记录时间="+evidence.path("event_time").asText("未确认")+"]";
            } else if(kind.equals("coverage")) {
                evidence=request.path("coverage").get(key);
                if(evidence!=null&&evidence.isObject())replacement="数据覆盖[来源="+key+"；查询状态="+evidence.path("query_status").asText()+"；完整="+evidence.path("complete").asBoolean()+"；起始="+evidence.path("from").asText("未确认")+"；截止="+evidence.path("to").asText("未确认")+"；获取时间="+evidence.path("as_of").asText("未确认")+"]";
            } else if(kind.equals("count")) {
                JsonNode count=summary.path("event_counts").get(key);Set<String> required=new HashSet<>();
                for(JsonNode event:events.values())if((event.path("business_type").asText()+"/"+event.path("status").asText()).equals(key)&&prepared.decisions().get(event.path("fact_id").asText()).state()==EddFactService.State.INCLUDED)required.add(event.path("fact_id").asText());
                if(count!=null&&refs.containsAll(required)&&required.stream().noneMatch(id->blocked(id,prepared)))replacement="已纳入业务条数[口径="+key+"；条数="+count.asText()+"]";
            }
            if(replacement.isEmpty())add(errors,"INVALID_EVIDENCE_BINDING",path,"/business_summary/"+kind);
            matcher.appendReplacement(out,Matcher.quoteReplacement(replacement));
            if(out.length()>20000){add(errors,"RENDERED_TEXT_TOO_LARGE",path,"/business_summary");return "";}
        }
        matcher.appendTail(out);
        if(out.length()>20000){add(errors,"RENDERED_TEXT_TOO_LARGE",path,"/business_summary");return "";}
        if(out.toString().contains("{{")||out.toString().contains("}}"))add(errors,"INVALID_EVIDENCE_BINDING",path,"/business_summary");
        return out.toString();
    }
    private static void checkBoundaries(String text,String path,JsonNode rules,JsonNode summary,JsonNode request,ArrayNode errors) {
        for(String sentence:text.split("[，,。；;！!\\n]")) {
            if(sentence.isBlank())continue;
            // Recognized negative/modal formulations are not asserted decisions.
            boolean modal=matches(sentence,"不能|不得|不应|不代表|不等于|无法确定|尚不能|并非|不直接");
            if(!modal&&matches(sentence,"(?:建议|应当|应|决定|调整为|评定为|认定为|定为|维持)(?:客户|本客户|风险等级|为|至|是|按|保持|现有|原有|原|等级|风险|级别|的| )*(?:最高|高|中|低)风险|建议(?:调高|提高|上调|调低|降低|下调|维持).*风险等级")) {
                if(rules.path("proposed_grade").isNull())add(errors,"GRADE_WITHOUT_RULE_BASIS",path,"/proposed_grade");
                else {
                    String grade=rules.path("proposed_grade").asText();String label=Map.of("highest","最高风险","high","高风险","medium","中风险","low","低风险").get(grade);
                    String asserted=sentence.contains("最高风险")?"最高风险":sentence.contains("高风险")?"高风险":sentence.contains("中风险")?"中风险":sentence.contains("低风险")?"低风险":null;
                    if(label==null||!label.equals(asserted))add(errors,"GRADE_CONFLICT",path,"/proposed_grade");
                }
            }
            if(!modal&&text.contains("现金")&&matches(sentence,"(?:自动|直接|因此|所以).*(?:调高|上调|高风险)"))add(errors,"CASH_AUTO_GRADE",path,"/business_summary/cash");
            if(!modal&&matches(sentence,"(?:建议|应当|决定)(?:不上报|不予上报|无需上报|上报|提交可疑)")) {
                String value=rules.at("/report_recommendation/value").asText("");
                boolean negative=matches(sentence,"不上报|不予上报|无需上报");
                if(!Set.of("suggest_report","suggest_not_report").contains(value)||negative!=value.equals("suggest_not_report"))add(errors,"REPORT_ADVICE_CONFLICT",path,"/report_recommendation");
            }
            if(!modal&&matches(sentence,"无现金|没有现金|未发生现金|不存在现金" )&&!summary.at("/cash/status").asText().equals("not_observed_in_available_scope"))add(errors,"CASH_STATUS_CONFLICT",path,"/business_summary/cash");
            if(!modal&&matches(sentence,"(?:无|没有|未发现|不存在|从未|未曾).*(?:可疑交易上报|上报可疑|可疑交易记录)" )&&!rules.path("historical_report_status").asText().equals("not_observed_in_available_scope"))add(errors,"UNKNOWN_AS_NEGATIVE",path,"/historical_report_status");
            if(!modal&&matches(sentence,"(?:未|没有|从未|未曾)上报(?:过)?可疑交易|无(?:历史)?可疑交易(?:上报)?记录|历史可疑交易上报(?:为)?[：:]?否")&&!rules.path("historical_report_status").asText().equals("not_observed_in_available_scope"))add(errors,"UNKNOWN_AS_NEGATIVE",path,"/historical_report_status");
            Map<String,String> topics=Map.of("judicial","司法|查冻扣","blacklist","黑名单","individual_claims","理赔","underwriting","承保","preservation","保全","historical_suspicious_reports","可疑交易|上报");
            topics.forEach((source,topic)->{
                JsonNode coverage=request.path("coverage").path(source);
                if(!modal&&(!coverage.path("query_status").asText().equals("succeeded")||!coverage.path("complete").asBoolean())&&matches(sentence,"(?:无|没有|未发现|未命中|不在|不存在|未涉及|从未|未曾).*(?:"+topic+")|(?:"+topic+").*(?:为否|：否|:否|均无|不存在|无记录)"))add(errors,"UNKNOWN_AS_NEGATIVE",path,"/coverage/"+source);
            });
        }
    }
    private static boolean blocked(String id,EddFactService.Prepared p){return p.fieldChoices().stream().anyMatch(c->c.candidates().contains(id)&&(c.blocked()||!id.equals(c.adoptedFactId())));}
    private static boolean included(JsonNode refs,EddFactService.Prepared p){for(JsonNode ref:refs){var d=p.decisions().get(ref.asText());if(d==null||d.state()!=EddFactService.State.INCLUDED||blocked(ref.asText(),p))return false;}return !refs.isEmpty();}
    private static boolean containsAll(JsonNode refs,JsonNode expected){Set<String> ids=new HashSet<>();refs.forEach(r->ids.add(r.asText()));return containsAll(ids,expected);}
    private static boolean containsAll(Set<String> refs,JsonNode expected){for(JsonNode ref:expected)if(!refs.contains(ref.asText()))return false;return true;}
    private static boolean uncertain(String text){return matches(text,"待(?:机构)?核实|待确认|尚未确认|资料不足|无法确定|需机构核实|范围外|不纳入");}
    private static boolean matches(String text,String pattern){return Pattern.compile(pattern).matcher(text).find();}
    private static void add(ArrayNode out,String code,String path,String evidencePath){ObjectNode issue=JSON.objectNode().put("code",code).put("path",path).put("evidence_path",evidencePath);if(!contains(out,issue))out.add(issue);}
    private static boolean contains(ArrayNode out,JsonNode issue){for(JsonNode n:out)if(n.equals(issue))return true;return false;}
}
