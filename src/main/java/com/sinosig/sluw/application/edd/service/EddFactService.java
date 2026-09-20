package com.sinosig.sluw.application.edd.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

/** Scope preparation only. Caller validates the v1 schema before entering this service. */
public final class EddFactService {
    public enum State { INCLUDED, EXCLUDED, PENDING, DUPLICATE }
    public record Decision(String factId, State state, List<String> reasons, Set<String> roles,
                           String duplicateOf, boolean amountEligible) {
        public Decision { reasons=List.copyOf(reasons); roles=Set.copyOf(roles); }
    }
    public record FieldChoice(String conflictId, String field, List<String> candidates,
                              String adoptedFactId, boolean blocked) {
        public FieldChoice { candidates=List.copyOf(candidates); }
    }
    public record Prepared(EddInputAdapter.Adaptation input, Map<String,Decision> decisions,
                           List<FieldChoice> fieldChoices, List<EddInputAdapter.Issue> issues) {
        public Prepared { decisions=Collections.unmodifiableMap(new LinkedHashMap<>(decisions)); fieldChoices=List.copyOf(fieldChoices); issues=List.copyOf(issues); }
        /** A field with any unresolved conflict is unavailable; other fields remain usable. */
        public boolean fieldBlocked(String field) { return fieldChoices.stream().anyMatch(c -> c.field().equals(field)&&c.blocked()); }
    }
    private record Product(String contno, Set<String> roles, State term, String reason) {}

