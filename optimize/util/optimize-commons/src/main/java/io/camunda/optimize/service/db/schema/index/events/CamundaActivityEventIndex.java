/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.schema.index.events;

import static io.camunda.optimize.service.db.DatabaseConstants.CAMUNDA_ACTIVITY_EVENT_INDEX_PREFIX;
import static io.camunda.optimize.service.db.DatabaseConstants.OPTIMIZE_DATE_FORMAT;

import io.camunda.optimize.dto.optimize.query.event.process.CamundaActivityEventDto;
import io.camunda.optimize.service.db.DatabaseConstants;
import io.camunda.optimize.service.db.schema.DefaultIndexMappingCreator;
import java.io.IOException;
import java.util.Locale;
import org.elasticsearch.xcontent.XContentBuilder;

public abstract class CamundaActivityEventIndex<TBuilder>
    extends DefaultIndexMappingCreator<TBuilder> {

  public static final String ACTIVITY_ID = CamundaActivityEventDto.Fields.activityId;
  public static final String ACTIVITY_NAME = CamundaActivityEventDto.Fields.activityName;
  public static final String ACTIVITY_TYPE = CamundaActivityEventDto.Fields.activityType;
  public static final String ACTIVITY_INSTANCE_ID =
      CamundaActivityEventDto.Fields.activityInstanceId;
  public static final String PROCESS_DEFINITION_KEY =
      CamundaActivityEventDto.Fields.processDefinitionKey;
  public static final String PROCESS_INSTANCE_ID = CamundaActivityEventDto.Fields.processInstanceId;
  public static final String PROCESS_DEFINITION_VERSION =
      CamundaActivityEventDto.Fields.processDefinitionVersion;
  public static final String PROCESS_INSTANCE_NAME =
      CamundaActivityEventDto.Fields.processDefinitionName;
  public static final String ENGINE = CamundaActivityEventDto.Fields.engine;
  public static final String TENANT_ID = CamundaActivityEventDto.Fields.tenantId;
  public static final String TIMESTAMP = CamundaActivityEventDto.Fields.timestamp;
  public static final String ORDER_COUNTER = CamundaActivityEventDto.Fields.orderCounter;
  public static final String CANCELED = CamundaActivityEventDto.Fields.canceled;

  public static final int VERSION = 2;

  private final String indexName;

  protected CamundaActivityEventIndex(final String processDefinitionKey) {
    indexName = constructIndexName(processDefinitionKey);
  }

  public static String constructIndexName(final String processDefinitionKey) {
    return CAMUNDA_ACTIVITY_EVENT_INDEX_PREFIX + processDefinitionKey.toLowerCase(Locale.ENGLISH);
  }

  @Override
  public String getIndexName() {
    return indexName;
  }

  @Override
  public String getIndexNameInitialSuffix() {
    return DatabaseConstants.INDEX_SUFFIX_PRE_ROLLOVER;
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
        .endObject();
    // @formatter:on
  }
}
