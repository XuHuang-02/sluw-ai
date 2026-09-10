package com.sinosig.sluw.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * RAGFlow 检索接口请求体封装。
 * <p>
 * 对应 POST /api/v1/retrieval 接口的请求参数结构。
 * 所有字段与 RAGFlow API 文档严格保持一致，使用 {@code @JsonProperty} 确保序列化字段名正确。
 * </p>
 *
 * @author SinoSig AI Team
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RagFlowRequest {

    /** 用户查询问题或关键词，必填 */
    @JsonProperty("question")
    private String question;

    /** 待检索的数据集 ID 列表，与 {@code documentIds} 至少提供一个 */
    @JsonProperty("dataset_ids")
    private List<String> datasetIds;

    /** 待检索的文档 ID 列表，需保证所有文档使用相同的 Embedding 模型 */
    @JsonProperty("document_ids")
    private List<String> documentIds;

    /** 分页页码，从 1 开始，默认 1 */
    @JsonProperty("page")
    private Integer page;

    /** 每页返回的最大切片数，默认 30 */
    @JsonProperty("page_size")
    private Integer pageSize;

    /** 最小相似度阈值，取值范围 [0, 1]，默认 0.2 */
    @JsonProperty("similarity_threshold")
    private Double similarityThreshold;

    /** 向量相似度在混合评分中的权重，取值范围 [0, 1]，默认 0.3 */
    @JsonProperty("vector_similarity_weight")
    private Double vectorSimilarityWeight;

    /** 向量检索阶段召回的最大候选切片数，默认 1024 */
    @JsonProperty("top_k")
    private Integer topK;

    /** 重排序模型 ID，可选 */
    @JsonProperty("rerank_id")
    private String rerankId;

    /** 是否启用关键词匹配，默认 false */
    @JsonProperty("keyword")
    private Boolean keyword;

    /** 是否在结果中高亮命中词，默认 false */
    @JsonProperty("highlight")
    private Boolean highlight;

    /**
     * 无参构造器，供 Jackson 反序列化使用。
     */
    public RagFlowRequest() {
    }

    /**
     * 全参构造器，用于手动创建请求对象。
     *
     * @param question                查询问题
     * @param datasetIds              数据集 ID 列表
     * @param documentIds             文档 ID 列表
     * @param page                    页码
     * @param pageSize                每页大小
     * @param similarityThreshold     相似度阈值
     * @param vectorSimilarityWeight  向量权重
     * @param topK                    向量召回数
     * @param rerankId                重排序模型 ID
     * @param keyword                 是否开启关键词匹配
     * @param highlight               是否高亮
     */
    public RagFlowRequest(String question,
                          List<String> datasetIds,
                          List<String> documentIds,
                          Integer page,
                          Integer pageSize,
                          Double similarityThreshold,
                          Double vectorSimilarityWeight,
                          Integer topK,
                          String rerankId,
                          Boolean keyword,
                          Boolean highlight) {
        this.question = question;
        this.datasetIds = datasetIds;
        this.documentIds = documentIds;
        this.page = page;
        this.pageSize = pageSize;
        this.similarityThreshold = similarityThreshold;
        this.vectorSimilarityWeight = vectorSimilarityWeight;
        this.topK = topK;
        this.rerankId = rerankId;
        this.keyword = keyword;
        this.highlight = highlight;
    }

    // ======================== Getter & Setter ========================
    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public List<String> getDatasetIds() {
        return datasetIds;
    }

    public void setDatasetIds(List<String> datasetIds) {
        this.datasetIds = datasetIds;
    }

    public List<String> getDocumentIds() {
        return documentIds;
    }

    public void setDocumentIds(List<String> documentIds) {
        this.documentIds = documentIds;
    }

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    public Double getSimilarityThreshold() {
        return similarityThreshold;
    }

    public void setSimilarityThreshold(Double similarityThreshold) {
        this.similarityThreshold = similarityThreshold;
    }

    public Double getVectorSimilarityWeight() {
        return vectorSimilarityWeight;
    }

    public void setVectorSimilarityWeight(Double vectorSimilarityWeight) {
        this.vectorSimilarityWeight = vectorSimilarityWeight;
    }

    public Integer getTopK() {
        return topK;
    }

    public void setTopK(Integer topK) {
        this.topK = topK;
    }

    public String getRerankId() {
        return rerankId;
    }

    public void setRerankId(String rerankId) {
        this.rerankId = rerankId;
    }

    public Boolean getKeyword() {
        return keyword;
    }

    public void setKeyword(Boolean keyword) {
        this.keyword = keyword;
    }

    public Boolean getHighlight() {
        return highlight;
    }

    public void setHighlight(Boolean highlight) {
        this.highlight = highlight;
    }

    /**
     * 请求对象构建器（手动实现 Builder 模式）。
     */
    public static class Builder {
        private String question;
        private List<String> datasetIds;
        private List<String> documentIds;
        private Integer page;
        private Integer pageSize;
        private Double similarityThreshold;
        private Double vectorSimilarityWeight;
        private Integer topK;
        private String rerankId;
        private Boolean keyword;
        private Boolean highlight;

        public Builder question(String question) {
            this.question = question;
            return this;
        }

        public Builder datasetIds(List<String> datasetIds) {
            this.datasetIds = datasetIds;
            return this;
        }

        public Builder documentIds(List<String> documentIds) {
            this.documentIds = documentIds;
            return this;
        }

        public Builder page(Integer page) {
            this.page = page;
            return this;
        }

        public Builder pageSize(Integer pageSize) {
            this.pageSize = pageSize;
            return this;
        }

        public Builder similarityThreshold(Double similarityThreshold) {
            this.similarityThreshold = similarityThreshold;
            return this;
        }

        public Builder vectorSimilarityWeight(Double vectorSimilarityWeight) {
            this.vectorSimilarityWeight = vectorSimilarityWeight;
            return this;
        }

        public Builder topK(Integer topK) {
            this.topK = topK;
            return this;
        }

        public Builder rerankId(String rerankId) {
            this.rerankId = rerankId;
            return this;
        }

        public Builder keyword(Boolean keyword) {
            this.keyword = keyword;
            return this;
        }

        public Builder highlight(Boolean highlight) {
            this.highlight = highlight;
            return this;
        }

        public RagFlowRequest build() {
            return new RagFlowRequest(
                    question, datasetIds, documentIds, page, pageSize,
                    similarityThreshold, vectorSimilarityWeight, topK,
                    rerankId, keyword, highlight
            );
        }
    }

    /**
     * 创建 Builder 实例的便捷方法。
     *
     * @return Builder 实例
     */
    public static Builder builder() {
        return new Builder();
    }
}