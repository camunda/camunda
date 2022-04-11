/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.engine.service.definition;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.es.reader.ProcessDefinitionReader;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

@AllArgsConstructor
@Component
@Slf4j
public class ProcessDefinitionResolverService extends AbstractDefinitionResolverService<ProcessDefinitionOptimizeDto> {

  private final ProcessDefinitionReader processDefinitionReader;

  @Override
  protected ProcessDefinitionOptimizeDto fetchFromEngine(final String definitionId,
                                                         final EngineContext engineContext) {
    return engineContext.fetchProcessDefinition(definitionId);
  }

  @Override
  protected void syncCache() {
    processDefinitionReader.getAllProcessDefinitions()
      .forEach(this::addToCacheIfNotNull);
  }

  public <T> T enrichEngineDtoWithDefinitionKey(final EngineContext engineContext,
                                                final T engineEntity,
                                                final Function<T, String> definitionKeyGetter,
                                                final Function<T, String> definitionIdGetter,
                                                final BiConsumer<T, String> definitionKeySetter) {
    // Under some circumstances, eg due to very old process instance data or specific userOperationLogs, the
    // definitionKey may not be present. It is required to write to the correct instanceIndex, so we need to retrieve
    // it if possible
    if (definitionKeyGetter.apply(engineEntity) == null) {
      Optional<String> definitionKey = Optional.empty();
      if (definitionIdGetter.apply(engineEntity) != null) {
        definitionKey = getDefinition(
          definitionIdGetter.apply(engineEntity),
          engineContext
        ).map(ProcessDefinitionOptimizeDto::getKey);
      }
      definitionKey.ifPresent(key -> definitionKeySetter.accept(engineEntity, key));
    }
    return engineEntity;
  }

}
