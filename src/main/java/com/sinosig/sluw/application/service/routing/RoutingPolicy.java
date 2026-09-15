package com.sinosig.sluw.application.service.routing;

import java.util.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.converter.CompositeResponseTextCleaner;
import org.springframework.ai.converter.WhitespaceCleaner;
import static com.sinosig.sluw.application.service.routing.RoutingTypes.*;

/** One versioned decision table supplies both the model instructions and runtime guard. */
public final class RoutingPolicy {
    private final Map<String, Node> nodes = new LinkedHashMap<>();
    private final String prompt;
    private final String fingerprint;
    private final String entry;
    private final BeanOutputConverter<Selection> converter;
    public RoutingPolicy() throws java.io.IOException {
        String table=new ClassPathResource("prompts/deal-issue-routing.json").getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        Table definition=RoutingInput.newJsonMapper().readValue(table,Table.class);
        entry=definition.entry();
        loadTable(definition.nodes());
        // Keep strict typing, duplicate-key and trailing-token checks; selection arms have optional mutually exclusive keys.
        var mapper=RoutingInput.newJsonMapper();
        // Reuse framework cleaners, but do not discard thinking tags or extract JSON from arbitrary prose.
        var cleaner=CompositeResponseTextCleaner.builder()
            .addCleaner(new WhitespaceCleaner()).build();
        converter=new BeanOutputConverter<>(Selection.class,mapper,cleaner);
        prompt="""
            你执行DEAL_ISSUE_V1选路实验，不进行医学判断，不调用工具，不读取聊天历史。
            输入仅为服务端计算的conditions，各值为TRUE/FALSE/UNKNOWN。不得自行改写条件值。
            从决策表entry指定的节点开始，沿TRUE走yes、FALSE走no，不能跳过条件。
            条件UNKNOWN时立即停止，只输出status=INSUFFICIENT和blockedAt=当前判断节点id。
            抵达叶节点，只输出status=SELECTED和branchId=该叶节点id。
            SELECTED不得提供blockedAt；INSUFFICIENT不得提供branchId。不得选择INVALID保护节点。
            路径、去向、引用、缺失字段及处理项由服务端生成，禁止输出这些字段。
            不可添加解释或接口执行结果。只返回一个JSON对象，不要Markdown代码围栏或任何前后文字。
            决策表：
            """+table+"\n"+converter.getFormat();
        fingerprint=hash(prompt);
    }
    public String prompt(){return prompt;}
    /** For validation/evaluation only. Never supplied as the model's answer or fallback. */
    public Decision expected(Facts facts) {
        try { return calculateExpected(facts); }
        catch (RuntimeException e) { throw new InternalFailure(FailurePhase.COMPARISON,e); }
    }
    private Decision calculateExpected(Facts facts) {
        var path=new ArrayList<String>();var refs=new TreeSet<String>();String id=entry;
        for(int guard=0;guard<nodes.size()+1;guard++){
            Node n=nodes.get(id);if(n==null||"INVALID".equals(id))throw new IllegalStateException("invalid decision table");
            if(n.condition()==null)return new Decision(Status.SELECTED,n.route(),id,List.copyOf(path),List.copyOf(refs),List.of(),n.itemKey()==null?List.of():canonicalItems(n.itemKey(),facts));
            Fact f=facts.conditions().get(n.condition());if(f==null)throw new IllegalStateException("missing calculated condition");
            path.add(id);refs.addAll(f.refs());
            if(f.value()==Truth.UNKNOWN)return new Decision(Status.INSUFFICIENT,null,id,List.copyOf(path),List.copyOf(refs),f.missing().stream().distinct().sorted().toList(),List.of());
            id=f.value()==Truth.TRUE?n.yes():n.no();
        }
        throw new IllegalStateException("decision cycle");
    }
    public record Audit(boolean formatCorrect, boolean branchCorrect, boolean pathCorrect) {}
    public static final class Rejected extends IllegalArgumentException {
        private final Audit audit;
        public Rejected(Audit audit, String reason){super(reason);this.audit=audit;}
        public Audit audit(){return audit;}
    }
    public enum FailurePhase { COMPARISON, REPORT }
    public static final class InternalFailure extends IllegalStateException {
        private final FailurePhase phase;
        InternalFailure(FailurePhase phase, RuntimeException cause) {
            super("选路服务端处理失败："+phase,cause);this.phase=phase;
        }
        public FailurePhase phase() { return phase; }
    }
    public String fingerprint() { return fingerprint; }
    private static String hash(String text) {
        try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
            .digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    /** Model receives only predicate values; source references and service items stay on the server. */
    public Map<String, Object> modelInput(Facts facts) {
        var conditions=new LinkedHashMap<String, Truth>();
        facts.conditions().forEach((key, fact)->conditions.put(key,fact.value()));
        return Map.of("conditions",conditions);
    }
    public Decision validate(String raw,Facts facts) {
        Selection selection;
        try {
            if(raw==null||raw.length()>4096)throw new IllegalArgumentException();
            selection=converter.convert(raw);
            if(selection==null||selection.status()==null)throw new IllegalArgumentException();
            if(selection.status()==Status.SELECTED) {
                if(selection.branchId()==null||selection.branchId().isBlank()||selection.blockedAt()!=null)throw new IllegalArgumentException();
            } else if(selection.blockedAt()==null||selection.blockedAt().isBlank()||selection.branchId()!=null)throw new IllegalArgumentException();
        } catch(Exception e) {
            throw new Rejected(new Audit(false,false,false),"模型输出无法转换为规定的小结构：仅允许状态及分支或阻塞节点。");
        }
        String selected=selection.status()==Status.SELECTED?selection.branchId():selection.blockedAt();
        Node node=nodes.get(selected);
        if(node==null||"INVALID".equals(selected)||(selection.status()==Status.SELECTED)!=(node.condition()==null))
            throw new Rejected(new Audit(true,false,false),"分支或阻塞节点不存在，或节点类型不匹配。");
        // Compare only the model's actual responsibility. A mismatch never produces a report or fallback.
        Decision comparison=expected(facts);
        if(selection.status()!=comparison.status()||!selected.equals(comparison.branchId()))
            throw new Rejected(new Audit(true,true,false),"所选分支或阻塞节点不符合条件及原代码优先顺序。");
        try { return assemble(selection,facts); }
        catch (RuntimeException e) { throw new InternalFailure(FailurePhase.REPORT,e); }
    }
    /** Assemble server-owned fields only after selection has passed the guard. */
    private Decision assemble(Selection selection,Facts facts) {
        String target=selection.status()==Status.SELECTED?selection.branchId():selection.blockedAt();
        var path=new ArrayList<String>();var refs=new TreeSet<String>();String id=entry;
        for(int guard=0;guard<nodes.size()+1;guard++) {
            Node node=nodes.get(id);
            if(node==null||"INVALID".equals(id))throw new IllegalStateException("report path invalid");
            if(node.condition()==null) {
                if(selection.status()!=Status.SELECTED||!id.equals(target))throw new IllegalStateException("report target mismatch");
                List<Item> items=node.itemKey()==null?List.of():canonicalItems(node.itemKey(),facts);
                return new Decision(Status.SELECTED,node.route(),id,List.copyOf(path),List.copyOf(refs),List.of(),items);
            }
            Fact fact=facts.conditions().get(node.condition());
            if(fact==null||fact.value()==null||fact.refs()==null||fact.missing()==null)throw new IllegalStateException("report fact missing");
            path.add(id);refs.addAll(fact.refs());
            if(fact.value()==Truth.UNKNOWN) {
                if(selection.status()!=Status.INSUFFICIENT||!id.equals(target)||fact.missing().isEmpty())throw new IllegalStateException("report blocker mismatch");
                return new Decision(Status.INSUFFICIENT,null,id,List.copyOf(path),List.copyOf(refs),fact.missing().stream().distinct().sorted().toList(),List.of());
            }
            id=fact.value()==Truth.TRUE?node.yes():node.no();
        }
        throw new IllegalStateException("report cycle");
    }
    private List<Item> canonicalItems(String key,Facts facts) {
        Fact ready=facts.conditions().get(key+"Ready");
        if(ready==null||ready.value()!=Truth.TRUE)throw new IllegalStateException("report items not complete");
        List<Item> source=facts.items().get(key);
        if(source==null||source.isEmpty())throw new IllegalStateException("report items missing");
        var grouped=new TreeMap<String,Item>();
        for(Item item:source) {
            if(item==null||item.type()==null||item.subject()==null||item.refs()==null||item.refs().isEmpty()
                ||item.refs().stream().anyMatch(Objects::isNull))throw new IllegalStateException("report item invalid");
            boolean kindValid=switch(key) {
                case "internal"->item.type()==Kind.INTERNAL;
                case "external1"->item.type()==Kind.EXTERNAL1;
                case "noteExam"->item.type()==Kind.EXAM;
                case "combined"->Set.of(Kind.EXAM,Kind.INVESTIGATION,Kind.EXTERNAL2).contains(item.type());
                default->false;
            };
            boolean subjectValid=Set.of(Kind.INTERNAL,Kind.EXTERNAL1).contains(item.type())?item.subject().equals("POLICY"):
                item.subject().startsWith("PERSON:")&&item.subject().length()>7||key.equals("combined")&&item.subject().equals("APPLICANT");
            if(!kindValid||!subjectValid)throw new IllegalStateException("report item contract mismatch");
            String id=item.type()+"/"+item.subject();
            var refs=new TreeSet<>(item.refs());
            if(grouped.containsKey(id))refs.addAll(grouped.get(id).refs());
            grouped.put(id,new Item(item.type(),item.subject(),List.copyOf(refs)));
        }
        return List.copyOf(grouped.values());
    }
    // Package-private table injection permits validation tests without a mutable runtime table.
    RoutingPolicy(String entry, Node[] table) { this.entry=entry;loadTable(table);prompt="";fingerprint=hash(prompt);converter=null; }
    private void loadTable(Node[] table) {
        if(table==null||entry==null||entry.isBlank())throw new IllegalArgumentException("missing table metadata");
        for(Node node:table) {
            if(node==null||node.id()==null||node.id().isBlank()||nodes.putIfAbsent(node.id(),node)!=null)
                throw new IllegalArgumentException("duplicate or missing node id");
        }
        if(!nodes.containsKey(entry))throw new IllegalArgumentException("missing entry");
        for(Node node:nodes.values()) {
            if(node.condition()!=null) {
                if(!RoutingPrecalculator.CONDITION_KEYS.contains(node.condition())||node.route()!=null||node.itemKey()!=null
                    ||!nodes.containsKey(node.yes())||!nodes.containsKey(node.no()))throw new IllegalArgumentException("invalid condition node");
            } else {
                if(node.route()==null||node.yes()!=null||node.no()!=null)throw new IllegalArgumentException("invalid leaf");
                String required=switch(node.route()) {
                    case INTERNAL->"internal";case EXTERNAL1->"external1";case NOTE_EXAM->"noteExam";case COMBINED->"combined";default->null;
                };
                if(!Objects.equals(required,node.itemKey()))throw new IllegalArgumentException("invalid item key");
            }
        }
        var reached=new HashSet<String>();visit(entry,new HashSet<>(),reached);
        if(reached.size()!=nodes.size())throw new IllegalArgumentException("unreachable nodes");
        Set<String> used=new HashSet<>();nodes.values().stream().map(Node::condition).filter(Objects::nonNull).forEach(used::add);
        if(!used.equals(RoutingPrecalculator.CONDITION_KEYS))throw new IllegalArgumentException("condition coverage mismatch");
    }
    private void visit(String id,Set<String> active,Set<String> reached) {
        if(active.contains(id))throw new IllegalArgumentException("decision cycle");
        if(reached.contains(id))return;
        active.add(id);Node node=nodes.get(id);
        if(node.condition()!=null){visit(node.yes(),active,reached);visit(node.no(),active,reached);}
        active.remove(id);reached.add(id);
    }
    public String display(Decision d,Facts facts) throws java.io.IOException {
        StringBuilder out=new StringBuilder("【AI选路实验：已通过四层校验，未执行任何业务操作】\n");
        out.append(d.status()==Status.INSUFFICIENT?"信息不足，无法确定":nodes.get(d.branchId()).description()).append("\n");
        for(String id:d.conditionIds()){
            Node n=nodes.get(id);out.append(id).append("：").append(n.description()).append(" 条件=").append(facts.conditions().get(n.condition()).value()).append("\n");
        }
        return out+"结构化结果：\n"+RoutingInput.newJsonMapper().writerWithDefaultPrettyPrinter().writeValueAsString(d);
    }
}
