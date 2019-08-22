/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.es.schema.indices;

import java.io.IOException;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.springframework.stereotype.Component;

@Component
public class WorkflowIndex extends AbstractIndexCreator {

  public static final String INDEX_NAME = "workflow";
  public static final String ID = "id";
  public static final String KEY = "key";
  public static final String BPMN_PROCESS_ID = "bpmnProcessId";
  public static final String NAME = "name";
  public static final String VERSION = "version";
  public static final String BPMN_XML = "bpmnXml";
  public static final String RESOURCE_NAME = "resourceName";
  public static final String ACTIVITIES = "activities";
  public static final String ACTIVITY_NAME = "name";
  public static final String ACTIVITY_TYPE = "type";

  @Override
  public String getIndexName() {
    return String.format("%s-%s_", operateProperties.getElasticsearch().getIndexPrefix(), INDEX_NAME);
  }

  @Override
  protected XContentBuilder addProperties(XContentBuilder xContentBuilder) throws IOException {
    XContentBuilder newBuilder = xContentBuilder
      .startObject(ID)
        .field("type", "keyword")
      .endObject()
      .startObject(KEY)
        .field("type", "keyword")
      .endObject()
      .startObject(BPMN_PROCESS_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(NAME)
        .field("type", "keyword")
      .endObject()
      .startObject(RESOURCE_NAME)
        .field("type", "keyword")
      .endObject()
      .startObject(VERSION)
        .field("type", "long")
      .endObject()
      .startObject(BPMN_XML)
        .field("type", "text")
        .field("index", false)
      .endObject()
      .startObject(PARTITION_ID)
        .field("type", "integer")
      .endObject()
      .startObject(ACTIVITIES)
        .field("type", "nested")
        .startObject("properties");
          addNestedActivitiesField(newBuilder)
        .endObject()
      .endObject();
    return newBuilder;
  }

  private XContentBuilder addNestedActivitiesField(XContentBuilder builder) throws IOException {
    builder
      .startObject(ID)
        .field("type", "keyword")
      .endObject()
      .startObject(ACTIVITY_NAME)
        .field("type", "keyword")
      .endObject()
      .startObject(ACTIVITY_TYPE)
        .field("type", "keyword")
      .endObject();
    return builder;
  }

}
