package com.sinosig.sluw.application.edd.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sinosig.sluw.application.client.RagFlowClient;
import java.net.SocketTimeoutException;
import java.util.*;

/** Thin EDD response adapter. Configuration, authentication and HTTP belong to RagFlowClient. */
public final class EddRagFlowRetriever implements EddRuleRetrieval.Retriever {
    private final RagFlowClient client;
    private final String libraryName;
    public EddRagFlowRetriever(RagFlowClient client,String libraryName) {
        this.client=Objects.requireNonNull(client);
        if(libraryName==null||!libraryName.matches("[A-Za-z0-9_-]+"))throw new IllegalArgumentException("Named EDD library required");
        this.libraryName=libraryName;
    }
    @Override public List<EddRuleRetrieval.Candidate> retrieve(EddRuleRetrieval.Query query) throws EddRuleRetrieval.RetrievalFailure {
        try {
            JsonNode response=client.doRetrieve(query.question(),libraryName,query.documentIds(),query.datasetId());
            if(response==null||!response.path("code").isIntegralNumber()||response.path("code").asInt()!=0
                    ||!response.at("/data/chunks").isArray()||response.at("/data/chunks").size()>200)
                throw new EddRuleRetrieval.RetrievalFailure(false);
            List<EddRuleRetrieval.Candidate> chunks=new ArrayList<>();
            for(JsonNode n:response.at("/data/chunks"))chunks.add(new EddRuleRetrieval.Candidate(
                    string(n,"dataset_id"),string(n,"document_id"),string(n,"id"),string(n,"content")));
            return List.copyOf(chunks);
        } catch(RagFlowClient.RagFlowServiceException e) {
            Throwable cause=e;boolean timeout=false;
            while(cause!=null){if(cause instanceof SocketTimeoutException){timeout=true;break;}cause=cause.getCause();}
            throw new EddRuleRetrieval.RetrievalFailure(timeout);
        } catch(IllegalArgumentException e){throw new EddRuleRetrieval.RetrievalFailure(false);}
    }
    private static String string(JsonNode n,String key){return n.path(key).isTextual()?n.path(key).asText():null;}
}
