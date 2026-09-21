# 自然人尽调开发入口

当前交付：任务01 JSON契约、任务02核心表级JSON适配、任务03事实范围与冲突准备、任务04全历史统计与现金信号、任务05规则包与建议判定、任务06独立RAG规则检索（用户已确认另机真实验证通过）、任务07风险概述与建议稿生成（本机fake模型测试通过）。没有可调用的尽调HTTP端点，不需配置或运行AI/RAG即可测试适配。

- [接口与字段契约](contract-v1.md)
- [核心JSON适配及调用示例](core-json-adapter.md)
- [事实范围、去重与字段冲突](fact-preparation.md)
- [全历史统计、状态轨迹与现金信号](history-statistics.md)
- [规则包版本与建议判定](rule-evaluation.md)
- [RAGFlow尽调规则检索](rule-retrieval.md)
- [风险概述与建议稿生成](draft-generation.md)
- [结果校验与交付边界](result-validation.md)
- [任务07—08另机真实模型验证](llm-live-testing.md)
- 正式Schema：`src/main/resources/edd/contracts/v1/edd.schema.json`
- Java实现：`src/main/java/com/sinosig/sluw/application/edd/service/EddInputAdapter.java`
- 可运行的10例合成输入：`tools/edd/fixtures/core-db-cases.json`

在仓库根目录，使用已配置的JDK和Maven：

```text
mvn "-Dtest=EddInputAdapterTest,EddFactServiceTest,EddHistoryServiceTest,EddRuleServiceTest,EddRuleRetrievalTest,EddDraftServiceTest,EddResultValidatorTest" test
python -m pip install -r tools/edd/requirements.txt
python tools/edd/check_adapter_outputs.py
python tools/edd/check_rule_outputs.py
python -m unittest discover -s tools/edd -p "test_*.py"
```

Python校验读取Java测试生成的target/edd-adapter-output，先运行Java测试。项目原有ext-lib依赖由开发环境提供，本次不上传第三方jar或环境配置。普通Java17环境不需本机JDK24的ByteBuddy额外参数。

fixture-routing.json中的22个legacy场景记录完整分析链路的后续设计映射；任务03单独迁移10个事实准备场景到fact-scope-cases.json，尚不代表完整分析已实现；9MB原始合成全集不随本次代码分发，测试只要求已实现的10个DB场景。真实客户案例、临时提取文件和本地生成脚本均不包含在本次提交中。

真实AI/RAG由另一台电脑后续运行，当前代码执行本地JSON转换、事实准备和确定性统计。任务05累计验证：122项相关Java测试、30项Python契约测试通过；32组Java实际建议输出符合v1契约。默认不装载正式规则，建议等级保留为空。

任务06：131项相关Java测试通过，包含独立检索fake、HTTP模拟及旧核保检索契约验证；真实RAGFlow连接和召回效果待另机联调。

RAG复用调整：尽调通过原RagFlowClient指定库重载读取ConfigReader，配置真实链路与application.config前缀见rule-retrieval.md；不再创建独立HTTP客户端。

任务07：采用独立尽调提示词，复用既有模型隔离与ChatClient；模型仅生成六维概述/处置建议，固定评级和上报建议由程序保留。长历史全量分组，组引用可展开到全部事实。当前输出为待任务08校验的草稿，不是已完成报告。13项新增测试通过，全量328通过、1项真实联网测试默认跳过。

2026-09-21：任务07接入Spring AI DTO结构化输出，新增脱敏错误分类及最多一次格式修复（共用总预算）；原生JSON_OBJECT为显式另机验证开关。此项记录为任务07阶段，任务08后续实现见result-validation.md；原实现详见draft-generation.md和llm-live-testing.md。

任务08已接入独立结果校验与同预算的一次修复。业务链路使用analyze，低层generate仍只返回未校验草稿；当前边界及验证说明见result-validation.md。
