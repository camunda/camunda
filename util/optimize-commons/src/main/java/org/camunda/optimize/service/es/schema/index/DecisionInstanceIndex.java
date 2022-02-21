/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.schema.index;

import org.elasticsearch.common.xcontent.XContentBuilder;

import java.io.IOException;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_INSTANCE_INDEX_PREFIX;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.FIELDS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;

public class DecisionInstanceIndex extends AbstractInstanceIndex {

  public static final int VERSION = 5;

  public static final String PROCESS_DEFINITION_ID = "processDefinitionId";
  public static final String PROCESS_DEFINITION_KEY = "processDefinitionKey";

  public static final String DECISION_DEFINITION_ID = "decisionDefinitionId";
  public static final String DECISION_DEFINITION_KEY = "decisionDefinitionKey";
  public static final String DECISION_DEFINITION_VERSION = "decisionDefinitionVersion";

  public static final String DECISION_INSTANCE_ID = "decisionInstanceId";

  public static final String EVALUATION_DATE_TIME = "evaluationDateTime";

  public static final String PROCESS_INSTANCE_ID = "processInstanceId";
  public static final String ROOT_PROCESS_INSTANCE_ID = "rootProcessInstanceId";

  public static final String ACTIVITY_ID = "activityId";

  public static final String COLLECT_RESULT_VALUE = "collectResultValue";

  public static final String ROOT_DECISION_INSTANCE_ID = "rootDecisionInstanceId";

  public static final String INPUTS = "inputs";
  public static final String VARIABLE_ID = "id";
  public static final String VARIABLE_CLAUSE_ID = "clauseId";
  public static final String VARIABLE_CLAUSE_NAME = "clauseName";
  public static final String VARIABLE_VALUE_TYPE = "type";
  public static final String VARIABLE_VALUE = "value";

  public static final String OUTPUTS = "outputs";
  public static final String OUTPUT_VARIABLE_RULE_ID = "ruleId";
  public static final String OUTPUT_VARIABLE_RULE_ORDER = "ruleOrder";
  public static final String OUTPUT_VARIABLE_NAME = "variableName";

  public static final String MATCHED_RULES = "matchedRules";

  public static final String ENGINE = "engine";
  public static final String TENANT_ID = "tenantId";

  private final String indexName;

  public DecisionInstanceIndex(final String decisionDefinitionKey) {
    indexName = DECISION_INSTANCE_INDEX_PREFIX + decisionDefinitionKey.toLowerCase();
  }

  @Override
  public String getIndexName() {
    return indexName;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  public String getDefinitionKeyFieldName() {
    return DECISION_DEFINITION_KEY;
  }

  @Override
  public String getDefinitionVersionFieldName() {
    return DECISION_DEFINITION_VERSION;
  }

  @Override
  public String getTenantIdFieldName() {
    return TENANT_ID;
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
              .startObject(DECISION_DEFINITION_VERSION)
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
            .startObject(EVALUATION_DATE_TIME)
              .field("type", "date")
              .field("format", OPTIMIZE_DATE_FORMAT)
            .endObject()
            .startObject(ACTIVITY_ID)
              .field("type", "keyword")
            .endObject()
            .startObject(INPUTS)
              .field("type", "nested")
              .startObject("properties");
                addNestedInputField(newBuilder)
              .endObject()
            .endObject()
            .startObject(OUTPUTS)
              .field("type", "nested")
              .startObject("properties");
                addNestedOutputField(newBuilder)
              .endObject()
            .endObject()
            .startObject(MATCHED_RULES)
              .field("type", "keyword")
            .endObject()
            .startObject(COLLECT_RESULT_VALUE)
              .field("type", "double")
            .endObject()
            .startObject(ROOT_DECISION_INSTANCE_ID)
              .field("type", "keyword")
            .endObject()
            .startObject(ENGINE)
              .field("type", "keyword")
            .endObject()
            .startObject(TENANT_ID)
              .field("type", "keyword")
            .endObject();
    // @formatter:on
    return newBuilder;
  }

  private XContentBuilder addNestedInputField(XContentBuilder builder) throws IOException {
    // @formatter:off
    builder
      .startObject(VARIABLE_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(VARIABLE_CLAUSE_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(VARIABLE_CLAUSE_NAME)
        .field("type", "keyword")
      .endObject()
      .startObject(VARIABLE_VALUE_TYPE)
        .field("type", "keyword")
      .endObject()
      .startObject(VARIABLE_VALUE)
        .field("type", "keyword")
        .startObject(FIELDS);
          addValueMultifields(builder)
        .endObject()
      .endObject();
    return builder;
    // @formatter:on
  }

  private XContentBuilder addNestedOutputField(XContentBuilder builder) throws IOException {
    // @formatter:off
    builder
      .startObject(VARIABLE_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(VARIABLE_CLAUSE_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(VARIABLE_CLAUSE_NAME)
        .field("type", "keyword")
      .endObject()
      .startObject(VARIABLE_VALUE_TYPE)
        .field("type", "keyword")
      .endObject()
      .startObject(VARIABLE_VALUE)
        .field("type", "keyword")
        .startObject(FIELDS);
          addValueMultifields(builder)
        .endObject()
      .endObject()
      .startObject(OUTPUT_VARIABLE_RULE_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(OUTPUT_VARIABLE_RULE_ORDER)
        .field("type", "long")
      .endObject()
      .startObject(OUTPUT_VARIABLE_NAME)
        .field("type", "keyword")
      .endObject();
    return builder;
    // @formatter:on
  }

}
