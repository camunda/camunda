/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.engine.service;

import io.camunda.optimize.dto.engine.HistoricProcessInstanceDto;
import io.camunda.optimize.rest.engine.EngineContext;
import io.camunda.optimize.service.db.reader.ProcessInstanceReader;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
@Slf4j
public class ProcessInstanceResolverService {

  private final ProcessInstanceReader processInstanceReader;

  public Optional<String> getProcessInstanceDefinitionKey(
      final String processInstanceId, final EngineContext engineContext) {
    Optional<String> instanceDefinitionKey =
        processInstanceReader.getProcessDefinitionKeysForInstanceId(processInstanceId);

    if (instanceDefinitionKey.isEmpty() && engineContext != null) {
      log.info(
          "Instance with id [{}] hasn't been imported yet. "
              + "Trying to directly fetch the instance from the engine.",
          processInstanceId);
      instanceDefinitionKey =
          Optional.ofNullable(engineContext.fetchProcessInstance(processInstanceId))
              .map(HistoricProcessInstanceDto::getProcessDefinitionKey);
    }

    return instanceDefinitionKey;
  }
}
