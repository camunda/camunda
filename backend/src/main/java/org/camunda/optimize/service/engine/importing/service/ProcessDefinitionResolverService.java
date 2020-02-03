/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.engine.importing.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.service.es.reader.ProcessDefinitionReader;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@AllArgsConstructor
@Component
@Slf4j
public class ProcessDefinitionResolverService {

  // map contains not xml
  private final Map<String, ProcessDefinitionOptimizeDto> idToDefinitionMap = new ConcurrentHashMap<>();
  private final ProcessDefinitionReader processDefinitionReader;

  public Optional<ProcessDefinitionOptimizeDto> getDefinitionForProcessDefinitionId(final String processDefinitionId) {
    // #1 read field value from internal cache
    final ProcessDefinitionOptimizeDto processDefinition = Optional.ofNullable(idToDefinitionMap.get(processDefinitionId))
      // #2 on miss sync the cache and try again
      .orElseGet(() -> {
        log.debug(
          "No definition for processDefinitionId {} in cache, syncing Process Definitions",
          processDefinitionId
        );
        syncCache();
        return Optional.ofNullable(idToDefinitionMap.get(processDefinitionId)).orElse(null);
      });

    return Optional.ofNullable(processDefinition);
  }

  private void syncCache() {
    processDefinitionReader.getProcessDefinitions(false, false)
      .forEach(processDefinitionOptimizeDto -> idToDefinitionMap.putIfAbsent(
        processDefinitionOptimizeDto.getId(), processDefinitionOptimizeDto
      ));
  }

}
