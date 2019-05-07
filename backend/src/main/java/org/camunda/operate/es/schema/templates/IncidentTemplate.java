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
public class IncidentTemplate extends AbstractTemplateCreator implements WorkflowInstanceDependant {

  public static final String INDEX_NAME = "incident";

  public static final String ID = "id";
  public static final String KEY = "key";
  public static final String WORKFLOW_INSTANCE_ID = "workflowInstanceId";
  public static final String FLOW_NODE_ID = "flowNodeId";
  public static final String FLOW_NODE_INSTANCE_ID = "flowNodeInstanceId";
  public static final String JOB_ID = "jobId";
  public static final String ERROR_TYPE = "errorType";
  public static final String ERROR_MSG = "errorMessage";
  public static final String STATE = "state";
  public static final String CREATION_TIME = "creationTime";

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
      .startObject(CREATION_TIME)
        .field("type", "date")
        .field("format", operateProperties.getElasticsearch().getDateFormat())
      .endObject()
      .startObject(ERROR_MSG)
        .field("type", "keyword")
      .endObject()
      .startObject(ERROR_TYPE)
        .field("type", "keyword")
      .endObject()
      .startObject(STATE)
       .field("type", "keyword")
      .endObject()
      .startObject(FLOW_NODE_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(FLOW_NODE_INSTANCE_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(JOB_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(WORKFLOW_INSTANCE_ID)
        .field("type", "keyword")
      .endObject();
    return newBuilder;
  }

}
