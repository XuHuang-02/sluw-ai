package com.sinosig.sluw.assessment;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Base64;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties={"assessment.bootstrap-user=test-admin","assessment.bootstrap-password=synthetic-password","assessment.bootstrap-org=test-org","assessment.simulation=true"})
@AutoConfigureMockMvc
class RuleApiTest {
    static final Path dir;
    static { try { dir=Files.createTempDirectory("assessment-rules-api-"); } catch(Exception e) {throw new ExceptionInInitializerError(e);} }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p){p.add("assessment.data-dir",()->dir.toString());}
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    String auth(){return "Basic "+Base64.getEncoder().encodeToString("test-admin:synthetic-password".getBytes(StandardCharsets.UTF_8));}
    @AfterAll static void cleanup() throws Exception {try(var paths=Files.walk(dir)){for(Path p:paths.sorted(java.util.Comparator.reverseOrder()).toList())Files.delete(p);}}
    @Test void missingCredentialsRejected() throws Exception {mvc.perform(get("/api/rules/drafts/id")).andExpect(status().isUnauthorized());}
    @Test void incorrectCredentialsRejected() throws Exception {mvc.perform(get("/api/rules/drafts/id").header("Authorization","Basic eDp5")).andExpect(status().isUnauthorized());}
    @Test void malformedCredentialsRejected() throws Exception {mvc.perform(get("/api/rules/drafts/id").header("Authorization","Basic ???")).andExpect(status().isUnauthorized());}
    @Test void browserOriginRejected() throws Exception {mvc.perform(get("/api/rules/drafts/id").header("Authorization",auth()).header("Origin","https://untrusted.example")).andExpect(status().isForbidden());}
    @Test void forgedIdentityHeaderDoesNotAuthenticate() throws Exception {mvc.perform(get("/api/rules/drafts/id").header("X-User","admin").header("X-Org","test-org")).andExpect(status().isUnauthorized());}
    @Test void encodedRouteCannotBypassAuthentication() throws Exception {mvc.perform(get("/api/%72ules/drafts/id")).andExpect(status().isUnauthorized());}
    @Test void httpLifecycleUsesServerIdentityAndWriteOnceVersion() throws Exception {
        String content=mapper.writeValueAsString(RuleCatalogTest.proposal("api-v1",RuleCatalogTest.items()));
        String response=mvc.perform(post("/api/rules/drafts").header("Authorization",auth()).contentType("application/json").content(content))
                .andExpect(status().isOk()).andExpect(jsonPath("$.createdBy").value("test-admin"))
                .andExpect(jsonPath("$.org").value("test-org")).andExpect(jsonPath("$.proposal.simulation").value(true))
                .andReturn().getResponse().getContentAsString();
        String id=mapper.readTree(response).get("id").asText();
        mvc.perform(post("/api/rules/drafts/"+id+"/publish").header("Authorization",auth()).contentType("application/json")).andExpect(status().isNotFound());
        mvc.perform(post("/api/rules/drafts/"+id+"/confirm").header("Authorization",auth()).contentType("application/json").content("{\"note\":\"confirmed synthetic checklist\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.confirmedBy").value("test-admin"));
        mvc.perform(post("/api/rules/drafts/"+id+"/publish").header("Authorization",auth()).contentType("application/json")).andExpect(status().isOk());
        mvc.perform(post("/api/rules/drafts/"+id+"/publish").header("Authorization",auth()).contentType("application/json")).andExpect(status().isConflict());
        mvc.perform(get("/api/rules/versions").param("channel","channel").param("product","product").param("version","api-v1").header("Authorization",auth()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.confirmation.draft.proposal.items.length()").value(2));
    }
    @Test void invalidProposalReturnsSafeBadRequest() throws Exception {
        mvc.perform(post("/api/rules/drafts").header("Authorization",auth()).contentType("application/json").content("{}")).andExpect(status().isBadRequest());
    }
}
