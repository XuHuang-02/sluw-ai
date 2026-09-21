# 任务08：结果校验与交付边界

任务08由 `EddResultValidator` 独立校验器和 `EddDraftService.analyze(evaluation, retrieval)` 共用生成流程组成。继续输入结构化JSON，复用任务03—06事实、统计、规则和检索结果，不新增取数、模型客户端或规则计算。

## 调用与状态

```java
var result = drafts.analyze(ruleEvaluation, verifiedRetrievalResult);
```

`generate(...)` 仍是任务07低层草稿入口，只能返回DRAFT等中间状态；不会返回COMPLETED。业务集成与另机验证应使用 `analyze(...)`。独立校验器的输入须先经过任务07的DTO、六维结构与引用检查；不要用它代替JSON结构校验。

| 内部状态 | 含义 |
| --- | --- |
| COMPLETED | 已通过本期程序校验，没有显式资料缺项或待复核项 |
| COMPLETED_WITH_GAPS | 已通过本期程序校验，保留资料缺项或待复核事项 |
| VALIDATION_FAILED | 结构、引用或事实/规则检查未通过，draft为空，保留facts和错误 |
| TIMEOUT / BUSY / MODEL_ERROR / CONTEXT_LIMIT / INTERRUPTED | 沿用技术故障语义，不伪装成资料不足 |

这些是内部Java结果状态，尚不是任务10的完整v1 HTTP响应封装。完成只指AI分析流程完成，不代表机构审批；`execution_permitted=false`始终保留。通过校验时 `requires_validation=false`，同时 `requires_institution_review=true`、`semantic_entailment_verified=false`。无正式规则时不会生成确定建议等级。

## 数量事实的证据绑定

只比较“数字是否出现在输入”不能证明正确。模型在text/reason内使用以下标记，程序使用本次已核定的事实值渲染完整口径，不采用模型算术或模型自评。

| 标记 | 数据和渲染方式 |
| --- | --- |
| `{{metric:0}}` | business_summary.transaction_metrics零起始下标；填入交易类型、金额含义、资金方向、状态、金额、币种、统计粒度 |
| `{{source_metric:0}}` | source_metrics零起始下标；明确标注原始快照值，不代表实际交易 |
| `{{event:原始fact_id}}` | 已纳入且无冲突事件；填入类型、状态、时间、金额、币种和资金方向 |
| `{{risk:原始fact_id}}` | 已纳入且无冲突风险记录的类型和时间 |
| `{{coverage:underwriting}}` | coverage完整键；填入查询状态、完整性及实际覆盖区间，不要求事实引用 |
| `{{count:premium_payment/completed}}` | event_counts完整键；填入该类型/状态的条数 |

除coverage外，每个标记必须与同段fact_refs相配：金额汇总和数量须引用完整统计证据；原始事件须引用对应事实。任务07的组句柄会先展开为完整原始引用。未知标记、缺引用、未纳入事实、冲突或非机构采用事实不能渲染。

模型不要在正文自行书写阿拉伯数字、日期、金额或带单位的中文数量；这些表述返回 `UNBOUND_QUANTITATIVE_CLAIM`，允许使用标记定向修复。这里采用受约束输出，而不是声称能从任意中文句子中准确提取数值和统计口径。程序填入的内部口径名称暂保留原字段含义，面向机构的排版属于后续展示工作。

## 业务边界检查

- 引用不存在或不属于允许的规则范围，拒绝交付。
- 范围外、待确认或冲突事实写成确定事实，拒绝；明确作为待核实说明时保留复核项。
- 无依据确定评级、现金自动定级、正文评级与程序建议冲突，拒绝。
- 未完整查询却写“无/未发现”等确定否定、已确认现金却被否认，拒绝。
- 同时建议上报与不上报、上调与下调，以及与程序上报建议矛盾，拒绝。
- 已确认历史现金必须在现金维度说明风险因素并包含全部现金证据，不能仅以资料不足替代。
- 程序确定的等级、上报建议和已有缺项不能被模型覆盖或删除。无引用且不是待核实表达的自由说明列入 `TEXT_REQUIRES_EVIDENCE_REVIEW`。

错误以code、生成结果path、事实/规则evidence_path返回。metadata保留validation_version、validation_errors、review_items和各次attempts；不把模型全文或远端异常正文写入日志。待复核项保留在metadata，资料缺项保留在draft.missing_items，后续接口应同时展示。

自然语言检查只覆盖明确表达，不能证明引用支持全文，也不能穷举所有同义句、否定结构或风险遗漏。固定统计和结论有程序保障；其余语义仍需真实模型评测与机构审核。不能把本期检查通过宣传为全文事实核实完成。

## 统一修复预算

首次生成和格式/业务修复合计最多两次模型提交，共用同一总截止时间和线程队列。校验失败后将安全错误清单、同一事实和不可信原文放入用户数据；修复后重新通过结构及业务检查。

格式修复已用掉机会时，业务错误不再触发第三次调用。假引用、工具调用等原有不可修复错误直接失败。修复上下文超限时不再提交，返回VALIDATION_FAILED及repair_skipped=CONTEXT_LIMIT。超时仍返回TIMEOUT，丢弃晚到结果；无后台重复重试。

## 验证

```text
mvn "-Dtest=EddResultValidatorTest,EddDraftServiceTest,EddStructuredOutputTest" test
```

包含金额/日期/条数错误、口径与引用绑定、假引用、未确认或排除业务、冲突、缺规则评级、未知改否、现金遗漏、互斥建议、固定结论篡改、修复成功/失败、共用调用次数和截止时间、修复上下文超限。使用合成输入和fake ChatModel，本机不调用真实AI。

另机入口继续使用 `EddDraftLiveTest`，现已切换到analyze。详见[另机验证](llm-live-testing.md)。

2026-09-21本机验收：19项新增校验测试通过；全量360项中358通过、2项真实联网跳过，0失败/错误。渲染后的每个文本字段限制为20000字符，防止标记展开导致输出无限增大。真实模型对证据标记的遵循度尚需另机验证。
