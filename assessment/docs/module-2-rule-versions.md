# 模块二：规则版本管理与依据接入

实现与验证日期：2026-09-10。工程功能已实现；真实业务规则与批准服务尚未联调。

## 与原有逻辑的关系

原问答仍通过 RagFlowClient 检索相关片段，将上下文交给模型生成回答。本次未修改 src/ 下的问答代码。

新增 RuleCatalog 管理经过确认的必查清单，按机构、渠道、产品及明确版本读取，将固定规则交给 RuleEngine。没有自动使用最新版本的回退，没有按相似度决定检查项目，也没有自动将模型生成的规则发布。

模块一的公开 evaluate 方法保持兼容，只新增内部入口，用于传入条件缺失、业务标记的冲突等原因。这些项目保留为无法完成，并继续执行后续项目。

## 已实现的管理流程

1. 从批准的 RAGFlow 数据集按数据集、文档、切片 ID 精确获取原文及 SHA-256。
2. 人工将原文整理为结构化草稿，区分 COMMON 公共规则和 PRODUCT 产品规则。草稿有独立 ID、创建人、机构及时间。
3. 管理员确认完整检查范围、公共与产品规则均已核查，并填写确认说明。执行条件不完整的必查项必须填写 blockedReason；它仍保留在清单中。
4. 正式模式确认时重新读取每一条精确来源；失败或内容变化阻止确认，不保存部分确认。模拟模式允许使用合成来源，但永久保留模拟标记。
5. 发布固定版本。草稿、确认记录及发布版本均不可覆盖；修改创建新草稿，业务版本修改使用新版本号。
6. 判断时读取已发布快照，校验存储完整性与来源内容指纹，调用模块一。既有可信快照无需依赖 RAGFlow 当前在线状态，平台后续更新不改变历史版本。

版本命名由业务提供，目前按明确指定版本选择，不自行推断生效日期。确认、发布可由同一个管理员完成，尚未引入未确认需求中的双人审批。

同编码不同分支保留各自来源；重复编码+分支或重复检查项 ID 被拒绝。公共及产品规则共同检查；当前不提供自动覆盖/抑制规则功能。冲突需由业务在有关项目的 blockedReason 中登记，系统不会自动识别任意自然语言冲突，也不会默认产品规则优先。

疾病手册可作为来源；尚未建立业务确认映射时，登记无法完成原因，不自动生成问题件条件。AI辅助整理是可选能力，本模块不包含自动话术转可执行规则的模型调用。

## 文件与接口

- RuleCatalog.java：草稿、确认、发布、读取与判断核心衔接。
- RuleStore.java：本地不可覆盖记录，JSON内容指纹校验。
- RuleSources.java：精确 RAGFlow 来源、授权数据集检查及异常分类。
- RuleApi.java：管理接口与独立管理员账号鉴权。
- [合成草稿](../examples/rule-draft.synthetic.json)：可演示两条 YBCR0045 分支。

默认地址前缀为 http://localhost:8091/assessment。

| 方法与路径（前缀后） | 用途 |
| --- | --- |
| POST /api/rules/sources | 输入 datasetId、documentId、chunkId，获取原文和指纹 |
| POST /api/rules/drafts | 提交 Proposal，得到草稿 ID |
| GET /api/rules/drafts/{id} | 查看原始草稿 |
| POST /api/rules/drafts/{id}/confirm | JSON请求体包含 note 确认说明 |
| POST /api/rules/drafts/{id}/publish | 发布已确认草稿，使用 application/json |
| GET /api/rules/versions?channel=...&product=...&version=... | 查看固定发布版本与确认记录 |

确认请求示例：

~~~json
{"note":"已确认公共规则、产品规则及本版本必查范围，未完成项目已明确登记"}
~~~

修改草稿时重新提交完整 Proposal，得到新草稿 ID。没有覆盖更新接口。GET草稿始终返回原始草稿，已发布版本返回完整确认及发布信息。

本模块不提供规则列表页面、人员管理页面或保单判断 HTTP 入口。RuleCatalog.evaluate 为后续逐单流程提供服务入口，调用方必须使用服务器已认证 Actor，不能把前端身份声明当作 Actor。

## 运行配置

~~~powershell
mvn -f assessment/pom.xml package
java -jar assessment/target/assessment-0.1.0.jar
~~~

通过环境变量提供配置，不在仓库填写真实密码：

