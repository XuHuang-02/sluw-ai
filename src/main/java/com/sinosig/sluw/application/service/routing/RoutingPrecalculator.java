package com.sinosig.sluw.application.service.routing;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import java.util.function.Function;
import static com.sinosig.sluw.application.service.routing.RoutingTypes.*;

/** Pure predicates transcribed from AutoSendBL SQL; no model, database or workflow calls. */
public final class RoutingPrecalculator {
    private record Row(JsonNode data, String ref) {}
    private final RoutingInput input;
    private final Map<String, Fact> facts = new LinkedHashMap<>();
    private final Map<String, List<Item>> items = new LinkedHashMap<>();
    public RoutingPrecalculator(RoutingInput input) { this.input = input; }
    private static Fact known(boolean v, String... refs) { return new Fact(v ? Truth.TRUE : Truth.FALSE, List.of(refs), List.of()); }
    private static Fact unknown(String name) { return new Fact(Truth.UNKNOWN, List.of(), List.of(name)); }
    private static List<String> union(List<String> a, List<String> b) { var s=new TreeSet<>(a);s.addAll(b);return List.copyOf(s); }
    private static Fact merge(Truth v, Fact a, Fact b) { return new Fact(v, union(a.refs(),b.refs()), union(a.missing(),b.missing())); }
    private static Fact not(Fact a) { return new Fact(a.value()==Truth.UNKNOWN ? Truth.UNKNOWN : a.value()==Truth.TRUE ? Truth.FALSE : Truth.TRUE,a.refs(),a.missing()); }
    private static Fact and(Fact a, Fact b) {
        if(a.value()==Truth.FALSE)return a;if(b.value()==Truth.FALSE)return b;
        return merge(a.value()==Truth.UNKNOWN||b.value()==Truth.UNKNOWN?Truth.UNKNOWN:Truth.TRUE,a,b);
    }
    private static Fact or(Fact a, Fact b) {
        if(a.value()==Truth.TRUE)return a;if(b.value()==Truth.TRUE)return b;
        return merge(a.value()==Truth.UNKNOWN||b.value()==Truth.UNKNOWN?Truth.UNKNOWN:Truth.FALSE,a,b);
    }
    private List<Row> rows(String group) {
        var out=new ArrayList<Row>();var rows=input.rows(group);
        for(int i=0;i<rows.size();i++)out.add(new Row(rows.get(i),group+"["+i+"]"));return out;
    }
    private Fact exists(String group, Function<Row, Fact> predicate) {
        Fact result=known(false);
        for(Row r:rows(group))result=or(result,predicate.apply(r));
        if(result.value()==Truth.TRUE)return result;
        return input.complete(group) ? merge(result.value(),result,known(false,"completeness."+group)) : or(result,unknown(group));
    }
    private Fact allErrors(Function<Row, Fact> predicate) { return or(exists("currentErrors",predicate),exists("historyErrors",predicate)); }
    private static String value(Row r,String k){ return r.data().path(k).isNull()?null:r.data().path(k).asText(); }
    private static boolean eq(Row r,String k,String v){return v.equals(value(r,k));}
    private static boolean sqlEq(String a,String b){return a!=null&&b!=null&&a.equals(b);}
    private static Fact test(Row r,boolean b){return known(b,r.ref());}
    private Fact excluded(Row r) {
        String code=value(r,"uwrulecode");
        if(code==null||Set.of("CS0001","CS0002").contains(code))return test(r,true);
        return not(inDictionaryComplement(r));
    }
    /** SQL NOT IN: NULL in the dictionary prevents a TRUE match even for an absent code. */
    private Fact inDictionaryComplement(Row r) {
        String code=value(r,"uwrulecode");
        if(code==null)return test(r,false);
        var dict=input.rows("autoallotbyerr");
        for(int i=0;i<dict.size();i++)if(dict.get(i).isNull()||code.equals(dict.get(i).asText()))return known(false,r.ref(),"autoallotbyerr["+i+"]");
        return input.complete("autoallotbyerr")?known(true,r.ref(),"completeness.autoallotbyerr"):unknown("autoallotbyerr");
    }
    private Fact internalHistory(Row r,boolean samePerson) {
        return exists("historyErrors",h->test(h,eq(h,"lettertype","4")
            &&sqlEq(value(h,"uwrulecode"),value(r,"uwrulecode"))
            &&(!samePerson||sqlEq(value(h,"insuredno"),value(r,"insuredno")))));
    }
    private Fact newInternal(Row r,boolean samePerson){return and(test(r,eq(r,"lettertype","4")),not(internalHistory(r,samePerson)));}
    private Fact limitReached(Row r) {
        if(!eq(r,"lettertype","2"))return test(r,false);
        var batches=new HashSet<Integer>();var refs=new ArrayList<String>();refs.add(r.ref());
        for(Row h:rows("historyErrors"))if(eq(h,"lettertype","2")&&eq(h,"autoflag","3")&&sqlEq(value(h,"uwrulecode"),value(r,"uwrulecode"))){batches.add(h.data().path("uwno").asInt());refs.add(h.ref());}
        if(batches.size()>=2)return new Fact(Truth.TRUE,List.copyOf(refs),List.of());
        return input.complete("historyErrors")?new Fact(Truth.FALSE,union(refs,List.of("completeness.historyErrors")),List.of()):unknown("historyErrors");
    }
    private Kind combinedKind(Row r) {
        String type=value(r,"lettertype");
        if(type==null)return Kind.EXAM;
        return switch(type){case "1","5"->Kind.INVESTIGATION;case "3"->Kind.EXTERNAL2;default->null;};
    }
    /** Produces a complete candidate list only when every possibly contributing row is resolved. */
    private Fact candidates(String key,List<String> groups,Function<Row,Fact> predicate,Function<Row,Kind> kind) {
        Fact ready=known(true);var grouped=new TreeMap<String,List<String>>();var kinds=new HashMap<String,Kind>();var subjects=new HashMap<String,String>();
        for(String group:groups){
            if(!input.complete(group))ready=and(ready,unknown(group));
            else ready=and(ready,known(true,"completeness."+group));
            for(Row r:rows(group)){
                Fact selected=predicate.apply(r);
                if(selected.value()==Truth.UNKNOWN){ready=and(ready,selected);continue;}
                ready=merge(ready.value(),ready,new Fact(Truth.TRUE,selected.refs(),List.of()));
                if(selected.value()!=Truth.TRUE)continue;
                Kind k=kind.apply(r);
                if(k==null)continue; // dealAll has no service arm for this type; do not invent one.
                String subject = k==Kind.INTERNAL||k==Kind.EXTERNAL1?"POLICY":key.equals("combined")&&(eq(r,"lettertype","3")||eq(r,"lettertype","5"))?"APPLICANT":value(r,"insuredno")==null?null:"PERSON:"+value(r,"insuredno");
                if(subject==null){ready=and(ready,unknown(r.ref()+".insuredno"));continue;}
                String id=k.name()+"/"+subject;kinds.put(id,k);subjects.put(id,subject);
                grouped.computeIfAbsent(id,x->new ArrayList<>()).add(r.ref());
            }
        }
        var out=new ArrayList<Item>();grouped.forEach((id,refs)->out.add(new Item(kinds.get(id),subjects.get(id),refs.stream().sorted().toList())));
        items.put(key,List.copyOf(out));facts.put(key+"Ready",ready);
        Fact has=known(!out.isEmpty());
        if(out.isEmpty()&&ready.value()==Truth.UNKNOWN)has=ready;
        return has;
    }
    public Facts calculate() {
        facts.put("nonAutoTask",or(exists("lwmission",r->test(r,eq(r,"activityid","0000001100")&&value(r,"lastoperator")!=null&&!eq(r,"lastoperator","mzhb-lhq"))),
            exists("lbmission",r->test(r,eq(r,"activityid","0000001100")&&value(r,"lastoperator")!=null&&!eq(r,"lastoperator","mzhb-lhq")))));
        facts.put("priorOtherAuto",allErrors(r->test(r,value(r,"autoflag")!=null&&Set.of("1","3","5").contains(Objects.toString(value(r,"lettertype"),"")))));
        facts.put("priorInternalOrExternal1",allErrors(r->test(r,Set.of("1","2","3").contains(Objects.toString(value(r,"autoflag"),"")))));
        facts.put("nonAutomaticRule",exists("currentErrors",r->and(test(r,value(r,"lettertype")==null&&value(r,"peitem")==null&&value(r,"positivesign")==null),not(excluded(r)))));
        Fact noError=exists("currentErrors",r->test(r,eq(r,"uwerror","问题件修改完毕后，没有未通过的核保规则")));
        Fact allExcluded=not(exists("currentErrors",r->not(excluded(r))));
        Fact hasInternal=exists("currentErrors",r->and(not(excluded(r)),test(r,eq(r,"lettertype","4"))));
        Fact hasOther=exists("currentErrors",r->and(not(excluded(r)),test(r,!eq(r,"lettertype","4"))));
        Fact onlyRepeated=and(and(hasInternal,not(hasOther)),not(exists("currentErrors",r->newInternal(r,false))));
        facts.put("passCandidate",or(or(noError,allExcluded),onlyRepeated));
        facts.put("hasNote",exists("lwnotepad",r->test(r,value(r,"noteflag")!=null)));
        facts.put("positiveUnmarked",allErrors(r->test(r,eq(r,"positivesign","1")&&value(r,"lettertype")==null&&value(r,"peitem")==null)));
        facts.put("blockingNote",exists("lwnotepad",r->test(r,eq(r,"noteflag","disagree")||eq(r,"noteflag","kidamntrisk"))));
        facts.put("newInternal",exists("currentErrors",r->newInternal(r,true)));
        candidates("internal",List.of("currentErrors"),r->newInternal(r,true),r->Kind.INTERNAL);
        facts.put("hasExternal1",exists("currentErrors",r->test(r,eq(r,"lettertype","2"))));
        facts.put("external1Limit",exists("currentErrors",this::limitReached));
        candidates("external1",List.of("currentErrors"),r->test(r,eq(r,"lettertype","2")),r->Kind.EXTERNAL1);
        Fact autope=exists("lwnotepad",r->test(r,eq(r,"noteflag","autope")));
        Function<Row,Fact> notePredicate=r->and(test(r,eq(r,"positivesign","1")&&r.data().path("uwno").asInt()==1),autope);
        facts.put("hasNoteExam",allErrors(notePredicate));
        candidates("noteExam",List.of("currentErrors","historyErrors"),notePredicate,r->Kind.EXAM);
        Fact investigationPositive=allErrors(r->test(r,eq(r,"positivesign","1")&&(eq(r,"lettertype","1")||eq(r,"lettertype","5"))));
        Function<Row,Fact> combined=r->{
            boolean current=r.ref().startsWith("currentErrors[");
            Fact a=and(test(r,current&&(eq(r,"lettertype","3")||value(r,"peitem")!=null)&&value(r,"positivesign")==null),inDictionaryComplement(r));
            Fact b=and(test(r,eq(r,"positivesign","1")&&r.data().path("uwno").asInt()==1&&value(r,"peitem")!=null),autope);
            Fact c=and(test(r,current&&(eq(r,"lettertype","1")||eq(r,"lettertype","5"))),not(investigationPositive));
            return or(or(a,b),c);
        };
        facts.put("hasCombined",allErrors(combined));
        facts.put("hasCombinedServices",candidates("combined",autope.value()==Truth.FALSE?List.of("currentErrors"):List.of("currentErrors","historyErrors"),combined,this::combinedKind));
        // Empty optional candidate queries can be resolved without requiring unrelated groups.
        if(facts.get("hasNoteExam").value()==Truth.FALSE)facts.put("noteExamReady",facts.get("hasNoteExam"));
        return new Facts(RoutingInput.VERSION,Collections.unmodifiableMap(facts),Collections.unmodifiableMap(items));
    }
}
