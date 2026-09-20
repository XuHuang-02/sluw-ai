# 任务04：全历史统计、状态轨迹与现金信号

2026-09-20：本机实现与相关测试通过。服务不调用数据库、LLM或RAG；当前输出为后续分析模块使用的内部事实摘要，不是最终分析结果或HTTP响应。

## 调用

```java
EddFactService.Prepared prepared = new EddFactService().prepare(adaptation);
EddHistoryService.History history = new EddHistoryService().analyze(prepared);
ObjectNode summary = history.summary();
```

history.prepared()保留任务02/03的全部来源、缺口、范围结论、冲突和原始输入。summary()返回防御性副本。所有统计使用BigDecimal和Java时间类型，不交给LLM计算。

## 输出与口径

- transaction_metrics：按业务类型、金额含义、币种原码、收付方向、状态分别统计。退款和原支付分别保留总额，不互相抵销；pending/cancelled/reversed/unknown独立列示，不能当作已完成金额。缺失金额、币种、方向、金额含义或对应冲突未解决时不进入确定金额统计，保留gaps。
- source_metrics：逐条保留保单/产品/责任/计划/领取等来源金额，以及CLAIMTIMES、ENDORSETIMES、SUPPRISKSCORE等原始指标。保额、保费、已领金额分别展示，不跨层合计、不补齐币种、不换汇、不生成虚构事件，也不解释为反洗钱评分。每项保留fact_refs和粒度；待核实记录的原值仍可展示，范围以prepared.decisions为准。
- event_history：保留全量事件及范围结论、业务状态、发生时间、来源采集时间和归并后的角色，包括非资金保全、排除和重复记录。event_counts只计INCLUDED非重复事件，按业务类型/状态区分。
- observed_event_policy_count与observed_source_policy_count：分别是已提供事件和核心资料中观察到的关联保单数，不把二者相加，不把部分范围计数解释为公司全历史总数。来源保单数仅用于资料背景，不代表所有险种已符合范围。
- coverage原样保留每类查询状态、snapshot/history_events和时间区间；缺口不改成零业务。

明确支持的资金业务：premium_payment、premium_refund、policy_loan、loan_repayment、maturity_payment、surrender_payment、claim_payment、benefit_payment。未知业务保留原始记录并提示待分类，不擅自作为实际支付。underwriting等非支付事件金额只作来源值。

## 现金信号

cash.status分为found、unknown、not_observed_in_available_scope。只要范围内已确认完成、正金额、支付方式为cash且相关字段无阻断冲突，就输出found，其他数据缺口同时披露；没有金额门槛。不因退款抹除历史现金，不自动给出评级。

未支付/取消不证明现金实际发生；冲正、未知支付状态、未知渠道、未确定范围或覆盖不齐会阻止“范围内未见”的结论。cash_indicators单独展示核心PAYMODE=1/2的现金方式线索，不把GETMODE或NEWPAYMODE套用同一码表。十个DB场景均无实际支付明细，因此实际现金结论为unknown。

覆盖优先使用actual_payments作为全部实际支付历史的声明；没有该项时，必须分别提供underwriting、preservation、individual_claims的成功、完整history_events及起止区间。实际支付事件还必须落在其对应声明区间内。该规则只支撑“已提供范围未见”，不宣称客户一生无现金；上游不得把仅保费查询填写为全部actual_payments。

## 状态与签单背景

state_history保留LCCONTSTATE来源行，按STARTDATE和fact_id稳定排列；当前状态按CONTNO/POLNO/INSUREDNO/STATETYPE粒度判断。历史、未来、当前候选及日期边界不明分别记录。日期精度不足以确认截止当天的生效/失效顺序时待核实，不猜测闭区间规则。

同粒度只有一条可用当前候选且无区间疑点时，current_states返回原始STATE；多当前候选、缺时间、无效区间、字段冲突时返回unknown。没有正式整单归并规则，whole_policy_state始终unknown；不从状态码推出客户风险等级，也不按采集/修改时间覆盖历史。

latest_signing按SIGNDATE+SIGNTIME选最新已提供候选；并列、时间缺失、范围/字段冲突不唯一时未知，晚于截止时点不选。只保留最新签单背景候选，不据此裁剪历史，不宣称该保单全部产品都在范围内。

## 验证

```text
mvn "-Dtest=EddInputAdapterTest,EddFactServiceTest,EddHistoryServiceTest" test
python -m unittest discover -s tools/edd -p "test_*.py"
python tools/edd/check_adapter_outputs.py
```

79项Java测试（任务04新增32项）通过；30项Python契约测试通过。万条历史在测试中生成，定位LONG-08765，CNY累计100000.00；无需存储重复的9MB数据。覆盖SYN-V1-001—009/018—020、全部10个DB现金未知、状态重叠/边界、字段冲突、未知币种、非金额事件与保额保费分离。

历史退款关联字段不在冻结v1中，迁移样本时不传related_event_id；当前按独立总额展示，冲正关联规则不足时待核实，不实现净额或自动冲销。日期型覆盖边界使用明确的合成时间约定，见fixture notes。模型效果、并发和真实服务联调仍属后续任务。
