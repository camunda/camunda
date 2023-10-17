/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.schema.index;

import org.camunda.optimize.service.es.schema.DefaultIndexMappingCreator;
import org.camunda.optimize.service.db.DatabaseConstants;
import org.elasticsearch.xcontent.XContentBuilder;

import java.io.IOException;

import static org.camunda.optimize.service.db.DatabaseConstants.OPTIMIZE_DATE_FORMAT;

public abstract class TerminatedUserSessionIndex<TBuilder> extends DefaultIndexMappingCreator<TBuilder> {

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
    return DatabaseConstants.TERMINATED_USER_SESSION_INDEX_NAME;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }
}
