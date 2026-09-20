package com.sinosig.sluw.application.edd.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Deterministic history statistics; never calls a model or infers a risk grade. */
public final class EddHistoryService {
    private static final JsonNodeFactory JSON=JsonNodeFactory.instance;
    private static final Set<String> PAYMENTS=Set.of("premium_payment","premium_refund","policy_loan",
            "loan_repayment","maturity_payment","surrender_payment","claim_payment","benefit_payment");
    private static final Set<String> NON_PAYMENTS=Set.of("underwriting","beneficiary_change","address_change",
            "contact_change","policy_freeze","policy_unfreeze","customer_information_change");
    private static final Set<String> SOURCE_FIELDS=Set.of("PREM","SUMPREM","AMNT","ACTUGET","SUMMONEY",
            "CLAIMTIMES","ENDORSETIMES","SUPPRISKSCORE");
    public record History(EddFactService.Prepared prepared,ObjectNode summary) {
        public History { summary=summary.deepCopy(); }
        @Override public ObjectNode summary(){return summary.deepCopy();}
    }
    private record Group(String type,String kind,String currency,String direction,String status) {}
    private static final class Total {
        BigDecimal amount=BigDecimal.ZERO; final List<String> refs=new ArrayList<>();
    }

    public History analyze(EddFactService.Prepared prepared) {
        ObjectNode request=prepared.input().request(),out=JSON.objectNode();
        out.set("coverage",request.path("coverage").deepCopy());
        ArrayNode metrics=out.putArray("transaction_metrics"),sourceMetrics=out.putArray("source_metrics");
        ArrayNode events=out.putArray("event_history"),gaps=out.putArray("gaps"),cashRefs=JSON.arrayNode();
        Map<Group,Total> totals=new LinkedHashMap<>();
        Set<String> policies=new TreeSet<>();
        Map<String,Integer> eventCounts=new TreeMap<>();
        boolean cashUnknown=!paymentCoverageComplete(request.path("coverage"));
        if(cashUnknown)gap(gaps,"PAYMENT_COVERAGE_INCOMPLETE",null);
        for(JsonNode event:request.path("events")) {
            String id=text(event,"fact_id");var decision=prepared.decisions().get(id);
            ObjectNode history=events.addObject();history.put("fact_id",id).put("scope",decision.state().name());
            copy(event,history,"event_time","business_type","status","policy_id");
            history.set("roles",JSON.arrayNode().addAll(decision.roles().stream().sorted().map(TextNode::valueOf).toList()));
            history.set("source",event.path("source").deepCopy());
            if(decision.state()==EddFactService.State.EXCLUDED||decision.state()==EddFactService.State.DUPLICATE)continue;
            String type=text(event,"business_type"),status=text(event,"status");
            boolean payment=PAYMENTS.contains(type);
            if(decision.state()==EddFactService.State.PENDING) {
                if(payment||!NON_PAYMENTS.contains(type)){cashUnknown=true;gap(gaps,"PAYMENT_SCOPE_UNRESOLVED",id);}
                continue;
            }
            if(!text(event,"policy_id").isEmpty())policies.add(text(event,"policy_id"));
            eventCounts.merge(type+"/"+status,1,Integer::sum);
            if(!payment) {
                if(event.hasNonNull("amount"))
                    sourceMetrics.add(metric("event."+text(event,"amount_kind"),text(event,"amount"),
                            "source_value",nullable(event,"currency"),null,"event:"+id,List.of(id)));
                if(!NON_PAYMENTS.contains(type)){cashUnknown=true;gap(gaps,"BUSINESS_TYPE_UNCLASSIFIED",id);}
                continue;
            }
            if(!coveredEvent(request.path("coverage"),event)) {
                cashUnknown=true;gap(gaps,"EVENT_OUTSIDE_CONFIRMED_PAYMENT_COVERAGE",id);
            }
            boolean coreAllowed=usable(prepared,id,Set.of("business_type","subject_match","one_year_product","claim_channel","event_time","status"));
            boolean amountAllowed=coreAllowed&&usable(prepared,id,Set.of("amount","amount_kind","currency","direction"));
            BigDecimal amount=decimal(event.get("amount"));
            boolean dimensions=event.hasNonNull("amount_kind")&&event.hasNonNull("currency")&&event.hasNonNull("direction");
            if(amount!=null&&amount.signum()>=0&&dimensions&&amountAllowed) {
                Group key=new Group(type,text(event,"amount_kind"),text(event,"currency"),text(event,"direction"),status);
                Total total=totals.computeIfAbsent(key,k->new Total());total.amount=total.amount.add(amount);total.refs.add(id);
            } else gap(gaps,"TRANSACTION_AMOUNT_OR_DIMENSIONS_UNRESOLVED",id);
            // Pending/cancelled payments are not evidence that cash was actually paid.
            if(status.equals("pending")||status.equals("cancelled"))continue;
            if(!status.equals("completed")||!coreAllowed||!usable(prepared,id,Set.of("payment_method","amount"))) {
                cashUnknown=true;gap(gaps,"PAYMENT_STATUS_OR_CONFLICT_UNRESOLVED",id);continue;
            }
            String method=text(event,"payment_method");
            if(method.equals("cash")) {
                if(amount!=null&&amount.signum()>0)cashRefs.add(id);
                else if(amount==null||amount.signum()<0){cashUnknown=true;gap(gaps,"CASH_AMOUNT_UNRESOLVED",id);}
            } else if(!method.equals("bank_transfer")) {
                cashUnknown=true;gap(gaps,"PAYMENT_METHOD_UNRESOLVED",id);
            }
        }
        for(var entry:totals.entrySet()) {
            Group k=entry.getKey();Total t=entry.getValue();
            metrics.add(metric(k.type()+"."+k.kind()+"."+k.direction()+"."+k.status(),t.amount.toPlainString(),
                    "money",k.currency(),null,"business_type/amount_kind/currency/direction/status",t.refs));
        }
        ObjectNode counts=out.putObject("event_counts");eventCounts.forEach(counts::put);
        out.put("observed_event_policy_count",policies.size());
        out.set("observed_event_policy_ids",strings(policies));
        ObjectNode cash=out.putObject("cash");
        cash.put("status",!cashRefs.isEmpty()?"found":cashUnknown?"unknown":"not_observed_in_available_scope");
        cash.set("fact_refs",cashRefs);cash.put("coverage_or_evidence_gaps",cashUnknown);
        cash.put("meaning","Risk-increasing factor only; no automatic grade change");
        collectSnapshots(prepared,request,out,sourceMetrics,gaps);
        return new History(prepared,out);
    }

