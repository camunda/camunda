/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository;

import io.camunda.optimize.dto.optimize.datasource.DataSourceDto;
import io.camunda.optimize.dto.optimize.index.EngineImportIndexDto;
import io.camunda.optimize.dto.optimize.index.ImportIndexDto;
import io.camunda.optimize.dto.optimize.index.PositionBasedImportIndexDto;
import io.camunda.optimize.dto.optimize.index.TimestampBasedImportIndexDto;
import java.util.List;
import java.util.Optional;

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

  void importIndices(String importItemName, List<EngineImportIndexDto> engineImportIndexDtos);
}
