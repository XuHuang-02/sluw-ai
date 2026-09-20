package com.sinosig.sluw.application.client;

import com.sinosig.sluw.application.commons.web.ConfigReader;
import com.sinosig.sluw.application.dto.RagFlowRequest;
import com.sinosig.sluw.application.dto.RagFlowResponse;
import jakarta.annotation.Resource;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * RAGFlow 知识库检索客户端。
 * <p>
 * 封装与 RAGFlow 服务端的 HTTP 通信，提供知识库检索能力。
 * 支持结构化返回结果，并允许调用方覆盖默认检索参数。
 * </p>
 *
 * @author your-name
 * @version 2.0
 */
@Component
public class RagFlowClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(RagFlowClient.class);

    @Resource
    private RestTemplate restTemplate;
    @Resource
    private ConfigReader configReader;

    /**
     * 执行检索请求（允许覆盖部分检索参数）。
     *
     * @param question              用户查询问题
     * @return RAGFlow 响应对象
     */
    public RagFlowResponse doRetrieve(String question) {
        return retrieveConfigured(question, "ragFlow", "ragFlow", null, null, RagFlowResponse.class);
    }

    /** Existing named-library contract; configuration is supplied by Spring Environment. */
    public RagFlowResponse doRetrieve(String question, String kbName) {
        return retrieveConfigured(question, "ragFlow.base", libraryPrefix(kbName), null, null, RagFlowResponse.class);
    }

    /** Restricted retrieval keeps the raw envelope so callers can reject missing code/chunks. */
    public JsonNode doRetrieve(String question, String kbName, List<String> documentIds, String expectedDatasetId) {
        if(documentIds==null||documentIds.isEmpty()||documentIds.stream().anyMatch(id->id==null||id.isBlank())
                ||expectedDatasetId==null||expectedDatasetId.isBlank())
            throw new IllegalArgumentException("Restricted retrieval requires document IDs and dataset");
        return retrieveConfigured(question,"ragFlow.base",libraryPrefix(kbName),List.copyOf(documentIds),
                expectedDatasetId,JsonNode.class);
    }

    public String getDatasetId(String kbName) {
        return configReader.getProperty(libraryPrefix(kbName),"datasetId");
    }

    private static String libraryPrefix(String kbName) {
        if(kbName==null||!kbName.matches("[A-Za-z0-9_-]+"))
            throw new IllegalArgumentException("Named library required");
        return "ragFlow."+kbName;
    }

    private <T> T retrieveConfigured(String question,String connectionPrefix,String libraryPrefix,
                                     List<String> documentIds,String expectedDatasetId,Class<T> responseType) {
        String datasetId=configReader.getProperty(libraryPrefix,"datasetId");
        if(expectedDatasetId!=null&&!expectedDatasetId.equals(datasetId))
            throw new IllegalArgumentException("Configured dataset does not match approved catalogue");
        String url=configReader.getProperty(connectionPrefix,"apiUrl");
        HttpHeaders headers=new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(configReader.getProperty(connectionPrefix,"apiKey"));
        RagFlowRequest request=RagFlowRequest.builder()
                .question(question).datasetIds(Collections.singletonList(datasetId)).documentIds(documentIds)
                .page(Integer.valueOf(configReader.getProperty(libraryPrefix,"page")))
                .pageSize(Integer.valueOf(configReader.getProperty(libraryPrefix,"pageSize")))
                .similarityThreshold(Double.valueOf(configReader.getProperty(libraryPrefix,"similarityThreshold")))
                .vectorSimilarityWeight(Double.valueOf(configReader.getProperty(libraryPrefix,"vectorSimilarityWeight")))
                .topK(Integer.valueOf(configReader.getProperty(libraryPrefix,"topK")))
                .keyword(Boolean.valueOf(configReader.getProperty(libraryPrefix,"keyword")))
                .highlight(Boolean.valueOf(configReader.getProperty(libraryPrefix,"highlight"))).build();
        return getRagFlowResponse(url,headers,request,responseType);
    }

    private <T> T getRagFlowResponse(String url,HttpHeaders headers,RagFlowRequest request,Class<T> responseType) {
        try {
            return restTemplate.postForObject(url,new HttpEntity<>(request,headers),responseType);
        } catch(RestClientException e) {
            // Do not log remote response bodies, credentials or customer query text.
            LOGGER.error("RAGFlow service call failed: {}",e.getClass().getSimpleName());
            throw new RagFlowServiceException("知识库服务暂时不可用，请稍后重试",e);
        }
    }

