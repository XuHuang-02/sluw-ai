package com.sinosig.sluw.application.edd;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sinosig.sluw.application.edd.service.*;
import com.sinosig.sluw.application.client.RagFlowClient;
import com.sinosig.sluw.application.commons.web.ConfigReader;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import java.net.*;
import org.springframework.mock.env.MockEnvironment;
import com.sinosig.sluw.application.config.AiClientConfig;
import java.time.OffsetDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class EddRuleRetrievalTest {
    private final OffsetDateTime asOf=OffsetDateTime.parse("2026-09-18T09:00:00+08:00");
    private final OffsetDateTime from=OffsetDateTime.parse("2026-01-01T00:00:00+08:00");
    private final URI endpoint=URI.create("https://rag.example.invalid/api/v1/retrieval");
    private final String content="Synthetic approved explanation.";
    private ObjectNode packageJson() throws Exception {
        return (ObjectNode)new ObjectMapper().readTree("""
            {"id":"P","version":"1","approval":"approved","approval_ref":"SYN-APPROVAL",
             "approved_by":"SYN-REVIEWER","approved_at":"2026-01-01T00:00:00+08:00",
             "valid_from":"2026-01-01T00:00:00+08:00","valid_to":null,"test_only":true,
             "scope":{"subject_type":"natural_person","triggers":["risk_review"]},
             "rules":[{"id":"R1","target":"risk_factor","value":"risk_increasing","clause_ref":"SYN-1",
             "conditions":[{"fact":"cash_status","operator":"eq","expected":"found"}]}]}
            """);
    }
    private EddRulePackage pack() throws Exception{return EddRulePackage.load(packageJson().toString());}
    private EddRuleRetrieval.Clause clause(String version,boolean approved) {
        return new EddRuleRetrieval.Clause("EDD","DOC","CHUNK","P",version,"R1","content-v1","p1/section1",
                EddRuleRetrieval.hash(content),"SYN-APPROVAL",from,from,null,Set.of("risk_review"),approved,true);
    }
    private EddRuleRetrieval.Candidate candidate(){return new EddRuleRetrieval.Candidate("EDD","DOC","CHUNK",content);}
    private EddRuleRetrieval service(EddRuleRetrieval.Retriever fake,List<EddRuleRetrieval.Clause> clauses) {
        return new EddRuleRetrieval("EDD",fake,clauses,true);
    }
    private FakeEddRuleRetriever fake(FakeEddRuleRetriever.Behavior b,List<EddRuleRetrieval.Candidate> c){return new FakeEddRuleRetriever(b,c);}
    @Test void approvedHitHasFullProvenanceAndDoesNotMutatePackage() throws Exception {
        var p=pack();
        var result=service(query->{
            assertEquals(List.of("DOC"),query.documentIds());assertEquals("1",query.version());
            assertEquals(asOf,query.asOf());assertEquals("risk_review",query.trigger());
            return List.of(candidate());
        },List.of(clause("1",true))).retrieve(p,asOf,"risk_review",Set.of("R1"));
        assertEquals(EddRuleRetrieval.Status.FOUND,result.status());assertTrue(result.testOnly());
        var e=result.evidence().get(0);
        assertEquals("P@1:R1",e.ruleRef());assertEquals("content-v1",e.contentVersion());
        assertEquals("p1/section1",e.location());assertEquals("DOC",e.documentId());assertEquals("CHUNK",e.chunkId());
        assertEquals(content,e.content());assertEquals(1,p.rules().size());
    }
    @Test void emptyTimeoutAndServiceErrorRemainDistinct() throws Exception {
        var p=pack();var clauses=List.of(clause("1",true));
        assertEquals(EddRuleRetrieval.Status.NO_MATCH,service(fake(FakeEddRuleRetriever.Behavior.RETURN,List.of()),clauses).retrieve(p,asOf,"risk_review",Set.of("R1")).status());
        assertEquals(EddRuleRetrieval.Status.TIMEOUT,service(fake(FakeEddRuleRetriever.Behavior.TIMEOUT,List.of()),clauses).retrieve(p,asOf,"risk_review",Set.of("R1")).status());
        assertEquals(EddRuleRetrieval.Status.SERVICE_ERROR,service(fake(FakeEddRuleRetriever.Behavior.ERROR,List.of()),clauses).retrieve(p,asOf,"risk_review",Set.of("R1")).status());
    }
    @Test void wrongVersionAndUnapprovedCatalogueNeverCallRemote() throws Exception {
        EddRuleRetrieval.Retriever never=q->{fail("Must not call remote");return List.of();};
        for(var c:List.of(clause("2",true),clause("1",false)))
            assertEquals(EddRuleRetrieval.Status.RULES_MISSING,service(never,List.of(c)).retrieve(pack(),asOf,"risk_review",Set.of("R1")).status());
        assertEquals(EddRuleRetrieval.Status.NOT_CONFIGURED,new EddRuleRetrieval(null,never,List.of(),false).retrieve(pack(),asOf,"risk_review",Set.of("R1")).status());
        assertEquals(EddRuleRetrieval.Status.RULES_MISSING,service(never,List.of(clause("1",true))).retrieve(pack(),asOf,"other",Set.of("R1")).status());
        assertEquals(EddRuleRetrieval.Status.RULES_MISSING,service(never,List.of(clause("1",true))).retrieve(pack(),from.minusSeconds(1),"risk_review",Set.of("R1")).status());
        assertEquals(EddRuleRetrieval.Status.RULES_MISSING,new EddRuleRetrieval("EDD",never,List.of(clause("1",true)),false).retrieve(pack(),asOf,"risk_review",Set.of("R1")).status());
    }
    @Test void missingCitationChangedContentAndForeignDatasetAreFiltered() throws Exception {
        for(var c:List.of(new EddRuleRetrieval.Candidate("EDD","DOC",null,content),
                new EddRuleRetrieval.Candidate("UNDERWRITING","DOC","CHUNK",content),
                new EddRuleRetrieval.Candidate("EDD","DOC","CHUNK","Changed unapproved version"))) {
            var result=service(fake(FakeEddRuleRetriever.Behavior.RETURN,List.of(c)),List.of(clause("1",true)))
                    .retrieve(pack(),asOf,"risk_review",Set.of("R1"));
            assertEquals(EddRuleRetrieval.Status.FILTERED,result.status());assertTrue(result.evidence().isEmpty());
        }
    }
    @Test void duplicatesAreCollapsedAndPartialCoverageIsExplicit() throws Exception {
        ObjectNode p=packageJson();ObjectNode r=((ObjectNode)p.path("rules").get(0)).deepCopy().put("id","R2");
        ((com.fasterxml.jackson.databind.node.ArrayNode)p.path("rules")).add(r);
        var result=service(fake(FakeEddRuleRetriever.Behavior.RETURN,List.of(candidate(),candidate())),List.of(clause("1",true)))
                .retrieve(EddRulePackage.load(p.toString()),asOf,"risk_review",Set.of("R1","R2"));
        assertEquals(EddRuleRetrieval.Status.PARTIAL,result.status());assertEquals(List.of("R2"),result.missingRuleIds());
        assertEquals(1,result.evidence().size());
        assertThrows(UnsupportedOperationException.class,()->result.evidence().clear());
    }
    private RagFlowClient configuredClient(RestTemplate http) {
        MockEnvironment env=new MockEnvironment()
                .withProperty("application.config.ragFlow.base.apiUrl",endpoint.toString())
                .withProperty("application.config.ragFlow.base.apiKey","SYN-KEY");
        Map<String,String> values=Map.of("datasetId","EDD","page","1","pageSize","25",
                "similarityThreshold","0.4","vectorSimilarityWeight","0.6","topK","80","keyword","true","highlight","false");
        values.forEach((key,value)->env.setProperty("application.config.ragFlow.edd."+key,value));
        ConfigReader config=new ConfigReader();ReflectionTestUtils.setField(config,"env",env);
        RagFlowClient client=new RagFlowClient();ReflectionTestUtils.setField(client,"configReader",config);
        ReflectionTestUtils.setField(client,"restTemplate",http);return client;
    }
    @Test void existingNamedLibraryUsesConfigReaderPrefixAndConfiguredParameters() throws Exception {
        RestTemplate http=new RestTemplate();var server=MockRestServiceServer.bindTo(http).build();
        var client=configuredClient(http);
        server.expect(requestTo(endpoint)).andExpect(header("Authorization","Bearer SYN-KEY"))
                .andExpect(jsonPath("$.dataset_ids[0]").value("EDD")).andExpect(jsonPath("$.page_size").value(25))
                .andExpect(jsonPath("$.similarity_threshold").value(0.4)).andExpect(jsonPath("$.top_k").value(80))
                .andExpect(jsonPath("$.document_ids").doesNotExist())
                .andRespond(withSuccess("{\"code\":0,\"data\":{\"chunks\":[]}}",MediaType.APPLICATION_JSON));
        assertEquals("EDD",client.getDatasetId("edd"));
        assertEquals(0,client.doRetrieve("named query","edd").getCode());server.verify();
    }
    @Test void wrongDatasetOrEmptyRestrictionsFailBeforeHttp() {
        var client=configuredClient(new RestTemplate());
        assertThrows(IllegalArgumentException.class,()->client.doRetrieve("q","edd",List.of("DOC"),"OTHER"));
        assertThrows(IllegalArgumentException.class,()->client.doRetrieve("q","edd",List.of(),"EDD"));
    }
    @Test void sharedHttpFactoryHasBoundedTimeouts() {
        var config=new AiClientConfig();
        var factory=config.restTemplate().getRequestFactory();
        assertEquals(10000,ReflectionTestUtils.getField(factory,"connectTimeout"));
        assertEquals(60000,ReflectionTestUtils.getField(factory,"readTimeout"));
        ReflectionTestUtils.setField(config,"ragReadTimeoutMillis",0);
        assertThrows(IllegalArgumentException.class,config::restTemplate);
    }
    @Test void httpUsesOnlyDedicatedDatasetAndRestrictedDocuments() throws Exception {
        RestTemplate http=new RestTemplate();var server=MockRestServiceServer.bindTo(http).build();
        server.expect(requestTo(endpoint)).andExpect(method(HttpMethod.POST)).andExpect(header("Authorization","Bearer SYN-KEY"))
                .andExpect(jsonPath("$.dataset_ids[0]").value("EDD")).andExpect(jsonPath("$.document_ids[0]").value("DOC"))
                .andExpect(jsonPath("$.highlight").value(false))
                .andRespond(withSuccess("""
                    {"code":0,"data":{"chunks":[{"dataset_id":"EDD","document_id":"DOC","id":"CHUNK","content":"Synthetic approved explanation."}]}}
                    """,MediaType.APPLICATION_JSON));
        var service=service(new EddRagFlowRetriever(configuredClient(http),"edd"),List.of(clause("1",true)));
        assertEquals(EddRuleRetrieval.Status.FOUND,service.retrieve(pack(),asOf,"risk_review",Set.of("R1")).status());
        server.verify();
    }
    @Test void sharedResponseDtoUsesDatasetId() throws Exception {
        var mapper = new ObjectMapper();
        var response = mapper.readValue("""
                {"code":0,"data":{"chunks":[{"dataset_id":"EDD","document_id":"DOC","id":"CHUNK","content":"test"}]}}
                """, com.sinosig.sluw.application.dto.RagFlowResponse.class);
        JsonNode chunk = mapper.valueToTree(response.getData().getChunks().get(0));
        assertEquals("EDD", chunk.path("dataset_id").asText());
        assertFalse(chunk.has("kb_id"));
    }
    @Test void oldDatasetFieldIsNotAcceptedAsProvenance() throws Exception {
        RestTemplate http = new RestTemplate();
        var server = MockRestServiceServer.bindTo(http).build();
        server.expect(requestTo(endpoint)).andRespond(withSuccess("""
                {"code":0,"data":{"chunks":[{"kb_id":"EDD","document_id":"DOC","id":"CHUNK","content":"Synthetic approved explanation."}]}}
                """, MediaType.APPLICATION_JSON));
        var result = service(new EddRagFlowRetriever(configuredClient(http),"edd"), List.of(clause("1",true)))
                .retrieve(pack(), asOf, "risk_review", Set.of("R1"));
        assertEquals(EddRuleRetrieval.Status.FILTERED, result.status());
        assertTrue(result.evidence().isEmpty());
        server.verify();
    }
    @Test void malformedAndRemoteErrorsAreNotEmptySuccesses() throws Exception {
        for(String json:List.of("{\"data\":{\"chunks\":[]}}","{\"code\":5,\"data\":{\"chunks\":[]}}","{\"code\":0,\"data\":{}}")) {
            RestTemplate http=new RestTemplate();var server=MockRestServiceServer.bindTo(http).build();
            server.expect(requestTo(endpoint)).andRespond(withSuccess(json,MediaType.APPLICATION_JSON));
            var result=service(new EddRagFlowRetriever(configuredClient(http),"edd"),List.of(clause("1",true))).retrieve(pack(),asOf,"risk_review",Set.of("R1"));
            assertEquals(EddRuleRetrieval.Status.SERVICE_ERROR,result.status());server.verify();
        }
    }
    @Test void httpTimeoutIsClassifiedWithoutExposingRemoteMessage() throws Exception {
        RestTemplate http=new RestTemplate();var server=MockRestServiceServer.bindTo(http).build();
        server.expect(requestTo(endpoint)).andRespond(withException(new SocketTimeoutException("SENSITIVE")));
        var result=service(new EddRagFlowRetriever(configuredClient(http),"edd"),List.of(clause("1",true))).retrieve(pack(),asOf,"risk_review",Set.of("R1"));
        assertEquals(EddRuleRetrieval.Status.TIMEOUT,result.status());assertFalse(result.toString().contains("SENSITIVE"));server.verify();
    }
    @Test void existingUnderwritingClientContractStillWorks() {
        RestTemplate http=new RestTemplate();var server=MockRestServiceServer.bindTo(http).build();
        ConfigReader config=mock(ConfigReader.class);
        Map<String,String> values=Map.of("apiUrl",endpoint.toString(),"apiKey","OLD-KEY","datasetId","UNDERWRITING",
                "page","1","pageSize","10","similarityThreshold","0.2","vectorSimilarityWeight","0.3","topK","20","keyword","true","highlight","false");
        when(config.getProperty(eq("ragFlow"),anyString())).thenAnswer(call->values.get(call.getArgument(1)));
        RagFlowClient old=new RagFlowClient();ReflectionTestUtils.setField(old,"restTemplate",http);ReflectionTestUtils.setField(old,"configReader",config);
        server.expect(requestTo(endpoint)).andExpect(jsonPath("$.dataset_ids[0]").value("UNDERWRITING"))
                .andRespond(withSuccess("{\"code\":0,\"data\":{\"chunks\":[],\"total\":0}}",MediaType.APPLICATION_JSON));
        assertEquals(0,old.doRetrieve("existing underwriting query").getCode());server.verify();
    }
}
