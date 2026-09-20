package com.sinosig.sluw.application.edd.service;

import java.util.List;

/** Explicitly injected offline fake. Never registered as a production fallback. */
public final class FakeEddRuleRetriever implements EddRuleRetrieval.Retriever {
    public enum Behavior { RETURN, TIMEOUT, ERROR }
    private final Behavior behavior;
    private final List<EddRuleRetrieval.Candidate> candidates;
    public FakeEddRuleRetriever(Behavior behavior,List<EddRuleRetrieval.Candidate> candidates) {
        this.behavior=java.util.Objects.requireNonNull(behavior);this.candidates=List.copyOf(candidates);
    }
    @Override public List<EddRuleRetrieval.Candidate> retrieve(EddRuleRetrieval.Query query) throws EddRuleRetrieval.RetrievalFailure {
        if(behavior!=Behavior.RETURN)throw new EddRuleRetrieval.RetrievalFailure(behavior==Behavior.TIMEOUT);
        return candidates;
    }
}
