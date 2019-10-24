/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.schema.index.index;

import org.camunda.optimize.service.es.schema.StrictIndexMappingCreator;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TIMESTAMP_BASED_IMPORT_INDEX_NAME;

@Component
public class TimestampBasedImportIndex extends StrictIndexMappingCreator {

  public static final int VERSION = 2;

  public static final String TIMESTAMP_OF_LAST_ENTITY = "timestampOfLastEntity";
  public static final String ES_TYPE_INDEX_REFERS_TO = "esTypeIndexRefersTo";
  private static final String ENGINE = "engine";

  @Override
  public String getIndexName() {
    return TIMESTAMP_BASED_IMPORT_INDEX_NAME;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  public XContentBuilder addProperties(XContentBuilder xContentBuilder) throws IOException {
    return xContentBuilder
      .startObject(ENGINE)
      .field("type", "keyword")
      .endObject()
      .startObject(ES_TYPE_INDEX_REFERS_TO)
      .field("type", "keyword")
      .endObject()
      .startObject(TIMESTAMP_OF_LAST_ENTITY)
      .field("type", "date")
      .field("format", OPTIMIZE_DATE_FORMAT)
      .endObject();
  }
}
