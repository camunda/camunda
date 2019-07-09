/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.es.schema.templates;

import java.io.IOException;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.springframework.stereotype.Component;

@Component
public class SequenceFlowTemplate extends AbstractTemplateCreator implements WorkflowInstanceDependant {

  public static final String INDEX_NAME = "sequence-flow";

  public static final String ID = "id";
  public static final String KEY = "key";
  public static final String WORKFLOW_INSTANCE_KEY = "workflowInstanceKey";
  public static final String ACTIVITY_ID = "activityId";

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
      .startObject(WORKFLOW_INSTANCE_KEY)
        .field("type", "long")
      .endObject()
      .startObject(ACTIVITY_ID)
        .field("type", "keyword")
      .endObject();
    return newBuilder;
  }
}