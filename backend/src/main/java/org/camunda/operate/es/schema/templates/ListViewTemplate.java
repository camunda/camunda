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
public class ListViewTemplate extends AbstractTemplateCreator {

  public static final String INDEX_NAME = "list-view";

  public static final String ID = "id";
  public static final String KEY = "key";
  public static final String WORKFLOW_INSTANCE_ID = "workflowInstanceId";
  public static final String BPMN_PROCESS_ID = "bpmnProcessId";
  public static final String WORKFLOW_VERSION = "workflowVersion";
  public static final String WORKFLOW_ID = "workflowId";
  public static final String WORKFLOW_NAME = "workflowName";
  public static final String START_DATE = "startDate";
  public static final String END_DATE = "endDate";
  public static final String STATE = "state";

  public static final String ACTIVITY_ID = "activityId";
  public static final String ACTIVITY_STATE = "activityState";
  public static final String ACTIVITY_TYPE = "activityType";

  public static final String INCIDENT_KEY = "incidentKey";
  public static final String INCIDENT_JOB_KEY = "incidentJobKey";
  public static final String ERROR_MSG = "errorMessage";

  public static final String VAR_NAME = "varName";
  public static final String VAR_VALUE = "varValue";
  public static final String SCOPE_KEY = "scopeKey";

  public static final String JOIN_RELATION = "joinRelation";
  public static final String WORKFLOW_INSTANCE_JOIN_RELATION = "workflowInstance";
  public static final String ACTIVITIES_JOIN_RELATION = "activity";
  public static final String VARIABLES_JOIN_RELATION = "variable";

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
      .startObject(WORKFLOW_INSTANCE_ID)
        .field("type", "long")
      .endObject()
    //TODO: Refactor ES-Schema
      //workflow instance fields
      .startObject(WORKFLOW_ID)
        .field("type", "long")
      .endObject()
      // WORKFLOW_NAME index is stored as lowercase keyword: https://www.elastic.co/guide/en/elasticsearch/reference/current/keyword.html
      .startObject(WORKFLOW_NAME)
        .field("type", "keyword")
        .field("normalizer","case_insensitive")
      .endObject()
      .startObject(WORKFLOW_VERSION)
        .field("type", "long")
      .endObject()
      .startObject(BPMN_PROCESS_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(STATE)
        .field("type", "keyword")
      .endObject()
      .startObject(START_DATE)
        .field("type", "date")
        .field("format", operateProperties.getElasticsearch().getDateFormat())
      .endObject()
      .startObject(END_DATE)
        .field("type", "date")
        .field("format", operateProperties.getElasticsearch().getDateFormat())
      .endObject()
      //incident fields
      .startObject(INCIDENT_KEY)
        .field("type", "long")
      .endObject()
      .startObject(ERROR_MSG)
        .field("type", "keyword")
      .endObject()
      .startObject(INCIDENT_JOB_KEY)
        .field("type", "keyword")
      .endObject()
      //activity instance fields
      .startObject(ACTIVITY_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(ACTIVITY_STATE)
        .field("type", "keyword")
      .endObject()
      .startObject(ACTIVITY_TYPE)
        .field("type", "keyword")
      .endObject()
      //variable fields
      .startObject(SCOPE_KEY)
        .field("type", "long")
      .endObject()
      .startObject(VAR_NAME)
        .field("type", "keyword")
      .endObject()
      .startObject(VAR_VALUE)
        .field("type", "keyword")
      .endObject()
      .startObject(JOIN_RELATION)
        .field("type", "join")
        .startObject("relations")
          .startArray(WORKFLOW_INSTANCE_JOIN_RELATION)
            .value(ACTIVITIES_JOIN_RELATION)
            .value(VARIABLES_JOIN_RELATION)
          .endArray()
        .endObject()
      .endObject()
      .startObject(PARTITION_ID)
        .field("type", "integer")
      .endObject()
      .startObject(KEY)
        .field("type", "long")
      .endObject();
    return newBuilder;
  }

}
