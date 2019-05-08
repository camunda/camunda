/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.engine.importing.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.es.reader.DecisionDefinitionReader;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@AllArgsConstructor
@Component
@Slf4j
public class DecisionDefinitionVersionResolverService {
  private final Map<String, String> idToVersionMap = new ConcurrentHashMap<>();
  private final DecisionDefinitionReader decisionDefinitionReader;

  public Optional<String> getVersionForDecisionDefinitionId(final String decisionDefinitionId) {
    // #1 read version from internal cache
    final String version = Optional.ofNullable(idToVersionMap.get(decisionDefinitionId))
      // #2 on miss sync the cache and try again
      .orElseGet(() -> {
        log.debug(
          "No version for decisionDefinitionId {} in cache, syncing decision Definitions",
          decisionDefinitionId
        );

        syncCache();

        return idToVersionMap.get(decisionDefinitionId);
      });

    return Optional.ofNullable(version);
  }

  private void syncCache() {
    decisionDefinitionReader.getDecisionDefinitions(false, false)
      .forEach(decisionDefinitionOptimizeDto -> idToVersionMap.putIfAbsent(
        decisionDefinitionOptimizeDto.getId(), decisionDefinitionOptimizeDto.getVersion()
      ));
  }

}
