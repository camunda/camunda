/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.reader.importindex;

import java.util.Optional;
import org.camunda.optimize.dto.optimize.datasource.DataSourceDto;
import org.camunda.optimize.dto.optimize.index.ImportIndexDto;

public interface ImportIndexReader<T extends ImportIndexDto<D>, D extends DataSourceDto> {
  Optional<T> getImportIndex(String typeIndexComesFrom, D dataSourceDto);
}
