/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.reader;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;

public interface ProcessDefinitionReader {

  default List<ProcessDefinitionOptimizeDto> getAllProcessDefinitions() {
    return getDefinitionReader().getDefinitions(DefinitionType.PROCESS, false, false, true);
  }

  default String getLatestVersionToKey(String key) {
    return getDefinitionReader().getLatestVersionToKey(DefinitionType.PROCESS, key);
  }

  Optional<ProcessDefinitionOptimizeDto> getProcessDefinition(final String definitionId);

  Set<String> getAllNonOnboardedProcessDefinitionKeys();

  DefinitionReader getDefinitionReader();
}
