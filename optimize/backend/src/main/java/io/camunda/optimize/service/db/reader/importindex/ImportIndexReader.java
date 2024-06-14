/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.service.db.reader.importindex;

import io.camunda.optimize.dto.optimize.datasource.DataSourceDto;
import io.camunda.optimize.dto.optimize.index.ImportIndexDto;
import java.util.Optional;

public interface ImportIndexReader<T extends ImportIndexDto<D>, D extends DataSourceDto> {
  Optional<T> getImportIndex(String typeIndexComesFrom, D dataSourceDto);
}
