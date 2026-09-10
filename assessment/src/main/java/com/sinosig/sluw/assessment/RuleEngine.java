package com.sinosig.sluw.assessment;

import static com.sinosig.sluw.assessment.Model.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.util.*;

/** No model call can override completeness, evidence requirements or the final verdict. */
public final class RuleEngine {
    public interface Services {
        /** Unconfigured adapters are conservatively labelled as simulated. */
        default boolean simulation() { return true; }
        boolean verify(Source source);
        Extraction extract(Upload upload);
        String review(Rule rule, Input input, List<Extraction> extractions);
    }

    public Report evaluate(Input input, RuleSet set, List<Upload> uploads,
            List<Extraction> extractions, Services services, java.util.function.IntConsumer progress) {
        List<Check> checks = new ArrayList<>();
        List<String> notices = new ArrayList<>();
        boolean scopeMatches = !blank(set.channel()) && !blank(set.product())
                && set.channel().equals(input.channel()) && set.product().equals(input.product());
        boolean versionIdentified = !blank(set.version()) && !blank(set.confirmedBy())
                && !blank(set.confirmedAt()) && !blank(set.scope());
        Set<String> duplicateIds = duplicates(set.rules().stream().map(Rule::id).toList());
        Set<List<String>> duplicateBranches = duplicates(set.rules().stream()
                .map(r -> Arrays.asList(r.code(), r.branch())).toList());
        boolean coverage = set.coverageConfirmed() && !set.rules().isEmpty() && scopeMatches
                && versionIdentified && duplicateIds.isEmpty() && duplicateBranches.isEmpty();
        if (!coverage) notices.add("适用必查清单、版本或范围未确认，或分支标识重复，不能判定为非问题件。");
        for (Rule rule : set.rules()) {
            List<Evidence> evidence = new ArrayList<>();
            Check check;
            try {
                require(scopeMatches, "本规则集与保单渠道或产品不匹配，未执行检查。");
                require(versionIdentified, "规则版本或确认记录缺失，未执行检查。");
                require(!duplicateIds.contains(rule.id())
                        && !duplicateBranches.contains(Arrays.asList(rule.code(), rule.branch())),
                        "规则分支标识重复，不能确定检查范围。");
                validate(rule);
                check = evaluateOne(input, rule, uploads, extractions, services, evidence);
            } catch (IncompleteCheck e) {
                check = result(rule, CheckStatus.INCOMPLETE, e.getMessage(), evidence);
            } catch (Exception e) {
                // Never put raw service exceptions (which may contain data or credentials) in a report.
                check = result(rule, CheckStatus.INCOMPLETE, "资料格式、规则条件或外部服务异常，需核实。", evidence);
            }
            checks.add(check);
            progress.accept(checks.size());
        }
        boolean simulation = input.synthetic() || set.simulation() || services.simulation()
                || extractions.stream().anyMatch(Extraction::simulation);
        if (simulation) notices.add("包含合成资料、模拟规则或模拟服务，仅用于功能验证，不是正式业务判断。");
        return new Report(aggregate(checks, coverage), simulation, set,
                List.copyOf(checks), List.copyOf(notices), null);
    }

    public static Verdict aggregate(List<Check> checks, boolean coverageConfirmed) {
        if (checks.stream().anyMatch(c -> c.status() == CheckStatus.PROBLEM)) return Verdict.PROBLEM;
        if (!coverageConfirmed || checks.isEmpty() || checks.stream().anyMatch(c -> c.status() == CheckStatus.INCOMPLETE))
            return Verdict.UNDETERMINED;
        return Verdict.NON_PROBLEM;
    }

