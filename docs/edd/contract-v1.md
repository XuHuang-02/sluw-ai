# 尽调分析 JSON 契约 v1

状态：任务01交付。JSON Schema是字段规范的唯一来源，离线参考校验器补充跨记录约束；本任务未注册HTTP接口、未实现分析服务。任务02实现JSON适配，09/10实现任务和HTTP链路。

## 模块边界

根包为 `com.sinosig.sluw.application.edd`，本期在现有sluw-ai内增加能力。保持单一根CONTEXT.md，不改变核保领域术语和既有Controller。

| 边界 | 所属包及拟定类型 | 职责与接续任务 |
| --- | --- | --- |
| HTTP | edd.controller.EddAnalysisController | 从可信登录会话取得调用方、解析请求、返回错误/受理/查询响应；任务09/10实现 |
| 契约 | edd.contract | 本次以resources/edd/contracts/v1/edd.schema.json交付可机读规范；Java DTO在任务02/09按Schema实现，不能各自改变字段 |
| 输入 | edd.service.EddInputAdapter | 结构化JSON到统一快照/事件，保留来源与覆盖；任务02 |
| 事实 | edd.service.EddFactService | 范围、冲突、状态、金额与现金信号；任务03/04 |
| 规则 | edd.service.EddRuleService | 加载服务端批准规则并评估；任务05 |
| AI | edd.service.EddDraftService | 复用AiClientConfig/ChatClient，独立尽调规则库和提示词；任务06/07/08 |
| 编排 | edd.service.EddAnalysisService | 编排分析与发布结果；任务10 |
| 任务 | edd.service.EddJobService | 幂等、持久化、恢复、授权查询；任务09 |

现有RoutingTrialController使用AuthController.checkAuth和ResponseStatusException。新HTTP层沿用认证来源及HTTP状态处理方式，EDD错误正文由仅作用于EDD Controller的处理器映射；不安装影响其他Controller的全局异常策略。API密钥和模型配置复用服务端配置，不从分析JSON读取。

## Schema及版本

规范文件：`src/main/resources/edd/contracts/v1/edd.schema.json`，JSON Schema Draft 2020-12。默认根为request；其他入口为`#/$defs/result`、accepted、task、error。验证器必须启用format检查，日期严格合法且包含UTC偏移。

- schema_version固定为字符串`1.0`。未知版本拒绝，不猜测兼容；未来新增字段或枚举必须发布新版本并提供显式适配。
- 协议对象拒绝未知字段，数组缺失与空数组不同：顶层集合必须出现，可为空；coverage至少一个明确来源状态。扩展业务类型允许非空字符串，处理层保留未识别业务并标记待核实，不能悄悄丢弃。
- 标识符为1—128字符且无空白；事实ID在core_snapshots/events/risk_records/manual_excerpts之间全局唯一。来源业务主键可重复返回，但fact_id唯一；业务去重归任务03。
- 数组每类最多100000项；HTTP请求上限16 MiB（应用配置默认，调大需压测）。此为输入保护，不是业务历史时间限制；超限显式413，不截断历史。
- 时间使用带时区的RFC3339字符串。缺少真实时间用允许为空的字段传null，不能补造时刻；只有日期的旧字段保留在snapshot.values，并由明确适配规则处理，不私自补00:00。
- 业务金额为十进制字符串，最多10位小数、40字符；统计用BigDecimal。币种缺失可null，源码不明不默认人民币。snapshot.values保留上游原始字段类型，金额归一化由任务02执行，不从模型转换。
- source保存来源类型、表/主键/字段、采集时间和版本。未知元数据可null；空source.key不构成完整来源。附件仅ID和描述，人工摘录通过attachment_id关联，不含文件内容。
- 角色只能policyholder、insured、beneficiary；不能将“受理人”作为合法角色。payment_method中unknown明确表达未知；其他支付方式为other并由来源保留原码。
- rule_context只允许请求已有rule_package_id和expected_version。审批状态、规则原文/可执行条件、模型配置均由服务端解析，不信任客户端自报“已批准”或评分。

## HTTP约定（任务09/10实现）

| 方法 | 地址 | 成功响应 | 说明 |
| --- | --- | --- | --- |
| POST | /api/edd/analyses | 202 accepted | 请求体=request；必须带Idempotency-Key；Location指向状态地址 |
| GET | /api/edd/analyses/{analysis_id} | 200 task | 可立即查看真实阶段与已有安全摘要；不把草稿文本流当持久化完成 |
| GET | /api/edd/analyses/{analysis_id}/result | 200 result | 仅completed/completed_with_gaps时可取；其余状态返回409 RESULT_NOT_READY并通过状态查询了解原因 |

同调用方相同幂等键、同一规范化输入及解析后的规则版本返回同analysis_id（202 accepted允许返回其当前状态），不重复分析；同键不同内容409。对象键顺序不影响指纹，数组顺序保留；实际指纹算法任务09实现并固定版本。parent_analysis_id必须属于同调用方和同客户，新输入必须用新幂等键。调用方身份从会话取得，JSON不得声明owner或institution以越权。未授权返回401；查询不属于自己或不存在的任务统一404以避免泄漏。

