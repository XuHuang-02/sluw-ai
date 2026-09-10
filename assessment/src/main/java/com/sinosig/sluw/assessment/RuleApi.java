package com.sinosig.sluw.assessment;

import static com.sinosig.sluw.assessment.Model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.filter.OncePerRequestFilter;

/** API-only management; identity is always supplied by the authentication filter. */
@RestController
@RequestMapping("/api/rules")
public class RuleApi {
    private final RuleCatalog catalog;
    public RuleApi(RuleCatalog catalog) { this.catalog = catalog; }
    private Actor actor(HttpServletRequest request) { return (Actor) request.getAttribute("assessment.actor"); }
    public record Note(String note) {}
    @PostMapping(value="/sources", consumes="application/json")
    public Source source(HttpServletRequest r, @RequestBody RuleSources.Ref ref) { return catalog.importSource(actor(r), ref); }
    @PostMapping(value="/drafts", consumes="application/json")
    public RuleCatalog.Draft create(HttpServletRequest r, @RequestBody RuleCatalog.Proposal p) { return catalog.create(actor(r), p); }
    @GetMapping("/drafts/{id}")
    public RuleCatalog.Draft draft(HttpServletRequest r, @PathVariable String id) { return catalog.draft(actor(r), id); }
    @PostMapping(value="/drafts/{id}/confirm", consumes="application/json")
    public RuleCatalog.Confirmation confirm(HttpServletRequest r, @PathVariable String id, @RequestBody Note note) {
        return catalog.confirm(actor(r), id, note.note());
    }
    @PostMapping(value="/drafts/{id}/publish", consumes="application/json")
    public RuleCatalog.Published publish(HttpServletRequest r, @PathVariable String id) { return catalog.publish(actor(r), id); }
    @GetMapping("/versions")
    public RuleCatalog.Published version(HttpServletRequest r, @RequestParam String channel,
            @RequestParam String product, @RequestParam String version) { return catalog.version(actor(r), channel, product, version); }
    @ExceptionHandler(IllegalArgumentException.class) @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String,String> invalid() { return Map.of("error", "规则数据无效，请检查必查项、范围和来源；缺少执行条件的项须填写原因"); }
    @ExceptionHandler(SecurityException.class) @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String,String> forbidden() { return Map.of("error", "无此操作权限"); }
    @ExceptionHandler(NoSuchElementException.class) @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String,String> missing() { return Map.of("error", "记录不存在或不在授权范围内"); }
    @ExceptionHandler(IllegalStateException.class) @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String,String> conflict() { return Map.of("error", "记录已存在或存储校验失败，不允许覆盖"); }
    @ExceptionHandler(RuleSources.SourceException.class) @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public Map<String,String> sourceFailure(RuleSources.SourceException e) { return Map.of("error", e.failure().name()); }

    @Configuration
    static class Wiring {
        @Bean RuleCatalog ruleCatalog(AssessmentSettings settings, ObjectMapper mapper,
                @org.springframework.beans.factory.annotation.Value("${assessment.rag-datasets:}") String datasets) {
            return new RuleCatalog(new RuleStore(Path.of(settings.dataDir()).resolve("rules"), mapper),
                    new RuleSources.RagFlow(settings.ragUrl(), settings.ragKey(), mapper,
                            new HashSet<>(Arrays.stream(datasets.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList())), settings.simulation());
        }
        @Bean OncePerRequestFilter ruleAuthentication(AssessmentSettings settings) {
            return new OncePerRequestFilter() {
                @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                        FilterChain chain) throws ServletException, IOException {
                    // Protect the entire independent application, including encoded/normalized paths.
                    // No UI in this module: reject browser cross-origin/ambient credential requests.
                    if (request.getHeader("Origin") != null) { response.sendError(403); return; }
                    String authorization = request.getHeader("Authorization");
                    boolean allowed = false;
                    if (!RuleEngine.blank(settings.bootstrapPassword()) && !RuleEngine.blank(settings.bootstrapUser())
                            && !RuleEngine.blank(settings.bootstrapOrg()) && authorization != null && authorization.startsWith("Basic ")) {
                        try {
                            byte[] actual = Base64.getDecoder().decode(authorization.substring(6));
                            byte[] expected = (settings.bootstrapUser() + ":" + settings.bootstrapPassword()).getBytes(StandardCharsets.UTF_8);
                            allowed = MessageDigest.isEqual(actual, expected);
                        } catch (IllegalArgumentException ignored) { }
                    }
                    if (!allowed) { response.sendError(401); return; }
                    request.setAttribute("assessment.actor", new Actor(settings.bootstrapUser(), settings.bootstrapOrg(), Role.ADMIN));
                    chain.doFilter(request, response);
                }
            };
        }
    }
}
