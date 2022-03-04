/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.schema.index.events;

import org.camunda.optimize.dto.optimize.query.event.process.CamundaActivityEventDto;
import org.camunda.optimize.service.es.schema.DefaultIndexMappingCreator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.xcontent.XContentBuilder;

import java.io.IOException;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.CAMUNDA_ACTIVITY_EVENT_INDEX_PREFIX;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SORT_FIELD_SETTING;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SORT_ORDER_SETTING;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SORT_SETTING;

public class CamundaActivityEventIndex extends DefaultIndexMappingCreator {

  public static final String ACTIVITY_ID = CamundaActivityEventDto.Fields.activityId;
  public static final String ACTIVITY_NAME = CamundaActivityEventDto.Fields.activityName;
  public static final String ACTIVITY_TYPE = CamundaActivityEventDto.Fields.activityType;
  public static final String ACTIVITY_INSTANCE_ID = CamundaActivityEventDto.Fields.activityInstanceId;
  public static final String PROCESS_DEFINITION_KEY = CamundaActivityEventDto.Fields.processDefinitionKey;
  public static final String PROCESS_INSTANCE_ID = CamundaActivityEventDto.Fields.processInstanceId;
  public static final String PROCESS_DEFINITION_VERSION = CamundaActivityEventDto.Fields.processDefinitionVersion;
  public static final String PROCESS_INSTANCE_NAME = CamundaActivityEventDto.Fields.processDefinitionName;
  public static final String ENGINE = CamundaActivityEventDto.Fields.engine;
  public static final String TENANT_ID = CamundaActivityEventDto.Fields.tenantId;
  public static final String TIMESTAMP = CamundaActivityEventDto.Fields.timestamp;
  public static final String ORDER_COUNTER = CamundaActivityEventDto.Fields.orderCounter;
  public static final String CANCELED = CamundaActivityEventDto.Fields.canceled;

  public static final int VERSION = 2;

  private String indexName;

  public CamundaActivityEventIndex(final String processDefinitionKey) {
    indexName = CAMUNDA_ACTIVITY_EVENT_INDEX_PREFIX + processDefinitionKey.toLowerCase();
  }

  @Override
  public String getIndexName() {
    return indexName;
  }

  @Override
  public String getIndexNameInitialSuffix() {
    return ElasticsearchConstants.INDEX_SUFFIX_PRE_ROLLOVER;
  }

  @Override
  public boolean isCreateFromTemplate() {
    return true;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  public XContentBuilder addProperties(final XContentBuilder xContentBuilder) throws IOException {
    // @formatter:off
    return xContentBuilder
      .startObject(ACTIVITY_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(ACTIVITY_NAME)
        .field("type", "keyword")
      .endObject()
      .startObject(ACTIVITY_TYPE)
        .field("type", "keyword")
      .endObject()
      .startObject(ACTIVITY_INSTANCE_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(PROCESS_DEFINITION_KEY)
        .field("type", "keyword")
      .endObject()
      .startObject(PROCESS_INSTANCE_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(PROCESS_DEFINITION_VERSION)
        .field("type", "keyword")
      .endObject()
      .startObject(PROCESS_INSTANCE_NAME)
        .field("type", "keyword")
      .endObject()
      .startObject(ENGINE)
        .field("type", "keyword")
      .endObject()
      .startObject(TENANT_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(TIMESTAMP)
        .field("type", "date")
        .field("format", OPTIMIZE_DATE_FORMAT)
      .endObject()
      .startObject(ORDER_COUNTER)
        .field("type", "keyword")
      .endObject()
      .startObject(CANCELED)
        .field("type", "boolean")
      .endObject()
      ;
    // @formatter:on
  }

  @Override
  public XContentBuilder getStaticSettings(XContentBuilder xContentBuilder,
                                           ConfigurationService configurationService) throws IOException {
    // @formatter:off
    final XContentBuilder newXContentBuilder = super.getStaticSettings(xContentBuilder, configurationService);
    return newXContentBuilder
      .startObject(SORT_SETTING)
        .field(SORT_FIELD_SETTING, TIMESTAMP)
        .field(SORT_ORDER_SETTING, "asc")
      .endObject();
    // @formatter:on
  }

}
