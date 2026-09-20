# 任务06另机真实RAGFlow联调

本指南配合 EddRuleRetrievalLiveTest 使用。普通 EddRuleRetrievalTest 全部是离线模拟；修改地址后跑普通测试不会访问真实服务。新入口默认跳过，必须显式设置 edd.live=true。这里只验证真实检索、出处与内容校验，不调用LLM，不生成客户风险概述，也不证明正式规则的业务正确性。

## 1. 准备代码和环境

确保另机已有 src/test/java/com/sinosig/sluw/application/edd/EddRuleRetrievalLiveTest.java（本次新增文件；旧提交43cec74不包含它）。以下命令均在项目根目录执行。使用JDK17和Maven，并保留原项目构建所需的ext-lib厂商依赖。无需启动整个应用、数据库、Redis或LLM。

先跑离线回归：

```powershell
mvn "-Dtest=EddRuleRetrievalTest" test
```

预期12项通过。这一步不代表真实RAGFlow通过。

## 2. 创建独立合成测试库

在另一台电脑的RAGFlow中建立独立测试知识库，上传一份UTF-8文本文件，内容如下，等待解析/索引完成：

```text
SYNTHETIC TEST ONLY — not an approved company policy.
Natural person EDD approved rules: EDD-LIVE version 1
Effective at 2026-09-20T12:00:00+08:00; trigger risk_review; rule IDs CASH-HISTORY.
测试条款 CASH-HISTORY：调查范围内有证据确认的历史现金支付，作为风险增加因素。不能仅因现金支付自动提高一级或认定为高风险；等级取决于正式评级规则与完整证据。
此文档仅用于检索联调，不是正式评级规则，不用于真实客户决策。
```

建议让短文本保持在一个切片中。记录该测试库dataset ID及文档document ID。该合成文本特意包含当前代码实际查询中的技术标识，便于先验证链路；它的命中率不能代表真实中文业务条款的召回效果。

## 3. 配置本机参数

在项目根目录已有的 local-settings.properties 中增加/更新下列键，保留其他原有配置。该文件已被Git忽略。地址填写当前部署可用的完整检索endpoint；API Key只保存在另一台电脑。

```properties
application.config.ragFlow.base.apiUrl=<完整检索接口地址>
application.config.ragFlow.base.apiKey=<API Key>
application.config.ragFlow.edd.datasetId=<独立合成测试库ID>
application.config.ragFlow.edd.page=1
application.config.ragFlow.edd.pageSize=100
application.config.ragFlow.edd.similarityThreshold=0.2
application.config.ragFlow.edd.vectorSimilarityWeight=0.3
application.config.ragFlow.edd.topK=100
application.config.ragFlow.edd.keyword=true
application.config.ragFlow.edd.highlight=false
application.config.ragFlow.base.connectTimeoutMillis=10000
application.config.ragFlow.base.readTimeoutMillis=60000
edd.live.documentId=<合成文档ID>
```

真实应用仍通过Spring Boot配置来源装载环境。本联调入口只启动小型Spring上下文，显式将该UTF-8 properties文件加入Environment，然后使用原ConfigReader、RagFlowClient和AiClientConfig的RestTemplate；不自动加载各application YAML/profile。系统属性/环境变量优先于此文件。也可以用 -Dedd.live.config=C:/path/to/local-settings.properties 指定文件。Windows文件路径在properties里用正斜杠。

## 4. 第一次真实调用：收集并核对切片

```powershell
mvn "-Dtest=EddRuleRetrievalLiveTest" "-Dedd.live=true" "-Dedd.live.mode=capture" test
```

输出目录：target/edd-live/capture-时间戳/，包含：

- report.json：候选数量和本次耗时。
- candidates.json：实际返回的datasetId/documentId/chunkId/content。
- candidate-0.txt等：对应切片原文，保留精确UTF-8内容。

需要确认返回切片属于测试库/文档，内容与上传的合成条款一致，且标识均非空。capture只证明拿到候选，不把候选自动认定为批准依据。空切片或接口错误会导致测试失败；优先检查解析完成状态、地址、权限和检索阈值。返回结构须满足当前适配器要求：整数code=0、data.chunks数组，以及切片kb_id/document_id/id/content字段。

