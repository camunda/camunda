/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.reader.importindex;

import static org.camunda.optimize.service.db.DatabaseConstants.POSITION_BASED_IMPORT_INDEX_NAME;

import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.datasource.ZeebeDataSourceDto;
import org.camunda.optimize.dto.optimize.index.PositionBasedImportIndexDto;
import org.camunda.optimize.service.db.repository.ImportRepository;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@AllArgsConstructor
public class PositionBasedImportIndexReader
    implements ImportIndexReader<PositionBasedImportIndexDto, ZeebeDataSourceDto> {

  private final ImportRepository importRepository;

  @Override
  public Optional<PositionBasedImportIndexDto> getImportIndex(
      final String typeIndexComesFrom, final ZeebeDataSourceDto dataSourceDto) {
    return importRepository.getImportIndex(
        POSITION_BASED_IMPORT_INDEX_NAME,
        "position based",
        PositionBasedImportIndexDto.class,
        typeIndexComesFrom,
        dataSourceDto);
  }
}
