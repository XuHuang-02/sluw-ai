package com.sinosig.sluw.application.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sinosig.sluw.application.commons.datasource.dao.DAO;
import com.sinosig.sluw.application.commons.datasource.dao.DaoException;
import com.sinosig.sluw.application.commons.entity.TransactionTrackEntity;
import com.sinosig.sluw.application.tools.config.JsonSemanticConverter;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 数据库直连工具服务。
 * <p>使用 MyBatis 查询数据库，并通过 {@link JsonSemanticConverter} 将结果转换为语义描述。</p>
 *
 * @author SinoSig AI Team
 */
@Service
public class DbToolService {

    private static final Logger logger = LoggerFactory.getLogger(DbToolService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Resource
    private DAO dao;

    @Autowired
    private JsonSemanticConverter semanticConverter;

    /**
     * 查询保单的交易轨迹。
     *
     * @param proposalNo 投保单号
     * @param bankCode   银行编码
     * @return 交易轨迹的语义描述
     */
    @Tool(description = "查询保单的交易轨迹，返回最近1条记录的报错原因，适用于用户询问'看一下保单的交易报错信息'、'查一下保单交易轨迹'等场景。")
    public String getTransactionTrack(
            @ToolParam(description = "投保单号，是以`1006`开头+`11位数字`+`8`结尾 (共16位，例如1006100000000008)，不可拆分") String proposalNo,
            @ToolParam(description = "银行编码，（以3开头的3位数字，如301代表工商银行）。只有当用户明确说出银行名称或数字编码时才填写，禁止从投保单号中截取数字填充") String bankCode) {

        logger.info("调用 getTransactionTrack: proposalNo={}, bankCode={}", proposalNo, bankCode);

        if (StringUtils.isBlank(proposalNo) && StringUtils.isBlank(bankCode)) {
            return buildErrorResponse("投保单号和银行编码不能同时为空，请至少提供一个查询条件");
        }

        TransactionTrackEntity trackEntity = new TransactionTrackEntity();
        trackEntity.setProposalNo(proposalNo);
        trackEntity.setBankCode(bankCode);

        List<TransactionTrackEntity> list;
        try {
            list = dao.list("mybatis.mapper.TransactionTrackMapper.list", trackEntity);
            if (list == null || list.isEmpty()) {
                logger.info("未查询到交易轨迹记录，proposalNo={}, bankCode={}", proposalNo, bankCode);
                return "未查询到相关交易记录";
            }
        } catch (DaoException e) {
            logger.error("数据库查询失败", e);
            return buildErrorResponse("系统繁忙，请稍后重试");
        }

        // 取第一条记录进行转换
        TransactionTrackEntity firstRecord = list.get(0);
        try {
            String jsonStr = objectMapper.writeValueAsString(firstRecord);
            logger.debug("交易轨迹原始数据: {}", jsonStr);
            return semanticConverter.convert("db.transaction-track", jsonStr);
        } catch (JsonProcessingException e) {
            logger.error("JSON序列化失败", e);
            return buildErrorResponse("数据序列化失败");
        }
    }

    private String buildErrorResponse(String message) {
        return String.format("{\"error\": \"%s\"}", message.replace("\"", "\\\""));
    }
}