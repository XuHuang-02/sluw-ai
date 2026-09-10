package com.sinosig.sluw.assessment;

import static com.sinosig.sluw.assessment.Model.*;
import java.util.*;
import java.util.function.IntConsumer;

/** Governs the checklist independently of similarity retrieval and chat responses. */
public final class RuleCatalog {
    public enum Layer { COMMON, PRODUCT }
    public record Item(Rule rule, Layer layer, String blockedReason) {}
    public record Proposal(String version, String channel, String product, String scope,
            boolean simulation, boolean commonReviewed, boolean productReviewed, List<Item> items) {}
    public record Draft(String id, String org, String createdBy, String createdAt, Proposal proposal) {}
    public record Confirmation(Draft draft, String confirmedBy, String confirmedAt, String note) {}
    public record Published(Confirmation confirmation, String publishedBy, String publishedAt) {}
    private final RuleStore store;
    private final RuleSources sources;
    private final boolean simulation;

    public RuleCatalog(RuleStore store, RuleSources sources, boolean simulation) {
        this.store = store; this.sources = sources; this.simulation = simulation;
    }
    public Draft create(Actor actor, Proposal proposal) {
        admin(actor); validate(proposal);
        proposal = new Proposal(proposal.version(), proposal.channel(), proposal.product(), proposal.scope(),
                simulation || proposal.simulation(), proposal.commonReviewed(), proposal.productReviewed(), proposal.items());
        // Freeze caller-owned lists via the serialized storage boundary.
        Draft draft = new Draft(UUID.randomUUID().toString(), actor.org(), actor.username(), now(), proposal);
        store.put("drafts", actor.org(), draft.id(), draft);
        return draft(actor, draft.id());
    }
    public Draft draft(Actor actor, String id) {
        identity(actor);
        return store.get("drafts", actor.org(), id, Draft.class);
    }
    public Confirmation confirm(Actor actor, String id, String note) {
        admin(actor); required(note, "确认说明不能为空");
        Draft d = draft(actor, id); validate(d.proposal());
        if (!d.proposal().commonReviewed() || !d.proposal().productReviewed())
            throw new IllegalArgumentException("必须确认公共规则和产品规则检查范围，包括无适用规则的情况");
        if (!simulation && d.proposal().simulation()) throw new IllegalArgumentException("正式环境不能确认模拟规则");
        // Re-fetch each exact source before confirmation; failure never silently drops an item.
        if (!simulation) for (Item item : d.proposal().items()) {
            Source s = item.rule().source();
            if (!sources.fetch(new RuleSources.Ref(s.datasetId(), s.documentId(), s.chunkId())).equals(s))
                throw new IllegalArgumentException("规则原文已变化，请新建草稿并重新确认");
        }
        Confirmation confirmation = new Confirmation(d, actor.username(), now(), note);
        store.put("confirmed", actor.org(), id, confirmation);
        return confirmation;
    }
    public Published publish(Actor actor, String id) {
        admin(actor);
        Confirmation confirmation = store.get("confirmed", actor.org(), id, Confirmation.class);
        if (!simulation && confirmation.draft().proposal().simulation())
            throw new IllegalArgumentException("正式环境不能发布模拟规则");
        Published published = new Published(confirmation, actor.username(), now());
        store.put("versions", actor.org(), key(confirmation.draft().proposal()), published);
        return published;
    }
    public Published version(Actor actor, String channel, String product, String version) {
        identity(actor);
        return store.get("versions", actor.org(), key(channel, product, version), Published.class);
    }
    public Source importSource(Actor actor, RuleSources.Ref ref) { admin(actor); return sources.fetch(ref); }

    public Report evaluate(Actor actor, String version, Input input, List<Upload> uploads,
            List<Extraction> extractions, RuleEngine.Services services, IntConsumer progress) {
        Published p = version(actor, input.channel(), input.product(), version);
        Confirmation c = p.confirmation(); Proposal proposal = c.draft().proposal();
        Map<String, String> blocked = new HashMap<>();
        proposal.items().forEach(i -> { if (!RuleEngine.blank(i.blockedReason())) blocked.put(i.rule().id(), i.blockedReason()); });
        RuleSet set = new RuleSet(proposal.version(), proposal.channel(), proposal.product(),
                simulation || proposal.simulation(), true, c.confirmedBy(), c.confirmedAt(), proposal.scope(),
                proposal.items().stream().map(Item::rule).toList());
        RuleEngine.Services pinned = new RuleEngine.Services() {
            public boolean simulation() { return services.simulation(); }
            public boolean verify(Source source) { return RuleSources.valid(source); }
            public Extraction extract(Upload upload) { return services.extract(upload); }
            public String review(Rule rule, Input i, List<Extraction> e) { return services.review(rule, i, e); }
        };
        return new RuleEngine().evaluate(input, set, uploads, extractions, pinned, progress, blocked);
    }
    private void validate(Proposal p) {
        if (p == null) throw new IllegalArgumentException("规则草稿不能为空");
        required(p.version(), "版本不能为空"); required(p.channel(), "渠道不能为空");
        required(p.product(), "产品不能为空"); required(p.scope(), "检查范围不能为空");
        if (p.items() == null || p.items().isEmpty()) throw new IllegalArgumentException("必查清单不能为空");
        Set<String> ids = new HashSet<>(); Set<List<String>> branches = new HashSet<>();
        for (Item i : p.items()) {
            if (i == null || i.rule() == null || i.layer() == null) throw new IllegalArgumentException("检查项及规则层级不能为空");
            Rule r = i.rule(); required(r.id(), "检查项ID不能为空"); required(r.code(), "编码不能为空"); required(r.branch(), "分支不能为空");
            if (!ids.add(r.id()) || !branches.add(List.of(r.code(), r.branch()))) throw new IllegalArgumentException("检查项或编码分支重复");
            if (!RuleSources.valid(r.source())) throw new IllegalArgumentException("规则来源或内容指纹无效");
            if (RuleEngine.blank(i.blockedReason())) new RuleEngine().validate(r);
        }
    }
    static void identity(Actor actor) {
        if (actor == null || RuleEngine.blank(actor.org()) || RuleEngine.blank(actor.username()) || actor.role() == null)
            throw new SecurityException("需要有效身份");
    }
    static void admin(Actor actor) { identity(actor); if (actor.role() != Role.ADMIN) throw new SecurityException("需要管理员权限"); }
    private static void required(String s, String reason) { if (RuleEngine.blank(s)) throw new IllegalArgumentException(reason); }
    private static String key(Proposal p) { return key(p.channel(), p.product(), p.version()); }
    private static String key(String channel, String product, String version) {
        required(channel, "渠道不能为空"); required(product, "产品不能为空"); required(version, "版本不能为空");
        return channel.length() + ":" + channel + product.length() + ":" + product + version.length() + ":" + version;
    }
}
