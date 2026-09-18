# 自然人尽调开发入口

当前交付：任务01 JSON契约、任务02核心表级JSON适配。没有可调用的尽调HTTP端点，不需配置或运行AI/RAG即可测试适配。

- [接口与字段契约](contract-v1.md)
- [核心JSON适配及调用示例](core-json-adapter.md)
- 正式Schema：`src/main/resources/edd/contracts/v1/edd.schema.json`
- Java实现：`src/main/java/com/sinosig/sluw/application/edd/service/EddInputAdapter.java`
- 可运行的10例合成输入：`tools/edd/fixtures/core-db-cases.json`

在仓库根目录，使用已配置的JDK和Maven：

```text
mvn -Dtest=EddInputAdapterTest test
python -m pip install -r tools/edd/requirements.txt
python tools/edd/check_adapter_outputs.py
python -m unittest discover -s tools/edd -p "test_*.py"
```

Python校验读取Java测试生成的target/edd-adapter-output，先运行Java测试。项目原有ext-lib依赖由开发环境提供，本次不上传第三方jar或环境配置。普通Java17环境不需本机JDK24的ByteBuddy额外参数。

fixture-routing.json中的22个legacy场景仅记录后续设计映射，尚未完成适配；9MB原始合成全集不随本次代码分发，测试只要求已实现的10个DB场景。真实客户案例、临时提取文件和本地生成脚本均不包含在本次提交中。

真实AI/RAG由另一台电脑后续运行，当前代码仅执行本地JSON转换与验证。