//    /**
//     * 多知识库检索（串行调用，合并结果）
//     */
//    public List<RagFlowResponse.Chunk> retrieveChunksFromMultipleKbs(String question, Map<String, String> kbNameToId) {
//        List<RagFlowResponse.Chunk> allChunks = new ArrayList<>();
//        for (Map.Entry<String, String> entry : kbNameToId.entrySet()) {
//            String kbName = entry.getKey();   // 例如 "dataregular"
//            String datasetId = entry.getValue();
//            RagFlowResponse response = doRetrieve(question, datasetId, kbName);
//            if (response != null && response.getData() != null) {
//                allChunks.addAll(response.getData().getChunks());
//            }
//        }
//        // 可选：对 allChunks 去重、按相似度重排序
//        return allChunks;
//    }

    public List<RagFlowResponse.Chunk> retrieveChunksParallelAndMerge(String question, Map<String, String> kbNameToId, long timeoutMillis) {
        List<CompletableFuture<List<RagFlowResponse.Chunk>>> futures = retrieveChunksParallel(question, kbNameToId, timeoutMillis);
        List<RagFlowResponse.Chunk> all = futures.stream()
                .flatMap(f -> f.join().stream())
                .collect(Collectors.toList());
        List<RagFlowResponse.Chunk> distinct = distinctByUniqueKey(all);
        distinct.sort((a, b) -> Double.compare(b.getSimilarity(), a.getSimilarity()));
        return distinct;
    }
    /**
     * 并行从多个知识库检索（使用公共 ForkJoinPool），合并结果并去重排序。
     *
     * @param question      用户问题
     * @param kbNameToId    知识库名称 -> datasetId 映射
     * @param timeoutMillis 每个知识库调用的超时时间（毫秒）
     * @return 合并后的切片列表（已按相似度降序排序，并去重）
     */
    public List<CompletableFuture<List<RagFlowResponse.Chunk>>> retrieveChunksParallel(
            String question,
            Map<String, String> kbNameToId,
            long timeoutMillis) {

        if (kbNameToId == null || kbNameToId.isEmpty()) {
            return Collections.emptyList();
        }

        List<CompletableFuture<List<RagFlowResponse.Chunk>>> futures = kbNameToId.entrySet().stream()
                .map(entry -> {
                    String kbName = entry.getKey();
                    // supplyAsync 内部明确返回 List<Chunk>
                    CompletableFuture<List<RagFlowResponse.Chunk>> future = CompletableFuture.supplyAsync(() -> {
                        long start = System.currentTimeMillis();
                        try {
                            RagFlowResponse response = doRetrieve(question, kbName);
                            long cost = System.currentTimeMillis() - start;
                            if (response != null && response.getData() != null) {
                                List<RagFlowResponse.Chunk> chunks = response.getData().getChunks();
                                LOGGER.info("知识库 [{}] 检索成功，耗时 {} ms，返回 {} 条切片", kbName, cost, chunks.size());
                                for (RagFlowResponse.Chunk chunk : chunks) {
                                    LOGGER.info(chunk.getContent());
                                }
                                return chunks;
                            } else {
                                LOGGER.warn("知识库 [{}] 返回空结果，耗时 {} ms", kbName, cost);
                                return Collections.emptyList(); // 这里返回 List<Chunk>
                            }
                        } catch (Exception e) {
                            long cost = System.currentTimeMillis() - start;
                            LOGGER.error("知识库 [{}] 检索异常，耗时 {} ms，错误: {}", kbName, cost, e.getMessage(), e);
                            return Collections.emptyList(); // 这里也返回 List<Chunk>
                        }
                    });
                    // 应用超时和异常处理，显式指定返回类型为 List<Chunk>
                    return future.orTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
                            .exceptionally(ex -> {
                                LOGGER.warn("知识库 [{}] 调用超时或失败: {}", kbName, ex.getMessage());
                                return Collections.<RagFlowResponse.Chunk>emptyList(); // 关键：使用 Collections.<Chunk>emptyList()
                            });
                })
                .collect(Collectors.toList());

        return futures;
    }

    /**
     * 去重：优先使用 chunkId；若无则用 documentId + 内容前100字符的哈希。
     */
    private List<RagFlowResponse.Chunk> distinctByUniqueKey(List<RagFlowResponse.Chunk> chunks) {
        Map<String, RagFlowResponse.Chunk> map = new LinkedHashMap<>();
        for (RagFlowResponse.Chunk chunk : chunks) {
            String key = chunk.getId();
            if (key == null || key.isEmpty()) {
                // 降级唯一键：文档ID + 内容签名
                String contentSig = chunk.getContent().length() > 100 ?
                        chunk.getContent().substring(0, 100) : chunk.getContent();
                key = chunk.getDocumentId() + "_" + contentSig.hashCode();
            }
            // 如果已存在相同键，保留相似度更高的
            if (!map.containsKey(key) || chunk.getSimilarity() > map.get(key).getSimilarity()) {
                map.put(key, chunk);
            }
        }
        return new ArrayList<>(map.values());
    }

    /**
     * 检索知识库并返回格式化的文本内容（向后兼容）。
     *
     * @param question 用户查询问题
     * @return 格式化后的检索结果字符串
     */
    public String retrieve(String question) {
        Map<String, String> kbNameToId = new HashMap<>();
        kbNameToId.put("dataregular", "dataregular");   // 检核规则库
        kbNameToId.put("issuett", "issuett");       // 问题台账库
        kbNameToId.put("tablestructure", "tablestructure");   // 表结构规范库
        List<RagFlowResponse.Chunk> chunks = retrieveChunks(question);
//        List<RagFlowResponse.Chunk> chunks = retrieveChunksParallelAndMerge(question,kbNameToId,100000);
        if (chunks.isEmpty()) {
            return "未在知识库中找到相关内容。";
        }
        return formatChunks(chunks);
    }

    /**
     * 检索知识库并返回原始切片列表，供上层灵活处理。
     *
     * @param question 用户查询问题
     * @return 切片列表，若无结果则返回空列表
     */
    public List<RagFlowResponse.Chunk> retrieveChunks(String question) {
        RagFlowResponse response = doRetrieve(question);
        if (response == null || response.getCode() != 0) {
            String errMsg = (response == null) ? "无响应" : response.getMessage();
            LOGGER.warn("RAGFlow 检索失败: code={}, message={}",
                    response != null ? response.getCode() : -1, errMsg);
            return Collections.emptyList();
        }
        if (response.getData() == null || response.getData().getChunks() == null) {
            LOGGER.debug("RAGFlow 返回空数据");
            return Collections.emptyList();
        }

        return response.getData().getChunks();
    }

    /**
     * 将切片列表格式化为可读性强的文本。
     *
     * @param chunks 切片列表
     * @return 格式化后的字符串
     */
    private String formatChunks(List<RagFlowResponse.Chunk> chunks) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 检索到的知识库内容 ===\n\n");
        for (RagFlowResponse.Chunk chunk : chunks) {
            sb.append("来源: ")
                    .append(chunk.getDocumentKeyword() != null ? chunk.getDocumentKeyword() : "未知文档")
                    .append("\n");
            sb.append("内容: ").append(chunk.getContent()).append("\n\n");
            sb.append("相似度: ").append(String.format("%.4f", chunk.getSimilarity())).append("\n");
            sb.append("向量相似度: ").append(String.format("%.4f", chunk.getVectorSimilarity())).append("\n");
            sb.append("---\n");
        }
        sb.append("总共检索到 ").append(chunks.size()).append(" 条相关内容\n");
        return sb.toString();
    }

    /**
     * RAGFlow 服务调用异常，用于上层统一异常处理。
     */
    public static class RagFlowServiceException extends RuntimeException {
        public RagFlowServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}