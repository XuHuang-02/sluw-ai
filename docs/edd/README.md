# 自然人尽调开发入口

当前交付：任务01 JSON契约、任务02核心表级JSON适配、任务03事实范围与冲突准备、任务04全历史统计与现金信号、任务05规则包与建议判定、任务06独立RAG规则检索（本机模拟测试已通过）。没有可调用的尽调HTTP端点，不需配置或运行AI/RAG即可测试适配。

- [接口与字段契约](contract-v1.md)
- [核心JSON适配及调用示例](core-json-adapter.md)
- [事实范围、去重与字段冲突](fact-preparation.md)
- [全历史统计、状态轨迹与现金信号](history-statistics.md)
- [规则包版本与建议判定](rule-evaluation.md)
- [RAGFlow尽调规则检索](rule-retrieval.md)
- 正式Schema：`src/main/resources/edd/contracts/v1/edd.schema.json`
- Java实现：`src/main/java/com/sinosig/sluw/application/edd/service/EddInputAdapter.java`
- 可运行的10例合成输入：`tools/edd/fixtures/core-db-cases.json`

在仓库根目录，使用已配置的JDK和Maven：

```text
mvn "-Dtest=EddInputAdapterTest,EddFactServiceTest,EddHistoryServiceTest,EddRuleServiceTest,EddRuleRetrievalTest" test
python -m pip install -r tools/edd/requirements.txt
python tools/edd/check_adapter_outputs.py
python tools/edd/check_rule_outputs.py
python -m unittest discover -s tools/edd -p "test_*.py"
```

Python校验读取Java测试生成的target/edd-adapter-output，先运行Java测试。项目原有ext-lib依赖由开发环境提供，本次不上传第三方jar或环境配置。普通Java17环境不需本机JDK24的ByteBuddy额外参数。

fixture-routing.json中的22个legacy场景记录完整分析链路的后续设计映射；任务03单独迁移10个事实准备场景到fact-scope-cases.json，尚不代表完整分析已实现；9MB原始合成全集不随本次代码分发，测试只要求已实现的10个DB场景。真实客户案例、临时提取文件和本地生成脚本均不包含在本次提交中。

真实AI/RAG由另一台电脑后续运行，当前代码执行本地JSON转换、事实准备和确定性统计。任务05累计验证：122项相关Java测试、30项Python契约测试通过；32组Java实际建议输出符合v1契约。默认不装载正式规则，建议等级保留为空。

任务06：131项相关Java测试通过，包含独立检索fake、HTTP模拟及旧核保检索契约验证；真实RAGFlow连接和召回效果待另机联调。