    private static void collectSnapshots(EddFactService.Prepared prepared,ObjectNode request,ObjectNode out,
                                         ArrayNode metrics,ArrayNode gaps) {
        ArrayNode indicators=out.putArray("cash_indicators"),trajectory=out.putArray("state_history");
        Map<String,List<ObjectNode>> stateGroups=new TreeMap<>();
        List<ObjectNode> signing=new ArrayList<>();
        Set<String> policyIds=new TreeSet<>();
        LocalDate cutoff=OffsetDateTime.parse(text(request,"analysis_as_of")).toLocalDate();
        LocalDateTime cutoffTime=OffsetDateTime.parse(text(request,"analysis_as_of")).toLocalDateTime();
        for(JsonNode row:request.path("core_snapshots")) {
            String id=text(row,"fact_id"),type=text(row,"snapshot_type");JsonNode v=row.path("values");
            var decision=prepared.decisions().get(id);
            if(decision.state()==EddFactService.State.EXCLUDED||decision.state()==EddFactService.State.DUPLICATE)continue;
            for(String field:new TreeSet<>(SOURCE_FIELDS))if(v.hasNonNull(field)) {
                ObjectNode m=metric(type+"."+field,text(v,field),"source_value",nullable(v,"CURRENCY"),null,type+":"+id,List.of(id));
                // These are per-row original metrics, not a scope-qualified sum.
                metrics.add(m);
            }
            if((text(v,"PAYMODE").equals("1")||text(v,"PAYMODE").equals("2"))&&
                    Set.of("policy","product","premium_plan").contains(type)) {
                indicators.addObject().put("fact_id",id).put("field","PAYMODE").put("raw_code",text(v,"PAYMODE"))
                        .put("status","actual_payment_unconfirmed").put("scope",decision.state().name());
            }
            if(type.equals("policy")) {
                if(decision.state()==EddFactService.State.INCLUDED)policyIds.add(text(v,"CONTNO"));
                ObjectNode candidate=JSON.objectNode().put("fact_id",id).put("policy_id",text(v,"CONTNO"));
                copy(v,candidate,"SIGNDATE","SIGNTIME");candidate.set("source",row.path("source").deepCopy());
                String timestamp=text(v,"SIGNDATE")+"T"+text(v,"SIGNTIME");
                try {
                    LocalDateTime signingTime=LocalDateTime.parse(timestamp);
                    if(signingTime.isAfter(cutoffTime))continue;
                    candidate.put("signing_time",timestamp);
                    candidate.put("eligible",decision.state()==EddFactService.State.INCLUDED&&usable(prepared,id,Set.of("SIGNDATE","SIGNTIME")));
                } catch(DateTimeException e){candidate.putNull("signing_time");candidate.put("eligible",false);}
                signing.add(candidate);
            }
            if(!type.equals("policy_state"))continue;
            ObjectNode state=JSON.objectNode().put("fact_id",id).put("scope",decision.state().name());
            copy(v,state,"CONTNO","POLNO","INSUREDNO","STATETYPE","STATE","STARTDATE","ENDDATE");
            state.set("source",row.path("source").deepCopy());
            String period="unknown";
            try {
                LocalDate start=LocalDate.parse(text(v,"STARTDATE"));
                LocalDate end=v.hasNonNull("ENDDATE")?LocalDate.parse(text(v,"ENDDATE")):null;
                if(end!=null&&end.isBefore(start))period="unknown";
                else if(start.isAfter(cutoff))period="future";
                else if(end!=null&&end.isBefore(cutoff))period="historical";
                else if(start.equals(cutoff)||(end!=null&&end.equals(cutoff)))period="boundary_unconfirmed";
                else period="current_candidate";
            } catch(DateTimeException ignored){}
            state.put("period",period);trajectory.add(state);
            String key=text(v,"CONTNO")+"/"+text(v,"POLNO")+"/"+text(v,"INSUREDNO")+"/"+text(v,"STATETYPE");
            stateGroups.computeIfAbsent(key,k->new ArrayList<>()).add(state);
        }
        List<JsonNode> orderedStates=new ArrayList<>();trajectory.forEach(orderedStates::add);
        orderedStates.sort(Comparator.comparing((JsonNode n)->text(n,"STARTDATE")).thenComparing(n->text(n,"fact_id")));
        trajectory.removeAll();orderedStates.forEach(trajectory::add);
        out.put("observed_source_policy_count",policyIds.size());
        ArrayNode states=out.putArray("current_states");
        for(var entry:stateGroups.entrySet()) {
            List<ObjectNode> active=entry.getValue().stream().filter(s->text(s,"period").equals("current_candidate")).toList();
            boolean uncertain=entry.getValue().stream().anyMatch(s->text(s,"period").equals("unknown")||text(s,"period").equals("boundary_unconfirmed"));
            ObjectNode s=states.addObject().put("grain",entry.getKey());
            boolean known=active.size()==1&&!uncertain&&text(active.get(0),"scope").equals("INCLUDED")&&
                    active.get(0).hasNonNull("STATE")&&usable(prepared,text(active.get(0),"fact_id"),Set.of("STATE","STATETYPE","STARTDATE","ENDDATE"));
            s.put("status",known?"single_current_source_state":"unknown");
            if(known)s.set("raw_state",active.get(0).get("STATE"));else s.putNull("raw_state");
            s.set("fact_refs",strings(entry.getValue().stream().map(n->text(n,"fact_id")).toList()));
            if(!known)gap(gaps,"CURRENT_SOURCE_STATE_UNRESOLVED",null);
        }
        out.put("whole_policy_state","unknown");
        gap(gaps,"WHOLE_POLICY_STATE_RULES_UNAVAILABLE",null);
        signing.sort(Comparator.comparing(n->text(n,"signing_time")));
        out.set("signing_history",JSON.arrayNode().addAll(signing));
        ObjectNode latest=out.putObject("latest_signing");
        String max=signing.isEmpty()?"":text(signing.get(signing.size()-1),"signing_time");
        List<ObjectNode> candidates=signing.stream().filter(n->text(n,"signing_time").equals(max)).toList();
        boolean certain=!signing.isEmpty()&&signing.stream().allMatch(n->n.path("eligible").asBoolean())&&candidates.size()==1;
        latest.put("status",certain?"latest_available_source_candidate":"unknown");
        latest.set("fact_refs",strings(candidates.stream().map(n->text(n,"fact_id")).toList()));
        if(certain)latest.set("policy_id",candidates.get(0).get("policy_id"));else latest.putNull("policy_id");
    }

