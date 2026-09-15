# 问题件选路实验：操作与字段说明

本轮基线：DEAL_ISSUE_V1。只根据已有核保记录选择处理服务，不重新进行医学核保，不调用业务接口。覆盖 AutoSendBL.dealIssue 和必要辅助判断，不覆盖 dealTransUW。

## 与原有代码的关系

- 继续使用 AgentController、ChatRequest.issueSubmissionTrial、原同步/SSE接口和页面消息展示；普通问答与其 RAGFlow 不变。
- 在原 IssueSubmissionTrialService 中替换旧的自由文本试判；新增纯条件计算、共享分支规范和严格结果校验。
- 复用已配置 ChatModel 的服务选择和连接设置。DashScope复制现有实例配置，DeepSeek复用原模型持有的同一个DeepSeekApi实例。选路实例温度固定为0，最多尝试一次，禁用工具执行及正文观察日志；不改普通问答实例。
- 复用现有 Jackson 和 Spring AI BeanOutputConverter。后者同时生成输出契约提示并将返回值转换为Selection；传入严格Jackson映射器，保留重复键、额外字段、尾随内容和字段类型检查，并校验状态和互斥节点字段，不依赖模型原生JSON Schema支持。
- 原始业务 Java 文件不修改、不引入其数据库或工作流依赖。通知单生成、任务池、下发标记更新等由未来服务执行。
- 旧 BEFORE_SEND_INTERNAL_AGENCY / ISSUE_FLOW 模板已退役，旧输入会提示使用新模板，不会静默按新含义判断。旧模板和提示词已移到docs/trial/archive，仅供历史核对，不随应用发布。

## 直接操作

1. 使用更新后的项目重新启动，刷新聊天页面。
2. 在输入框附近找到“问题件选路实验”。点击“填入合成示例”，页面自动填入JSON并开启实验。
3. 点击发送。默认合成示例对应建议核保通过；只有模型输出通过四层校验，页面才显示有效建议。
4. 试自己的数据时，先下载模板，对照下面的字段说明填写完整JSON。必须使用合成或脱敏资料及公司批准的模型服务。
5. 每次重新提交整份JSON，补充资料也如此。聊天历史不参与本次选路事实。
6. 关闭实验开关即可继续普通问答。

模板：`src/main/resources/static/trial/deal-issue-template.json`；完整示例：同目录`deal-issue-example.json`。

## 最小输入契约

| 字段 | 含义与用途 |
| --- | --- |
| uwno | 顶层正整数：当前核保批次，只填一次；schemaVersion已从输入删除，规则版本仍由后台记录 |
| contno | 本单脱敏编号，仅在输入端标识，不发送给模型 |
| currentErrors | 当前最新核保批次记录，统一使用顶层uwno，行内不再接受uwno |
| historyErrors | 当前批次之前的核保记录，保留所有相关历史，不能只取最近一次 |
| lwmission / lbmission | 本单两类任务查询结果，分开提交 |
| lwnotepad | 本单记事本标记，不提交记事本文字 |
| autoallotbyerr | ldcode中codetype=autoallotbyerr的code列表，不是全部核保知识库 |
| completeness | 以上六组资料分别标记true/false/null；含义见下文 |

### 核保记录

当前记录每行7个字段（noFailedRules及下面六个字符串字段），历史记录每行7个字段（uwno及六个字符串字段）。历史不接受noFailedRules，当前和历史均不接受uwerror：

| 业务字段 | 类型 | 判断用途 |
| --- | --- | --- |
| uwno | 顶层及历史每行的正整数 | 顶层给出当前批次；历史逐行保留批次，支持首批和不同历史批次统计 |
| uwrulecode | 字符串或null | 排除规则、内部历史匹配、外1历史次数 |
| insuredno | 字符串或null | 内部问题件按人匹配，体检/契调候选分组 |
| noFailedRules | 必填布尔值，仅当前记录 | true表示业务端确认该记录符合原固定话术“问题件修改完毕后，没有未通过的核保规则”；false表示不符合。不做语义分析；未知不能填false或null，应补齐或按不完整记录处理 |
| lettertype | 字符串或null | 函件类型与处理分组 |
| peitem | 字符串或null | 是否存在体检项，不生成体检计划 |
| positivesign | 字符串或null | 阳性条件 |
| autoflag | 字符串或null | 已自动下发记录识别 |

