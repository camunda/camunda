/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.schema.index;

import org.camunda.optimize.service.es.schema.StrictIndexMappingCreator;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class DecisionDefinitionIndex extends StrictIndexMappingCreator {

  public static final int VERSION = 2;

  public static final String DECISION_DEFINITION_ID = "id";
  public static final String DECISION_DEFINITION_KEY = "key";
  public static final String DECISION_DEFINITION_VERSION = "version";
  public static final String DECISION_DEFINITION_VERSION_TAG = "versionTag";
  public static final String DECISION_DEFINITION_NAME = "name";
  public static final String DECISION_DEFINITION_XML = "dmn10Xml";
  public static final String ENGINE = "engine";
  public static final String TENANT_ID = "tenantId";
  public static final String INPUT_VARIABLE_NAMES = "inputVariableNames";
  public static final String OUTPUT_VARIABLE_NAMES = "outputVariableNames";

  @Override
  public String getIndexName() {
    return ElasticsearchConstants.DECISION_DEFINITION_INDEX_NAME;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  public XContentBuilder addProperties(XContentBuilder xContentBuilder) throws IOException {
    // @formatter:off
    return xContentBuilder
      .startObject(DECISION_DEFINITION_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(DECISION_DEFINITION_KEY)
        .field("type", "keyword")
      .endObject()
      .startObject(DECISION_DEFINITION_VERSION)
        .field("type", "keyword")
      .endObject()
      .startObject(DECISION_DEFINITION_VERSION_TAG)
        .field("type", "keyword")
      .endObject()
      .startObject(ENGINE)
        .field("type", "keyword")
      .endObject()
      .startObject(TENANT_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(DECISION_DEFINITION_NAME)
        .field("type", "keyword")
      .endObject()
      .startObject(INPUT_VARIABLE_NAMES)
        .field("enabled", "false")
      .endObject()
      .startObject(OUTPUT_VARIABLE_NAMES)
        .field("enabled", "false")
      .endObject()
      .startObject(DECISION_DEFINITION_XML)
        .field("type", "text")
        .field("index", true)
      .endObject();
    // @formatter:on
  }

}
