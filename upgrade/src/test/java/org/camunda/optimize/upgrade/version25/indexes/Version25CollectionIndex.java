/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.version25.indexes;

import org.camunda.optimize.service.es.schema.StrictIndexMappingCreator;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.COLLECTION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;

@Component
public class Version25CollectionIndex extends StrictIndexMappingCreator {

  public static final int VERSION = 1;

  public static final String ID = "id";
  public static final String NAME = "name";
  public static final String LAST_MODIFIED = "lastModified";
  public static final String CREATED = "created";
  public static final String OWNER = "owner";
  public static final String LAST_MODIFIER = "lastModifier";

  public static final String DATA = "data";

  public static final String ENTITIES = "entities";
  public static final String CONFIGURATION = "configuration";

  @Override
  public String getIndexName() {
    return COLLECTION_INDEX_NAME;
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
    .endObject();
    newBuilder = addDataField(newBuilder);
    return newBuilder;
     // @formatter:on
  }

  protected XContentBuilder addDataField(XContentBuilder xContentBuilder) throws IOException {
    // @formatter:off
    return xContentBuilder.
      startObject(DATA)
        .field("type", "nested")
        .startObject("properties")
          .startObject(CONFIGURATION)
            .field("enabled", false)
          .endObject()
          .startObject(ENTITIES)
            .field("type", "keyword")
          .endObject()
        .endObject()
      .endObject();
    // @formatter:on
  }

}

