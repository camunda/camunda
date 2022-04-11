/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.reader;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@AllArgsConstructor
@Component
@Slf4j
public class ProcessDefinitionReader {

  private final DefinitionReader definitionReader;

  public List<ProcessDefinitionOptimizeDto> getAllProcessDefinitions() {
    return definitionReader.getDefinitions(DefinitionType.PROCESS, false, false, true);
  }

  public List<ProcessDefinitionOptimizeDto> getProcessDefinitions(final Set<String> definitionKeys) {
    return definitionReader.getDefinitions(DefinitionType.PROCESS, definitionKeys, false, false, true);
  }

  public Optional<ProcessDefinitionOptimizeDto> getProcessDefinition(final String definitionId) {
    return definitionReader.getDefinitionsByIds(
      DefinitionType.PROCESS,
      Collections.singleton(definitionId),
      false,
      false,
      true
    ).stream().findFirst().map(ProcessDefinitionOptimizeDto.class::cast);
  }

  public String getLatestVersionToKey(String key) {
    return definitionReader.getLatestVersionToKey(DefinitionType.PROCESS, key);
  }
}
