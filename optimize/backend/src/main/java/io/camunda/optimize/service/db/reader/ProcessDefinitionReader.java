/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.reader;

import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ProcessDefinitionReader {

  default List<ProcessDefinitionOptimizeDto> getAllProcessDefinitions() {
    return getDefinitionReader().getDefinitions(DefinitionType.PROCESS, false, false, true);
  }

  default String getLatestVersionToKey(final String key) {
    return getDefinitionReader().getLatestVersionToKey(DefinitionType.PROCESS, key);
  }

  Optional<ProcessDefinitionOptimizeDto> getProcessDefinition(final String definitionId);

  Set<String> getAllNonOnboardedProcessDefinitionKeys();

  DefinitionReader getDefinitionReader();
}