    public Prepared prepare(ObjectNode validatedRequest) {
        return prepare(new EddInputAdapter.Adaptation(validatedRequest,List.of(),List.of(),List.of()));
    }
    public Prepared prepare(EddInputAdapter.Adaptation input) {
        Objects.requireNonNull(input,"input");
        ObjectNode request=input.request();
        if (!"1.0".equals(text(request,"schema_version"))) throw invalid("Unsupported input version");
        String subject=required(request.path("subject"),"customer_id");
        OffsetDateTime cutoff=parseTime(required(request,"analysis_as_of"));
        Map<String,JsonNode> facts=new LinkedHashMap<>();
        for(String group:List.of("core_snapshots","events","risk_records","manual_excerpts"))
            for(JsonNode fact:array(request,group)) {
                String id=required(fact,"fact_id");
                if(facts.putIfAbsent(id,fact)!=null) throw invalid("Duplicate fact_id");
            }
        List<EddInputAdapter.Issue> issues=new ArrayList<>(input.issues());
        Map<String,Decision> decisions=new LinkedHashMap<>();
        Map<String,Set<String>> policyRoles=new HashMap<>();
        Map<String,List<Product>> products=new HashMap<>();
        // Only explicit customer IDs establish a relationship. Entry role is context, not a filter.
        for(JsonNode snapshot:array(request,"core_snapshots")) {
            String type=text(snapshot,"snapshot_type"); JsonNode v=snapshot.path("values");
            if(type.equals("policy")||type.equals("product")) {
                Set<String> roles=directRoles(v,subject);String cont=text(v,"CONTNO");
                if(!roles.isEmpty()&&!cont.isEmpty()) policyRoles.computeIfAbsent(cont,k->new TreeSet<>()).addAll(roles);
            }
        }
        for(JsonNode snapshot:array(request,"core_snapshots")) {
            if(!text(snapshot,"snapshot_type").equals("product"))continue;
            JsonNode v=snapshot.path("values");String pol=text(v,"POLNO"),cont=text(v,"CONTNO");
            Set<String> roles=new TreeSet<>(policyRoles.getOrDefault(cont,Set.of()));roles.addAll(directRoles(v,subject));
            State term=term(v);String reason=term==State.EXCLUDED?"ONE_YEAR_PRODUCT":term==State.PENDING?"PRODUCT_TERM_UNKNOWN":"";
            if(!pol.isEmpty()) products.computeIfAbsent(pol,k->new ArrayList<>()).add(new Product(cont,roles,term,reason));
        }
        for(JsonNode snapshot:array(request,"core_snapshots")) {
            String id=text(snapshot,"fact_id"),type=text(snapshot,"snapshot_type");JsonNode v=snapshot.path("values");
            String cont=text(v,"CONTNO"),pol=text(v,"POLNO");Set<String> roles=new TreeSet<>(policyRoles.getOrDefault(cont,Set.of()));
            State state=State.INCLUDED;List<String> reasons=new ArrayList<>();boolean amount=false;
            if(type.equals("policy")) {
                // A policy may mix excluded/eligible products. Never use its undivided total here.
                reasons.add("POLICY_TOTAL_REQUIRES_PRODUCT_SCOPE");
            } else if(type.equals("policy_extension")) {
                state=State.PENDING;reasons.add("PRINT_NUMBER_RELATIONSHIP_REQUIRES_CONFIRMATION");
            } else {
                List<Product> matches=products.getOrDefault(pol,List.of());
                Set<String> matchCont=new HashSet<>();Set<State> terms=new HashSet<>();
                for(Product p:matches){matchCont.add(p.contno());terms.add(p.term());roles.addAll(p.roles());}
                if(matches.isEmpty()||matchCont.size()!=1||terms.size()!=1||(!cont.isEmpty()&&!matchCont.contains(cont))) {
                    state=State.PENDING;reasons.add("PRODUCT_RELATIONSHIP_OR_TERM_AMBIGUOUS");
                } else {
                    state=terms.iterator().next();
                    if(state!=State.INCLUDED)reasons.add(state==State.EXCLUDED?"ONE_YEAR_PRODUCT":"PRODUCT_TERM_UNKNOWN");
                }
                // Snapshot amounts remain source metrics; never actual transaction amounts.
                amount=false;
            }
            if(roles.isEmpty()&&state!=State.EXCLUDED){state=State.PENDING;reasons.add("CUSTOMER_RELATIONSHIP_UNKNOWN");}
            decisions.put(id,new Decision(id,state,reasons,roles,null,amount));
        }
        for(JsonNode event:array(request,"events")) {
            String id=text(event,"fact_id");List<String> reasons=new ArrayList<>();State state=State.INCLUDED;
            Set<String> roles=new TreeSet<>();for(JsonNode role:event.path("roles"))roles.add(role.asText());
            String match=text(event,"subject_match");
            if(match.equals("confirmed")&&!subject.equals(text(event,"customer_id"))) {state=State.EXCLUDED;reasons.add("OTHER_CUSTOMER");}
            else if(!match.equals("confirmed")||!subject.equals(text(event,"customer_id"))||roles.isEmpty()) {state=State.PENDING;reasons.add("CUSTOMER_RELATIONSHIP_UNKNOWN");}
            JsonNode year=event.get("one_year_product");
            if(year!=null&&year.isBoolean()&&year.booleanValue()){state=State.EXCLUDED;reasons.add("ONE_YEAR_PRODUCT");}
            else if(year==null||year.isNull()){if(state!=State.EXCLUDED)state=State.PENDING;reasons.add("PRODUCT_TERM_UNKNOWN");}
            if(text(event,"business_type").startsWith("claim")) {
                String channel=text(event,"claim_channel");
                if(channel.equals("group")){state=State.EXCLUDED;reasons.add("NON_INDIVIDUAL_CLAIM");}
                else if(!channel.equals("individual")){if(state!=State.EXCLUDED)state=State.PENDING;reasons.add("CLAIM_CHANNEL_UNKNOWN");}
            }
            state=eventTime(event,cutoff,state,reasons);
            boolean eligible=state==State.INCLUDED&&text(event,"status").equals("completed")&&event.hasNonNull("amount");
            decisions.put(id,new Decision(id,state,reasons,roles,null,eligible));
        }
        for(JsonNode risk:array(request,"risk_records")) {
            String id=text(risk,"fact_id");List<String> reasons=new ArrayList<>();State state=State.INCLUDED;
            if(!text(risk,"subject_match").equals("confirmed")){state=State.PENDING;reasons.add("RISK_SUBJECT_UNCONFIRMED");}
            state=eventTime(risk,cutoff,state,reasons);
            decisions.put(id,new Decision(id,state,reasons,Set.of(),null,false));
        }
        Set<String> attachments=new HashSet<>(),usedAttachments=new HashSet<>();
        for(JsonNode a:array(request,"attachments")) if(!attachments.add(required(a,"attachment_id")))throw invalid("Duplicate attachment ID");
        for(JsonNode excerpt:array(request,"manual_excerpts")) {
            String id=text(excerpt,"fact_id"),attachment=text(excerpt,"attachment_id");boolean confirmed=excerpt.path("confirmed").asBoolean();
            if(!attachment.isEmpty()) {if(!attachments.contains(attachment))throw invalid("Unknown attachment reference");usedAttachments.add(attachment);}
            List<String> reasons=new ArrayList<>();State state=confirmed?State.INCLUDED:State.PENDING;
            if(!confirmed)reasons.add("EXCERPT_UNCONFIRMED");
            else if(text(excerpt,"confirmed_at").isEmpty())throw invalid("Confirmed excerpt requires confirmation time");
            else if(parseTime(text(excerpt,"confirmed_at")).isAfter(cutoff)){state=State.PENDING;reasons.add("CONFIRMED_AFTER_CUTOFF");}
            decisions.put(id,new Decision(id,state,reasons,Set.of(),null,false));
        }
        for(String id:attachments)if(!usedAttachments.contains(id))issues.add(issue("ATTACHMENT_NOT_EXTRACTED","/attachments","Attachment has no provided excerpt; contents unavailable"));

        // De-duplicate only same-source business identities. Keep every original fact in input.
        deduplicate(array(request,"events"),decisions,false,issues);
        deduplicate(array(request,"core_snapshots"),decisions,true,issues);
        List<FieldChoice> choices=conflicts(request,facts,decisions);
        JsonNode coverage=request.get("coverage");if(coverage==null||!coverage.isObject())throw invalid("Coverage required");
        coverage.fields().forEachRemaining(e->{if(!text(e.getValue(),"query_status").equals("succeeded")||!e.getValue().path("complete").asBoolean())
            issues.add(issue("COVERAGE_INCOMPLETE","/coverage/"+e.getKey(),"Preserve source status and period; no absence conclusion"));});
        if(text(request.path("context"),"role").equals("beneficiary"))
            issues.add(issue("ENTRY_BENEFICIARY_RELATIONSHIP_UNVERIFIED","/context/role","Core flags do not establish beneficiary identity; explicit other roles remain valid"));
        return new Prepared(input,decisions,choices,issues);
    }

