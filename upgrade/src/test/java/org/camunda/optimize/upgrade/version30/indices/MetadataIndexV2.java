/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.version30.indices;

import org.camunda.optimize.service.es.schema.DefaultIndexMappingCreator;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.common.xcontent.XContentBuilder;

import java.io.IOException;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.METADATA_TYPE_SCHEMA_VERSION;

public class MetadataIndexV2 extends DefaultIndexMappingCreator {

  public static final int VERSION = 2;

  public static final String SCHEMA_VERSION = METADATA_TYPE_SCHEMA_VERSION;

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
    return xContentBuilder
      .startObject(SCHEMA_VERSION)
      .field("type", "keyword")
      .endObject();
  }
}
