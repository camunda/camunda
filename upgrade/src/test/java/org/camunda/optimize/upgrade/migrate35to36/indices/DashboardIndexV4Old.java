/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.migrate35to36.indices;

import org.camunda.optimize.service.es.schema.DefaultIndexMappingCreator;
import org.elasticsearch.common.xcontent.XContentBuilder;

import java.io.IOException;

import static org.camunda.optimize.service.es.schema.index.DashboardIndex.AVAILABLE_FILTERS;
import static org.camunda.optimize.service.es.schema.index.DashboardIndex.COLLECTION_ID;
import static org.camunda.optimize.service.es.schema.index.DashboardIndex.CONFIGURATION;
import static org.camunda.optimize.service.es.schema.index.DashboardIndex.CREATED;
import static org.camunda.optimize.service.es.schema.index.DashboardIndex.DIMENSION;
import static org.camunda.optimize.service.es.schema.index.DashboardIndex.FILTER_DATA;
import static org.camunda.optimize.service.es.schema.index.DashboardIndex.FILTER_TYPE;
import static org.camunda.optimize.service.es.schema.index.DashboardIndex.HEIGHT;
import static org.camunda.optimize.service.es.schema.index.DashboardIndex.ID;
import static org.camunda.optimize.service.es.schema.index.DashboardIndex.LAST_MODIFIED;
import static org.camunda.optimize.service.es.schema.index.DashboardIndex.LAST_MODIFIER;
import static org.camunda.optimize.service.es.schema.index.DashboardIndex.NAME;
import static org.camunda.optimize.service.es.schema.index.DashboardIndex.OWNER;
import static org.camunda.optimize.service.es.schema.index.DashboardIndex.POSITION;
import static org.camunda.optimize.service.es.schema.index.DashboardIndex.REPORTS;
import static org.camunda.optimize.service.es.schema.index.DashboardIndex.REPORT_ID;
import static org.camunda.optimize.service.es.schema.index.DashboardIndex.WIDTH;
import static org.camunda.optimize.service.es.schema.index.DashboardIndex.X_POSITION;
import static org.camunda.optimize.service.es.schema.index.DashboardIndex.Y_POSITION;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DASHBOARD_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;

public class DashboardIndexV4Old extends DefaultIndexMappingCreator {

  public static final int VERSION = 4;

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
      .field("type", "keyword")
      .endObject()
      .startObject(NAME)
      .field("type", "keyword")
      .endObject()
      .startObject(LAST_MODIFIED)
      .field("type", "date")
      .field("format", OPTIMIZE_DATE_FORMAT)
      .endObject()
      .startObject(CREATED)
      .field("type", "date")
      .field("format", OPTIMIZE_DATE_FORMAT)
      .endObject()
      .startObject(OWNER)
      .field("type", "keyword")
      .endObject()
      .startObject(LAST_MODIFIER)
      .field("type", "keyword")
      .endObject()
      .startObject(REPORTS)
      .field("type", "nested")
      .startObject("properties");
    addNestedReportsField(newBuilder)
      .endObject()
      .endObject()
      .startObject(COLLECTION_ID)
      .field("type","keyword")
      .endObject()
      .startObject(AVAILABLE_FILTERS)
      .field("type", "object")
      .startObject("properties")
      .startObject(FILTER_TYPE)
      .field("type", "keyword")
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