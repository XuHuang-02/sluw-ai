# 任务06：RAGFlow尽调规则检索适配

2026-09-20完成离线实现与本机测试。真实RAGFlow、正式尽调知识库及条款目录尚未接入；不将模拟测试视为实际召回效果验证。当前为后续任务07/10提供独立Java接口，不注册HTTP业务端点或修改现有核保Bean。

## 组成与调用

- EddRuleRetrieval：按已批准规则包和服务端条款目录筛选、校验与保留引用。
- EddRagFlowRetriever：复用现有RagFlowRequest DTO及POST协议，使用独立RestTemplate、连接/读取超时和显式配置。
- FakeEddRuleRetriever：显式注入的离线fake，支持返回候选、超时、服务错误；不作为生产降级后备。

```java
var transport = new EddRagFlowRetriever(
    URI.create(eddRetrievalEndpoint), eddApiKey, 2000, 8000);
var retrieval = new EddRuleRetrieval(
    eddDatasetId, transport, approvedClauseCatalogue, false);
var result = retrieval.retrieve(
    approvedRulePackage, analysisAsOf, trigger, Set.of("APPROVED-RULE-ID"));
```

上述变量由服务端受控配置提供。本模块不读取ragFlow默认库或ragFlow.base，也不自动继承核保datasetId。建议后续装配使用独立edd.rag配置段，具体Spring配置绑定属于任务10；本轮未写真实URL、密钥或知识库ID。未提供dataset或transport时返回NOT_CONFIGURED。

批准规则包来自任务05受控注册表，精确版本由请求rule_context选择。查询使用规则ID、版本、截止时间和发起事项，不拼接客户姓名、证件、交易明细或人工附件正文。

## 为什么使用条款目录

现有RAG响应DTO有document_id、chunk id、kb_id、内容及位置，但没有可依赖的批准状态和内容版本。因此用服务端受控Clause目录登记：

- datasetId/documentId/chunkId、packageId/version/ruleId；
- contentVersion、location、原始文本SHA-256；
- approved、approvalRef、approvedAt、生效区间、triggers、testOnly。

目录由业务批准资料及知识库发布流程准备；不是从相似度、文件名、检索文本中的“已批准”字样或客户输入推断。每个目录条目必须有出处与内容摘要。没有目录返回RULES_MISSING，不把未登记材料交给模型。

先按已批准包、[validFrom, validTo)、审批时间不晚于analysisAsOf及trigger筛选，再以独立dataset_ids和筛出的document_ids请求RAG。版本、时点和范围也写入查询文本。没有新增或假设部署版本支持的metadata过滤参数；真正强约束来自文档白名单与本地返回校验。

返回后要求dataset/document/chunk完全对应且原始UTF-8内容SHA-256一致，拒绝无出处、跨库、未批准版本、被改写内容和超过20000字符的切片。精确hash不做空白归一化，知识库重新切片或文本变化需更新受控目录；不能为了命中而跳过版本校验。location使用已批准目录位置，避免依赖未经核实的返回位置格式。

## 返回状态

| 状态 | 含义 |
| --- | --- |
| FOUND | 请求的规则ID都有至少一条已验证解释依据 |
| PARTIAL | 部分请求规则有依据，其余列入missingRuleIds |
| NOT_CONFIGURED | 独立尽调库或检索器未配置 |
| RULES_MISSING | 无适用已批准包/目录，或请求规则ID不在包内 |
| NO_MATCH | 服务正常返回空切片 |
| FILTERED | 有返回，但全部未通过引用/版本/内容验证 |
| TIMEOUT | 检索超时 |
| SERVICE_ERROR | HTTP/业务错误或响应结构异常 |

每条Evidence保留ruleRef（包@版本:规则ID）、datasetId、documentId、chunkId、contentVersion、location、approvalRef、sha256和content。重复切片归并；缺失和过滤原因单列。Result及集合不可变，testMode结果带testOnly=true，生产模式拒绝testOnly包和目录条目。

FOUND只表示所请求解释已找到，不表示规则全集完整或客户没有风险。执行规则始终由任务05评估；RAG空结果、故障或文本不能改写建议等级、上报结论或补成默认“否”。条款文本只能作为待引用资料，任务07不得将其作为系统指令执行。

## HTTP边界

复用现有POST检索字段：question、dataset_ids、document_ids、page=1、page_size=100、top_k=100、similarity_threshold=0.2、vector_similarity_weight=0.3、keyword=true、highlight=false。本期不分页追求全库覆盖；未召回的规则返回缺口，不推断不存在。

正常响应必须有整数code=0和data.chunks数组；缺code、非零业务码、缺chunks与HTTP错误均不是空结果。单次返回上限200条，单切片超长直接过滤。连接和读取超时显式配置，不自动重试，不额外创建线程池；这些是HTTP阶段超时，不是端到端总耗时保证。错误输出不携带远程正文或密钥。

复用既有RagFlowRequest而不修改RagFlowClient/RagFlowResponse/核保调用链；HTTP模拟额外验证旧doRetrieve(question)仍使用其原知识库配置。

## 验证与另机联调

```text
mvn "-Dtest=EddInputAdapterTest,EddFactServiceTest,EddHistoryServiceTest,EddRuleServiceTest,EddRuleRetrievalTest" test
```

本机131项相关Java测试通过，其中任务06为9项。覆盖命中完整引用、空结果、fake及HTTP超时、服务错误、版本/批准过滤、缺引用、跨库与内容变动、重复/部分命中、HTTP字段及旧核保客户端兼容。测试使用MockRestServiceServer和fake，无真实网络调用。

另机联调需要独立EDD库、实际检索endpoint和密钥，以及与该环境document_id/chunk_id对应的批准目录。需确认实际部署返回kb_id/document_id/id/content和code/data.chunks；不一致时根据真实响应适配后重跑离线测试。核对切片hash、条款位置及版本，再验证实际召回与超时表现。正式知识库未就绪时保留规则缺失，不借用核保资料。

代码及文档随任务04—06统一交付。
