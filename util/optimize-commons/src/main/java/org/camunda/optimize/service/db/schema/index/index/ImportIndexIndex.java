/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.schema.index.index;

import org.camunda.optimize.service.db.schema.DefaultIndexMappingCreator;
import org.camunda.optimize.service.db.DatabaseConstants;
import org.elasticsearch.xcontent.XContentBuilder;

import java.io.IOException;

public abstract class ImportIndexIndex<TBuilder> extends DefaultIndexMappingCreator<TBuilder> {

  public static final int VERSION = 3;

  public static final String IMPORT_INDEX = "importIndex";
  public static final String ENGINE = "engine";

  @Override
  public String getIndexName() {
    return DatabaseConstants.IMPORT_INDEX_INDEX_NAME;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  public boolean isImportIndex() {
    return true;
  }

  @Override
  public XContentBuilder addProperties(XContentBuilder xContentBuilder) throws IOException {
    // @formatter:off
    return xContentBuilder
      .startObject(ENGINE)
        .field("type", "keyword")
      .endObject()
      .startObject(IMPORT_INDEX)
        .field("type", "long")
      .endObject();
    // @formatter:on
  }
}
