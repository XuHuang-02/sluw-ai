package com.sinosig.sluw.application.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sinosig.sluw.application.commons.utils.AesUtil;
import com.sinosig.sluw.application.tools.config.JsonSemanticConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * HTTP 外部接口工具服务。
 * <p>使用 RestTemplate 调用第三方 REST API，并通过 {@link JsonSemanticConverter} 进行响应语义转换。</p>
 *
 * @author SinoSig AI Team
 */
@Service
public class HttpToolService {

    private static final Logger logger = LoggerFactory.getLogger(HttpToolService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private JsonSemanticConverter semanticConverter;

    @Value("${external.nrt.api.url:}")
    private String nrtApiBaseUrl;

    @Value("${external.nrt.api.key:}")
    private String nrtApiKey;

    private static final String NRT_QUERY_PATH = "/sluw/service/getNrtInfo?accessSys=L001";

    /**
     * 非实时出单信息查询接口（建行、中信、农行、蒙商等）。
     *
     * @param proposalNo 投保单号
     * @return 语义化查询结果
     */
    @Tool(description = "查询非实时出单信息，适用于建行、中信、农行、蒙商等银行。需要提供投保单号。返回投保人信息、保单年期、银行编码等关键信息。")
    public String queryNonRealTimeInfo(
            @ToolParam(description = "投保单号，是以`1006`开头+`11位数字`+`8`结尾 (共16位，例如1006100000000008)，不可拆分") String proposalNo) {

        logger.info("调用 queryNonRealTimeInfo: proposalNo={}", proposalNo);

        if (proposalNo == null || proposalNo.trim().isEmpty()) {
            return buildErrorResponse("001", "传入参数有误：投保单号不能为空");
        }

        try {
            if (nrtApiBaseUrl.isBlank() || nrtApiKey.isBlank()) {
                return buildErrorResponse("002", "业务接口尚未配置");
            }
            String url = nrtApiBaseUrl + NRT_QUERY_PATH;
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("proposalNo", proposalNo.trim());
            String requestJson = objectMapper.writeValueAsString(requestBody);
            String encryptData = AesUtil.encryptAES(requestJson,nrtApiKey);

            HttpEntity<String> entity = new HttpEntity<>(encryptData, headers);
            logger.debug("请求URL: {}, 请求体: {}", url, requestJson);

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                String rawJson = response.getBody();
                String decryptData = AesUtil.decryptAES(rawJson,nrtApiKey);
                logger.info("非实时出单查询成功，proposalNo={}", proposalNo);
                return semanticConverter.convert("http.nrt-query", decryptData);
            } else {
                logger.warn("非实时出单查询返回非200状态码: {}, proposalNo={}", response.getStatusCode(), proposalNo);
                return buildErrorResponse("002", "外部接口返回错误，状态码: " + response.getStatusCode());
            }
        } catch (Exception e) {
            logger.error("调用非实时出单查询接口异常，proposalNo={}", proposalNo, e);
            return buildErrorResponse("002", "调用外部接口失败: " + e.getMessage());
        }
    }

    private String buildErrorResponse(String errCode, String message) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("Success", false);
        errorResponse.put("message", "失败: " + message);
        errorResponse.put("errCode", errCode);
        errorResponse.put("resultData", null);
        return serialize(errorResponse);
    }

    private String serialize(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            logger.error("JSON序列化失败", e);
            return "{\"Success\":false,\"message\":\"失败: 序列化失败\",\"errCode\":\"002\"}";
        }
    }

}