    private Check evaluateOne(Input input, Rule r, List<Upload> uploads,
            List<Extraction> extractions, Services services, List<Evidence> evidence) {
        if (!services.verify(r.source())) return result(r, CheckStatus.INCOMPLETE, "无法核对固定版本的规则原文与切片，未执行此项。", evidence);
        if (!applies(input, r, evidence)) return result(r, CheckStatus.NOT_APPLICABLE, "不符合本规则的适用条件。", evidence);
        if (r.kind() == Kind.AI_REVIEW) {
            String note = services.review(r, input, extractions);
            return result(r, CheckStatus.INCOMPLETE, "需人工核实，模型建议不构成规则命中：" + note, evidence);
        }
        if (r.kind() == Kind.REQUIRED_DOCUMENT) {
            Material m = input.materials().stream().filter(x -> r.field().equals(x.type())).findFirst().orElse(null);
            if (m != null && "NOT_SUBMITTED".equals(m.submissionStatus()) && input.manifest().reliable()) {
                evidence.add(new Evidence("manifest/" + m.id(), input.manifest().declaredBy() + ": " + input.manifest().statement()));
                return result(r, CheckStatus.PROBLEM, "可靠材料清单确认必交材料未提交。", evidence);
            }
            if (m == null) return result(r, CheckStatus.INCOMPLETE, "本次未获取所需材料，不能认定客户缺件。", evidence);
            Upload upload = uploads.stream().filter(x -> x.id().equals(m.id())).findFirst().orElse(null);
            Extraction ex = extractions.stream().filter(x -> x.uploadId().equals(m.id())).findFirst().orElse(null);
            if (upload == null || ex == null || !"READABLE".equals(ex.state()))
                return result(r, CheckStatus.INCOMPLETE, "材料未上传或无法识别，需补充可读材料。", evidence);
            evidence.add(new Evidence("material/" + upload.id() + "/pages/1-" + upload.pages(), upload.sha256()));
            return result(r, CheckStatus.PASS, "材料已获取并可读；本项仅核查材料存在与可读性。", evidence);
        }
        Fact fact = fact(input, r.field(), evidence);
        if (r.kind() == Kind.REQUIRED_FACT) return result(r, CheckStatus.PASS, "所需字段完整且无已标记冲突。", evidence);
        String person = "APPLICANT".equals(r.personRole()) ? input.applicantId() : input.insuredId();
        if (blank(person) || !Objects.equals(person, fact.personId()) || !Objects.equals(r.amountType(), fact.amountType())
                || !Objects.equals(r.currency(), fact.currency()) || !Objects.equals(r.basis(), fact.basis())
                || !Objects.equals(r.includesCurrent(), fact.includesCurrent()) || blank(fact.asOf()))
            return result(r, CheckStatus.INCOMPLETE, "保额所属人员、类型、币种、时点或计算口径不满足本规则。", evidence);
        LocalDate.parse(fact.asOf());
        BigDecimal amount = new BigDecimal(fact.value());
        BigDecimal limit = new BigDecimal(r.limit());
        if (amount.signum() < 0) throw new IllegalArgumentException("negative amount");
        return result(r, amount.compareTo(limit) > 0 ? CheckStatus.PROBLEM : CheckStatus.PASS,
                "风险保额 " + amount.toPlainString() + "，限额 " + limit.toPlainString() + " " + r.currency() + "（等于限额不超限）。", evidence);
    }

    private boolean applies(Input input, Rule r, List<Evidence> evidence) {
        if (r.condition() == Condition.ALWAYS) return true;
        if (r.condition() == Condition.IN_SET) {
            Fact f = fact(input, r.conditionFact(), evidence);
            return r.values().contains(f.value());
        }
        LocalDate birth = LocalDate.parse(fact(input, "insuredBirthDate", evidence).value());
        LocalDate base = LocalDate.parse(fact(input, r.conditionFact(), evidence).value());
        if (base.isBefore(birth)) throw new IllegalArgumentException("invalid age");
        int age = Period.between(birth, base).getYears();
        return age >= r.minAge() && age < r.maxAge();
    }
    private Fact fact(Input i, String name, List<Evidence> evidence) {
        Fact f = i.facts().get(name);
        if (f == null || blank(f.value()) || blank(f.source()) || !f.complete() || f.conflict())
            throw new IncompleteCheck("字段 " + name + " 缺失、不完整、缺少来源或存在冲突，需核实。");
        evidence.add(new Evidence("facts/" + name + " @ " + f.source(), f.value()));
        return f;
    }
    private Check result(Rule r, CheckStatus s, String why, List<Evidence> evidence) {
        return new Check(r.id(), r.code(), r.branch(), s, why, r.advice(), r.source(), List.copyOf(evidence));
    }

    /** Validate executable conditions before deciding a rule is not applicable. */
    private void validate(Rule r) {
        require(!blank(r.id()) && !blank(r.code()) && !blank(r.branch()), "规则或条件分支标识缺失。");
        require(r.kind() != null && r.condition() != null, "规则检查类型或适用条件缺失。");
        Source source = r.source();
        require(source != null && !blank(source.datasetId()) && !blank(source.documentId())
                && !blank(source.chunkId()) && !blank(source.text()) && !blank(source.sha256()),
                "规则来源或原文版本标识不完整。");
        if (r.condition() != Condition.ALWAYS) require(!blank(r.conditionFact()), "适用条件字段缺失。");
        if (r.condition() == Condition.AGE_RANGE) {
            require(r.minAge() != null && r.maxAge() != null && r.minAge() >= 0
                    && r.maxAge() > r.minAge(), "年龄适用区间无效或缺失。");
        }
        if (r.condition() == Condition.IN_SET) {
            require(r.values() != null && !r.values().isEmpty() && r.values().stream().noneMatch(RuleEngine::blank),
                    "人群代码映射未提供。");
        }
        if (r.kind() != Kind.AI_REVIEW) require(!blank(r.field()), "待检查字段或材料类型缺失。");
        if (r.kind() == Kind.MAX_AMOUNT) {
            require(!blank(r.limit()) && new BigDecimal(r.limit()).signum() >= 0, "风险保额限额无效或缺失。");
            require("INSURED".equals(r.personRole()) || "APPLICANT".equals(r.personRole()), "保额所属人员角色未明确。");
            require(!blank(r.amountType()) && !blank(r.currency()) && !blank(r.basis())
                    && r.includesCurrent() != null, "风险保额类型、币种或计算口径未明确。");
        }
    }

    private static <T> Set<T> duplicates(List<T> values) {
        Set<T> seen = new HashSet<>();
        Set<T> duplicate = new HashSet<>();
        for (T value : values) if (!seen.add(value)) duplicate.add(value);
        return duplicate;
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IncompleteCheck(message);
    }
    private static final class IncompleteCheck extends RuntimeException {
        private IncompleteCheck(String message) { super(message); }
    }
    static boolean blank(String s) { return s == null || s.isBlank(); }
}