    /** Conflicts apply only to their declared field and candidate records. */
    private static boolean usable(EddFactService.Prepared p,String id,Set<String> fields) {
        for(var c:p.fieldChoices())if(c.candidates().contains(id)&&fields.contains(c.field()))
            if(c.blocked()||!id.equals(c.adoptedFactId()))return false;
        return true;
    }
    private static boolean paymentCoverageComplete(JsonNode coverage) {
        // actual_payments is the canonical aggregate; legacy business domains must all be supplied.
        if(coverage.has("actual_payments"))return complete(coverage.get("actual_payments"));
        return complete(coverage.path("underwriting"))&&complete(coverage.path("preservation"))&&complete(coverage.path("individual_claims"));
    }
    private static boolean coveredEvent(JsonNode coverage,JsonNode event) {
        String type=text(event,"business_type");
        String domain=type.equals("premium_payment")?"underwriting":type.equals("claim_payment")?"individual_claims":"preservation";
        JsonNode c=coverage.has("actual_payments")?coverage.path("actual_payments"):coverage.path(domain);
        if(!complete(c))return false;
        OffsetDateTime time=OffsetDateTime.parse(text(event,"event_time"));
        return !time.isBefore(OffsetDateTime.parse(text(c,"from")))&&!time.isAfter(OffsetDateTime.parse(text(c,"to")));
    }
    private static boolean complete(JsonNode c) {
        return text(c,"query_status").equals("succeeded")&&text(c,"coverage_kind").equals("history_events")
                &&c.path("complete").asBoolean()&&c.hasNonNull("from")&&c.hasNonNull("to");
    }
    private static ObjectNode metric(String name,String value,String unit,String code,String label,String grain,List<String> refs) {
        ObjectNode m=JSON.objectNode().put("name",name).put("value",value).put("unit",unit)
                .put("currency_code",code).put("currency_label",label).put("grain",grain);
        m.set("fact_refs",strings(refs));return m;
    }
    private static ArrayNode strings(Collection<String> values){ArrayNode n=JSON.arrayNode();values.forEach(n::add);return n;}
    private static void gap(ArrayNode gaps,String code,String id){gaps.addObject().put("code",code).put("fact_id",id);}
    private static void copy(JsonNode from,ObjectNode to,String... fields){for(String field:fields)to.set(field,from.path(field).isMissingNode()?NullNode.instance:from.get(field).deepCopy());}
    private static String text(JsonNode n,String key){return n.path(key).isNull()?"":n.path(key).asText("");}
    private static String nullable(JsonNode n,String key){String v=text(n,key);return v.isEmpty()?null:v;}
    private static BigDecimal decimal(JsonNode n){if(n==null||n.isNull())return null;try{return new BigDecimal(n.asText());}catch(NumberFormatException e){return null;}}
}
