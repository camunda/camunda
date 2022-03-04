/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.schema.index;

import org.camunda.optimize.service.es.schema.DefaultIndexMappingCreator;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.xcontent.XContentBuilder;

import java.io.IOException;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;

public class TerminatedUserSessionIndex extends DefaultIndexMappingCreator {

  public static final int VERSION = 3;

  public static final String ID = "id";
  public static final String TERMINATION_TIMESTAMP = "terminationTimestamp";

  @Override
  public XContentBuilder addProperties(final XContentBuilder builder) throws IOException {
    // @formatter:off
    return  builder
      .startObject(ID)
        .field("type", "keyword")
      .endObject()
      .startObject(TERMINATION_TIMESTAMP)
        .field("type", "date")
        .field("format", OPTIMIZE_DATE_FORMAT)
      .endObject();
    // @formatter:on
  }

  @Override
  public String getIndexName() {
    return ElasticsearchConstants.TERMINATED_USER_SESSION_INDEX_NAME;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }
}
