package com.sinosig.sluw.application.controller;
import com.sinosig.sluw.application.dto.routing.RoutingEvaluation;
import com.sinosig.sluw.application.dto.routing.RoutingEvaluation.Mode;
import com.sinosig.sluw.application.service.IssueSubmissionTrialService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
/** Same login session as the existing page; no credentials or raw records are logged. */
@RestController
@RequestMapping("/api/agent/trial")
public class RoutingTrialController {
    private final IssueSubmissionTrialService service;
    private final AuthController auth;
    public RoutingTrialController(IssueSubmissionTrialService service,AuthController auth){this.service=service;this.auth=auth;}
    public record Request(String question,Mode mode) {}
    @PostMapping("/evaluate")
    public Mono<RoutingEvaluation> evaluate(@RequestBody Request request,HttpSession session) {
        var state=auth.checkAuth(session).getBody();
        if(state==null||!Boolean.TRUE.equals(state.get("isAuthenticated")))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        if(request==null||request.mode()==null||request.question()==null||request.question().isBlank()||request.question().length()>60000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"question and mode required; input limit 60000 characters");
        return service.evaluate(request.question(),request.mode());
    }
}
