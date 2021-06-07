/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.migrate34To35.indices;

import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.service.es.schema.DefaultIndexMappingCreator;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.common.xcontent.XContentBuilder;

import java.io.IOException;

import static org.camunda.optimize.service.es.schema.index.AbstractDefinitionIndex.DEFINITION_DELETED;
import static org.camunda.optimize.service.es.schema.index.AbstractDefinitionIndex.DEFINITION_ID;
import static org.camunda.optimize.service.es.schema.index.AbstractDefinitionIndex.DEFINITION_KEY;
import static org.camunda.optimize.service.es.schema.index.AbstractDefinitionIndex.DEFINITION_NAME;
import static org.camunda.optimize.service.es.schema.index.AbstractDefinitionIndex.DEFINITION_TENANT_ID;
import static org.camunda.optimize.service.es.schema.index.AbstractDefinitionIndex.DEFINITION_VERSION;
import static org.camunda.optimize.service.es.schema.index.AbstractDefinitionIndex.DEFINITION_VERSION_TAG;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.MAPPING_ENABLED_SETTING;

public class ProcessDefinitionIndexV4Old extends DefaultIndexMappingCreator {

  public static final int VERSION = 4;

  public static final String PROCESS_DEFINITION_XML = ProcessDefinitionOptimizeDto.Fields.bpmn20Xml;
  public static final String USER_TASK_NAMES = ProcessDefinitionOptimizeDto.Fields.userTaskNames;
  public static final String ENGINE = "engine";

  @Override
  public String getIndexName() {
    return ElasticsearchConstants.PROCESS_DEFINITION_INDEX_NAME;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

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
      .startObject(ENGINE)
        .field("type", "keyword")
      .endObject()
      .startObject(DEFINITION_TENANT_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(DEFINITION_NAME)
        .field("type", "keyword")
      .endObject()
      .startObject(DEFINITION_DELETED)
        .field("type", "boolean")
      .endObject()
      .startObject("flowNodeNames")
      .field("type", "object")
      .field(MAPPING_ENABLED_SETTING, "false")
      .endObject()
      .startObject(USER_TASK_NAMES)
      .field("type", "object")
      .field(MAPPING_ENABLED_SETTING, "false")
      .endObject()
      .startObject(PROCESS_DEFINITION_XML)
      .field("type", "text")
      .field("index", true)
      .field("analyzer", "is_present_analyzer")
      .endObject();
    // @formatter:on
  }

}
