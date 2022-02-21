/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.migrate37to38.indices;

import org.camunda.optimize.dto.optimize.index.ImportIndexDto;
import org.camunda.optimize.dto.optimize.index.TimestampBasedImportIndexDto;
import org.camunda.optimize.service.es.schema.DefaultIndexMappingCreator;
import org.elasticsearch.common.xcontent.XContentBuilder;

import java.io.IOException;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TIMESTAMP_BASED_IMPORT_INDEX_NAME;

public class TimestampBasedImportIndexOld extends DefaultIndexMappingCreator {

  public static final int VERSION = 4;

  public static final String LAST_IMPORT_EXECUTION_TIMESTAMP = ImportIndexDto.Fields.lastImportExecutionTimestamp;
  public static final String TIMESTAMP_OF_LAST_ENTITY = ImportIndexDto.Fields.timestampOfLastEntity;
  public static final String ES_TYPE_INDEX_REFERS_TO = TimestampBasedImportIndexDto.Fields.esTypeIndexRefersTo;
  public static final String ENGINE = "engine";

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
    // @formatter:off
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
      .endObject()
      .startObject(LAST_IMPORT_EXECUTION_TIMESTAMP)
        .field("type", "date")
        .field("format", OPTIMIZE_DATE_FORMAT)
      .endObject();
    // @formatter:on
  }
}
