/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.migrate39preview1to39.indices;

import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.service.es.schema.index.AbstractDefinitionIndex;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.xcontent.XContentBuilder;

import java.io.IOException;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.MAPPING_ENABLED_SETTING;

public class ProcessDefinitionIndex39preview1 extends AbstractDefinitionIndex {

  public static final int VERSION = 5;

  public static final String PROCESS_DEFINITION_ID = DEFINITION_ID;
  public static final String PROCESS_DEFINITION_KEY = DEFINITION_KEY;
  public static final String PROCESS_DEFINITION_VERSION = DEFINITION_VERSION;
  public static final String PROCESS_DEFINITION_VERSION_TAG = DEFINITION_VERSION_TAG;
  public static final String PROCESS_DEFINITION_NAME = DEFINITION_NAME;
  public static final String PROCESS_DEFINITION_XML = ProcessDefinitionOptimizeDto.Fields.bpmn20Xml;
  public static final String FLOW_NODE_DATA = ProcessDefinitionOptimizeDto.Fields.flowNodeData;
  public static final String USER_TASK_NAMES = ProcessDefinitionOptimizeDto.Fields.userTaskNames;
  public static final String TENANT_ID = DEFINITION_TENANT_ID;
  public static final String ONBOARDED = ProcessDefinitionOptimizeDto.Fields.onboarded;

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
    return super.addProperties(xContentBuilder)
      .startObject(FLOW_NODE_DATA)
        .field("type", ElasticsearchConstants.TYPE_OBJECT)
        .field(MAPPING_ENABLED_SETTING, "false")
      .endObject()
      .startObject(USER_TASK_NAMES)
        .field("type", ElasticsearchConstants.TYPE_OBJECT)
        .field(MAPPING_ENABLED_SETTING, "false")
      .endObject()
      .startObject(PROCESS_DEFINITION_XML)
        .field("type", ElasticsearchConstants.TYPE_TEXT)
        .field("index", true)
        .field("analyzer", "is_present_analyzer")
      .endObject();
    // @formatter:on
  }
}
