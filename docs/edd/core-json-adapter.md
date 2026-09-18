# 核心表级JSON适配

任务02已实现。入口类为`com.sinosig.sluw.application.edd.service.EddInputAdapter`，无Spring Bean注册、不访问数据库或AI/RAG，不修改现有Controller、模型配置及pom依赖。

## 调用方式

```java
var adapter = new EddInputAdapter();
var context = new EddInputAdapter.Context(
    "snapshot-001", true, "risk_review", "synthetic-core",
    null, "core-structure-v1", null);
var adapted = adapter.adaptCoreTables(coreJson, context);
var request = adapted.request(); // v1规范化请求
var issues = adapted.issues(); // 必须保留，交给事实准备与待核实项处理
var interpretations = adapted.interpretations(); // 原码、已知标签或缺码表状态
var provenance = adapted.provenance(); // factId、输入JSON Pointer和原始行
```

`coreJson`使用`tools/edd/fixtures/core-db-cases.json`中每个case的input对象结构：subject_id、context、analysis_as_of、raw_tables、coverage，可带旧样本的approved_rating_rules_available=false和codebook_version；不接收客户端approved=true。请求元信息由调用方显式传入Context，不从表内修改日期猜采集时间。Context.synthetic和sourceId由可信上游设置，不根据客户ID前缀推断。

输出request是正式v1输入而非风险分析结果；issues、interpretations、provenance属于适配侧旁路信息，不能丢弃，也不能随意加进拒绝未知字段的v1请求。任务03将待核实诊断纳入事实处理上下文，任务10编排时保留这些信息。

## 映射行为

| 表 | snapshot_type | 来源主键 |
| --- | --- | --- |
| LCCONT | policy | CONTNO |
| LCPOL | product | POLNO |
| LCDUTY | duty | POLNO、DUTYCODE |
| LCPREM | premium_plan | POLNO、DUTYCODE、PAYPLANCODE |
| LCGET | benefit | POLNO、DUTYCODE、GETDUTYCODE |
| LCCONTSTATE | policy_state | CONTNO、INSUREDNO、POLNO、STATETYPE、STARTDATE |
| T_SLIS_LC_EXPANSION | policy_extension | PRTNO |

表名接受大小写及SLISDATA前缀，字段转大写。同一输入存在大小写别名碰撞时拒绝，避免覆盖。未知表拒绝并要求显式适配；未知标量字段保留并返回UNKNOWN_CORE_FIELD；嵌套字段对象/数组拒绝。

字段定义随应用打包于`src/main/resources/edd/core/dictionary-v1.json`，含7表376字段、源工作表行号与原文件hash；这是固定字段解释版本，不是数据库DDL执行器。源主键缺失时保留行和SOURCE_KEY_INCOMPLETE，不生成虚假的数据库主键。

NUMBER/FLOAT用BigDecimal转普通十进制字符串并移除无意义尾零；INTEGER以精确整数归一。文本、日期和编码必须为字符串，不能把数字证件号转换后假装前导零完整。日期原值保留在values，不自动补时区或补零点。数字超大/非有限/非整数INTEGER等返回安全错误。原始值由provenance.originalRow保留。

每行一个fact_id，基于规范表名与排序后的完整归一行计算摘要并附重复序号。不同快照更新分开，同主键重复行仍保留，去重属于任务03；不跨表求和、不将快照转换为events。

PAYMODE标签来自已提供字典，保留原码；1/2仅产生现金方式线索和CASH_PAYMENT_UNCONFIRMED诊断。NEWPAYMODE、GETMODE、币种及未定义标签保持codebook_required，不继承其他字段码表。标签不构成已支付、无现金或风险评级结论。

缺受益人关系、完整主档、支付/保全/理赔明细及风险来源时显式返回缺口；七表不能支持这些类别的完整覆盖。如果输入宣称这些类别complete=true，则降低为false并返回COVERAGE_NOT_SUPPORTED_BY_PAYLOAD，不默默信任快照声明。源查询成功/失败状态保留，不将失败改为空记录成功。

## 验证与本机运行

```text
mvn -Dtest=EddInputAdapterTest test
python tools/edd/check_adapter_outputs.py
python -m unittest discover -s tools/edd -p "test_*.py"
```

运行Java测试后生成`target/edd-adapter-output/DB-001.json`至DB-010.json，Python交叉校验读取的是实际Java输出，不是另写一套适配。10个合成场景输入已移到可版本控制的tools/edd/fixtures目录，去除了源文件个人绝对路径；原始样例未覆盖。

本机已发现`.scratch/tools/apache-maven-3.9.9/bin/mvn.cmd`与`.scratch/m2`离线缓存；JAVA_HOME使用C:/Code/JDK24，编译目标release17。完整本地回归命令为：

```text
mvn -o -Dmaven.repo.local=.scratch/m2 -DargLine=-Dnet.bytebuddy.experimental=true test
```

上面的ByteBuddy参数用于本机JDK24与现有Mockito组合，其他JDK环境按实际支持情况运行。验证结果：205项Java测试通过（含26项适配测试），10个Java输出符合v1 Schema与引用约束，29项既有契约测试通过。仅编译与单元回归，未启动真实业务服务；真实RAG/AI仍由用户另一台电脑运行。

## 后续边界

任务03负责客户关联、范围过滤、业务去重和冲突；04负责统计、状态归并和现金事实判断。本任务不把DB样本中的分析预期当作适配阶段已经给出的结论。22个早期业务事件样本的格式迁移仍为开发样本准备事项，不影响本任务七表适配接口；它们未被误报为已通过Java业务分析。
