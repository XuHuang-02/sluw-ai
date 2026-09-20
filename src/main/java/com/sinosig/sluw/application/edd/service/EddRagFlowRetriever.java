package com.sinosig.sluw.application.edd.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sinosig.sluw.application.dto.RagFlowRequest;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.*;
import java.net.URI;
import java.net.SocketTimeoutException;
import java.util.*;

/** Dedicated HTTP transport; never reads underwriting RAG settings or installs a shared bean. */
public final class EddRagFlowRetriever implements EddRuleRetrieval.Retriever {
    private final URI endpoint;
    private final String apiKey;
    private final RestTemplate http;
    public EddRagFlowRetriever(URI endpoint,String apiKey,int connectTimeoutMillis,int readTimeoutMillis) {
        if(endpoint==null||!Set.of("http","https").contains(endpoint.getScheme())||endpoint.getHost()==null
                ||endpoint.getUserInfo()!=null||endpoint.getFragment()!=null||endpoint.getQuery()!=null)
            throw new IllegalArgumentException("Explicit retrieval endpoint required");
        if(apiKey==null||apiKey.isBlank()||connectTimeoutMillis<1||readTimeoutMillis<1)
            throw new IllegalArgumentException("Credentials and bounded timeouts required");
        this.endpoint=endpoint;this.apiKey=apiKey;
        SimpleClientHttpRequestFactory factory=new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMillis);factory.setReadTimeout(readTimeoutMillis);
        http=new RestTemplate(factory);
    }
    /** Test seam; a dedicated template is required, not the application's shared instance. */
    public EddRagFlowRetriever(URI endpoint,String apiKey,RestTemplate dedicatedTemplate) {
        this(endpoint,apiKey,1000,5000);
        this.http.setRequestFactory(Objects.requireNonNull(dedicatedTemplate).getRequestFactory());
    }
    @Override public List<EddRuleRetrieval.Candidate> retrieve(EddRuleRetrieval.Query query) throws EddRuleRetrieval.RetrievalFailure {
        if(query.documentIds().isEmpty()||query.datasetId()==null||query.datasetId().isBlank())throw new IllegalArgumentException("Restricted EDD query required");
        RagFlowRequest body=RagFlowRequest.builder().question(query.question()).datasetIds(List.of(query.datasetId()))
                .documentIds(query.documentIds()).page(1).pageSize(100).topK(100)
                .similarityThreshold(0.2).vectorSimilarityWeight(0.3).keyword(true).highlight(false).build();
        HttpHeaders headers=new HttpHeaders();headers.setContentType(MediaType.APPLICATION_JSON);headers.setBearerAuth(apiKey);
        try {
            JsonNode response=http.postForObject(endpoint,new HttpEntity<>(body,headers),JsonNode.class);
            if(response==null||!response.path("code").isIntegralNumber()||response.path("code").asInt()!=0
                    ||!response.at("/data/chunks").isArray()||response.at("/data/chunks").size()>200)
                throw new EddRuleRetrieval.RetrievalFailure(false);
            List<EddRuleRetrieval.Candidate> chunks=new ArrayList<>();
            for(JsonNode n:response.at("/data/chunks"))chunks.add(new EddRuleRetrieval.Candidate(
                    string(n,"kb_id"),string(n,"document_id"),string(n,"id"),string(n,"content")));
            return List.copyOf(chunks);
        } catch(ResourceAccessException e) {
            Throwable cause=e;boolean timeout=false;
            while(cause!=null){if(cause instanceof SocketTimeoutException){timeout=true;break;}cause=cause.getCause();}
            throw new EddRuleRetrieval.RetrievalFailure(timeout);
        } catch(RestClientException e){throw new EddRuleRetrieval.RetrievalFailure(false);}
    }
    private static String string(JsonNode n,String key){return n.path(key).isTextual()?n.path(key).asText():null;}
}
