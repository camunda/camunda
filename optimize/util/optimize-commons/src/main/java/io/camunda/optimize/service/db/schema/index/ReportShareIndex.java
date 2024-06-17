/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.schema.index;

import io.camunda.optimize.service.db.DatabaseConstants;
import io.camunda.optimize.service.db.schema.DefaultIndexMappingCreator;
import java.io.IOException;
import org.elasticsearch.xcontent.XContentBuilder;

public abstract class ReportShareIndex<TBuilder> extends DefaultIndexMappingCreator<TBuilder> {

  public static final int VERSION = 3;

  public static final String ID = "id";
  public static final String REPORT_ID = "reportId";
  public static final String POSITION = "position";
  public static final String X_POSITION = "x";
  public static final String Y_POSITION = "y";

  @Override
  public String getIndexName() {
    return DatabaseConstants.REPORT_SHARE_INDEX_NAME;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  public XContentBuilder addProperties(XContentBuilder xContentBuilder) throws IOException {
    // @formatter:off
    XContentBuilder newBuilder =
        xContentBuilder
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
