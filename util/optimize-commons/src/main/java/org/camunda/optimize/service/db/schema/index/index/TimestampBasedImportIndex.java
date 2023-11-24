/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.schema.index.index;

import org.camunda.optimize.dto.optimize.index.ImportIndexDto;
import org.camunda.optimize.dto.optimize.index.TimestampBasedImportIndexDto;
import org.camunda.optimize.service.db.schema.DefaultIndexMappingCreator;
import org.elasticsearch.xcontent.XContentBuilder;

import java.io.IOException;

import static org.camunda.optimize.service.db.DatabaseConstants.OPTIMIZE_DATE_FORMAT;
import static org.camunda.optimize.service.db.DatabaseConstants.TIMESTAMP_BASED_IMPORT_INDEX_NAME;

public abstract class TimestampBasedImportIndex<TBuilder> extends DefaultIndexMappingCreator<TBuilder> {

  public static final int VERSION = 5;

  private static final String LAST_IMPORT_EXECUTION_TIMESTAMP = ImportIndexDto.Fields.lastImportExecutionTimestamp;
  public static final String TIMESTAMP_OF_LAST_ENTITY = ImportIndexDto.Fields.timestampOfLastEntity;
  public static final String DB_TYPE_INDEX_REFERS_TO = TimestampBasedImportIndexDto.Fields.esTypeIndexRefersTo;
  public static final String DATA_SOURCE = ImportIndexDto.Fields.dataSource;

  @Override
  public String getIndexName() {
    return TIMESTAMP_BASED_IMPORT_INDEX_NAME;
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
      .startObject(DATA_SOURCE)
        .field("type", "object")
        .field("dynamic", true)
      .endObject()
      .startObject(DB_TYPE_INDEX_REFERS_TO)
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