任务每行只保留`activityid`、`lastoperator`；记事本每行只保留`noteflag`。各组须已限定为当前这一个单号，因此不重复填写missionprop1、otherno、contno。

不提交出生日期、地址、联系方式、通知单内容、MissionId、serialno或数据库更新参数。来源用`currentErrors[0]`等本次JSON位置引用，不要求手工编造记录ID。人员在输出中用`PERSON:人员编号`，投保人角色用`APPLICANT`，整单处理用`POLICY`，不是接口实参。

### 空、未知和完整性

- `[]`且completeness对应值为true：已确认没有记录。
- `null`、未提供该组，或完整性为false/null：资料尚不完整。已提供的可靠记录可以证明某条件成立，但不能仅凭未看到记录认定不存在。
- 每一条已提供的记录，字段必须齐全。字段值null表示已确认数据库NULL，不表示“我不知道”。不知道某字段时，先补齐该记录，或暂不纳入这条记录并将该组完整性标为false；不得伪填null。
- 当前批次不能为空且宣称完整。没有当前核保记录的情况属于进入dealIssue之前的入口问题，不属于本次试验。
- 拒绝额外字段、重复JSON键、尾随文本、类型混用，以及大于或等于当前批次的历史批次。noFailedRules只接受JSON布尔值，不接受字符串、数字或null。
- 字符串空值统一以null表示；非空字符串保留原值，不擅自trim影响代码精确比较。
- 由于任务不重复带单号，程序不能验证用户是否混入别单记录；数据准备者必须保证本单范围及完整性声明真实。

## 分支顺序

共享分支表：`src/main/resources/prompts/deal-issue-routing.json`。它同时用于构造模型提示词和核验返回路径，避免维护两套不同的优先级。

1. 非自动任务记录 → 退出自动处理。
2. 任意批次存在autoflag非空、lettertype为1/3/5 → 退出自动处理。
3. 任意批次不存在autoflag为1/2/3 → 退出自动处理。
4. 当前存在非自动下发规则 → 退出自动处理。
5. 满足noFailedRules=true、全部排除规则或仅历史重复内部问题件之一：
   - 无非空记事本标记 → 建议通过。
   - 有记事本 → 按顺序检查未标记阳性、disagree/kidamntrisk；命中则退出。
   - 否则进入记事本阳性体检候选；无候选则代码无后续动作。
6. 不满足第5项：
   - 优先新的内部问题件。
   - 再检查未标记阳性、disagree/kidamntrisk；命中则退出。
   - 有外1 → 任一规则历史不同批次自动下发达到2次则退出，否则外1处理。
   - 无外1 → 按check(1)结果进行组合处理，可能包含体检、契调、外2。
   - 无候选或没有匹配的服务分组 → 原代码未指定处理动作，不臆造人工服务。

程序严格保留的细节：

- nbOnlyAndExists的历史匹配只比规则编码，getnbwtj2还要比insuredno，两者不能合并成一个口径。
- 外1按规则编码统计其他不同核保批次，限定lettertype=2且autoflag=3，不按人数或记录条数计数。
- sendjsbyx的首批阳性候选查询没有要求peitem非空，不额外增加选路条件；具体服务是否接受内容属于执行阶段。
- check(1)的三段查询条件不同：当前体检/外2、autope首批阳性体检、当前契调。契调需排除任意批次存在契调阳性的情况。
- SQL NULL不等于一般字符串，NOT IN遇到字典NULL的行为也保留。
- 退出只表示本段自动处理结束，不声称已经完成转人工；建议通过不等于业务通过成功。

## 模型选择与服务端报告

模型输入只有conditions的三态值，不含保单号、原始记录、引用、缺失字段名或处理项。模型依同一决策表选择分支，不计算历史次数。

模型成功选路只输出：

```json
{"status":"SELECTED","branchId":"INTERNAL"}
```

必要前置条件未知只输出：

```json
{"status":"INSUFFICIENT","blockedAt":"D03"}
```

