> 本文为历史实验说明。当前选路实验请使用 [新操作与字段说明](deal-issue-routing.md)，旧模板已退役。

# 问题件提交试判

本次在原 sluw-ai 中增加聊天试验，不包含独立 assessment 工程，不执行业务查询或核保、下发操作。

## 当前推荐的局部实验

页面模板入口现指向“内部/机构下发前”实验，先从这一段试用。[逐步操作及预期结果](send-preparation.md)。

以下为此前整条问题件提交去向试判的说明。原模板仍可使用，未填写experimentStage或明确为ISSUE_FLOW时按此前整条流程分析。

## 使用

1. 按原方式启动 sluw-ai，进入原聊天页面。
2. 勾选“问题件提交试判（每次完整JSON）”。
3. 下载空模板，或者下载合成示例查看字段写法。
4. 填写一单脱敏业务信息，粘贴完整JSON发送。
5. 根据“处理去向、命中的代码条件、引用的输入记录、缺失信息”核对结果。
6. 补充信息时重新发送完整JSON。关闭开关后恢复普通问答。

试判绕过普通图流程、历史记忆、问题润色、工具调用和问答轨迹写库。仍使用原聊天加解密协议。模型先完成一次回答，再通过现有SSE接口返回，不是逐字流式；50秒超时会报告未完成。

仅本次JSON参与判断。当前首版不向RAGFlow发送保单资料；普通问答仍保留知识解释能力。试判后端不将回答或输入加入普通问答历史，后续解释应明确粘贴要解释的内容，不依赖上一单事实。

## 输入约定

[空模板](archive/issue-submission-template.json)默认全部资料未获取，不能用来演示完整判断。[合成示例](archive/issue-submission-example.json)提供可填写的完整记录结构，非真实保单。

| 字段 | 来源和含义 |
| --- | --- |
| contno | 本单标识，使用脱敏值 |
| maxuwno | 业务提供的最新核保轮次，不由AI根据部分记录推算 |
| currentErrors | 最新轮次lcuwerror记录 |
| historyErrors | 所有其他历史轮次lcuwerror记录，不仅上一轮 |
| lwmission、lbmission | 本单任务及历史任务记录 |
| lwnotepad | 本单记事本记录 |
| autoallotbyerr | ldcode中对应codetype的code字符串数组 |
| completeness | 上述各组资料是否完整，使用true/false/null |
| appntno | 需要定位外2或投保人契调对象时提供脱敏投保人编号 |

lcuwerror记录保留：contno、uwno、uwrulecode、uwerror、lettertype、autoflag、positivesign、peitem、insuredno、serialno、proposalno。
任务记录保留：missionprop1、activityid、lastoperator。记事本保留：otherno、noteflag。

数组null/缺失代表未获取。[]且完整性为true才能表示查无记录。每行显式null代表数据库NULL；字段遗漏代表未知，不能替代SQL的is null条件。不要将未知历史写成空数组并声明完整。

单次上限60000字符。非JSON、重复字段、尾部多余JSON及错误容器类型会在调用模型前拒绝；业务分支和资料充分性仍由模型判断，不用硬编码决定处理结果。

## 流程依据

archive/issue-submission-trial.txt保存AutoSendBL中dealIssue及相关依赖方法的只读文本，来源是用户提供的GBK原文件，保留原始行号。这不是自动同步机制；业务代码修改后需人工更新说明并重新核对案例。

注意：isAllAuto为true表示存在非自动处理规则；新增内部问题的判断顺序、历史重复规则的匹配粒度、count(distinct uwno)和SQL NULL语义均不可改写。代码return或拟调用uwPass不等于已转人工、已通过或已下发。

所有流程去向由真实模型调用产生；程序仅处理输入格式、消息隔离和服务异常。固定提示词不能保证模型永不出错，业务验收需逐项核对。

## 验证

原项目依赖解析后编译成功。自动化工程测试7项通过（0失败/错误/跳过）：

- 试判流式和同步请求不调用普通AgentService。
- 默认请求仍走普通问答；SSE保留DONE结束标识。
- 两次请求只包含各自JSON及系统说明。
- 无效JSON不调用模型；缺失历史保持null。
- 服务异常不暴露原始错误，不编造业务去向。

前端内联JavaScript通过node --check。未进行浏览器视觉和真实模型/业务环境联调。

[16个业务对照案例](cases.json)包含输入、预期去向和代码依据，覆盖主要分支和缺失情况。预期结果为依据代码整理，等待业务人工核对；未调用真实模型执行这些案例，不能把工程测试通过解释为模型判断准确率达标。试用时对每例记录实际去向、条件/引用是否正确及差异原因。

测试复现：JDK17与Maven可用时运行 mvn -Pdev test。本机JDK24使用测试兼容参数：

~~~powershell
mvn -Pdev '-DargLine=-Dnet.bytebuddy.experimental=true -Djdk.net.unixdomain.tmpdir=C:/Code/Python_Code/sluw-ai/.scratch/socket-temp' test
~~~

目录需提前创建。该参数仅针对本机测试库/JDK兼容性，不改变生产设置。原项目仍依赖本机厂商JAR及公司批准的模型、配置和服务，测试未验证真实部署。

真实保单只可发送给公司批准的模型服务。本次未启动原应用连接真实业务系统、未发送真实业务资料。
