/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.engine.importing.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.service.es.reader.DecisionDefinitionReader;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@AllArgsConstructor
@Component
@Slf4j
public class DecisionDefinitionResolverService {

  // map contains not xml
  private final Map<String, DecisionDefinitionOptimizeDto> idToDefinitionMap = new ConcurrentHashMap<>();
  private final DecisionDefinitionReader decisionDefinitionReader;

  public Optional<String> getVersionForDecisionDefinitionId(final String decisionDefinitionId) {
    return getFieldForDecisionDefinitionId(decisionDefinitionId, DecisionDefinitionOptimizeDto::getVersion);
  }

  public Optional<String> getKeyForDecisionDefinitionId(final String decisionDefinitionId) {
    return getFieldForDecisionDefinitionId(decisionDefinitionId, DecisionDefinitionOptimizeDto::getKey);
  }

  private Optional<String> getFieldForDecisionDefinitionId(final String decisionDefinitionId,
                                                           final Function<DecisionDefinitionOptimizeDto, String> getField) {
    // #1 read field value from internal cache
    final String fieldValue = Optional.ofNullable(idToDefinitionMap.get(decisionDefinitionId))
      .map(getField)
      // #2 on miss sync the cache and try again
      .orElseGet(() -> {
        log.debug(
          "No definition for decisionDefinitionId {} in cache, syncing decision Definitions",
          decisionDefinitionId
        );

        syncCache();

        DecisionDefinitionOptimizeDto requestedDefinition = idToDefinitionMap.get(decisionDefinitionId);
        return Optional.ofNullable(requestedDefinition).map(getField).orElse(null);
      });

    return Optional.ofNullable(fieldValue);
  }

  private void syncCache() {
    decisionDefinitionReader.getDecisionDefinitions(false, false)
      .forEach(decisionDefinitionOptimizeDto -> idToDefinitionMap.putIfAbsent(
        decisionDefinitionOptimizeDto.getId(), decisionDefinitionOptimizeDto
      ));
  }

}
