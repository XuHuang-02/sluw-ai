# 核保选路双模式实验

范围仍为 AutoSendBL.dealIssue，不接 dealTransUW、保单影像、RAGFlow、聊天历史或业务执行接口。两种模式输出相同的小结构，由同一套服务端逻辑核验和组装报告。错误不兜底、不自动重试。

## 页面使用

启动应用并登录，勾选“问题件选路实验”（原实验勾选项），使用下拉框：

- 条件模式（默认）：发送预计算条件，原有行为保持。
- 记录模式：发送校验后的当前/历史记录、记事本、任务、排除清单及完整性声明；不发送 contno、预计算条件、次数、候选项或对照答案。
- 双模式对比：同一输入独立调用两次，并列展示。单侧失败仍显示另一侧结果；窄屏改成上下排列。

记录模式使用同一决策表和互斥输出Schema，并补充 prompts/deal-issue-records.txt 的精确条件定义。条件模式与记录模式分别记录 promptHash。提供给模型的noFailedRules仍为已确认的固定话术映射，不代表读取自由文本进行语义判断。

结构化接口 POST /api/agent/trial/evaluate，请求为 {"question":"完整业务JSON字符串","mode":"CONDITIONS或RECORDS"}。复用 /api/auth/check 的登录状态，不新增开放认证路径。此接口使用普通JSON和登录Cookie，不使用旧聊天流的自定义AES包装；跨机器访问应使用HTTPS。原普通聊天接口不变。

响应包含 requestId、mode、version、promptHash、model、modelCallStarted、outcome、reason、stage、selection、decision、elapsedMs、callMs、inputTokens、outputTokens、totalTokens、text。selection是通过基础结构检查的模型小结构，可能选错；只有成功核验并组装报告后decision才有效。不能仅因selection存在就执行业务。页面默认费用未知。

## 在另一台电脑准备

1. 同步本次代码，使用已有Java/Maven及本机批准的模型配置启动项目。Python 3.9或更高即可运行工具，无需pip安装依赖。测试源按既有约定不上传，不影响评测工具使用。
2. 从项目根目录生成计划（此步骤完全不访问网络、不调用模型）：

```bash
python tools/routing_eval.py prepare --out routing-eval-runs/first
```

UOS若命令为python3，用python3替换。默认36案例×5次×2模式=360次。计划中交替安排模式，实际调用顺序执行，不与页面并发对比的耗时混算。

3. 逐条复核 docs/trial/deal-issue-cases.json 的 expectedStatus/expectedBranch，确认它们符合业务代码。在生成的 review.json 中填写真实 reviewer、reviewedAt，并在复核完成后设置 approved=true。保留 casesSha256，不手改哈希绕过案例版本检查。程序对照不等于人工业务验收。
4. 在浏览器正常登录，开发者工具Network里选一个已登录请求，复制其请求头Cookie的值（不要复制到聊天或提交到Git）。仅放到本次终端环境变量。

Windows PowerShell：

```powershell
$env:SLUW_EVAL_COOKIE = [System.Net.NetworkCredential]::new('', (Read-Host '粘贴已登录请求的Cookie值' -AsSecureString)).Password
```

UOS Bash：

```bash
read -rs -p '粘贴已登录请求的Cookie值: ' SLUW_EVAL_COOKIE
export SLUW_EVAL_COOKIE
```

工具不保存Cookie、不打印请求头，不会自动登录或绕过验证码。登录过期后应重新登录并更新环境变量。

## 执行真实评测（会调用已配置模型）

确认模型名与实际返回元数据一致，以下名称仅对应当前环境；若环境不同请替换。确认服务正常、不要在评测中修改模型配置/应用版本/提示词。

```bash
python tools/routing_eval.py run --out routing-eval-runs/first --base-url http://localhost:8089 --model deepseek-v3-2-com --execute
```

非本机地址必须HTTPS。模型连接密钥仍只配置在Java应用中，不传给Python工具。默认HTTP等待90秒，服务端默认响应预算50秒；如果修改服务端预算，相应增大 --timeout。超时不保证底层调用终止或停止计费。

- 结果逐次追加到 results.jsonl，含小结构结果和审计报告，不含完整输入原文或模型原文。仍应按业务数据保管。
- plan.json 固定案例版本及工具版本；environment.json 固定地址、期望模型、规则版本及两种模式提示词指纹。检测到漂移保存本次结果后停止，不混作同一稳定实验。
- 模型没有提供model元数据时无法核实服务端实际模型版本，需结合配置和服务端日志核查，不据此宣称完整可复现。
- 遇到调用前失败、模型调用失败、服务端错误或超时会停止；不会自动重试该次。格式或选路错误则记录并继续，以观察失败率。
- 同样的run命令可继续未开始的任务。已经写下PENDING的请求即使尚无结果也不会重发，因为无法确定是否已调用/计费。UNKNOWN/PENDING需结合requestId（若收到）及服务端日志人工核对，不能将缺失结果按正确或零费用处理。
- run.lock 防止同一目录并发运行。进程崩溃后若锁残留，确认旧进程已停止，再删除该目录内的run.lock；不要删除results.jsonl来强行续跑。
- 如更换机器，复制整个结果目录和完全相同的案例/工具，再在新机器配置登录Cookie；地址改变或版本改变应另建计划，旧结果保留。

结果目录 routing-eval-runs 已加入.gitignore。不要把Cookie或本机配置放入评测目录。

## 汇总和费用

```bash
python tools/routing_eval.py summarize --out routing-eval-runs/first
```

summary.json分别提供计划数、已尝试HTTP次数、确认的模型调用数、未知送达次数、调用前失败、有效正确率、失败原因、耗时和用量。

有效正确率：模型小结构的状态与目标节点符合人工确认预期 / 已确认发起的模型调用数。格式失败、错误选择、调用异常、超时均不能从已确认调用分母剔除。HTTP响应丢失时无法确定是否调用，单独列为deliveryUnknown，因此有未知送达时不是完整评测率。服务端报告失败和模型原始选择分开看，不将其混为模型选错。

费用默认未知。若公司批准的服务可按统一输入/输出单价估算，可创建本地价格JSON，包含 model、currency、inputPerMillion、outputPerMillion（两个单价需为已确认的非负数字），然后：

```bash
python tools/routing_eval.py summarize --out routing-eval-runs/first --prices 本地价格文件.json
```

模型名称不符、Token缺失、送达未知时费用仍未知。汇总的knownEstimatedCost仅为已知部分估算，不是完整账单；缓存命中、阶梯计价等未支持，不能冒充实际价格。晚返回Token可能仅在Java日志中，不会自动补回Python收到的超时响应，应按requestId补做核对。

## 工程验证与业务结论

工程验证使用合成样本和模拟模型，覆盖同一快照双输入隔离、错误不兜底、候选报告一致、登录校验、页面单侧失败、评测续跑及未知费用。真实模型360次评测需在批准环境执行；尚未执行前，不能宣称记录模式比条件模式更准确或更划算。
