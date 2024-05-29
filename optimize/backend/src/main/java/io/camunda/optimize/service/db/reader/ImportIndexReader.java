/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.service.db.reader;

import io.camunda.optimize.dto.optimize.index.AllEntitiesBasedImportIndexDto;
import io.camunda.optimize.service.db.repository.ImportRepository;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
@Slf4j
public class ImportIndexReader {
  private final ImportRepository importRepository;

  public Optional<AllEntitiesBasedImportIndexDto> getImportIndex(String id) {
    log.debug("Fetching import index of type [{}]", id);
    return importRepository.getImportIndex(id);
  }
}
