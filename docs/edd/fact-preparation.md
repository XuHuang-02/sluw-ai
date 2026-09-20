# 任务03：事实范围与冲突准备

交付状态：2026-09-20用户改为本机验证；任务02/03共47项Java测试、29项契约测试、10个实际Java输出校验通过。任务04补充检查后已修正旧样本空支付方式为unknown，并纳入契约回归。未运行真实AI/RAG。

## 调用

先按v1 Schema及语义约束校验输入，再调用纯Java服务；当前尚无尽调HTTP端点。

```java
EddInputAdapter.Adaptation adapted = adapter.adaptCoreTables(json, metadata);
EddFactService.Prepared prepared = new EddFactService().prepare(adapted);
```

已有v1标准JSON可调用prepare(ObjectNode)。适配器的issues、interpretations、provenance完整保留在prepared.input()，新增诊断在prepared.issues()；request的读取返回副本。原始事实不删除、不覆写，decisions以fact_id索引范围结论。

## 范围与金额

| 状态 | 后续使用 |
| --- | --- |
| INCLUDED | 可用事实；金额仍须检查amountEligible及字段冲突 |
| EXCLUDED | 明确范围外，保留原因，不参与本次范围内统计 |
| PENDING | 关联、期限、渠道、时间或来源冲突待核实 |
| DUPLICATE | 同源同主键且内容一致，duplicateOf指向保留事实 |

自然人全部明确角色归集；入口角色不作为筛选条件。核心APPNTNO/INSUREDNO可建立关系，BNFFLAG不能证明受益人身份。POLNO连接产品与责任、计划等快照；缺失关联待核实。YEARS明确为1时排除一年期，未知INSUYEARFLAG不推断，PAYYEARS/PAYINTV不作为产品期限。

事件按客户匹配、期限、个险理赔渠道及analysis_as_of判断。缺失事件时间待核实，截止后排除。非资金保全仍保留；未完成、取消、冲正事件不参与已完成金额统计。amountEligible仅表示范围内、完成、有金额的候选，还不代表币种、方向、金额口径已经可合计；这些校验属于任务04。

所有核心快照amountEligible=false，避免把PREM/SUMPREM当成实收款。混合主附险保留产品分别判断，整单总额不直接归入范围内金额。快照与history_events覆盖类型及全部query_status原样保留；缺失或失败不得推断无业务、无风险。

## 去重和字段冲突

事件按source_id、table和source_record_id，核心快照按source_id、table及完整复合主键去重。事件内容一致时合并角色，仅保留字典序最小fact_id参与统计；其他事实仍在原始输入中。金额字符串尾零及等价时区规范化仅用于比较。来源相同但内容不一致、跨源同业务标识均待核实，不按更新时间自动覆盖。

fieldChoices保留机构提供的候选、采用记录以及blocked。下游计算任何冲突字段之前必须检查fieldBlocked(field)；true时暂停该字段判断（例如收入比），其他事实继续使用。false且有冲突记录时，仅采用该字段的adoptedFactId；不要遍历所有INCLUDED摘录自行选择。机构选择的记录如果被范围排除、待核实或重复归并，也暂不采用。该层不解析收入文本、不计算收入比、不自动生成风险等级。

未解决冲突不能传adopted_fact_id；采用记录必须属于候选；人工摘录必须已确认且确认时间不晚于截止。未知引用直接拒绝。source原值、提取时间和所有候选保留，原稿与人工稿的版本持久化由后续任务完成。

附件只使用明确提供的人工摘录。只有附件元数据时返回ATTACHMENT_NOT_EXTRACTED，不读取文件或推断附件内容。

## 另机验证

```text
mvn -Dtest=EddInputAdapterTest,EddFactServiceTest test
```

EddFactServiceTest包含10个便携场景（SYN-V1-004/005/006/008/010/013/014/015/016/019）、DB-004/005/010和重复冲突、引用、不可变性等边界用例。fixture位于tools/edd/fixtures/fact-scope-cases.json，是旧提案的任务03子集迁移，并非生产输入适配器：日期型覆盖时间使用合成午夜；只有日期的风险时间保留未知；已确认摘录的合成确认时间明确采用原样本记录时间。

这些测试只检验事实准备，不代表风险评级、现金信号、LLM效果、全链路或性能已验收。
