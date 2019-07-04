/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.es.schema.templates;

import java.io.IOException;
import org.camunda.operate.property.OperateProperties;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ActivityInstanceTemplate extends AbstractTemplateCreator implements WorkflowInstanceDependant {

  public static final String INDEX_NAME = "activity-instance";

  public static final String ID = "id";
  public static final String KEY = "key";
  public static final String POSITION = "position";
  public static final String START_DATE = "startDate";
  public static final String END_DATE = "endDate";
  public static final String ACTIVITY_ID = "activityId";
  public static final String INCIDENT_KEY = "incidentKey";
  public static final String STATE = "state";
  public static final String TYPE = "type";
  public static final String SCOPE_KEY = "scopeKey";

  @Autowired
  private OperateProperties operateProperties;

  @Override
  protected String getIndexNameFormat() {
    return INDEX_NAME;
  }

  @Override
  protected XContentBuilder addProperties(XContentBuilder builder) throws IOException {
    XContentBuilder newBuilder =  builder
      .startObject(ID)
        .field("type", "keyword")
      .endObject()
      .startObject(PARTITION_ID)
        .field("type", "integer")
      .endObject()
      .startObject(POSITION)
        .field("type", "long")
      .endObject()
      .startObject(WORKFLOW_INSTANCE_ID)
        .field("type", "long")
      .endObject()
      .startObject(KEY)
        .field("type", "long")
      .endObject()
      .startObject(INCIDENT_KEY)
        .field("type", "long")
      .endObject()
      .startObject(STATE)
        .field("type", "keyword")
      .endObject()
      .startObject(TYPE)
        .field("type", "keyword")
      .endObject()
      .startObject(ACTIVITY_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(START_DATE)
        .field("type", "date")
        .field("format", operateProperties.getElasticsearch().getElsDateFormat())
      .endObject()
      .startObject(END_DATE)
        .field("type", "date")
        .field("format", operateProperties.getElasticsearch().getElsDateFormat())
      .endObject()
      .startObject(SCOPE_KEY)
        .field("type", "long")
      .endObject();
    return newBuilder;
  }

}
