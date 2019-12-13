/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.schema.type;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.es.schema.IndexMappingCreator;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.common.xcontent.XContentBuilder;

import java.io.IOException;

import static org.camunda.optimize.service.es.schema.index.MetadataIndex.SCHEMA_VERSION;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

@Slf4j
public class MyUpdatedEventIndex implements IndexMappingCreator {

  public static final String MY_NEW_FIELD = "myAwesomeNewField";

  @Override
  public String getIndexName() {
    return ElasticsearchConstants.METADATA_INDEX_NAME;
  }

  @Override
  public int getVersion() {
    return 2;
  }

  @Override
  public XContentBuilder getSource() {
    XContentBuilder source = null;
    try {
      // @formatter:off
      XContentBuilder content = jsonBuilder()
        .startObject()
          .startObject("properties")
            .startObject(SCHEMA_VERSION)
              .field("type", "keyword")
            .endObject()
            .startObject(MY_NEW_FIELD)
              .field("type", "keyword")
            .endObject()
          .endObject()
        .endObject();
      source = content;
      // @formatter:on
    } catch (IOException e) {
      String message = "Could not add mapping for type '" + getIndexName() + "'!";
      log.error(message, e);
    }
    return source;
  }

  @Override
  public XContentBuilder getCustomSettings(XContentBuilder xContentBuilder) {
    return xContentBuilder;
  }

}
