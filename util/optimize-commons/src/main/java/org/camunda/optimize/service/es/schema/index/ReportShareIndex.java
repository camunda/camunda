/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.schema.index;

import org.camunda.optimize.service.es.schema.StrictIndexMappingCreator;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;


@Component
public class ReportShareIndex extends StrictIndexMappingCreator {

  public static final int VERSION = 1;

  public static final String ID = "id";
  public static final String REPORT_ID = "reportId";
  public static final String POSITION = "position";
  public static final String X_POSITION = "x";
  public static final String Y_POSITION = "y";

  @Override
  public String getIndexName() {
    return ElasticsearchConstants.REPORT_SHARE_INDEX_NAME;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  public XContentBuilder addProperties(XContentBuilder xContentBuilder) throws IOException {
    XContentBuilder newBuilder = xContentBuilder
      .startObject(ID)
        .field("type", "keyword")
      .endObject()
      .startObject(POSITION)
        .field("type", "nested")
        .startObject("properties");
          addNestedPositionField(newBuilder)
        .endObject()
      .endObject()
      .startObject(REPORT_ID)
        .field("type", "keyword")
      .endObject();

    return newBuilder;
  }

  private XContentBuilder addNestedPositionField(XContentBuilder builder) throws IOException {
    return builder
      .startObject(X_POSITION)
        .field("type", "keyword")
      .endObject()
      .startObject(Y_POSITION)
        .field("type", "keyword")
      .endObject();
  }
}
