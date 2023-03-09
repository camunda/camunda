/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.migrate39To310.indices;

import org.camunda.optimize.dto.optimize.query.dashboard.tile.DashboardReportTileDto;
import org.camunda.optimize.dto.optimize.query.dashboard.tile.DimensionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.tile.PositionDto;
import org.camunda.optimize.service.es.schema.DefaultIndexMappingCreator;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.xcontent.XContentBuilder;

import java.io.IOException;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.MAPPING_ENABLED_SETTING;

public class DashboardShareIndexV3 extends DefaultIndexMappingCreator {

  public static final int VERSION = 3;

  public static final String ID = "id";
  public static final String DASHBOARD_ID = "dashboardId";
  public static final String REPORT_SHARES = "reportShares";

  public static final String POSITION = DashboardReportTileDto.Fields.position;
  public static final String X_POSITION = PositionDto.Fields.x;
  public static final String Y_POSITION = PositionDto.Fields.y;

  public static final String DIMENSION = DashboardReportTileDto.Fields.dimensions;
  public static final String HEIGHT = DimensionDto.Fields.height;
  public static final String WIDTH = DimensionDto.Fields.width;

  public static final String REPORT_ID = DashboardReportTileDto.Fields.id;
  public static final String REPORT_NAME = "name";

  public static final String CONFIGURATION = DashboardReportTileDto.Fields.configuration;

  @Override
  public String getIndexName() {
    return ElasticsearchConstants.DASHBOARD_SHARE_INDEX_NAME;
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
      .startObject(REPORT_SHARES)
      .field("type", "nested")
      .startObject("properties");
    addNestedReportsField(newBuilder)
      .endObject()
      .endObject()
      .startObject(DASHBOARD_ID)
      .field("type", "keyword")
      .endObject();
    // @formatter:on
    return newBuilder;
  }

  private XContentBuilder addNestedReportsField(XContentBuilder builder) throws IOException {
    // @formatter:off
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
      .field(MAPPING_ENABLED_SETTING, false)
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

  private XContentBuilder addNestedDimensionField(XContentBuilder builder) throws IOException {
    // @formatter:off
    return builder
      .startObject(WIDTH)
      .field("type", "keyword")
      .endObject()
      .startObject(HEIGHT)
      .field("type", "keyword")
      .endObject();
    // @formatter:on
  }

}
