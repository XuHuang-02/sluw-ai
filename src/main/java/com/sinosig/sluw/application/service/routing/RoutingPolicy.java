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
    private static final String GUARD_NODE = "INVALID";
    private final BeanOutputConverter<Selection> converter;
    private final com.fasterxml.jackson.databind.ObjectReader selectionTree;
    public RoutingPolicy() throws java.io.IOException { this(readDefinition()); }
    private static Table readDefinition() throws java.io.IOException {
        String raw=new ClassPathResource("prompts/deal-issue-routing.json").getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return RoutingInput.newJsonMapper().readValue(raw,Table.class);
    }
    RoutingPolicy(String entry, Node[] table) { this(new Table(entry,table==null?null:Arrays.asList(table))); }
    RoutingPolicy(String entry, List<Node> table) { this(new Table(entry,table)); }
    private RoutingPolicy(Table definition) {
        if(definition==null)throw new IllegalArgumentException("missing decision table");
        entry=definition.entry();
        loadTable(definition.nodes());
        String table;
        try { table=RoutingInput.writeJson(definition); }
        catch(com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalArgumentException("invalid decision table",e); }
        // Keep strict typing, duplicate-key and trailing-token checks; selection arms have optional mutually exclusive keys.
        var mapper=RoutingInput.newJsonMapper();
        selectionTree=mapper.readerFor(com.fasterxml.jackson.databind.JsonNode.class);
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
    public ExpectedPath expected(Facts facts) {
        try { return calculateExpected(facts); }
        catch (InternalFailure e) { throw e; }
        catch (RuntimeException e) { throw new InternalFailure(FailurePhase.PATH_EVALUATION,e); }
    }
    private ExpectedPath calculateExpected(Facts facts) {
        var path=new ArrayList<String>();var refs=new TreeSet<String>();String id=entry;
        for(int guard=0;guard<nodes.size()+1;guard++){
            Node n=nodes.get(id);
            if(GUARD_NODE.equals(id))throw new InternalFailure(FailurePhase.GUARD,new IllegalStateException("readiness invariant violated"));
            if(n==null)throw new IllegalStateException("invalid decision table");
            if(n.condition()==null)return new ExpectedPath(Status.SELECTED,n.route(),id,List.copyOf(path),List.copyOf(refs),List.of());
            Fact f=facts.conditions().get(n.condition());
            if(f==null||f.value()==null||f.refs()==null||f.missing()==null)throw new IllegalStateException("missing calculated condition");
            path.add(id);refs.addAll(f.refs());
            if(f.value()==Truth.UNKNOWN)return new ExpectedPath(Status.INSUFFICIENT,null,id,List.copyOf(path),List.copyOf(refs),f.missing().stream().distinct().sorted().toList());
            id=f.value()==Truth.TRUE?n.yes():n.no();
        }
        throw new IllegalStateException("decision cycle");
    }
    public enum RejectionReason {
        EMPTY_OUTPUT, TOO_LONG, INVALID_JSON, NOT_OBJECT, MISSING_STATUS, INVALID_STATUS_TYPE,
        INVALID_STATUS, EXTRA_FIELD, MISSING_NODE, INVALID_NODE_TYPE, CONFLICTING_NODES,
        CONVERSION_FAILED, UNKNOWN_NODE, GUARD_SELECTED, NODE_KIND_MISMATCH, PATH_MISMATCH
    }
    public record Audit(boolean formatCorrect, boolean branchCorrect, boolean pathCorrect, RejectionReason reason) {}
    public static final class Rejected extends IllegalArgumentException {
        private final Audit audit;
        private Rejected(Audit audit, String message){super(message);this.audit=audit;}
        public Audit audit(){return audit;}
    }
    private static Rejected reject(RejectionReason reason,boolean format,boolean branch) {
        String message=format?"所选节点不符合决策表或条件优先顺序。":"模型输出无法转换为规定的小结构：仅允许状态及分支或阻塞节点。";
        return new Rejected(new Audit(format,branch,false,reason),message);
    }
    public enum FailurePhase { PATH_EVALUATION, GUARD, REPORT }
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
    private Selection parseSelection(String raw) {
        if(raw==null||raw.isBlank())throw reject(RejectionReason.EMPTY_OUTPUT,false,false);
        if(raw.length()>4096)throw reject(RejectionReason.TOO_LONG,false,false);
        com.fasterxml.jackson.databind.JsonNode tree;
        try { tree=selectionTree.readTree(raw); }
        catch(java.io.IOException e) { throw reject(RejectionReason.INVALID_JSON,false,false); }
        if(tree==null||!tree.isObject())throw reject(RejectionReason.NOT_OBJECT,false,false);
        var keys=tree.fieldNames();
        while(keys.hasNext())if(!Set.of("status","branchId","blockedAt").contains(keys.next()))throw reject(RejectionReason.EXTRA_FIELD,false,false);
        var status=tree.get("status");
        if(status==null||status.isNull())throw reject(RejectionReason.MISSING_STATUS,false,false);
        if(!status.isTextual())throw reject(RejectionReason.INVALID_STATUS_TYPE,false,false);
        if(!Set.of("SELECTED","INSUFFICIENT").contains(status.asText()))throw reject(RejectionReason.INVALID_STATUS,false,false);
        boolean selected=status.asText().equals("SELECTED");
        var target=tree.get(selected?"branchId":"blockedAt");var other=tree.get(selected?"blockedAt":"branchId");
        if(other!=null&&!other.isNull())throw reject(RejectionReason.CONFLICTING_NODES,false,false);
        if(target==null||target.isNull())throw reject(RejectionReason.MISSING_NODE,false,false);
        if(!target.isTextual())throw reject(RejectionReason.INVALID_NODE_TYPE,false,false);
        if(target.asText().isBlank())throw reject(RejectionReason.MISSING_NODE,false,false);
        // Presence and union-arm requirements are explicit above, independent of Jackson annotations.
        try { return converter.convert(raw); }
        catch(RuntimeException e) { throw reject(RejectionReason.CONVERSION_FAILED,false,false); }
    }
    public Decision validate(String raw,Facts facts) {
        Selection selection=parseSelection(raw);
        String selected=selection.status()==Status.SELECTED?selection.branchId():selection.blockedAt();
        Node node=nodes.get(selected);
        if(node==null)throw reject(RejectionReason.UNKNOWN_NODE,true,false);
        if(GUARD_NODE.equals(selected))throw reject(RejectionReason.GUARD_SELECTED,true,false);
        if((selection.status()==Status.SELECTED)!=(node.condition()==null))throw reject(RejectionReason.NODE_KIND_MISMATCH,true,false);
        // One traversal per validation. The path contains no service items and is never a fallback.
        ExpectedPath comparison=expected(facts);
        if(selection.status()!=comparison.status()||!selected.equals(comparison.branchId()))throw reject(RejectionReason.PATH_MISMATCH,true,true);
        try { return assemble(comparison,facts); }
        catch (RuntimeException e) { throw new InternalFailure(FailurePhase.REPORT,e); }
    }
    /** No second traversal: assemble exclusively from the already validated path. */
    private Decision assemble(ExpectedPath path,Facts facts) {
        Node node=nodes.get(path.branchId());
        if(path.status()==Status.INSUFFICIENT && path.missingFields().isEmpty())throw new IllegalStateException("report blocker has no missing fields");
        List<Item> items=path.status()==Status.SELECTED && node.itemKey()!=null ? canonicalItems(node.itemKey(),facts) : List.of();
        return new Decision(path.status(),path.route(),path.branchId(),path.conditionIds(),path.evidenceRefs(),path.missingFields(),items);
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
    private void loadTable(List<Node> table) {
        if(table==null||entry==null||entry.isBlank())throw new IllegalArgumentException("missing table metadata");
        for(Node node:table) {
            if(node==null||node.id()==null||node.id().isBlank()||nodes.putIfAbsent(node.id(),node)!=null)
                throw new IllegalArgumentException("duplicate or missing node id");
        }
        if(GUARD_NODE.equals(entry))throw new IllegalArgumentException("guard cannot be entry");
        if(!nodes.containsKey(entry))throw new IllegalArgumentException("missing entry");
        for(Node node:nodes.values()) {
            // A reserved invariant-failure sentinel, never a business route. It may only be
            // reached on FALSE from a Ready condition; reaching it is a GUARD internal error.
            if(GUARD_NODE.equals(node.id()) && (node.condition()!=null||node.route()!=Route.NO_ACTION||node.itemKey()!=null))
                throw new IllegalArgumentException("invalid guard sentinel");
            if(GUARD_NODE.equals(node.yes()) || GUARD_NODE.equals(node.no()) && (node.condition()==null||!node.condition().endsWith("Ready")))
                throw new IllegalArgumentException("guard must be a false readiness edge");
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
    private void checkDisplay(Decision decision,Facts facts) {
        try {
            if(decision==null||decision.status()==null||decision.branchId()==null||decision.conditionIds()==null
                ||decision.evidenceRefs()==null||decision.missingFields()==null||decision.items()==null)
                throw new IllegalStateException("incomplete display report");
            Node leaf=nodes.get(decision.branchId());
            if(leaf==null||GUARD_NODE.equals(leaf.id())||(decision.status()==Status.SELECTED)!=(leaf.condition()==null)
                ||decision.status()==Status.SELECTED && decision.route()!=leaf.route()
                ||decision.status()==Status.INSUFFICIENT && (decision.route()!=null||!decision.items().isEmpty()))
                throw new IllegalStateException("invalid display branch");
            for(String id:decision.conditionIds()) {
                Node node=nodes.get(id);
                if(node==null||node.condition()==null||facts.conditions().get(node.condition())==null)
                    throw new IllegalStateException("invalid display path");
            }
        } catch(RuntimeException e) { throw new InternalFailure(FailurePhase.REPORT,e); }
    }
    public String display(Decision d,Facts facts) throws java.io.IOException {
        checkDisplay(d,facts);
        StringBuilder out=new StringBuilder("【AI选路实验：已通过四层校验，未执行任何业务操作】\n");
        out.append(d.status()==Status.INSUFFICIENT?"信息不足，无法确定":nodes.get(d.branchId()).description()).append("\n");
        for(String id:d.conditionIds()){
            Node n=nodes.get(id);out.append(id).append("：").append(n.description()).append(" 条件=").append(facts.conditions().get(n.condition()).value()).append("\n");
        }
        return out+"结构化结果：\n"+RoutingInput.newJsonMapper().writerWithDefaultPrettyPrinter().writeValueAsString(d);
    }
}
