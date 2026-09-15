# sluw-ai

Java 核保问答应用。此仓库用于私有版本管理。

## 项目结构

- `src/`：原 Spring Boot 核保问答应用。
- `CONTEXT.md`：业务术语。
- `docker/`：原应用部署脚本。

## 当前需求

2026-09-11：按用户要求移除此前新增的独立判断模块及规则管理模块。已按重新确认的范围在原聊天中加入问题件提交试判。

- 当前需求记录：[对话试判](.scratch/chat-issue-review/spec.md)。
- 旧独立系统方案停止执行，历史实现可从 Git 历史查看。
- 普通问答保留；开启试判后使用完整JSON独立调用模型，不接入业务数据库或执行业务流转。当前先试内部/机构下发前的一小段，操作见 [直接试用步骤](docs/trial/send-preparation.md)。

## 原应用本地配置

真实环境配置、厂商JAR、许可证与运行资料仅保留在本机，不上传 GitHub。

1. 将所需的 `config/examples/application-<环境>.example.yml` 复制至 `src/main/resources/application-<环境>.yml`，填写环境变量或本机值。
2. 将 `config/examples/local-settings.example.properties` 复制为项目根目录的 `local-settings.properties`，配置原登录用户及业务接口参数。原应用通过 `spring.config.import` 可选加载此文件；部署时应将该文件放在进程工作目录，或通过 Spring 外部配置提供同名属性。
3. 从公司批准渠道取得以下厂商依赖，放入项目根目录的 `ext-lib/`（整个目录仅保留本地，不再随仓库提供）：
   - `bes-lite-spring-boot-starter-11.5.0.004.jar`
   - `bes-jdbcra-11.5.0.004.jar`
   - `bes-websocket-11.5.0.004.jar`
4. 使用原有 Maven profile 构建，例如 `mvn -Pdev package`。原应用全量构建与真实环境联调尚未验证。

Docker 基础镜像通过 `--build-arg BASE_IMAGE=<批准的镜像>` 传入，不在仓库保存内部镜像地址。原部署脚本按现状保留，投入部署前另行验证。

## 版本管理约定

- 主分支为 `main`。按明确的改动范围提交，保留需求记录；`src/test/` 和根目录 `test/` 的测试代码仅保留本地。
- 不提交真实保单、影像、数据库导出、账号密码、密钥、令牌或许可证。
- 本机配置和缓存由 `.gitignore` 排除；提交前使用 `git diff --cached` 检查。
- 不自动部署，不自动邀请协作者或发布 GitHub Pages；远程仓库必须保持 Private。
