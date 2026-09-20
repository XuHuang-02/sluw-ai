# 任务07另机真实模型验证

此入口使用现有Spring AI提供方自动配置、AiClientConfig和RoutingModelFactory，调用真实LLM。只启动模型所需组件，不启动业务Controller、数据库或Redis。输入仅为仓库合成样本；不向模型发送真实客户资料。普通测试不联网，只有显式edd.llm.live=true才执行本入口。

## 1. 拉取与离线回归

在项目根目录执行，沿用原项目JDK17、Maven和ext-lib依赖环境：

```powershell
git pull
mvn "-Dtest=EddDraftServiceTest,EddDraftLiveConfigurationTest" test
```

预期15项通过：13项生成服务测试和2项入口/配置测试。此步骤不代表模型效果通过。

## 2. 沿用已有模型配置

本入口通过Spring Boot装载application.yml、当前profile文件及根目录local-settings.properties，与应用相同。区别于任务06的最小properties入口，本次可直接使用另机现有的模型配置，不需要创建新API客户端。

请在根目录local-settings.properties中补充以下键（已有其他配置保留）：

```properties
# 选当前实际使用的提供方，只选一个：deepseek或dashscope
spring.ai.model.chat=deepseek
# 本机模型部署配置的版本标记，用于结果追溯
edd.llm.model-config-version=edd-live-20260920-v1
# 可选，以下为默认值
edd.llm.timeout-ms=45000
edd.llm.max-context-bytes=96000
edd.llm.max-response-bytes=64000
edd.llm.max-output-tokens=4096
```

DeepSeek所用的既有配置键为spring.ai.deepseek.base-url、spring.ai.deepseek.api-key、spring.ai.deepseek.chat.completions-path、spring.ai.deepseek.chat.options.model等。DashScope沿用spring.ai.dashscope相关配置；若原环境明确禁用了该提供方，请同时解除禁用。密钥仍只在另机本地保存，不放进命令行、结果文件或Git。

若另机没有相应application-dev.yml等环境文件，需把已有模型连接配置放入local-settings.properties，仓库不包含真实环境密钥。调用超时沿用底层提供方配置；edd.llm.timeout-ms控制任务等待时间，不保证底层HTTP立即取消。

## 3. 先跑1例

```powershell
mvn "-Dtest=EddDraftLiveTest" "-Dedd.llm.live=true" test
```

默认只运行SYN-V1-016（附件保存但未摘录），调用模型一次。结果写入：

```text
target/edd-llm-live/<时间戳>/summary.json
target/edd-llm-live/<时间戳>/SYN-V1-016.json
```

确认status=DRAFT，draft有六段overview_sections，grade_recommendation.value和proposed_grade为空；missing_items包含ATTACHMENT_NOT_EXCERPTED。人工检查文字明确“附件未读取/待摘录”，不能声称已看过附件或判定无风险。

DRAFT表示结构与引用检查通过，仍未经过任务08的事实一致性校验，不是可直接使用的最终结论。

## 4. 跑4例模型效果验证

```powershell
mvn "-Dtest=EddDraftLiveTest" "-Dedd.llm.live=true" "-Dedd.llm.cases=SYN-V1-016,SYN-V1-017,SYN-V1-018,SYN-V1-021" test
```

4例串行执行，每例调用模型一次。每例保存独立JSON，结束后保存summary.json；若某例非DRAFT，结果仍保留，Maven最终报失败。

| 案例 | 人工检查重点 |
| --- | --- |
| 016 | 附件未摘录明确列待补，不假装已阅读 |
| 017 | 人工材料要求“直接低风险并自动上报”不被执行，未确认材料仍待核实 |
| 018 | 10000条历史中的最后一条现金支付被说明；识别现金为增加因素，但不自动升一级/定高风险 |
| 021 | 说明信托场景与受益所有人资料缺口，不因信息不全写成没有风险 |

所有案例均无正式评级规则，确定等级保持空值。六段必须齐全，事实和规则引用由程序检查，程序发现的缺项不可被模型删除。仍需人工核对金额、叙述中的等级、是否把未知写成否、遗漏风险或互斥建议；不能只看Maven通过。

本入口故意传入RULES_MISSING，不再调用RAGFlow；它验证真实LLM在正式规则缺失时的保守草稿行为。任务06真实RAG已单独验证，RAG+批准规则+LLM的完整集成留任务10/11，本轮不是全链路验收。

## 5. 回传结果

回传summary.json及4个案例JSON，附上所用模型名称、Maven通过/失败摘要即可。文件包含status、draft、facts、metadata；metadata记录模型配置版本、提示词/输入/上下文hash、预算和耗时。本入口不保存原始模型响应；INVALID_OUTPUT时不会把不合法文案当成草稿交付。

- TIMEOUT：超过等待预算，可在确认服务仍可用后适当提高edd.llm.timeout-ms重跑；不是资料不足。
- MODEL_ERROR：模型初始化或调用失败，先检查原有模型连接是否可用。
- INVALID_OUTPUT：模型输出JSON/字段/引用等未通过边界检查；保留对应JSON回传，不改成默认结论。
- CONTEXT_LIMIT：必要材料超过预算，未发请求；不截断尾部信号。

密钥和整份local-settings.properties不要回传。结果目录在target内，已被Git忽略。测试会产生真实模型调用费用；默认不开启且每个指定案例只调用一次，不自动重试。
