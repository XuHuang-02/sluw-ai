package com.sinosig.sluw.application.edd.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import org.springframework.ai.converter.BeanOutputConverter;
import java.util.List;

/** Model-facing shape only; deterministic grade/report decisions are deliberately absent. */
public record EddDraftOutput(
        @JsonProperty(value="overview_sections",required=true) List<Section> overviewSections,
        @JsonProperty(value="disposition_recommendations",required=true) List<Recommendation> dispositionRecommendations,
        @JsonProperty(value="missing_items",required=true) List<Issue> missingItems) {
    public enum Dimension { customer_and_transactions, risk_grade_basis, historical_cash,
        suspicious_reports, blacklist_and_external, high_risk_scenarios }
    public enum RecommendationStatus { suggestion, insufficient_rules, insufficient_evidence,
        requires_institution_review, not_applicable }
    public record Section(@JsonProperty(required=true) Dimension section,
                          @JsonProperty(required=true) String text,
                          @JsonProperty(value="fact_refs",required=true) List<String> factRefs,
                          @JsonProperty(value="rule_refs",required=true) List<String> ruleRefs) {}
    public record Recommendation(@JsonProperty(required=true) RecommendationStatus status,
                                 @JsonProperty(required=true) String value,
                                 @JsonProperty(required=true) String reason,
                                 @JsonProperty(value="fact_refs",required=true) List<String> factRefs,
                                 @JsonProperty(value="rule_refs",required=true) List<String> ruleRefs) {}
    public record Issue(@JsonProperty(required=true) String code,@JsonProperty(required=true) String path,
                        @JsonProperty(required=true) String message) {}

    public enum ErrorCode {
        EMPTY_OUTPUT, MALFORMED_JSON, MISSING_FIELD, SCHEMA_MISMATCH, UNEXPECTED_FIELD, DIMENSION_MISMATCH,
        FIELD_VALUE_INVALID, INVALID_RECOMMENDATION, INVALID_FACT_REFERENCE, INVALID_RULE_REFERENCE,
        OUTPUT_TOO_LARGE, OUTPUT_TRUNCATED, TOOL_CALL_NOT_ALLOWED
    }
    public static final class InvalidOutput extends IllegalArgumentException {
        private final ErrorCode code;
        public InvalidOutput(ErrorCode code) {super(code.name());this.code=code;}
        public ErrorCode code(){return code;}
        public boolean repairable() {
            return switch(code) {
                case EMPTY_OUTPUT,MALFORMED_JSON,MISSING_FIELD,SCHEMA_MISMATCH,UNEXPECTED_FIELD,DIMENSION_MISMATCH,
                     FIELD_VALUE_INVALID,INVALID_RECOMMENDATION,OUTPUT_TRUNCATED -> true;
                default -> false;
            };
        }
    }
    /** Spring AI 1.1.2's default convert logs the raw response on parse failure. Override only that
     * path: keep its DTO-derived schema/format, strict typed Jackson conversion, and safe errors. */
    public static final class Converter extends BeanOutputConverter<EddDraftOutput> {
        private static final ObjectMapper MAPPER=JsonMapperFactory.create();
        private final String schema;
        public Converter(){
            super(EddDraftOutput.class,MAPPER,text->text);
            try {
                JsonNode generated=MAPPER.readTree(super.getJsonSchema());
                nullableRecommendationValue(generated);
                schema=generated.toPrettyString();
            } catch(JsonProcessingException e){throw new IllegalStateException("EDD output schema unavailable");}
        }
        // The Java String is required but nullable; BeanOutputConverter's default Java schema
        // does not express this distinction. Keep the generated shape and adjust only value.
        private static void nullableRecommendationValue(JsonNode node) {
            if(node.isObject()) {
                JsonNode properties=node.path("properties");
                if(properties.has("status")&&properties.has("value")&&properties.has("reason"))
                    ((com.fasterxml.jackson.databind.node.ObjectNode)properties.path("value")).putArray("type").add("string").add("null");
                node.elements().forEachRemaining(Converter::nullableRecommendationValue);
            } else if(node.isArray())node.elements().forEachRemaining(Converter::nullableRecommendationValue);
        }
        @Override public String getJsonSchema(){return schema;}
        @Override public java.util.Map<String,Object> getJsonSchemaMap(){
            try {return MAPPER.readValue(schema,new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String,Object>>(){});}
            catch(JsonProcessingException e){throw new IllegalStateException("EDD output schema unavailable");}
        }
        @Override public String getFormat(){return super.getFormat().replace(super.getJsonSchema(),schema);}
        @Override public EddDraftOutput convert(String text) {
            try {
                EddDraftOutput result=MAPPER.readValue(text,EddDraftOutput.class);
                if(result==null)throw new InvalidOutput(ErrorCode.SCHEMA_MISMATCH);
                return result;
            } catch(UnrecognizedPropertyException e){throw new InvalidOutput(ErrorCode.UNEXPECTED_FIELD);}
              catch(MismatchedInputException e){
                  String detail=e.getOriginalMessage();
                  throw new InvalidOutput(detail!=null && (detail.startsWith("Missing required creator property")||detail.startsWith("Missing creator property"))
                          ?ErrorCode.MISSING_FIELD:ErrorCode.SCHEMA_MISMATCH);
              }
              catch(JsonProcessingException e){throw new InvalidOutput(ErrorCode.MALFORMED_JSON);}
        }
    }
    private static final class JsonMapperFactory {
        static ObjectMapper create() {
            ObjectMapper mapper=com.fasterxml.jackson.databind.json.JsonMapper.builder()
                    .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
                    .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS).build();
            for(var shape:List.of(com.fasterxml.jackson.databind.cfg.CoercionInputShape.Integer,com.fasterxml.jackson.databind.cfg.CoercionInputShape.Float,com.fasterxml.jackson.databind.cfg.CoercionInputShape.Boolean))
                mapper.coercionConfigFor(com.fasterxml.jackson.databind.type.LogicalType.Textual).setCoercion(shape,com.fasterxml.jackson.databind.cfg.CoercionAction.Fail);
            return mapper;
        }
    }
}
