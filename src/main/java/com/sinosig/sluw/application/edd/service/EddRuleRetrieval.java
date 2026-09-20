package com.sinosig.sluw.application.edd.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.*;

/** Retrieval provides cited explanations, never executable policy or customer-risk decisions. */
public final class EddRuleRetrieval {
    public enum Status { FOUND, PARTIAL, NOT_CONFIGURED, RULES_MISSING, NO_MATCH, FILTERED, TIMEOUT, SERVICE_ERROR }
    public record Query(String datasetId,List<String> documentIds,String packageId,String version,
                        OffsetDateTime asOf,String trigger,Set<String> ruleIds) {
        public Query { documentIds=List.copyOf(documentIds);ruleIds=Set.copyOf(ruleIds); }
        public String question() {
            return "Natural person EDD approved rules: "+packageId+" version "+version+
                    " effective at "+asOf+" trigger "+trigger+" rule IDs "+String.join(",",new TreeSet<>(ruleIds));
        }
    }
    public record Candidate(String datasetId,String documentId,String chunkId,String content) {}
    /** Trusted server catalogue entry, not metadata supplied by retrieved text. */
    public record Clause(String datasetId,String documentId,String chunkId,String packageId,String version,
                         String ruleId,String contentVersion,String location,String sha256,
                         String approvalRef,OffsetDateTime approvedAt,OffsetDateTime validFrom,
                         OffsetDateTime validTo,Set<String> triggers,boolean approved,boolean testOnly) {
        public Clause {
            for(String v:List.of(datasetId,documentId,chunkId,packageId,version,ruleId,contentVersion,location,approvalRef))
                if(v.isBlank())throw new IllegalArgumentException("Clause provenance required");
            if(sha256==null||!sha256.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Content hash required");
            Objects.requireNonNull(approvedAt);Objects.requireNonNull(validFrom);
            if(validTo!=null&&!validTo.isAfter(validFrom))throw new IllegalArgumentException("Invalid clause interval");
            triggers=Set.copyOf(triggers);if(triggers.isEmpty())throw new IllegalArgumentException("Clause scope required");
        }
    }
    public record Evidence(String ruleRef,String datasetId,String documentId,String chunkId,String contentVersion,
                           String location,String approvalRef,String sha256,String content) {}
    public record Result(Status status,boolean testOnly,List<Evidence> evidence,List<String> missingRuleIds,List<String> diagnostics) {
        public Result { evidence=List.copyOf(evidence);missingRuleIds=List.copyOf(missingRuleIds);diagnostics=List.copyOf(diagnostics); }
    }
    @FunctionalInterface public interface Retriever { List<Candidate> retrieve(Query query) throws RetrievalFailure; }
    public static final class RetrievalFailure extends Exception {
        private final boolean timeout;
        public RetrievalFailure(boolean timeout){super(timeout?"Retrieval timed out":"Retrieval failed");this.timeout=timeout;}
        public boolean timeout(){return timeout;}
    }
    private final String datasetId;
    private final Retriever retriever;
    private final List<Clause> catalogue;
    private final boolean testMode;
    public EddRuleRetrieval(String datasetId,Retriever retriever,Collection<Clause> catalogue,boolean testMode) {
        this.datasetId=datasetId;this.retriever=retriever;this.catalogue=List.copyOf(catalogue);this.testMode=testMode;
        Set<String> identities=new HashSet<>();
        for(Clause c:this.catalogue) {
            String key=c.datasetId()+"\0"+c.documentId()+"\0"+c.chunkId()+"\0"+c.packageId()+"\0"+c.version()+"\0"+c.ruleId();
            if(!identities.add(key))throw new IllegalArgumentException("Ambiguous clause catalogue");
        }
    }
    public Result retrieve(EddRulePackage pack,OffsetDateTime asOf,String trigger,Set<String> requestedRuleIds) {
        Objects.requireNonNull(asOf);Objects.requireNonNull(trigger);Objects.requireNonNull(requestedRuleIds);
        List<String> requested=new ArrayList<>(new TreeSet<>(requestedRuleIds));
        if(datasetId==null||datasetId.isBlank()||retriever==null)return result(Status.NOT_CONFIGURED,requested,"EDD_DATASET_NOT_CONFIGURED");
        if(pack==null||!pack.approval().equals("approved")||(!testMode&&pack.testOnly())||pack.approvedAt().isAfter(asOf)
                ||!effective(asOf,pack.validFrom(),pack.validTo())||!scope(pack.scope().triggers(),trigger))
            return result(Status.RULES_MISSING,requested,"APPROVED_APPLICABLE_PACKAGE_MISSING");
        Set<String> known=new HashSet<>();pack.rules().forEach(r->known.add(r.id()));
        if(requested.isEmpty()||!known.containsAll(requested))
            return result(Status.RULES_MISSING,requested,"REQUESTED_RULE_IDS_UNAVAILABLE");
        List<Clause> eligible=catalogue.stream().filter(c->c.datasetId().equals(datasetId)&&c.packageId().equals(pack.id())
                &&c.version().equals(pack.version())&&requested.contains(c.ruleId())&&c.approved()
                &&(testMode||!c.testOnly())&&!c.approvedAt().isAfter(asOf)
                &&effective(asOf,c.validFrom(),c.validTo())&&scope(c.triggers(),trigger)).toList();
        if(eligible.isEmpty())return result(Status.RULES_MISSING,requested,"APPROVED_CLAUSE_CATALOGUE_MISSING");
        List<String> docs=eligible.stream().map(Clause::documentId).distinct().sorted().toList();
        List<Candidate> candidates;
        try { candidates=retriever.retrieve(new Query(datasetId,docs,pack.id(),pack.version(),asOf,trigger,requestedRuleIds)); }
        catch(RetrievalFailure e){return result(e.timeout()?Status.TIMEOUT:Status.SERVICE_ERROR,requested,e.timeout()?"RETRIEVAL_TIMEOUT":"RETRIEVAL_FAILED");}
        if(candidates==null||candidates.size()>200)return result(Status.SERVICE_ERROR,requested,"INVALID_RETRIEVAL_RESPONSE");
        if(candidates.isEmpty())return result(Status.NO_MATCH,requested,"NO_RETRIEVED_CLAUSES");
        List<Evidence> evidence=new ArrayList<>();Set<String> matched=new HashSet<>(),seen=new HashSet<>();
        int rejected=0;
        for(Candidate candidate:candidates) {
            if(candidate==null||candidate.content()==null||candidate.content().isBlank()||candidate.content().length()>20000){rejected++;continue;}
            boolean accepted=false;
            for(Clause c:eligible)if(c.datasetId().equals(candidate.datasetId())&&c.documentId().equals(candidate.documentId())
                    &&c.chunkId().equals(candidate.chunkId())&&c.sha256().equals(hash(candidate.content()))) {
                String ref=pack.id()+"@"+pack.version()+":"+c.ruleId();
                String key=ref+"\0"+c.documentId()+"\0"+c.chunkId();
                if(seen.add(key))evidence.add(new Evidence(ref,c.datasetId(),c.documentId(),c.chunkId(),c.contentVersion(),
                        c.location(),c.approvalRef(),c.sha256(),candidate.content()));
                matched.add(c.ruleId());accepted=true;
            }
            if(!accepted)rejected++;
        }
        List<String> missing=requested.stream().filter(id->!matched.contains(id)).toList();
        List<String> diagnostics=new ArrayList<>();
        if(rejected>0)diagnostics.add("UNVERIFIED_CLAUSES_FILTERED:"+rejected);
        if(!missing.isEmpty())diagnostics.add("REQUESTED_RULE_EXPLANATIONS_MISSING");
        Status status=evidence.isEmpty()?Status.FILTERED:missing.isEmpty()?Status.FOUND:Status.PARTIAL;
        return new Result(status,testMode,evidence,missing,diagnostics);
    }
    private static boolean scope(Set<String> values,String trigger){return values.contains("*")||values.contains(trigger);}
    private static boolean effective(OffsetDateTime t,OffsetDateTime from,OffsetDateTime to){return !t.isBefore(from)&&(to==null||t.isBefore(to));}
    private Result result(Status s,List<String> ids,String reason){return new Result(s,testMode,List.of(),ids,List.of(reason));}
    public static String hash(String content) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
}
