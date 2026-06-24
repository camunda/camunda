/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.reader.importindex;

import io.camunda.optimize.dto.optimize.datasource.DataSourceDto;
import io.camunda.optimize.dto.optimize.index.ImportIndexDto;
import java.util.Optional;

public interface ImportIndexReader<T extends ImportIndexDto<D>, D extends DataSourceDto> {
  Optional<T> getImportIndex(String typeIndexComesFrom, D dataSourceDto);
}
