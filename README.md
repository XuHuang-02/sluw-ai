# sluw-ai

Java 核保问答应用与独立问题件判断模块。此仓库用于私有版本管理。

## 项目结构

- `src/`：原 Spring Boot 核保问答应用。
- `assessment/`：独立问题件判断模块，目前完成模块一核心实现及80个测试用例。
- `CONTEXT.md`：业务术语。
- `.scratch/policy-problem-assessment/`：已确认需求及实施记录，仅该需求目录纳入版本管理。
- `docker/`：原应用部署脚本。

## 验证模块一

使用 JDK 17 或以上版本和 Maven：

```sh
mvn -f assessment/pom.xml test
```

详见 [模块一验证记录](assessment/docs/module-1-verification.md)。这些测试使用合成数据和服务替身，不能替代真实业务验收。模块二尚未实施，独立模块尚无完整上传、登录、报告页面。

## 原应用本地配置

真实环境配置、厂商JAR、许可证与运行资料仅保留在本机，不上传 GitHub。

1. 将所需的 `config/examples/application-<环境>.example.yml` 复制至 `src/main/resources/application-<环境>.yml`，填写环境变量或本机值。
2. 将 `config/examples/local-settings.example.properties` 复制为项目根目录的 `local-settings.properties`，配置原登录用户及业务接口参数。原应用通过 `spring.config.import` 可选加载此文件；部署时应将该文件放在进程工作目录，或通过 Spring 外部配置提供同名属性。
3. 从公司批准渠道取得 `ext-lib/README.md` 中的厂商依赖。
4. 使用原有 Maven profile 构建，例如 `mvn -Pdev package`。原应用全量构建与环境联调未包含在模块一验证范围内。

Docker 基础镜像通过 `--build-arg BASE_IMAGE=<批准的镜像>` 传入，不在仓库保存内部镜像地址。原部署脚本按现状保留，投入部署前另行验证。

## 版本管理约定

- 主分支为 `main`。按模块提交，保留可复现测试和需求记录。
- 不提交真实保单、影像、数据库导出、账号密码、密钥、令牌或许可证。
- 本机配置和缓存由 `.gitignore` 排除；提交前使用 `git diff --cached` 检查。
- 不自动部署，不自动邀请协作者或发布 GitHub Pages；远程仓库必须保持 Private。
