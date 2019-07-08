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
public class EventTemplate extends AbstractTemplateCreator implements WorkflowInstanceDependant {

  public static final String INDEX_NAME = "event";

  public static final String ID = "id";

  public static final String KEY = "key";

  public static final String WORKFLOW_ID = "workflowId";
  public static final String BPMN_PROCESS_ID = "bpmnProcessId";

  public static final String ACTIVITY_ID = "activityId";
  public static final String FLOW_NODE_INSTANCE_KEY = "flowNodeInstanceKey";

  public static final String EVENT_SOURCE_TYPE = "eventSourceType";
  public static final String EVENT_TYPE = "eventType";
  public static final String DATE_TIME = "dateTime";
  public static final String PAYLOAD = "payload";

  public static final String METADATA = "metadata";

  public static final String JOB_TYPE = "jobType";
  public static final String JOB_RETRIES = "jobRetries";
  public static final String JOB_WORKER = "jobWorker";
  public static final String JOB_DEADLINE = "jobDeadline";
  public static final String JOB_CUSTOM_HEADERS = "jobCustomHeaders";

  public static final String INCIDENT_ERROR_TYPE = "incidentErrorType";
  public static final String INCIDENT_ERROR_MSG = "incidentErrorMessage";
  public static final String JOB_KEY = "jobKey";

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
      .startObject(KEY)
        .field("type", "long")
      .endObject()
      .startObject(WORKFLOW_ID)
        .field("type", "long")
      .endObject()
      .startObject(WORKFLOW_INSTANCE_ID)
        .field("type", "long")
      .endObject()
      .startObject(BPMN_PROCESS_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(ACTIVITY_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(FLOW_NODE_INSTANCE_KEY)
        .field("type", "long")
      .endObject()
      .startObject(EVENT_SOURCE_TYPE)
        .field("type", "keyword")
      .endObject()
      .startObject(EVENT_TYPE)
        .field("type", "keyword")
      .endObject()
      .startObject(DATE_TIME)
        .field("type", "date")
        .field("format", operateProperties.getElasticsearch().getElsDateFormat())
      .endObject()
      .startObject(PAYLOAD)
        .field("type", "keyword")   // TODO may be we should use Text data type here?
      .endObject()
      .startObject(METADATA)
        .field("type", "nested")
          .startObject("properties");
            addNestedMetadataField(newBuilder)
          .endObject()
      .endObject();

    return newBuilder;
  }

  private XContentBuilder addNestedMetadataField(XContentBuilder builder) throws IOException {
    builder
      .startObject(JOB_RETRIES)
        .field("type", "integer")
      .endObject()
      .startObject(JOB_TYPE)
        .field("type", "keyword")
      .endObject()
      .startObject(JOB_WORKER)
        .field("type", "keyword")
      .endObject()
      .startObject(JOB_DEADLINE)
        .field("type", "date")
        .field("format", operateProperties.getElasticsearch().getElsDateFormat())
      .endObject()
      .startObject(JOB_CUSTOM_HEADERS)
        .field("enabled", false)
      .endObject()
      .startObject(INCIDENT_ERROR_TYPE)
        .field("type", "keyword")
      .endObject()
      .startObject(INCIDENT_ERROR_MSG)
        .field("type", "keyword")
      .endObject()
      .startObject(JOB_KEY)
        .field("type", "long")
      .endObject();
    return builder;
  }

}
