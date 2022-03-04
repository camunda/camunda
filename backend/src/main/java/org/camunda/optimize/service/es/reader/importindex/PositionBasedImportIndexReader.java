/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.reader.importindex;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.datasource.ZeebeDataSourceDto;
import org.camunda.optimize.dto.optimize.index.PositionBasedImportIndexDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.springframework.stereotype.Component;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.POSITION_BASED_IMPORT_INDEX_NAME;

@Component
@Slf4j
public class PositionBasedImportIndexReader
  extends AbstractImportIndexReader<PositionBasedImportIndexDto, ZeebeDataSourceDto> {

  public PositionBasedImportIndexReader(final OptimizeElasticsearchClient esClient,
                                        final ObjectMapper objectMapper) {
    super(esClient, objectMapper);
  }

  @Override
  protected String getImportIndexType() {
    return "position based";
  }

  @Override
  protected String getImportIndexName() {
    return POSITION_BASED_IMPORT_INDEX_NAME;
  }

  @Override
  protected Class<PositionBasedImportIndexDto> getImportDTOClass() {
    return PositionBasedImportIndexDto.class;
  }

}
