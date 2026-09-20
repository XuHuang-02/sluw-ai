# 任务05：规则包版本与建议判定基础

2026-09-20本机完成。默认注册表为空，没有内置正式评级或上报映射。现金只作为已确认需求中的风险增加因素；没有正式映射时proposed_grade=null。此模块只产出建议，无等级回写、报告提交或其他执行入口。

## 接口

```java
// 服务端受控部署流程提供JSON，不从客户请求、附件或RAG结果装载规则。
EddRulePackage rules = EddRulePackage.load(serverManagedJson);
EddRuleService engine = new EddRuleService(List.of(rules), EddRuleService.Mode.PRODUCTION);
EddRuleService.Evaluation evaluated = engine.evaluate(history);
ObjectNode result = evaluated.result();
```

缺正式规则时直接使用new EddRuleService()。history来自任务04，result()返回防御性副本。客户请求的rule_context只能指定rule_package_id和expected_version，不能携带规则正文或将“有规则”布尔值当作批准依据。精确匹配ID和版本，不默认使用最新版本；同版本重复注册拒绝。

## 规则包结构

以下仅为演示引擎分支的测试规则，绝不是业务规则；test_only=true在PRODUCTION模式拒绝使用。

```json
{
  "id": "TEST-ONLY",
  "version": "1",
  "approval": "approved",
  "approval_ref": "SYN-APPROVAL",
  "approved_by": "SYN-REVIEWER",
  "approved_at": "2026-01-01T00:00:00+08:00",
  "valid_from": "2026-01-01T00:00:00+08:00",
  "valid_to": "2027-01-01T00:00:00+08:00",
  "test_only": true,
  "scope": {"subject_type": "natural_person", "triggers": ["risk_review"]},
  "rules": [{
    "id": "SYN-GRADE",
    "target": "grade",
    "value": "high",
    "clause_ref": "synthetic://tests/grade",
    "conditions": [{"fact": "cash_status", "operator": "eq", "expected": "found"}]
  }]
}
```

所有键显式提供；未知字段、重复JSON键、重复规则ID、无条件规则、未知事实名、任意脚本/操作符、无批准凭据的approved包直接拒绝。包最大1MiB、最多1000条规则，每条最多32个条件。ID及版本使用字母、数字、下划线、点、横线。

approval为approved/draft/rejected；批准时approved_by、approved_at、approval_ref必填。valid_to允许null；有效期为[valid_from, valid_to)，批准时间不得晚于本次analysis_as_of。scope固定自然人，可列具体trigger或显式“*”。当前输入没有机构等适用范围字段，尚不支持按机构限定规则，不能把这类正式规则直接简化为全公司适用。

批准凭据字段只是部署资料，并不自行证明批准真实性。后续正式规则由受控服务端配置渠道核实并加载；目前不实现审批系统、电子签名校验、热更新或远程拉取。重建不可变注册表用于切换版本，旧分析继续保留其版本引用。

## 事实依赖与判定

首期只开放三个确定性事实依赖，条件为eq，多个条件按AND求值；不解析附件文本、自然语言规则或模型输出作为可执行条件：

| 事实 | 可比较值 | 不足时 |
| --- | --- | --- |
| cash_status | found / not_observed_in_available_scope | unknown，不能匹配“未见” |
| current_grade | low / medium / high / highest | 缺明确当前记录、冲突、范围不明则未知 |
| historical_suspicious_report | found / not_observed_in_available_scope | 主体、上报事实或覆盖不齐则未知 |

current_grade只读取risk_grade/risk_grade_change中明确facts.current=true且facts.grade有效的已纳入记录，尊重机构采用的冲突候选。不用最新采集时间或历史最大等级推断当前等级。grade_history保留原始记录及来源，current_grade和grade_recommendation独立表达。

历史上报要求已纳入记录的type=suspicious_report、facts.reported=true；存在已确认历史上报即found，未见则要求historical_suspicious_reports覆盖成功、完整、risk_information及起止时间，并无未决记录。此处“未见”仅限声明范围，绝不表示一生未上报。原始历史记录保留在historical_reports，本次report_recommendation另行评估。

每条规则输出hit/not_hit/unknown、规则版本引用、clause_ref、fact_refs、missing_inputs。AND中任一已知条件不符即可not_hit；否则有未知条件则unknown。未知不当作“否”。

同一建议目标：多条命中结果相同可保留；命中不同结果则requires_institution_review；任一仍有可能命中的规则依赖未知，则insufficient_evidence；无目标映射或无匹配规则则insufficient_rules。没有默认最低风险、自动升一级、优先选最高等级等隐含策略。

目标grade和report独立处理。report可为suggest_report/suggest_not_report，上报建议不自动改变等级，历史上报也不触发默认上报建议。风险因素规则target=risk_factor只接受risk_increasing，不直接改变建议。正式业务规则若依赖其他字段、复杂条件或优先级，需要在取得并确认规则后扩展事实接口及评测，不能用现有有限条件冒充完整规则集。

## 输出边界

内部结果包括rule_package、rule_evaluations、missing_inputs、grade_history、current_grade、historical_reports、historical_report_status、grade_recommendation、report_recommendation、proposed_grade、test_only及execution_permitted=false。

grade/report建议片段遵守冻结v1的status/value/reason/fact_refs/rule_refs；整体是内部评估结果，后续任务08/10再装配最终result。内置requirement.cash-risk-factor.v1标记basis_kind=confirmed_requirement，不伪称正式评级条文；后续证据索引须保留该区别。

测试包只允许Mode.TEST且synthetic=true，所有测试模式结果test_only=true。测试规则只在JUnit源码及本说明中存在，生产资源没有默认测试包。调用方后续持久化或展示时必须保留test_only标记。

## 验证

```text
mvn "-Dtest=EddInputAdapterTest,EddFactServiceTest,EddHistoryServiceTest,EddRuleServiceTest" test
python -m unittest discover -s tools/edd -p "test_*.py"
python tools/edd/check_rule_outputs.py
python tools/edd/check_adapter_outputs.py
```

122项Java测试通过，任务05含43项；30项Python测试通过。32个既有合成场景（22个业务场景及10个DB场景）缺正式规则时均无确定目标等级，实际Java建议片段也通过v1校验。另覆盖未批准、起止边界、事后批准、范围不符、版本不匹配、缺依赖、事实/规则冲突、三态AND、测试隔离及历史/本次建议分离。

32场景复用已有小型fixture，新增rule-extra-cases.json补齐5个风险/人工材料场景，万条历史在测试中生成。合成日期补齐约定详见fixture notes；不能将迁移逻辑当作生产输入适配。

本轮没有真实AI/RAG运行，也不代表正式业务规则已交付或通过机构验收。代码及文档随任务04—06统一交付。
