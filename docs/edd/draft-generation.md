# 任务07：风险概述与建议稿生成

状态：本机fake ChatModel验收完成。任务06另机真实RAGFlow验证已由用户确认通过；任务07真实模型效果留待另机及任务11评测。本模块提供Java接口，不注册HTTP端点、不发布completed结果。

## 调用与复用

```java
// configuredModel、factory沿用现有Spring ChatModel与RoutingModelFactory Bean。
// 按应用生命周期创建单例，并在关闭时调用close，不要每个请求创建线程池。
var drafts = new EddDraftService(configuredModel, factory,
    EddDraftService.Settings.defaults("deployment-model-config-v1"));
var result = drafts.generate(ruleEvaluation, verifiedRetrievalResult);
```

输入为任务05的EddRuleService.Evaluation（包含任务03/04的事实与统计）及任务06的Result。正式输入必须先通过任务01契约校验。真实输入拒绝testOnly规则/检索结果，检索引用必须属于本次规则评估可用的规则集合。调用方不应自行构造“已验证”检索结果代替任务06。

复用RoutingModelFactory隔离当前配置的ChatModel，保留原API/transport，使用ChatClient调用；不新增API客户端或重复读取密钥。沿用既有隔离逻辑的一次调用、关闭工具执行与普通模型选项隔离。尽调客户端不注册SimpleLoggerAdvisor、聊天记忆或工具。尽调提示词位于resources/edd/prompts/draft-v1.txt，与核保提示词分开。已有模型提供方支持范围沿用RoutingModelFactory；不支持的模型返回MODEL_ERROR。

## 六个输出维度

| section | 内容 |
| --- | --- |
| customer_and_transactions | 基本信息完整性、承保、保全、个险理赔及非资金变更 |
| risk_grade_basis | 当前等级、评级原因与正式规则缺口 |
| historical_cash | 全历史现金事实、现金线索及覆盖范围 |
| suspicious_reports | 历史上报与本次建议分别说明 |
| blacklist_and_external | 黑名单、司法、负面信息及主体匹配 |
| high_risk_scenarios | 信托等场景、机构与受益所有人信息及资料缺口 |

模型只能返回overview_sections、disposition_recommendations、missing_items。每段保留fact_refs/rule_refs。固定的grade_recommendation、report_recommendation、proposed_grade与current_risk_grade由程序从任务05结果写入，模型额外返回这些字段将被拒绝。缺规则时等级仍为空。

模型不能删掉程序发现的缺项：附件未摘录、未确认事实、冲突、覆盖不足和规则解释缺口在结果中强制保留。未摘录附件只传ID与状态，不打开路径、不读取内容；已提供的摘录带确认/范围/来源信息，以数据身份进入用户消息。检索资料也只进入用户数据消息，不能修改系统职责。

## 长历史和预算

EddDraftContext先使用任务04的全历史定点统计，再按业务类型、状态、支付方式、币种、资金方向、金额含义、范围和月份对全部事件分组。不会仅保留前N条；现金及其他风险资料、已纳入/待核实快照、评级/司法等风险记录、人工摘录、状态轨迹、冲突与缺项保留。普通事件明细仍存在原事实输入，不重复塞进提示词。

长fact_refs使用临时EDD-GROUP-*句柄代替重复的ID数组。服务端保存句柄到完整fact_id列表的映射；模型引用句柄后，程序展开并去重，交付草稿只包含原始fact_id。组引用代表完整聚合证据，不能被解释为其中每一条单独支持相同事实。具体现金证据另有引用组，尾部事实不会因聚合而丢失。

预算是系统提示词和数据JSON的UTF-8字节数上限，不宣称是精确token数；模型输出另设maxTokens。根据另机模型上下文窗口留足输出和协议开销后配置。默认：

| 配置（Settings） | 默认值 |
| --- | --- |
| maxContextBytes | 96000 |
| maxResponseBytes | 64000 |
| maxOutputTokens | 4096 |
| timeout | 45秒 |
| workers | 4 |
| queueCapacity | 16 |
| modelConfigVersion | 由部署方明确传入 |

本阶段采用程序按业务/月分段汇总后的一次主生成。若保留全部必要材料后仍超限，返回CONTEXT_LIMIT和已有事实，不发送请求、不截断；未实现多轮模型分段摘要与归并。未来需要放宽时须先评测完整信号保留，不以模型自行压缩替代确定性证据。

Settings是服务端构造配置，不接受客户JSON覆盖；任务10负责Spring装配与部署配置绑定。默认参数仅为开发值，不能视为百级并发性能承诺。

## 状态、版本及校验边界

| status | 行为 |
| --- | --- |
| DRAFT | 六维结构与引用检查通过，仍需任务08校验，不能直接发布 |
| CONTEXT_LIMIT | 汇总后仍超过输入预算，未调用模型 |
| INVALID_OUTPUT | 输出超长、非法JSON、重复键、尾随内容、字段/枚举/维度/引用不合法或返回工具调用 |
| MODEL_ERROR | 模型初始化或调用失败，错误不暴露远端正文 |
| TIMEOUT | 排队加模型调用等待超时；取消任务、丢弃晚到结果，不自动重试 |
| BUSY | 工作线程和队列已满，或服务已关闭 |
| INTERRUPTED | 调用线程中断，保留中断标志 |

超时是等待截止，不保证底层提供方HTTP立即停止；若调用忽略中断，仍占用固定线程直到结束，不会创建无限线程。底层HTTP超时继续使用原提供方配置。输入整理及JSON校验不计入模型等待超时。

Result由status、draft（失败时null）、facts（确定性摘要/建议/缺项等）和metadata组成，是内部中间结果，不等于v1完整result。metadata记录input_version/input_hash、prompt_version/prompt_hash、context_hash、model_config_version、返回的model、规则包信息、context_bytes、elapsed_ms；requires_validation恒为true、execution_permitted恒为false。不记录明文输入、模型原文或远端异常消息日志。

本任务只完成结构与引用边界检查。引用存在并不证明文案受其支持；金额、日期、未知写成否、叙述中的等级冲突、互斥建议及定向修复属于任务08。当前不执行修复循环、不保存人工稿、不读写机构结论，结果版本与持久化归任务09/10。

## 已验证内容

```text
mvn "-Dtest=EddDraftServiceTest" test
```

13项fake ChatModel测试通过：六维结构与确定性建议、SYN-V1-016附件边界、017人工指令材料、021信托受益所有人缺口、018等价的一万条历史尾部现金、完整引用展开、预算超限不调用、假引用/越权评级字段/缺维度、重复键/尾随JSON/过长输出、模型异常脱敏与不重试、超时、RAG版本不匹配、RAG材料指令隔离、队列满载以及复用隔离工厂。场景覆盖合并在13个测试方法中。

全量Java回归329项中328通过、1项真实RAGFlow测试默认跳过。未在本机调用真实LLM；模型是否正确理解信托材料、是否遗漏风险信号，仍需另机实测和业务评审。

## 另机真实验证入口

已提供默认关闭的EddDraftLiveTest及操作步骤：[真实模型验证](llm-live-testing.md)。沿用Spring Boot模型自动配置，仅装载模型组件；不启动数据库/Redis，也不在本机进行真实LLM调用。另有2项离线测试验证DeepSeek/DashScope配置装载及长历史合成输入。

真实入口交付后的最终本机回归：332项中330通过，2项真实联网测试默认跳过；未在本机调用真实LLM。