选择核对通过的一条切片，把对应candidate-N.txt复制到 .scratch/edd-live/reviewed-content.txt。例如选择第0条，替换下面的实际目录名：

```powershell
New-Item -ItemType Directory -Force .scratch/edd-live
Copy-Item -LiteralPath 'target/edd-live/capture-实际时间戳/candidate-0.txt' -Destination '.scratch/edd-live/reviewed-content.txt'
```

不要手工重打、格式化或额外添加换行。然后增加配置：

```properties
edd.live.chunkId=<所选切片的chunkId>
edd.live.reviewedContentFile=.scratch/edd-live/reviewed-content.txt
edd.live.expectedStatus=FOUND
```

这里的人工核对只建立合成测试基准，不替代正式规则审批。入口中的规则包和目录都标记testOnly=true。

## 5. 第二次真实调用：验证任务06完整检索校验

```powershell
mvn "-Dtest=EddRuleRetrievalLiveTest" "-Dedd.live=true" "-Dedd.live.mode=verify" test
```

查看 target/edd-live/verify-时间戳/report.json，预期：

- result.status=FOUND、testOnly=true。
- evidence只有一条，ruleRef=EDD-LIVE@1:CASH-HISTORY。
- datasetId/documentId/chunkId与所核对切片一致，sha256和content与固定基准一致。
- missingRuleIds为空；elapsedMillis记录检索及本地处理耗时。

FOUND仅表示这条测试规则的解释依据通过校验。耗时包括服务/网络及少量本地处理，不是纯模型耗时，也不是最终尽调生成耗时。

## 6. 负向验证与结果解读

保留原reviewed-content.txt，另复制一份并修改一个字，将reviewedContentFile改指向修改后的文件、expectedStatus设为FILTERED，再运行verify。预期FILTERED、evidence为空。这验证真实响应与固定基准不一致时被拒绝。完成后恢复原文件路径和FOUND。

| 现象 | 解读/下一步 |
| --- | --- |
| FOUND | 指定解释引用通过；不代表规则全集或最终评级通过 |
| NO_MATCH | 实际服务成功返回空列表；检查查询、索引、阈值，不能解释成“没有风险” |
| FILTERED | 有返回但未符合冻结目录；核对ID、切片变更、精确内容/换行 |
| SERVICE_ERROR | HTTP/业务码/响应结构错误；核对地址、密钥、部署响应格式 |
| TIMEOUT | 真实网络或服务超时；记录配置和服务端耗时，不能归为空结果 |
| RULES_MISSING | 没有适用目录或规则包；本固定正例若出现此状态需查代码/配置 |

不要靠把expectedStatus改成实际失败状态让正例“通过”。只有明确的负向场景才设相应预期。超时、空结果、版本/审批/跨库、部分命中等边界已在普通12项离线测试覆盖；若在真实环境测这些场景，应单独记录设置和服务响应。随机调低超时不保证可重复触发TIMEOUT。

## 7. 从连通验证到真实效果评测

上述合成正例是链路冒烟测试。评测真实召回，需要业务核对后的代表性条款与固定目录，覆盖现金、高风险原因、可疑交易、黑名单、信托等，并包括不同版本、过期材料、无适用条款。先人工标注每次请求应命中的rule ID和切片，再通过与生产相同的Query测试，不能只在RAGFlow界面改成自然语言问题验证。

当前Query只携带包ID、版本、时间、事项和规则ID；若真实中文条款没有这些标识，召回可能偏低，需要记录证据后改进检索设计，不能以调低校验标准解决。记录预期规则数、实际验证命中数、缺失ID、错误接纳数及逐次耗时；多次运行后再计算P50/P95，单次结果不能证明并发百人时的性能。

把正例与负例的report.json、Maven通过/失败摘要、RAGFlow部署版本及检索参数发回来即可分析。合成库的candidates.json也可附上；不要发送API Key或整份local-settings.properties。真实环境结果只保存在target或.scratch，不提交Git。

本机验证记录：12项既有检索回归通过，新入口默认跳过；另以本机模拟HTTP验证capture、verify FOUND和verify FILTERED均通过。以上均不属于真实RAGFlow测评结果。
