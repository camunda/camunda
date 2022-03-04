/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.schema.index;

import org.camunda.optimize.dto.optimize.query.MetadataDto;
import org.camunda.optimize.service.es.schema.DefaultIndexMappingCreator;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.xcontent.XContentBuilder;

import java.io.IOException;

public class MetadataIndex extends DefaultIndexMappingCreator {

  public static final int VERSION = 3;
  public static final String ID = "1";

  public static final String SCHEMA_VERSION = MetadataDto.Fields.schemaVersion.name();
  public static final String INSTALLATION_ID = MetadataDto.Fields.installationId.name();

  @Override
  public String getIndexName() {
    return ElasticsearchConstants.METADATA_INDEX_NAME;
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
