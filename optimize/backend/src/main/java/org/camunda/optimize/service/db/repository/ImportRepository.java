/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.repository;

import java.util.List;
import java.util.Optional;
import org.camunda.optimize.dto.optimize.datasource.DataSourceDto;
import org.camunda.optimize.dto.optimize.index.AllEntitiesBasedImportIndexDto;
import org.camunda.optimize.dto.optimize.index.EngineImportIndexDto;
import org.camunda.optimize.dto.optimize.index.ImportIndexDto;
import org.camunda.optimize.dto.optimize.index.PositionBasedImportIndexDto;
import org.camunda.optimize.dto.optimize.index.TimestampBasedImportIndexDto;

public interface ImportRepository {
  List<TimestampBasedImportIndexDto> getAllTimestampBasedImportIndicesForTypes(
      List<String> indexTypes);

  <T extends ImportIndexDto<D>, D extends DataSourceDto> Optional<T> getImportIndex(
      String indexName,
      String indexType,
      Class<T> importDTOClass,
      String typeIndexComesFrom,
      D dataSourceDto);

  void importPositionBasedIndices(
      String importItemName, List<PositionBasedImportIndexDto> importIndexDtos);

  Optional<AllEntitiesBasedImportIndexDto> getImportIndex(String id);

  void importIndices(String importItemName, List<EngineImportIndexDto> engineImportIndexDtos);
}
