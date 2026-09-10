package com.sinosig.sluw.assessment;

import static com.sinosig.sluw.assessment.Model.*;
import static com.sinosig.sluw.assessment.RuleCatalog.*;
import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class RuleCatalogTest {
    @TempDir Path dir;
    final ObjectMapper mapper = new ObjectMapper();
    final Actor admin = new Actor("business-admin", "org-a", Role.ADMIN);
    final Actor reviewer = new Actor("reviewer", "org-a", Role.REVIEWER);
    final Actor other = new Actor("other-admin", "org-b", Role.ADMIN);
    static Source source(String id) { return new Source("dataset", "doc", id, "synthetic rule " + id, RuleSources.hash("synthetic rule " + id)); }
    static Rule rule(String id, String branch) {
        return new Rule(id, "YBCR0045", branch, "synthetic required field", Kind.REQUIRED_FACT,
                Condition.ALWAYS, null, List.of(), null, null, "field-" + id, null,
                null, null, null, null, null, "请核实", source(id));
    }
    static Proposal proposal(String version, List<Item> items) {
        return new Proposal(version, "channel", "product", "synthetic complete checklist", false, true, true, items);
    }
    static List<Item> items() { return List.of(new Item(rule("a","under10"), Layer.COMMON, null),
            new Item(rule("b","10to18"), Layer.PRODUCT, null)); }
    RuleCatalog catalog(boolean simulation) { return new RuleCatalog(new RuleStore(dir, mapper), r -> source(r.chunkId()), simulation); }
    Draft published(RuleCatalog c, Proposal p) {
        Draft d = c.create(admin, p); c.confirm(admin, d.id(), "已确认范围及全部分支"); c.publish(admin,d.id()); return d;
    }
    Input input() { return new Input("case", "channel", "product", "insured", "applicant", false,
            Map.of("field-a", new Fact("ok","input",true,false,null,null,null,null,null,null),
                   "field-b", new Fact("ok","input",true,false,null,null,null,null,null,null)),
            List.of(), new Manifest(false,"",""), ""); }
    RuleEngine.Services services() { return new RuleEngine.Services() {
        public boolean simulation() { return false; }
        public boolean verify(Source s) { throw new AssertionError("must use pinned source"); }
        public Extraction extract(Upload u) { throw new AssertionError(); }
        public String review(Rule r, Input i, List<Extraction> e) { return "review"; }
    }; }
    Report evaluate(RuleCatalog c, String version) { return c.evaluate(reviewer,version,input(),List.of(),List.of(),services(), i -> {}); }

    @Test void draftsCannotBeEvaluatedOrPublishedWithoutConfirmation() {
        RuleCatalog c=catalog(true); Draft d=c.create(admin,proposal("v1",items()));
        assertThrows(NoSuchElementException.class,()->evaluate(c,"v1"));
        assertThrows(NoSuchElementException.class,()->c.publish(admin,d.id()));
    }
    @Test void confirmationUsesAuthenticatedActorAndPersistsAcrossRestart() {
        RuleCatalog c=catalog(false); published(c,proposal("v1",items()));
        Published p=catalog(false).version(reviewer,"channel","product","v1");
        assertEquals("business-admin",p.confirmation().confirmedBy());
        assertFalse(p.confirmation().draft().proposal().simulation());
        assertNotNull(p.publishedAt());
        assertEquals(Verdict.NON_PROBLEM,evaluate(catalog(false),"v1").verdict());
    }
    @Test void sharedCodeBranchesAndLayersArePreserved() {
        RuleCatalog c=catalog(true); published(c,proposal("v1",items()));
        Report r=evaluate(c,"v1");
        assertEquals(List.of("under10","10to18"),r.checks().stream().map(Check::branch).toList());
        assertEquals(List.of(Layer.COMMON,Layer.PRODUCT),c.version(admin,"channel","product","v1").confirmation().draft().proposal().items().stream().map(Item::layer).toList());
    }
    @Test void duplicateVersionCannotOverwritePreviousRelease() {
        RuleCatalog c=catalog(true); Draft first=published(c,proposal("v1",items()));
        Draft next=c.create(admin,proposal("v1",List.of(items().get(0))));
        c.confirm(admin,next.id(),"second");
        assertThrows(IllegalStateException.class,()->c.publish(admin,next.id()));
        assertEquals(first.id(),c.version(admin,"channel","product","v1").confirmation().draft().id());
    }
    @Test void newVersionDoesNotChangeOldVersion() {
        RuleCatalog c=catalog(true); published(c,proposal("v1",items()));
        published(c,proposal("v2",List.of(items().get(0))));
        assertEquals(2,evaluate(c,"v1").checks().size()); assertEquals(1,evaluate(c,"v2").checks().size());
    }
    @Test void duplicateConfirmationCannotOverwriteAudit() {
        RuleCatalog c=catalog(true); Draft d=c.create(admin,proposal("v1",items()));
        c.confirm(admin,d.id(),"first"); assertThrows(IllegalStateException.class,()->c.confirm(admin,d.id(),"second"));
        assertEquals("first",c.publish(admin,d.id()).confirmation().note());
    }
    @Test void blockedItemKeepsReasonAndOtherChecksContinue() {
        RuleCatalog c=catalog(true);
        published(c,proposal("v1",List.of(new Item(rule("a","under10"),Layer.COMMON,"年龄基准日未确认"),items().get(1))));
        Report r=evaluate(c,"v1");
        assertEquals(Verdict.UNDETERMINED,r.verdict());
        assertEquals(List.of(CheckStatus.INCOMPLETE,CheckStatus.PASS),r.checks().stream().map(Check::status).toList());
        assertTrue(r.checks().get(0).reason().contains("年龄基准日未确认"));
    }
    @Test void explicitConflictIsNotAutomaticallyOverriddenByProductRule() {
        RuleCatalog c=catalog(true);
        published(c,proposal("v1",List.of(new Item(rule("a","under10"),Layer.COMMON,"与产品规则冲突，覆盖关系未确认"),
                new Item(rule("b","10to18"),Layer.PRODUCT,"与公共规则冲突，覆盖关系未确认"))));
        assertTrue(evaluate(c,"v1").checks().stream().allMatch(x->x.status()==CheckStatus.INCOMPLETE));
    }
    @Test void incompleteExecutableConditionRequiresExplicitBlockedReason() {
        Rule incomplete=new Rule("a","code","branch","title",null,null,null,null,null,null,null,null,null,null,null,null,null,null,source("a"));
        assertThrows(IllegalArgumentException.class,()->catalog(true).create(admin,proposal("v1",List.of(new Item(incomplete,Layer.COMMON,null)))));
        RuleCatalog c=catalog(true); published(c,proposal("v1",List.of(new Item(incomplete,Layer.COMMON,"仅有提示话术"))));
        assertEquals(Verdict.UNDETERMINED,evaluate(c,"v1").verdict());
    }
    @Test void problemWinsWhileBlockedCheckIsRetained() {
        Rule amount=new Rule("a","amount","max","amount",Kind.MAX_AMOUNT,Condition.ALWAYS,null,List.of(),null,null,
                "field-a","20","INSURED","risk","CNY","basis",true,"核实",source("a"));
        RuleCatalog c=catalog(true); published(c,proposal("v1",List.of(new Item(amount,Layer.COMMON,null),
                new Item(rule("b","other"),Layer.PRODUCT,"缺少口径"))));
        Input i=input();
        i=new Input(i.caseId(),i.channel(),i.product(),i.insuredId(),i.applicantId(),true,
                Map.of("field-a",new Fact("21","input",true,false,"insured","risk","2026-09-10",true,"CNY","basis")),
                i.materials(),i.manifest(),i.history());
        Report r=c.evaluate(reviewer,"v1",i,List.of(),List.of(),services(), n->{});
        assertEquals(Verdict.PROBLEM,r.verdict()); assertEquals(CheckStatus.INCOMPLETE,r.checks().get(1).status());
    }
    @Test void checklistScopeMustBeExplicitlyConfirmed() {
        Proposal p=proposal("v1",items()); RuleCatalog c=catalog(true);
        Draft d=c.create(admin,new Proposal(p.version(),p.channel(),p.product(),p.scope(),false,false,true,p.items()));
        assertThrows(IllegalArgumentException.class,()->c.confirm(admin,d.id(),"not enough"));
    }
    @Test void duplicateBusinessBranchRejectedButDistinctBranchesAccepted() {
        assertDoesNotThrow(()->catalog(true).create(admin,proposal("ok",items())));
        assertThrows(IllegalArgumentException.class,()->catalog(true).create(admin,proposal("bad",
                List.of(items().get(0),new Item(rule("b","under10"),Layer.PRODUCT,null)))));
    }
    @Test void emptyChecklistRejected() { assertThrows(IllegalArgumentException.class,()->catalog(true).create(admin,proposal("v",List.of()))); }
    @Test void reviewerCannotCreateConfirmPublishOrImport() {
        RuleCatalog c=catalog(true); Draft d=c.create(admin,proposal("v",items()));
        assertThrows(SecurityException.class,()->c.create(reviewer,proposal("v",items())));
        assertThrows(SecurityException.class,()->c.confirm(reviewer,d.id(),"x"));
        assertThrows(SecurityException.class,()->c.publish(reviewer,d.id()));
        assertThrows(SecurityException.class,()->c.importSource(reviewer,new RuleSources.Ref("d","d","d")));
    }
    @Test void institutionsCannotReadConfirmOrPublishEachOthersRecords() {
        RuleCatalog c=catalog(true); Draft d=published(c,proposal("v1",items()));
        assertThrows(NoSuchElementException.class,()->c.draft(other,d.id()));
        assertThrows(NoSuchElementException.class,()->c.confirm(other,d.id(),"x"));
        assertThrows(NoSuchElementException.class,()->c.publish(other,d.id()));
        assertThrows(NoSuchElementException.class,()->c.version(other,"channel","product","v1"));
    }
    @Test void channelProductAndVersionAreExactNotLatestFallback() {
        RuleCatalog c=catalog(true); published(c,proposal("v1",items()));
        assertThrows(NoSuchElementException.class,()->c.version(admin,"other","product","v1"));
        assertThrows(NoSuchElementException.class,()->c.version(admin,"channel","other","v1"));
        assertThrows(NoSuchElementException.class,()->c.version(admin,"channel","product","v2"));
    }
    @Test void tamperedStorageFailsClosed() throws Exception {
        RuleCatalog c=catalog(true); published(c,proposal("v1",items()));
        try(var paths=Files.walk(dir.resolve("versions"))) {
            Path file=paths.filter(Files::isRegularFile).findFirst().orElseThrow();
            String s=Files.readString(file); Files.writeString(file,s.replace("synthetic complete checklist","tampered scope"));
        }
        assertThrows(IllegalStateException.class,()->evaluate(c,"v1"));
    }
    @Test void sourceHashMustMatch() {
        Rule r=rule("a","under10");
        Rule bad=new Rule(r.id(),r.code(),r.branch(),r.title(),r.kind(),r.condition(),r.conditionFact(),r.values(),r.minAge(),r.maxAge(),
                r.field(),r.limit(),r.personRole(),r.amountType(),r.currency(),r.basis(),r.includesCurrent(),r.advice(),
                new Source("dataset","doc","a","altered",r.source().sha256()));
        assertThrows(IllegalArgumentException.class,()->catalog(true).create(admin,proposal("v",List.of(new Item(bad,Layer.COMMON,null)))));
    }
    @Test void sourceChangedBeforeConfirmationRequiresNewDraft() {
        RuleCatalog c=new RuleCatalog(new RuleStore(dir,mapper),ref->source("changed"),false);
        Draft d=c.create(admin,proposal("v",items()));
        assertThrows(IllegalArgumentException.class,()->c.confirm(admin,d.id(),"confirmed"));
    }
    @Test void outageBeforeConfirmationDoesNotPublishPartialChecklist() {
        RuleCatalog c=new RuleCatalog(new RuleStore(dir,mapper),ref->{throw new RuleSources.SourceException(RuleSources.Failure.UNAVAILABLE);},false);
        Draft d=c.create(admin,proposal("v",items()));
        assertThrows(RuleSources.SourceException.class,()->c.confirm(admin,d.id(),"confirmed"));
        assertThrows(NoSuchElementException.class,()->c.publish(admin,d.id()));
    }
    @Test void publishedSnapshotSurvivesRagflowChangeAndOutage() {
        RuleCatalog c=catalog(false); published(c,proposal("v1",items()));
        RuleCatalog offline=new RuleCatalog(new RuleStore(dir,mapper),ref->{throw new AssertionError("do not fetch latest");},false);
        assertEquals(Verdict.NON_PROBLEM,evaluate(offline,"v1").verdict());
    }
    @Test void simulationMarkerSurvivesRestartInFormalEnvironment() {
        RuleCatalog c=catalog(true); published(c,proposal("v1",items()));
        assertTrue(evaluate(catalog(false),"v1").simulation());
    }
    @Test void formalEnvironmentCannotPublishPreviouslySimulatedConfirmation() {
        RuleCatalog c=catalog(true); Draft d=c.create(admin,proposal("v",items())); c.confirm(admin,d.id(),"x");
        assertThrows(IllegalArgumentException.class,()->catalog(false).publish(admin,d.id()));
    }
}
