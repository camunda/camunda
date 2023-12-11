/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.schema.index;

import org.camunda.optimize.dto.optimize.query.MetadataDto;
import org.camunda.optimize.service.db.schema.DefaultIndexMappingCreator;
import org.camunda.optimize.service.db.DatabaseConstants;
import org.elasticsearch.xcontent.XContentBuilder;

import java.io.IOException;

public abstract class MetadataIndex<TBuilder> extends DefaultIndexMappingCreator<TBuilder> {

  public static final int VERSION = 3;
  public static final String ID = "1";

  public static final String SCHEMA_VERSION = MetadataDto.Fields.schemaVersion.name();
  private static final String INSTALLATION_ID = MetadataDto.Fields.installationId.name();

  @Override
  public String getIndexName() {
    return DatabaseConstants.METADATA_INDEX_NAME;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  public XContentBuilder addProperties(XContentBuilder xContentBuilder) throws IOException {
    // @formatter:off
    return xContentBuilder
      .startObject(SCHEMA_VERSION)
        .field("type", "keyword")
      .endObject()
      .startObject(INSTALLATION_ID)
        .field("type", "keyword")
      .endObject();
    // @formatter:on
  }
}
