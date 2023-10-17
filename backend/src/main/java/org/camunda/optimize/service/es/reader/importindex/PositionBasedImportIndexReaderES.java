/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.reader.importindex;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.datasource.ZeebeDataSourceDto;
import org.camunda.optimize.dto.optimize.index.PositionBasedImportIndexDto;
import org.camunda.optimize.service.db.reader.importindex.PositionBasedImportIndexReader;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import static org.camunda.optimize.service.db.DatabaseConstants.POSITION_BASED_IMPORT_INDEX_NAME;

@Component
@Slf4j
@Conditional(ElasticSearchCondition.class)
public class PositionBasedImportIndexReaderES extends AbstractImportIndexReaderES<PositionBasedImportIndexDto, ZeebeDataSourceDto> implements PositionBasedImportIndexReader {

  public PositionBasedImportIndexReaderES(final OptimizeElasticsearchClient esClient,
                                          final ObjectMapper objectMapper) {
    super(esClient, objectMapper);
  }

  @Override
  public String getImportIndexType() {
    return "position based";
  }

  @Override
  public String getImportIndexName() {
    return POSITION_BASED_IMPORT_INDEX_NAME;
  }

  @Override
  public Class<PositionBasedImportIndexDto> getImportDTOClass() {
    return PositionBasedImportIndexDto.class;
  }

}
