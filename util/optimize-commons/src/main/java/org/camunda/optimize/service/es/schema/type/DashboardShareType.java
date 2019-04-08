/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.schema.type;

import org.camunda.optimize.service.es.schema.StrictTypeMappingCreator;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class DashboardShareType extends StrictTypeMappingCreator {

  public static final int VERSION = 1;

  public static final String ID = "id";
  public static final String DASHBOARD_ID = "dashboardId";
  public static final String REPORT_SHARES = "reportShares";

  public static final String POSITION = "position";
  public static final String X_POSITION = "x";
  public static final String Y_POSITION = "y";

  public static final String DIMENSION = "dimensions";
  public static final String HEIGHT = "height";
  public static final String WIDTH = "width";

  public static final String REPORT_ID = "id";
  public static final String REPORT_NAME = "name";

  public static final String CONFIGURATION = "configuration";

  @Override
  public String getType() {
    return ElasticsearchConstants.DASHBOARD_SHARE_TYPE;
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
      .startObject(REPORT_SHARES)
        .field("type", "nested")
        .startObject("properties");
          addNestedReportsField(newBuilder)
        .endObject()
      .endObject()
      .startObject(DASHBOARD_ID)
        .field("type", "keyword")
      .endObject();

    return newBuilder;
  }

  private XContentBuilder addNestedReportsField(XContentBuilder builder) throws IOException {
    XContentBuilder newBuilder = builder
      .startObject(REPORT_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(REPORT_NAME)
        .field("type", "keyword")
      .endObject()
      .startObject(POSITION)
        .field("type", "nested")
        .startObject("properties");
          addNestedPositionField(newBuilder)
        .endObject()
      .endObject()
      .startObject(DIMENSION)
        .field("type", "nested")
        .startObject("properties");
          addNestedDimensionField(newBuilder)
        .endObject()
      .endObject()
      .startObject(CONFIGURATION)
        .field("enabled", false)
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

  private XContentBuilder addNestedDimensionField(XContentBuilder builder) throws IOException {
    return builder
      .startObject(WIDTH)
        .field("type", "keyword")
      .endObject()
      .startObject(HEIGHT)
        .field("type", "keyword")
      .endObject();
  }
}
