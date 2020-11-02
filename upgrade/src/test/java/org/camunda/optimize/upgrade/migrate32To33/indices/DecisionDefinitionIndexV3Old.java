/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.migrate32To33.indices;

import org.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import org.camunda.optimize.service.es.schema.DefaultIndexMappingCreator;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.common.xcontent.XContentBuilder;

import java.io.IOException;

public class DecisionDefinitionIndexV3Old extends DefaultIndexMappingCreator {

  public static final int VERSION = 3;

  public static final String DECISION_DEFINITION_XML = "dmn10Xml";
  public static final String INPUT_VARIABLE_NAMES = "inputVariableNames";
  public static final String OUTPUT_VARIABLE_NAMES = "outputVariableNames";
  public static final String DEFINITION_ID = DefinitionOptimizeResponseDto.Fields.id;
  public static final String DEFINITION_KEY = DefinitionOptimizeResponseDto.Fields.key;
  public static final String DEFINITION_VERSION = DefinitionOptimizeResponseDto.Fields.version;
  public static final String DEFINITION_VERSION_TAG = DefinitionOptimizeResponseDto.Fields.versionTag;
  public static final String DEFINITION_NAME = DefinitionOptimizeResponseDto.Fields.name;
  public static final String DEFINITION_ENGINE = DefinitionOptimizeResponseDto.Fields.engine;
  public static final String DEFINITION_TENANT_ID = DefinitionOptimizeResponseDto.Fields.tenantId;

  @Override
  public XContentBuilder addProperties(XContentBuilder xContentBuilder) throws IOException {
    // @formatter:off
    return xContentBuilder
      .startObject(DEFINITION_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(DEFINITION_KEY)
        .field("type", "keyword")
      .endObject()
      .startObject(DEFINITION_VERSION)
        .field("type", "keyword")
      .endObject()
      .startObject(DEFINITION_VERSION_TAG)
        .field("type", "keyword")
      .endObject()
      .startObject(DEFINITION_ENGINE)
        .field("type", "keyword")
      .endObject()
      .startObject(DEFINITION_TENANT_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(DEFINITION_NAME)
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
        .field("analyzer", "is_present_analyzer")
      .endObject();
    // @formatter:on
  }

  @Override
  public String getIndexName() {
    return ElasticsearchConstants.DECISION_DEFINITION_INDEX_NAME;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

}