    private static List<FieldChoice> conflicts(ObjectNode request,Map<String,JsonNode> facts,Map<String,Decision> decisions) {
        List<FieldChoice> result=new ArrayList<>();Set<String> ids=new HashSet<>();
        for(JsonNode c:array(request,"conflicts")) {
            String id=required(c,"conflict_id"),field=required(c,"field");if(!ids.add(id))throw invalid("Duplicate conflict ID");
            List<String> candidates=new ArrayList<>();for(JsonNode n:c.path("candidate_fact_ids")) {
                if(!n.isTextual()||!facts.containsKey(n.asText())||candidates.contains(n.asText()))throw invalid("Invalid conflict candidate");candidates.add(n.asText());
            }
            if(candidates.size()<2)throw invalid("Conflict requires two candidates");
            String adopted=text(c,"adopted_fact_id");boolean resolved=c.path("resolved").asBoolean();
            if(resolved) {
                if(!candidates.contains(adopted))throw invalid("Adopted fact must be a candidate");
                JsonNode fact=facts.get(adopted);if(fact.has("confirmed")&&!fact.path("confirmed").asBoolean())throw invalid("Unconfirmed excerpt cannot be adopted");
            } else if(!adopted.isEmpty())throw invalid("Unresolved conflict cannot adopt a fact");
            boolean blocked=!resolved||decisions.get(adopted).state()!=State.INCLUDED;
            result.add(new FieldChoice(id,field,candidates,adopted.isEmpty()?null:adopted,blocked));
        }
        // Two resolutions for the same field are not silently reduced to whichever appeared last.
        Map<String,Set<String>> adoptedByField=new HashMap<>();
        for(FieldChoice c:result)if(!c.blocked())adoptedByField.computeIfAbsent(c.field(),k->new HashSet<>()).add(c.adoptedFactId());
        return result.stream().map(c->adoptedByField.getOrDefault(c.field(),Set.of()).size()>1?
                new FieldChoice(c.conflictId(),c.field(),c.candidates(),c.adoptedFactId(),true):c).toList();
    }

