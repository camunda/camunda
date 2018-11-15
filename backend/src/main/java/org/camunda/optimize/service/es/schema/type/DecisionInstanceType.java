package org.camunda.optimize.service.es.schema.type;

import org.camunda.optimize.service.es.schema.StrictTypeMappingCreator;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class DecisionInstanceType extends StrictTypeMappingCreator {

  public static final String DECISION_INSTANCE_ID = "id";
  public static final String PROCESS_DEFINITION_ID = "processDefinitionId";
  public static final String PROCESS_DEFINITION_KEY = "processDefinitionKey";

  public static final String DECISION_DEFINITION_ID = "decisionDefinitionId";
  public static final String DECISION_DEFINITION_KEY = "decisionDefinitionKey";

  public static final String EVALUATION_TIME = "evaluationTime";

  public static final String PROCESS_INSTANCE_ID = "processInstanceId";
  public static final String ROOT_PROCESS_INSTANCE_ID = "rootProcessInstanceId";

  public static final String ACTIVITY_ID = "activityId";

  public static final String COLLECT_RESULT_VALUE = "collectResultValue";

  public static final String ROOT_DECISION_INSTANCE_ID = "rootDecisionInstanceId";

  public static final String INPUTS = "inputs";
  public static final String INPUT_ID = "id";
  public static final String INPUT_CLAUSE_ID = "clauseId";
  public static final String INPUT_CLAUSE_NAME = "clauseName";
  public static final String INPUT_VALUE_TYPE = "type";
  public static final String INPUT_VALUE = "value";

  public static final String OUTPUTS = "outputs";
  public static final String OUTPUT_ID = "id";
  public static final String OUTPUT_CLAUSE_ID = "clauseId";
  public static final String OUTPUT_CLAUSE_NAME = "clauseName";
  public static final String OUTPUT_VALUE_TYPE = "type";
  public static final String OUTPUT_VALUE = "value";
  public static final String OUTPUT_RULE_ID = "ruleId";
  public static final String OUTPUT_RULE_ORDER = "ruleOrder";
  public static final String OUTPUT_VARIABLE_NAME = "variableName";

  public static final String ENGINE = "engine";

  @Override
  public String getType() {
    return ElasticsearchConstants.DECISION_INSTANCE_TYPE;
  }

  @Override
  public XContentBuilder addProperties(XContentBuilder builder) throws IOException {
    // @formatter:off
    XContentBuilder newBuilder =  builder
            .startObject(DECISION_INSTANCE_ID)
              .field("type", "keyword")
            .endObject()
            .startObject(DECISION_DEFINITION_ID)
              .field("type", "keyword")
            .endObject()
            .startObject(DECISION_DEFINITION_KEY)
              .field("type", "keyword")
            .endObject()
            .startObject(PROCESS_DEFINITION_ID)
              .field("type", "keyword")
            .endObject()
            .startObject(PROCESS_DEFINITION_KEY)
              .field("type", "keyword")
            .endObject()
            .startObject(PROCESS_INSTANCE_ID)
              .field("type", "keyword")
            .endObject()
            .startObject(ROOT_PROCESS_INSTANCE_ID)
              .field("type", "keyword")
            .endObject()
            .startObject(EVALUATION_TIME)
              .field("type", "date")
              .field("format", configurationService.getOptimizeDateFormat())
            .endObject()
            .startObject(ACTIVITY_ID)
              .field("type", "keyword")
            .endObject()
            .startObject(INPUTS)
              .field("type", "nested")
              .field("include_in_all", false)
              .startObject("properties");
                addNestedInputField(newBuilder)
              .endObject()
            .endObject()
            .startObject(OUTPUTS)
              .field("type", "nested")
              .field("include_in_all", false)
              .startObject("properties");
                addNestedOutputField(newBuilder)
              .endObject()
            .endObject()
            .startObject(COLLECT_RESULT_VALUE)
              .field("type", "double")
            .endObject()
            .startObject(ROOT_DECISION_INSTANCE_ID)
              .field("type", "keyword")
            .endObject()
            .startObject(ENGINE)
              .field("type", "keyword")
            .endObject();
    // @formatter:on
    return newBuilder;
  }

  private XContentBuilder addNestedInputField(XContentBuilder builder) throws IOException {
    // @formatter:off
    builder
      .startObject(INPUT_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(INPUT_CLAUSE_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(INPUT_CLAUSE_NAME)
        .field("type", "keyword")
      .endObject()
      .startObject(INPUT_VALUE_TYPE)
        .field("type", "keyword")
      .endObject()
      .startObject(INPUT_VALUE)
        .field("type", "keyword")
        .startObject("fields");
          addTypedValueFields(builder)
        .endObject()
      .endObject();
    return builder;
    // @formatter:on
  }

  private XContentBuilder addNestedOutputField(XContentBuilder builder) throws IOException {
    // @formatter:off
    builder
      .startObject(OUTPUT_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(OUTPUT_CLAUSE_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(OUTPUT_CLAUSE_NAME)
        .field("type", "keyword")
      .endObject()
      .startObject(OUTPUT_VALUE_TYPE)
        .field("type", "keyword")
      .endObject()
      .startObject(OUTPUT_VALUE)
        .field("type", "keyword")
        .startObject("fields");
          addTypedValueFields(builder)
        .endObject()
      .endObject()
      .startObject(OUTPUT_RULE_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(OUTPUT_RULE_ORDER)
        .field("type", "long")
      .endObject()
      .startObject(OUTPUT_VARIABLE_NAME)
        .field("type", "keyword")
      .endObject();
    return builder;
    // @formatter:on
  }

  private XContentBuilder addTypedValueFields(XContentBuilder builder) throws IOException {
    // @formatter:off
    return builder
      .startObject("date")
        .field("type", "date")
        .field("format", configurationService.getOptimizeDateFormat())
        .field("ignore_malformed", true)
      .endObject()
      .startObject("long")
        .field("type", "long")
        .field("ignore_malformed", true)
      .endObject()
      .startObject("double")
        .field("type", "double")
        .field("ignore_malformed", true)
      .endObject()
      // boolean is not supported to be ignored if malformed, see https://github.com/elastic/elasticsearch/pull/29522
      // it is enough tough to just filter on the default string value with true/false at query time
       ;
    // @formatter:on
  }
}
