package com.sinosig.sluw.application.tools.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * JSON 工具元数据注册配置类。
 * <p>集中管理所有工具（HTTP、DB、MCP）的 JSON 解析规则和字段语义映射。</p>
 * <p>新增工具只需在此处添加对应的元数据配置即可。</p>
 *
 * @author SinoSig AI Team
 */
@Configuration
public class JsonToolMetaRegistry {

    private static final Logger logger = LoggerFactory.getLogger(JsonToolMetaRegistry.class);

    /**
     * 工具元数据注册表 Bean。
     * <p>key: 工具唯一标识，格式建议为 "类型.工具名"，如 "http.nrt-query"、"db.transaction-track"。</p>
     * <p>value: 对应的 JsonToolMeta 配置。</p>
     *
     * @return 工具元数据 Map
     */
    @Bean(name = "jsonToolMetaMap")
    public Map<String, JsonToolMeta> jsonToolMetaMap() {
        logger.info("开始加载 JSON 工具元数据配置...");
        Map<String, JsonToolMeta> map = new HashMap<>();

        // ========== HTTP 工具：非实时出单查询 ==========
        map.put("http.nrt-query", new JsonToolMeta()
                .dataPointer("/resultData")
                .successPointer("/Success", "true")
                .messagePointer("/message")
                .errorCodePointer("/errCode")
                .addField("/managecom", "机构编码")
                .addField("/agentcom", "代理机构编码")
                .addField("/proposalno", "投保单号")
                .addField("/riskcode", "险种代码")
                .addField("/appntidtype", "投保人证件类型")
                .addField("/appntidno", "投保人证件号码")
                .addField("/appntname", "投保人姓名")
                .addField("/appntnativeplacel", "投保人国籍")
                .addField("/idvalidate", "投保人证件有效期止期")
                .addField("/idvalidatestart", "投保人证件有效期起期")
                .addField("/mobilephone", "投保人手机号码")
                .addField("/payendyearflag", "缴费期间单位")
                .addField("/payendyear", "缴费期间")
                .addField("/insuyearflag", "保险期间单位")
                .addField("/insuyear", "保险期间")
                .addField("/payintv", "缴费方式", Map.of("0", "趸交", "12", "年交"))
                .addField("/signdate", "登记日期")
                .addField("/banknodetype", "银行渠道类型", Map.of("1", "自营", "2", "代理"))
                .addField("/bankcode", "银行编码")
                .addField("/bussourceflag", "业务来源", Map.of("1", "私行", "other", "个金"))
        );

        // ========== HTTP 工具：保单详情查询（示例） ==========
        map.put("http.policy-detail", new JsonToolMeta()
                .dataPointer("/data")
                .successPointer("/code", "0")
                .messagePointer("/msg")
                .addField("/policyNo", "保单号")
                .addField("/premium", "保费（元）")
                .addField("/effectiveDate", "生效日期")
        );

        // ========== 数据库工具：交易轨迹查询 ==========
        // 数据库查询无状态码，设置 skipSuccessCheck 跳过成功/失败校验
        map.put("db.transaction-track", new JsonToolMeta()
                .skipSuccessCheck(true)   // 无需判断成功/失败
                .dataPointer("")          // 数据体就是 JSON 根节点
                .addField("/bankCode", "银行编码")
                .addField("/proposalNo", "投保单号")
                .addField("/rCode", "交易结果", Map.of("0", "失败", "1", "成功"))
                .addField("/descr", "失败原因", null, "无")           // 空值显示"无"
                .addField("/makeDate", "交易日期", null, "未知日期")  // 空值显示"未知日期"
        );

        // ========== MCP 工具：规则条款查询 ==========
        map.put("mcp.rule-clause", new JsonToolMeta()
                .dataPointer("/data")
                .successPointer("/status", "success")
                .messagePointer("/errorMsg")
                .addField("/clauseId", "条款编号")
                .addField("/title", "条款标题")
                .addField("/content", "条款内容")
        );

        logger.info("JSON 工具元数据配置加载完成，共注册 {} 个工具", map.size());
        return map;
    }
}