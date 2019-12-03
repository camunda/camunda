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
public class BatchOperationTemplate extends AbstractTemplateCreator {

  public static final String INDEX_NAME = "batch-operation";

  public static final String ID = "id";
  public static final String TYPE = "type";
  public static final String NAME = "name";
  public static final String USERNAME = "username";
  public static final String START_DATE = "startDate";
  public static final String END_DATE = "endDate";
  public static final String INSTANCES_COUNT = "instancesCount";
  public static final String OPERATIONS_TOTAL_COUNT = "operationsTotalCount";
  public static final String OPERATIONS_FINISHED_COUNT = "operationsFinishedCount";

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
      .startObject(TYPE)
        .field("type", "keyword")
      .endObject()
      .startObject(NAME)
        .field("type", "keyword")
      .endObject()
      .startObject(USERNAME)
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
      .startObject(INSTANCES_COUNT)
        .field("type", "long")
      .endObject()
      .startObject(OPERATIONS_TOTAL_COUNT)
        .field("type", "long")
      .endObject()
      .startObject(OPERATIONS_FINISHED_COUNT)
        .field("type", "long")
      .endObject();
    return newBuilder;
  }

}
