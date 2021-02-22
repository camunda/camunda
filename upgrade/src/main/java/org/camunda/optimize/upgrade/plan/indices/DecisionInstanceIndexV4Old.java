/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.plan.indices;

import org.camunda.optimize.service.es.schema.DefaultIndexMappingCreator;
import org.camunda.optimize.service.es.schema.index.DefinitionBasedType;
import org.camunda.optimize.service.es.schema.index.InstanceType;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.common.xcontent.XContentBuilder;

import java.io.IOException;

import static org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex.ACTIVITY_ID;
import static org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex.COLLECT_RESULT_VALUE;
import static org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex.DECISION_DEFINITION_ID;
import static org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex.DECISION_DEFINITION_KEY;
import static org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex.DECISION_DEFINITION_VERSION;
import static org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex.DECISION_INSTANCE_ID;
import static org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex.ENGINE;
import static org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex.EVALUATION_DATE_TIME;
import static org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex.INPUTS;
import static org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex.MATCHED_RULES;
import static org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex.OUTPUTS;
import static org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex.OUTPUT_VARIABLE_NAME;
import static org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex.OUTPUT_VARIABLE_RULE_ID;
import static org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex.OUTPUT_VARIABLE_RULE_ORDER;
import static org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex.PROCESS_DEFINITION_ID;
import static org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex.PROCESS_DEFINITION_KEY;
import static org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex.PROCESS_INSTANCE_ID;
import static org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex.ROOT_DECISION_INSTANCE_ID;
import static org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex.ROOT_PROCESS_INSTANCE_ID;
import static org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex.TENANT_ID;
import static org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex.VARIABLE_CLAUSE_ID;
import static org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex.VARIABLE_CLAUSE_NAME;
import static org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex.VARIABLE_ID;
import static org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex.VARIABLE_VALUE;
import static org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex.VARIABLE_VALUE_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.FIELDS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_SHARDS_SETTING;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;

public class DecisionInstanceIndexV4Old extends DefaultIndexMappingCreator
  implements DefinitionBasedType, InstanceType {

  public static final int VERSION = 4;

  @Override
  public String getIndexName() {
    return "decision-instance";
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

  @Override
  public XContentBuilder getStaticSettings(XContentBuilder xContentBuilder,
                                           ConfigurationService configurationService) throws IOException {
    return xContentBuilder.field(NUMBER_OF_SHARDS_SETTING, configurationService.getEsNumberOfShards());
  }

}
