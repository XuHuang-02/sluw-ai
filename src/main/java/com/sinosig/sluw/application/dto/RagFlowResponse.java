package com.sinosig.sluw.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;

/**
 * RAGFlow 检索接口响应体封装。
 * <p>
 * 对应 POST /api/v1/retrieval 接口的成功响应结构。
 * 采用嵌套类表达 {@code data} 对象内的层级关系，字段名与 API 文档严格一致。
 * </p>
 *
 * @author SinoSig AI Team
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RagFlowResponse {

    /** 响应状态码，0 表示成功 */
    private int code;

    /** 错误或提示信息 */
    private String message;

    /** 检索结果数据 */
    private Data data;

    public RagFlowResponse() {
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Data getData() {
        return data;
    }

    public void setData(Data data) {
        this.data = data;
    }

    /**
     * 响应数据内部结构体。
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Data {

        /** 切片列表 */
        private List<Chunk> chunks;

        /** 按文档聚合的统计信息 */
        @JsonProperty("doc_aggs")
        private List<DocAgg> docAggs;

        /** 符合条件的切片总数 */
        private int total;

        public Data() {
        }

        public List<Chunk> getChunks() {
            return chunks != null ? chunks : Collections.emptyList();
        }

        public void setChunks(List<Chunk> chunks) {
            this.chunks = chunks;
        }

        public List<DocAgg> getDocAggs() {
            return docAggs != null ? docAggs : Collections.emptyList();
        }

        public void setDocAggs(List<DocAgg> docAggs) {
            this.docAggs = docAggs;
        }

        public int getTotal() {
            return total;
        }

        public void setTotal(int total) {
            this.total = total;
        }
    }

    /**
     * 切片（Chunk）详细信息。
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Chunk {

        /** 切片 ID */
        private String id;

        /** 切片原始文本内容 */
        private String content;

        /** 分词后的内容（内部匹配使用） */
        @JsonProperty("content_ltks")
        private String contentLtks;

        /** 所属文档 ID */
        @JsonProperty("document_id")
        private String documentId;

        /** 文档关键词或文件名 */
        @JsonProperty("document_keyword")
        private String documentKeyword;

        /** 高亮后的文本片段（仅在请求 highlight=true 时返回） */
        private String highlight;

        /** 关联图片 ID */
        @JsonProperty("image_id")
        private String imageId;

        /** 重要关键词列表 */
        @JsonProperty("important_keywords")
        private List<String> importantKeywords;

        /** 所属知识库 ID */
        @JsonProperty("dataset_id")
        private String datasetId;

        /** 切片在文档中的位置信息（如页码、段落） */
        private List<String> positions;

        /** 混合相似度总分 */
        private float similarity;

        /** 关键词匹配相似度 */
        @JsonProperty("term_similarity")
        private float termSimilarity;

        /** 向量余弦相似度 */
        @JsonProperty("vector_similarity")
        private float vectorSimilarity;

        public Chunk() {
        }

        // ---------- Getter & Setter ----------
        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public String getContentLtks() {
            return contentLtks;
        }

        public void setContentLtks(String contentLtks) {
            this.contentLtks = contentLtks;
        }

        public String getDocumentId() {
            return documentId;
        }

        public void setDocumentId(String documentId) {
            this.documentId = documentId;
        }

        public String getDocumentKeyword() {
            return documentKeyword;
        }

        public void setDocumentKeyword(String documentKeyword) {
            this.documentKeyword = documentKeyword;
        }

        public String getHighlight() {
            return highlight;
        }

        public void setHighlight(String highlight) {
            this.highlight = highlight;
        }

        public String getImageId() {
            return imageId;
        }

        public void setImageId(String imageId) {
            this.imageId = imageId;
        }

        public List<String> getImportantKeywords() {
            return importantKeywords != null ? importantKeywords : Collections.emptyList();
        }

        public void setImportantKeywords(List<String> importantKeywords) {
            this.importantKeywords = importantKeywords;
        }

        public String getDatasetId() {
            return datasetId;
        }

        public void setDatasetId(String datasetId) {
            this.datasetId = datasetId;
        }

        public List<String> getPositions() {
            return positions != null ? positions : Collections.emptyList();
        }

        public void setPositions(List<String> positions) {
            this.positions = positions;
        }

        public float getSimilarity() {
            return similarity;
        }

        public void setSimilarity(float similarity) {
            this.similarity = similarity;
        }

        public float getTermSimilarity() {
            return termSimilarity;
        }

        public void setTermSimilarity(float termSimilarity) {
            this.termSimilarity = termSimilarity;
        }

        public float getVectorSimilarity() {
            return vectorSimilarity;
        }

        public void setVectorSimilarity(float vectorSimilarity) {
            this.vectorSimilarity = vectorSimilarity;
        }
    }

    /**
     * 文档聚合统计信息。
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DocAgg {

        /** 该文档中被检索到的切片数量 */
        private int count;

        /** 文档 ID */
        @JsonProperty("doc_id")
        private String docId;

        /** 文档名称 */
        @JsonProperty("doc_name")
        private String docName;

        public DocAgg() {
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        public String getDocId() {
            return docId;
        }

        public void setDocId(String docId) {
            this.docId = docId;
        }

        public String getDocName() {
            return docName;
        }

        public void setDocName(String docName) {
            this.docName = docName;
        }
    }
}
