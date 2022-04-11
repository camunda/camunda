/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.schema.index;

import org.camunda.optimize.service.es.schema.DefaultIndexMappingCreator;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.xcontent.XContentBuilder;

import java.io.IOException;

public class ReportShareIndex extends DefaultIndexMappingCreator {

  public static final int VERSION = 3;

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
    // @formatter:off
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
    // @formatter:on
    return newBuilder;
  }

  private XContentBuilder addNestedPositionField(XContentBuilder builder) throws IOException {
    // @formatter:off
    return builder
      .startObject(X_POSITION)
        .field("type", "keyword")
      .endObject()
      .startObject(Y_POSITION)
        .field("type", "keyword")
      .endObject();
    // @formatter:on
  }
}