    private static void deduplicate(ArrayNode rows,Map<String,Decision> decisions,boolean snapshot,List<EddInputAdapter.Issue> issues) {
        Map<String,List<JsonNode>> groups=new LinkedHashMap<>();Map<String,Set<String>> crossSources=new HashMap<>();
        for(JsonNode row:rows) {
            String source=text(row.path("source"),"source_id"),table=text(row.path("source"),"table");
            String identity=snapshot?canonical(row.path("source").path("key")):text(row,"source_record_id");
            if(source.isEmpty()||identity.isEmpty()||identity.equals("{}")||(snapshot&&!completeKey(row))) {
                hold(decisions,text(row,"fact_id"),"SOURCE_IDENTITY_INCOMPLETE");continue;
            }
            String cross=table+"\u0000"+identity;crossSources.computeIfAbsent(cross,k->new HashSet<>()).add(source);
            groups.computeIfAbsent(source+"\u0000"+cross,k->new ArrayList<>()).add(row);
        }
        for(List<JsonNode> group:groups.values()) {
            JsonNode first=group.get(0);String identity=snapshot?canonical(first.path("source").path("key")):text(first,"source_record_id");
            String cross=text(first.path("source"),"table")+"\u0000"+identity;
            if(crossSources.get(cross).size()>1) {
                for(JsonNode row:group)hold(decisions,text(row,"fact_id"),"POSSIBLE_CROSS_SOURCE_DUPLICATE");
                issues.add(issue("POSSIBLE_CROSS_SOURCE_DUPLICATE","", "Matching identities from different sources require confirmation"));continue;
            }
            if(group.size()<2)continue;
            Set<String> bodies=new HashSet<>();for(JsonNode row:group)bodies.add(content(row,snapshot));
            if(bodies.size()>1) {for(JsonNode row:group)hold(decisions,text(row,"fact_id"),"SOURCE_RECORD_CONFLICT");continue;}
            group.sort(Comparator.comparing(x->text(x,"fact_id")));String primary=text(group.get(0),"fact_id");
            Set<String> roles=new TreeSet<>();for(JsonNode row:group)roles.addAll(decisions.get(text(row,"fact_id")).roles());
            Decision d=decisions.get(primary);decisions.put(primary,new Decision(primary,d.state(),d.reasons(),roles,null,d.amountEligible()));
            for(int i=1;i<group.size();i++){String id=text(group.get(i),"fact_id");decisions.put(id,new Decision(id,State.DUPLICATE,List.of("SAME_SOURCE_RECORD"),roles,primary,false));}
        }
    }
    private static boolean completeKey(JsonNode row) {
        List<String> keys=switch(text(row,"snapshot_type")) {
            case "policy" -> List.of("CONTNO");
            case "product" -> List.of("POLNO");
            case "duty" -> List.of("POLNO","DUTYCODE");
            case "premium_plan" -> List.of("POLNO","DUTYCODE","PAYPLANCODE");
            case "benefit" -> List.of("POLNO","DUTYCODE","GETDUTYCODE");
            case "policy_state" -> List.of("CONTNO","INSUREDNO","POLNO","STATETYPE","STARTDATE");
            case "policy_extension" -> List.of("PRTNO");
            default -> List.of();
        };
        JsonNode key=row.path("source").path("key"),values=row.path("values");
        return !keys.isEmpty()&&keys.stream().allMatch(k->!text(key,k).isEmpty()&&text(key,k).equals(text(values,k)));
    }
    private static String content(JsonNode row,boolean snapshot) {
        if(snapshot)return canonical(row.path("values"));
        ObjectNode body=((ObjectNode)row).deepCopy();body.remove(List.of("fact_id","source","roles"));
        if(body.hasNonNull("amount"))body.put("amount",new BigDecimal(body.get("amount").asText()).stripTrailingZeros().toPlainString());
        if(body.hasNonNull("event_time"))body.put("event_time",parseTime(body.get("event_time").asText()).toInstant().toString());
        return canonical(body);
    }
    private static String canonical(JsonNode value) {
        if(value.isObject()){ObjectNode sorted=JsonNodeFactory.instance.objectNode();TreeSet<String> names=new TreeSet<>();value.fieldNames().forEachRemaining(names::add);for(String k:names)sorted.set(k,value.get(k));return sorted.toString();}
        return value.toString();
    }
    private static void hold(Map<String,Decision> decisions,String id,String reason) {
        Decision d=decisions.get(id);if(d.state()==State.EXCLUDED)return;List<String> reasons=new ArrayList<>(d.reasons());reasons.add(reason);
        decisions.put(id,new Decision(id,State.PENDING,reasons,d.roles(),null,false));
    }
    private static State eventTime(JsonNode fact,OffsetDateTime cutoff,State state,List<String> reasons) {
        String time=text(fact,"event_time");if(time.isEmpty()){reasons.add("EVENT_TIME_UNKNOWN");return state==State.EXCLUDED?state:State.PENDING;}
        if(parseTime(time).isAfter(cutoff)){reasons.add("AFTER_ANALYSIS_CUTOFF");return State.EXCLUDED;}return state;
    }
    private static State term(JsonNode values) {
        // YEARS has explicit years units; an unknown age/year flag prevents interpreting conflicting fields.
        if(values.hasNonNull("INSUYEARFLAG"))return State.PENDING;
        if(!values.hasNonNull("YEARS"))return State.PENDING;
        try {BigDecimal years=new BigDecimal(values.get("YEARS").asText());if(years.compareTo(BigDecimal.ONE)==0)return State.EXCLUDED;if(years.compareTo(BigDecimal.ONE)>0)return State.INCLUDED;}
        catch(NumberFormatException ignored){}return State.PENDING;
    }
    private static Set<String> directRoles(JsonNode values,String subject) {
        Set<String> roles=new TreeSet<>();if(subject.equals(text(values,"APPNTNO")))roles.add("policyholder");if(subject.equals(text(values,"INSUREDNO")))roles.add("insured");return roles;
    }
    private static ArrayNode array(JsonNode node,String key){JsonNode value=node.get(key);if(value==null||!value.isArray())throw invalid("Required array missing");return (ArrayNode)value;}
    private static String required(JsonNode node,String key){String value=text(node,key);if(value.isBlank())throw invalid("Required text missing");return value;}
    private static String text(JsonNode node,String key){JsonNode value=node.get(key);return value==null||value.isNull()?"":value.asText();}
    private static OffsetDateTime parseTime(String value){try{return OffsetDateTime.parse(value);}catch(RuntimeException e){throw invalid("Invalid timestamp");}}
    private static IllegalArgumentException invalid(String message){return new IllegalArgumentException(message);}
    private static EddInputAdapter.Issue issue(String code,String path,String message){return new EddInputAdapter.Issue(code,path,message);}
}
