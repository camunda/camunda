/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.migrate37to38.indices;

import org.camunda.optimize.dto.optimize.index.ImportIndexDto;
import org.camunda.optimize.dto.optimize.index.PositionBasedImportIndexDto;
import org.camunda.optimize.service.es.schema.DefaultIndexMappingCreator;
import org.elasticsearch.xcontent.XContentBuilder;

import java.io.IOException;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.POSITION_BASED_IMPORT_INDEX_NAME;

public class PositionBasedImportIndexOld extends DefaultIndexMappingCreator {

  private static final int VERSION = 1;

  private static final String LAST_IMPORT_EXECUTION_TIMESTAMP =
    ImportIndexDto.Fields.lastImportExecutionTimestamp;
  private static final String POSITION_OF_LAST_ENTITY = PositionBasedImportIndexDto.Fields.positionOfLastEntity;
  private static final String ES_TYPE_INDEX_REFERS_TO = PositionBasedImportIndexDto.Fields.esTypeIndexRefersTo;
  private static final String DATA_SOURCE = "dataSourceDto";

  @Override
  public String getIndexName() {
    return POSITION_BASED_IMPORT_INDEX_NAME;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  public XContentBuilder addProperties(XContentBuilder xContentBuilder) throws IOException {
    // @formatter:off
    return xContentBuilder
      .startObject(DATA_SOURCE)
        .field("type", "object")
        .field("dynamic", true)
      .endObject()
      .startObject(ES_TYPE_INDEX_REFERS_TO)
        .field("type", "keyword")
      .endObject()
      .startObject(POSITION_OF_LAST_ENTITY)
        .field("type", "keyword")
      .endObject()
      .startObject(LAST_IMPORT_EXECUTION_TIMESTAMP)
        .field("type", "date")
        .field("format", OPTIMIZE_DATE_FORMAT)
      .endObject();
    // @formatter:on
  }
}
