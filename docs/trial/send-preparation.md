# 内部／机构问题件下发前实验：直接操作

## 先跑一遍示例

1. 按原来的方式启动 sluw-ai（例如在IDE运行 SluwAssistantApplication），不需要启动其他工程。
2. 打开原聊天页面；若页面已打开，刷新后确认出现“问题件试判：内部/机构下发前”。
3. 点击“填入合成示例”。按钮只填输入框并开启试判，不自动发送；已有未发送内容时会询问是否替换。
4. 点击发送，等待模型回答（最多约50秒，使用项目当前配置的模型）。
5. 核对下面的预期含义；不要仅看模型是否回答“下发”。

示例只包含机构候选，lcissuepol有一条未回复、未发送的机构内容，模拟已到达sendnbwtj调用前。预期分析是：

- 没有内部候选话术，但存在lettertype=6。
- 拟从lcissuepol[0].issuecont取得内容，生成一条内部问题件。
- 内部件的backobjtype=1、issuetype=000006，不能改成SLNB006。
- 只有后续dealData和dealNoticedata成功，代码才尝试更新候选autoflag和机构发送标记。
- 这些是代码拟执行的计划，不是“已发送”“已核保通过”。

这是人工核对预期，不是程序强制写死的答案。

## 再换成你的记录

通过“下载本实验模板”或在示例上修改，保留下面的输入结构：

| 字段 | 填什么 |
| --- | --- |
| experimentStage | 固定 BEFORE_SEND_INTERNAL_AGENCY |
| entryConfirmed | 已确认业务到达sendnbwtj调用前填true；未知填null，不要为了让AI出结论强行填true |
| contno | 当前保单的脱敏编号 |
| candidateErrors | 本次准备传给sendnbwtj的完整候选列表，保留4/6类型及业务字段，不是全部历史核保记录 |
| lcissuepol | 本单机构问题件记录；保留代码筛选所需状态和真实问题内容 |
| completeness.candidateErrors | 候选列表是否完整 |
| completeness.lcissuepol | 本次提供的本单机构记录查询范围是否完整 |

候选记录保留：contno、uwno、uwrulecode、lettertype、uwerror、insuredno、serialno、proposalno。

机构记录保留：contno、backobjtype、issuecont、issuetype、senddate、replyresult、standbyflag1。问题内容取issuecont；不要用候选uwerror替代。

未获取数组填null，完整性填false。已完整查询且无记录才用[]和true。记录字段缺失表示未知，显式null表示数据库NULL；不要把日期未获取写成null冒充“未发送”。

这个实验不再要求整条dealIssue链路的记事本、历史轮次、年龄或保额。它仅分析已选定候选的下发准备；不能据此证明前置筛选正确、整单应当进入本分支或实际调用一定成功。

## 建议依次试的场景

[8个对照案例](send-preparation-cases.json)，每次只复制一例的input对象到聊天框，不把expected发给模型：

1. 只有机构候选：从机构内容伴生内部件。
2. 内部与机构都有：使用已有内部话术，不额外生成第二条伴生件。
3. 只有内部候选：走内部内容路径，不无端索要机构内容。
4. 机构内容未获取：说明缺失，不认定查无、不改用候选话术。
5. 机构记录完整且为空：dealNoticedata返回false。
6. 机构有记录但内容全空白：指出源码没有第二次非空拦截，不能说“代码已拒绝空内容”。
7. 候选列表完整且为空：sendnbwtj直接return。
8. 入口未确认：仅给条件性模拟，不给无条件下发建议。

每次改完都发送完整JSON。回复仍为：处理去向、代码条件、引用输入、缺失信息。可记录模型实际回答与预期差异后交给开发修正或业务核对。

## 实现和边界

- 在现有IssueSubmissionTrialService按experimentStage选择局部系统提示词；原始完整去向模式（无此字段或ISSUE_FLOW）保持兼容。
- 新提示词只包含sendnbwtj及dealNoticedata的实际代码，不让模型重新遍历全部提交流程。
- 没有修改AutoSendBL.java，没有接入业务数据库或下发接口，没有恢复模块一二，没有新增自动审批。
- 没有将SLNBNUCC0005设为额外强条件，没有替换内部件000006，没有让AI改写内容或选择新业务政策。
- 本单全部符合条件的机构记录可能被更新标记，而非只更新某一条候选对应机构件；回复应说明这种影响范围。
- 仍使用公司批准的现有模型；不调用真实服务进行本次自动化测试。试验依据是固定代码文本，业务源码变化后需同步核对。

## 本轮验证

原工程重新编译并测试通过：11个工程测试，0失败、0错误、0跳过。包括旧分流回归、新阶段隔离、输入格式、未获取数据不被改写和模型调用次数。8个对照场景均经过请求链路的模型替身测试，但没有验证真实AI的判断准确率。

页面JavaScript语法检查通过；真实模型与浏览器视觉联调尚未执行。运行时若没有可用的批准模型，页面会返回服务未完成提示，而不是模拟一份业务判断。