两个节点字段互斥，不得同时提供非空值。结构转换器允许省略不适用字段，也接受其显式null；禁止route、conditionIds、evidenceRefs、missingFields、items等额外字段。SELECTED引用叶节点，INSUFFICIENT引用判断节点，INVALID不可选择。

服务端完成四层检查：小结构合法性、节点存在及类型、条件优先级及未知阻塞、服务端报告路径和处理项契约。expected()只作对照与拦截：不传给模型，不替换错误选择，不自动重试。只有模型选择通过后才组装报告。

完整报告仍含status、route、branchId、conditionIds、evidenceRefs、missingFields、items。服务端按实际路径汇总引用及缺失信息、组织候选处理项并排序去重。处理项使用POLICY、APPLICANT或PERSON:编号。这些字段不再由模型复制，也不计作模型集合操作准确率。

失败区分：模型结构失败、无效节点、条件路径不符，以及服务端规则核验/报告组装失败；都不生成有效建议、不执行业务操作。页面文本由固定规范和经过校验的报告生成。

决策表初始化时校验节点ID唯一、入口、条件契约、出口引用、叶节点route/itemKey匹配、无环、可达性及条件覆盖。预计算同时验证实际返回条件和处理项键符合声明契约。

## 验收与模型评测

当前工程验证：69项Java测试通过，含36个固定分支案例以及小结构转换、服务器报告组装、未知阻塞、顶层批次、布尔状态和非法决策表。测试使用模型替身，没有调用真实模型。测试代码按用户要求只保留本地并保持Git忽略。

工程对照集：`docs/trial/deal-issue-cases.json`。迁移输入格式时保留既有expectedBranch、expectedStatus、expectedKinds及expectedSubjects；每个案例同时验证预计算/对照和小结构选路后的完整报告。案例仍需业务负责人审阅。本轮未对真实业务数据库执行SQL对拍；这些测试不能替代数据库方言及数据抽取口径验收。

固定模型、参数和提示词重复运行案例；模型调用次数N包括传输失败，不含被输入校验拒绝的请求。分别统计：

- 结构失败率：模型不满足小结构的次数/N。
- 分支及阻塞节点正确率：模型状态和所选节点正确次数/N。
- 调用失败率、耗时和实际Token；没有已确认价格不估算费用。
- 服务端报告生成错误单独计数，不归因于模型遗漏引用或处理项。
- 整体有效报告率：成功通过拦截并生成报告的次数/N；拦截安全性不等于模型准确率。

首轮不得出现错误去向被放行、越过未知前置条件，或服务端漏处理项。真实模型尚未完成重复评测，不宣称业务选路准确率达标。

原始GBK业务文件SHA-256：4cb8da1734b783125c04339d8750dd750afc511df13950d6550cfdf1ad8a12d2。


## 格式清理与日志

复用Spring AI BeanOutputConverter和WhitespaceCleaner，只接受单个JSON对象及外围空白。已移除MarkdownCodeBlockCleaner：Markdown围栏、前后解释均为格式失败，与提示词保持一致，不修补业务字段。

之前需要模型复制完整报告，合成返回值曾因漏掉completeness.lwmission被拒绝。当前改为服务端生成完整引用，分支正确时无需模型复制该引用；旧版完整报告不再作为模型输出接受。

logback-spring.xml关闭BeanOutputConverter自身的原文错误日志；不打印AES密钥或解密正文。成功日志分别记录模型结构/分支通过及服务端报告成功，规则核验或组装异常记录SERVER_ERROR，并区分COMPARISON与REPORT阶段；只记录异常类型，不记录异常正文或输入内容。

本轮6项页面交互检查通过：示例填入、普通问答切换、取消保留输入、请求失败保留输入、窄屏布局及CDN不可用时填入示例。

## 模型连接复用（2026-09-15）

项目实际DeepSeek 1.1.2没有mutate()或公开API访问器，自动配置也没有发布API Bean。因此兼容适配集中在RoutingModelFactory：只读反射获取原模型deepSeekApi并复用，不从properties重建连接。字段缺失、访问受限或API为空时明确失败，不回退至另一套连接。依赖升级时必须检查这个版本边界；未来有公开复制接口时应替换此处。自定义模型子类明确拒绝，避免重建丢弃其覆盖行为。

