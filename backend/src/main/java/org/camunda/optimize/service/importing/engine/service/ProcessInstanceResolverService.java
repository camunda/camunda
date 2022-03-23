/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.engine.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.engine.HistoricProcessInstanceDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.es.reader.ProcessInstanceReader;
import org.springframework.stereotype.Component;

import java.util.Optional;

@AllArgsConstructor
@Component
@Slf4j
public class ProcessInstanceResolverService {

  private final ProcessInstanceReader processInstanceReader;

  public Optional<String> getProcessInstanceDefinitionKey(final String processInstanceId,
                                                          final EngineContext engineContext) {
    Optional<String> instanceDefinitionKey =
      processInstanceReader.getProcessDefinitionKeysForInstanceId(processInstanceId);

    if (instanceDefinitionKey.isEmpty() && engineContext != null) {
      log.info(
        "Instance with id [{}] hasn't been imported yet. " +
          "Trying to directly fetch the instance from the engine.",
        processInstanceId
      );
      instanceDefinitionKey = Optional.ofNullable(engineContext.fetchProcessInstance(processInstanceId))
        .map(HistoricProcessInstanceDto::getProcessDefinitionKey);
    }

    return instanceDefinitionKey;
  }
}