| HTTP | EDD错误码 | 场景 |
| --- | --- | --- |
| 400 | INVALID_JSON | 非法JSON、重复对象键、非有限数字 |
| 400 | UNSUPPORTED_SCHEMA_VERSION | 未支持的schema_version |
| 400 | INVALID_REQUEST | 必填、类型、枚举、格式、反向覆盖区间等协议错误 |
| 400 | INVALID_REFERENCE / DUPLICATE_ID | 悬空引用或全局重复事实ID |
| 401/403 | UNAUTHORIZED / FORBIDDEN | 未登录或无发起权限 |
| 404 | NOT_FOUND | 不存在或无权读取任务 |
| 409 | IDEMPOTENCY_CONFLICT / RESULT_NOT_READY | 幂等冲突或结果尚不可获取 |
| 413 | PAYLOAD_TOO_LARGE | 超过请求体上限 |
| 429 | QUEUE_FULL | 队列容量已满；可附Retry-After |
| 500 | INTERNAL_ERROR | 意外服务错误，返回脱敏信息，不回显SQL、凭据、原客户资料 |

技术执行失败在task.failure中表达MODEL_ERROR、RETRIEVAL_ERROR、VALIDATION_FAILED等阶段原因，不伪造result。各错误details使用JSON Pointer定位字段。Schema中的原始验证错误仅用于离线合成样例；生产处理器必须采用安全消息而非直接回显校验库文本。

## 语义校验与资料不足

Schema只检查结构，以下规则必须在生产校验实现中落实；tools/edd/validate_contract.py为离线参考实现，不作为Java服务依赖。

1. fact_id唯一；附件和冲突ID各自唯一；摘录的attachment_id必须存在。候选事实必须存在，adopted_fact_id必须在候选中；采用人工摘录必须已confirmed且有confirmed_at。未解决冲突采用值为null。
2. coverage起止时间不得倒置；query_status不是succeeded时complete只能false。snapshot完整不意味着history_events完整。未查询/失败/未提供不是“查无记录”。
3. result的input_version、parent_analysis_id、synthetic必须对应原请求；每个fact_refs同时存在于输入事实和evidence_index，来源应与输入一致。rule_refs只能引用服务端本次批准规则集合。
4. 建议status非suggestion时value必须null。确定等级/上报建议须有规则版本和有效规则引用；结果存在缺项或未解决冲突时为completed_with_gaps。引用存在仍不证明文字正确，金额/蕴含/遗漏检查归任务08/11。
5. 缺客户信息、收付明细或正式规则的合法请求仍受理；最终可输出completed_with_gaps。Schema错误不创建分析任务。参考最小请求与result-with-gaps示例，二者均无确定等级。

## 32个开发场景的输入路径

详见fixture-routing.json，包含每个场景ID、原文件、适配类型及负责的任务。原32例是早期设计格式，不直接声称符合v1。

- 22个SYN-V1场景：取每例input；customer_id→subject.customer_id，role/current_business_id→context，record_id→fact_id，coverage补齐显式来源时区/范围（未知保持null）；原payment_method=null→unknown。source/evidence_id按来源对象保留。额外的source_record_id没有时仅以原record_id作合成来源键。原expected、must_include等是评测元数据，不能送模型当事实。
- 10个DB场景：raw_tables经任务02显式适配为core_snapshots，保留表、复合键、字段和原始值；snapshot_type按七表映射。将subject_id/context映射到统一字段。不得由这些表造出events或反洗钱等级。旧approved_rating_rules_available等字段是开发期元信息，不能进入生产规则授权。
- 原事实中的未提供时间、来源、确认信息不可编造以通过Schema；列为待补充，只有协议允许字段使用null。若某旧样本需补齐必要元信息，应显式标注新增值为合成设定并保留派生关系。
- 适配完成后先过v1 Schema与语义校验，再进入范围、统计及AI处理；schema_version不能由LLM推断。

`.scratch`中的analysis-result-example.json为任务01之前的草案，正式响应以本Schema及docs/edd/examples为准；DB-001内容迁移由任务02/10完成，不直接把旧草案当服务响应。

## 验证方式与实际边界

安装tools/edd/requirements.txt的开发依赖后运行：

```text
python -m unittest discover -s tools/edd -p "test_*.py" -v
python tools/edd/validate_contract.py docs/edd/examples/request-minimal.json
```

本次用本机Anaconda自带jsonschema运行，无需联网安装。任务01没有新增Spring Bean、Controller、全局ObjectMapper或异常处理器，pom及既有Java源码未修改，现有启动路径与路由没有行为变化。已静态核对/api/edd命名空间无冲突；本机未发现Maven命令/包装器，因此未重跑完整Spring启动和核保JUnit回归，任务10/11需在可构建环境执行。

## 任务02实施更新

七表JSON适配已实现，详见[适配说明](core-json-adapter.md)。10个DB场景已有Java输出并通过本Schema；原22例仍属于后续事实处理测试。已找到本地缓存Maven，并完成205项Java回归测试，修正此前仅按PATH判断Maven不可用的记录；尚未进行真实Spring服务启动或AI/RAG验证。