DashScope继续mutate并复制options，空options回退默认值。两者模型级ObservationRegistry均显式NOOP，温度0、工具执行关闭、共用最多一次尝试的RetryTemplate；普通问答模型不变。共用API保留其HTTP客户端、headers、拦截器、代理和超时配置，但底层HTTP传输自身的重试/观测仍遵循原客户端配置；这里只限制模型层重试，不能保证第三方拦截器没有重试或日志。

工厂已删除ObjectProvider及properties依赖，因此不再需要getObject()/getIfAvailable()或baseUrl/apiKey空值拼接。

## 校验与维护性约定（2026-09-15）

决策表根对象改为entry和nodes，提示词和两个服务端遍历均使用entry，遍历保护上限由节点数量推导。完整提示词及SHA-256在构造时计算一次。模型不输出items/refs，集合由服务端排序去重，无模型列表顺序比较。

解析器ObjectMapper私有；对外只提供独立配置副本及序列化方法，修改副本不影响正式解析。数据组及必填字段保持固定遍历顺序。JSON解析错误保留cause供排障，页面仅显示固定安全说明。预计算中的规则集合提为常量，处理对象判定拆为resolveSubject，业务条件不变。

expected与报告组装错误包装为InternalFailure，保留cause和阶段；不包装成模型Rejected，不产生兜底答案。当前69项Java测试通过，包括围栏拒绝、内部错误归类、解析器隔离、稳定报错顺序、元数据入口与超过50节点遍历。未调用真实模型。

## SQL NULL及候选完整性核对（2026-09-15）

已用GBK原AutoSendBL.java核对：269–282行nbOnlyAndExists是SELECT DISTINCT lettertype，不是WHERE lettertype <> '4'。因此NULL也破坏“唯一类型为4”的条件，hasOther必须保留这一语义。407–413行将空类型且体检项目非空记录分组为peitem，1088–1093行明确调用体检服务；combinedKind仅对此返回EXAM。

将SQL NOT IN三值与资料缺失三态分开：SQL UNKNOWN到WHERE边界才转为未命中，缺少查询结果仍为资料UNKNOWN。移除含糊的excluded取反，改用正向matchesRuleWhere。修复NULL规则编码NOT IN空子查询应命中的边界（组合候选查询没有另外排除NULL编码）。

候选列表有任何未解决来源时返回UNKNOWN；保留R_INTERNAL/R_EXT/R_NOTE/R_COMBO门槛。叶子取得处理项还必须验证对应Ready为TRUE且列表存在、非空、类型和对象合法；缺失列表抛内部错误，禁止用空列表兜底。

144项Java测试通过，其中72组用本地SQLite执行原SQL筛选/DISTINCT逻辑生成预期的对拍覆盖编码NULL、类型NULL、空/含NULL字典和体检项组合。修复前有4个SQL结果不一致与1个缺失items失败，修复后通过。此为独立本地SQL语义验证，未在公司实际数据库及驱动上验证，不代替业务数据库方言验收。原36个分支案例继续通过。


## 2026-09-15：候选就绪与审计引用修复

删除hasNoteExam为FALSE时覆盖noteExamReady的逻辑，完整空列表保持Ready=TRUE。当前D09无候选本来就走NO_NOTE，之前没有复现报告失败，但Ready语义错误已修正。

hasCombined是原查询是否有候选，hasCombinedServices是是否有可执行服务；两者允许不同。D15经R_COMBO及D16，在没有服务时走NO_SERVICE，不补造映射，也不把查询改为排除原有类型。回归覆盖此路径。

NULL规则编码且字典非空时引用具体uwrulecode字段与整个字典（证明非空），不把第0项误写为匹配值。firstBatch同时保留当前行及顶层uwno。Ready的负向引用仍保留，使用appendAuditEvidence明确表示完整列表审计；exists命中后停止，其否定证据仍保留。输入拒绝字典空字符串，SQL NULL继续接受。

新增4项测试，修复前3项失败，修复后148项Java测试全部通过。此前5413549已通过GitHub API验证同步，仓库为私有。本节为后续补充修复；未修改用户新出现的test.py。