| 环境变量 | 含义 |
| --- | --- |
| ASSESSMENT_ADMIN | 独立管理员账号，默认 admin |
| ASSESSMENT_ADMIN_PASSWORD | 必填；为空时所有接口拒绝访问 |
| ASSESSMENT_ADMIN_ORG | 本部署服务的机构，默认 pilot |
| ASSESSMENT_SIMULATION | 默认 true；模拟标记写入草稿并保留 |
| ASSESSMENT_DATA_DIR | 数据目录，默认 ./assessment-data |
| ASSESSMENT_RAG_URL | 平台根地址，不含 /api/v1 或 /retrieval |
| ASSESSMENT_RAG_KEY | 批准的服务密钥 |
| ASSESSMENT_RAG_DATASETS | 本机构授权数据集ID，逗号分隔；默认空，禁止来源读取 |

API 使用 HTTP Basic；身份、机构和确认人由服务端生成。仅提供一个配置的独立管理员账号；未接入公司 SSO 或多账号管理，配置 sso-url 不会启用身份服务。真实远程部署须通过公司批准的 HTTPS 入口。当前为 API-only，带 Origin 的浏览器请求拒绝；后续页面模块需加入完整的会话及 CSRF 方案。

RAGFlow 接口实现依据[官方 SDK 的 list_chunks 方法](https://github.com/infiniflow/ragflow/blob/main/sdk/python/ragflow_sdk/modules/document.py)：GET /api/v1/datasets/{dataset}/documents/{document}/chunks，按 id 过滤，并核对返回切片唯一性。真实部署版本仍需联调。连接超时5秒，请求超时15秒。不自动重试、跟随重定向或记录原始响应、密钥及规则全文。未配置服务时可以启动应用并管理模拟草稿。

## 验证结果

本机 Maven 3.9.9、JDK 24，编译目标 Java 17：

| 测试 | 数量 | 结果 |
| --- | ---: | --- |
| RuleEngineTest：模块一回归 | 80 | 通过 |
| RuleCatalogTest：发布、隔离、版本及核心衔接 | 23 | 通过 |
| RuleApiTest：Spring应用、HTTP接口、身份及发布流程 | 8 | 通过 |
| RuleSourcesTest：本地HTTP替身、来源及异常分类 | 13 | 通过 |
| 合计 | 124 | 0失败、0错误、0跳过 |

覆盖三类整单结论、条件不完整时继续检查、未确认不能发布/判断、版本与确认记录不可覆盖、同编码分支与规则层级保留、跨机构拒绝访问、审核角色不能管理规则、来源改变阻止确认、发布后快照独立、存储损坏拒绝读取、模拟标记跨环境保留。

首轮103项业务测试通过，新增网络测试曾因本机 JDK24 在短文件名 TEMP 目录创建 Unix-domain 套接字失败而无法启动。已将测试套接字临时目录设为 Maven target，标准 test 命令全部通过；没有跳过测试或修改系统设置。若本机运行服务时遇到同一异常，可创建项目临时目录，通过 JVM 参数 -Djdk.net.unixdomain.tmpdir=<目录完整路径> 指定。

~~~powershell
& '.scratch/tools/apache-maven-3.9.9/bin/mvn.cmd' -o -f assessment/pom.xml '-Dmaven.repo.local=C:/Code/Python_Code/sluw-ai/.scratch/m2' '-Dstyle.color=never' test
~~~

## 打包应用实测

自动化测试通过后执行 package（不重复运行已通过的测试），可运行 JAR 构建成功。另启动实际 JAR，绑定本机随机端口，用临时账号和合成草稿实测：未登录401、未确认发布404、创建/确认/发布/读取成功、重复发布409、两个同编码分支完整保留且标为模拟。第二次以空密码启动，验证请求仍返回401。测试进程已停止，未留下后台服务。

## 交付边界

- 存储面向单实例试点，不支持多实例共享目录、跨节点事务或自动灾难恢复。目录需由部署账号独占管理，临时文件不会作为有效记录读取。
- 内容指纹检测意外损坏，不是防管理员篡改的数字签名。备份与保留期限按公司要求确定。
- 整个版本文件损坏时拒绝读取；不会部分加载损坏版本后输出非问题件。
- 可信快照判断不访问最新RAGFlow；来源导入/确认失败明确返回错误。模型或影像检查失败仍由模块一保留未完成项并继续其他检查。
- 用例全部为合成资料、本地HTTP替身或测试身份。不包含真实RAGFlow、模型、影像、SSO联调及业务准确率验收。
- 后续模块完成逐单输入、后台进度、报告页面/PDF、复核和补件。
