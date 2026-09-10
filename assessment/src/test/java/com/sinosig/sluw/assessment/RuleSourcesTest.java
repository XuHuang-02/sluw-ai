package com.sinosig.sluw.assessment;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;

class RuleSourcesTest {
    HttpServer server;
    AtomicReference<String> body = new AtomicReference<>("{\"code\":0,\"data\":{\"chunks\":[{\"id\":\"chunk\",\"content\":\"核保规则原文\"}]}}");
    int status=200;
    AtomicReference<String> path=new AtomicReference<>();
    AtomicReference<String> auth=new AtomicReference<>();
    RuleSources source;
    final RuleSources.Ref ref=new RuleSources.Ref("dataset","doc","chunk");
    @BeforeEach void start() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange->{
            path.set(exchange.getRequestURI().toString()); auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] bytes=body.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status,bytes.length);
            try(var output=exchange.getResponseBody()){ output.write(bytes); }
        });
        server.start();
        source=new RuleSources.RagFlow("http://127.0.0.1:"+server.getAddress().getPort(),"synthetic-token",new ObjectMapper(),java.util.Set.of("dataset"));
    }
    @AfterEach void stop(){if(server!=null)server.stop(0);}
    @Test void fetchesExactSourceWithUtf8AndHash() {
        Model.Source s=source.fetch(ref);
        assertEquals("/api/v1/datasets/dataset/documents/doc/chunks?id=chunk&page=1&page_size=2",path.get());
        assertEquals("Bearer synthetic-token",auth.get());
        assertEquals("核保规则原文",s.text()); assertTrue(RuleSources.valid(s));
    }
    void fails(RuleSources.Failure failure) {
        var e=assertThrows(RuleSources.SourceException.class,()->source.fetch(ref));
        assertEquals(failure,e.failure()); assertFalse(e.getMessage().contains("synthetic-token"));
    }
    @Test void emptyIsNotFound(){body.set("{\"code\":0,\"data\":{\"chunks\":[]}}");fails(RuleSources.Failure.NOT_FOUND);}
    @Test void httpNotFound(){status=404;fails(RuleSources.Failure.NOT_FOUND);}
    @Test void httpFailure(){status=503;fails(RuleSources.Failure.UNAVAILABLE);}
    @Test void malformedJson(){body.set("secret-server-message");fails(RuleSources.Failure.INVALID_RESPONSE);}
    @Test void errorCodeIsNotEmptySuccess(){body.set("{\"code\":100,\"message\":\"sensitive\"}");fails(RuleSources.Failure.INVALID_RESPONSE);}
    @Test void missingCodeIsNotSuccess(){body.set("{\"data\":{\"chunks\":[]}}");fails(RuleSources.Failure.INVALID_RESPONSE);}
    @Test void wrongChunkRejected(){body.set(body.get().replace("\"id\":\"chunk\"","\"id\":\"other\""));fails(RuleSources.Failure.INVALID_RESPONSE);}
    @Test void multipleChunksCannotSelectOneArbitrarily(){body.set("{\"code\":0,\"data\":{\"chunks\":[{\"id\":\"chunk\",\"content\":\"one\"},{\"id\":\"chunk\",\"content\":\"two\"}]}}");fails(RuleSources.Failure.INVALID_RESPONSE);}
    @Test void pathInjectionRejected(){assertThrows(IllegalArgumentException.class,()->source.fetch(new RuleSources.Ref("../x","doc","chunk")));assertNull(path.get());}
    @Test void unavailableConfigurationIsExplicit(){source=new RuleSources.RagFlow("","",new ObjectMapper(),java.util.Set.of("dataset"));fails(RuleSources.Failure.UNAVAILABLE);}
    @Test void unauthorizedDatasetNeverMakesNetworkRequest(){
        assertThrows(SecurityException.class,()->source.fetch(new RuleSources.Ref("other-org","doc","chunk")));
        assertNull(path.get());
    }
    @Test void redirectNotFollowed(){status=302;fails(RuleSources.Failure.UNAVAILABLE);}
}
