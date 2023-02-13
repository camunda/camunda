/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.schema.index;

import org.camunda.optimize.dto.optimize.query.dashboard.BaseDashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DimensionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.PositionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.ReportLocationDto;
import org.camunda.optimize.dto.optimize.query.dashboard.filter.DashboardFilterDto;
import org.camunda.optimize.service.es.schema.DefaultIndexMappingCreator;
import org.elasticsearch.xcontent.XContentBuilder;

import java.io.IOException;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DASHBOARD_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.MAPPING_PROPERTY_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TYPE_BOOLEAN;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TYPE_DATE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TYPE_KEYWORD;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TYPE_NESTED;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TYPE_OBJECT;

public class DashboardIndex extends DefaultIndexMappingCreator {

  public static final int VERSION = 7;

  public static final String ID = BaseDashboardDefinitionDto.Fields.id;
  public static final String NAME = BaseDashboardDefinitionDto.Fields.name;
  public static final String LAST_MODIFIED = BaseDashboardDefinitionDto.Fields.lastModified;
  public static final String CREATED = BaseDashboardDefinitionDto.Fields.created;
  public static final String OWNER = BaseDashboardDefinitionDto.Fields.owner;
  public static final String LAST_MODIFIER = BaseDashboardDefinitionDto.Fields.lastModifier;
  public static final String REFRESH_RATE_SECONDS = BaseDashboardDefinitionDto.Fields.refreshRateSeconds;
  public static final String REPORTS = DashboardDefinitionRestDto.Fields.reports;
  public static final String COLLECTION_ID = BaseDashboardDefinitionDto.Fields.collectionId;
  public static final String MANAGEMENT_DASHBOARD = BaseDashboardDefinitionDto.Fields.managementDashboard;
  public static final String INSTANT_PREVIEW_DASHBOARD = BaseDashboardDefinitionDto.Fields.instantPreviewDashboard;
  public static final String AVAILABLE_FILTERS = BaseDashboardDefinitionDto.Fields.availableFilters;

  public static final String POSITION = ReportLocationDto.Fields.position;
  public static final String X_POSITION = PositionDto.Fields.x;
  public static final String Y_POSITION = PositionDto.Fields.y;

  public static final String DIMENSION = ReportLocationDto.Fields.dimensions;
  public static final String HEIGHT = DimensionDto.Fields.height;
  public static final String WIDTH = DimensionDto.Fields.width;

  public static final String REPORT_ID = ReportLocationDto.Fields.id;
  public static final String CONFIGURATION = ReportLocationDto.Fields.configuration;

  public static final String FILTER_TYPE = "type";
  public static final String FILTER_DATA = DashboardFilterDto.Fields.data;

  @Override
  public String getIndexName() {
    return DASHBOARD_INDEX_NAME;
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
        .field(MAPPING_PROPERTY_TYPE, TYPE_KEYWORD)
      .endObject()
      .startObject(NAME)
        .field(MAPPING_PROPERTY_TYPE, TYPE_KEYWORD)
      .endObject()
      .startObject(LAST_MODIFIED)
        .field(MAPPING_PROPERTY_TYPE, TYPE_DATE)
        .field("format", OPTIMIZE_DATE_FORMAT)
      .endObject()
      .startObject(CREATED)
        .field(MAPPING_PROPERTY_TYPE, TYPE_DATE)
        .field("format", OPTIMIZE_DATE_FORMAT)
      .endObject()
      .startObject(OWNER)
        .field(MAPPING_PROPERTY_TYPE, TYPE_KEYWORD)
      .endObject()
      .startObject(LAST_MODIFIER)
        .field(MAPPING_PROPERTY_TYPE, TYPE_KEYWORD)
      .endObject()
      .startObject(REFRESH_RATE_SECONDS)
        .field(MAPPING_PROPERTY_TYPE, TYPE_KEYWORD)
      .endObject()
      .startObject(REPORTS)
        .field(MAPPING_PROPERTY_TYPE, TYPE_NESTED)
        .startObject("properties");
          addNestedReportsField(newBuilder)
        .endObject()
      .endObject()
      .startObject(COLLECTION_ID)
        .field(MAPPING_PROPERTY_TYPE,TYPE_KEYWORD)
      .endObject()
      .startObject(MANAGEMENT_DASHBOARD)
        .field(MAPPING_PROPERTY_TYPE,TYPE_BOOLEAN)
      .endObject()
      .startObject(INSTANT_PREVIEW_DASHBOARD)
        .field(MAPPING_PROPERTY_TYPE,TYPE_BOOLEAN)
      .endObject()
      .startObject(AVAILABLE_FILTERS)
        .field(MAPPING_PROPERTY_TYPE, TYPE_OBJECT)
        .startObject("properties")
          .startObject(FILTER_TYPE)
            .field(MAPPING_PROPERTY_TYPE, TYPE_KEYWORD)
          .endObject()
          .startObject(FILTER_DATA)
            .field("enabled", false)
          .endObject()
        .endObject()
      .endObject();
    // @formatter:on
    return newBuilder;
  }

  private XContentBuilder addNestedReportsField(XContentBuilder builder) throws IOException {
    // @formatter:off
    XContentBuilder newBuilder = builder
      .startObject(REPORT_ID)
        .field(MAPPING_PROPERTY_TYPE, TYPE_KEYWORD)
      .endObject()
      .startObject(POSITION)
        .field(MAPPING_PROPERTY_TYPE, TYPE_NESTED)
        .startObject("properties");
          addNestedPositionField(newBuilder)
        .endObject()
      .endObject()
      .startObject(DIMENSION)
        .field(MAPPING_PROPERTY_TYPE, TYPE_NESTED)
        .startObject("properties");
          addNestedDimensionField(newBuilder)
        .endObject()
      .endObject()
      .startObject(CONFIGURATION)
        .field("enabled", false)
      .endObject();
    // @formatter:on
    return newBuilder;
  }

  private XContentBuilder addNestedPositionField(XContentBuilder builder) throws IOException {
    // @formatter:off
    return builder
      .startObject(X_POSITION)
        .field(MAPPING_PROPERTY_TYPE, TYPE_KEYWORD)
      .endObject()
      .startObject(Y_POSITION)
        .field(MAPPING_PROPERTY_TYPE, TYPE_KEYWORD)
      .endObject();
    // @formatter:on
  }

  private XContentBuilder addNestedDimensionField(XContentBuilder builder) throws IOException {
    // @formatter:off
    return builder
      .startObject(WIDTH)
        .field(MAPPING_PROPERTY_TYPE, TYPE_KEYWORD)
      .endObject()
      .startObject(HEIGHT)
        .field(MAPPING_PROPERTY_TYPE, TYPE_KEYWORD)
      .endObject();
    // @formatter:on
  }

}
