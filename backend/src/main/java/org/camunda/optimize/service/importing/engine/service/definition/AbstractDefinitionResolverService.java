/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.engine.service.definition;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import org.camunda.optimize.rest.engine.EngineContext;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@AllArgsConstructor
@Slf4j
public abstract class AbstractDefinitionResolverService<T extends DefinitionOptimizeResponseDto> {

  // map contains not xml
  private final Map<String, T> idToDefinitionMap = new ConcurrentHashMap<>();

  public Optional<T> getDefinition(final String definitionId,
                                   final EngineContext engineContext) {
    // #1 read value from internal cache
    T value = idToDefinitionMap.get(definitionId);

    // #2 on miss sync the cache and try again
    if (value == null) {
      log.debug(
        "No definition for definitionId {} in cache, syncing definitions",
        definitionId
      );

      syncCache();
      value = idToDefinitionMap.get(definitionId);
    }

    // #3 on miss fetch directly from the engine
    if (value == null && engineContext != null) {
      log.info(
        "Definition with id [{}] hasn't been imported yet. " +
          "Trying to directly fetch the definition from the engine.",
        definitionId
      );
      value = fetchFromEngine(definitionId, engineContext);
      addToCacheIfNotNull(value);
    }

    return Optional.ofNullable(value);
  }

  protected abstract T fetchFromEngine(final String definitionId, final EngineContext engineContext);

  protected abstract void syncCache();

  protected void addToCacheIfNotNull(final T newEntry) {
    Optional.ofNullable(newEntry)
      .ifPresent(definition -> idToDefinitionMap.put(definition.getId(), definition));
  }

}
