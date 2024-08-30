/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.schema.index;

import io.camunda.optimize.dto.optimize.query.MetadataDto;
import io.camunda.optimize.service.db.DatabaseConstants;
import io.camunda.optimize.service.db.schema.DefaultIndexMappingCreator;
import java.io.IOException;
import org.elasticsearch.xcontent.XContentBuilder;

public abstract class MetadataIndex<TBuilder> extends DefaultIndexMappingCreator<TBuilder> {

  public static final int VERSION = 3;
  public static final String ID = "1";

  public static final String SCHEMA_VERSION = MetadataDto.Fields.schemaVersion.name();
  protected static final String INSTALLATION_ID = MetadataDto.Fields.installationId.name();

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
