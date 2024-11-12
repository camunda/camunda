/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.variable;

import static java.util.stream.Collectors.toMap;

import io.camunda.optimize.dto.optimize.query.variable.ExternalProcessVariableDto;
import io.camunda.optimize.service.db.writer.variable.ExternalProcessVariableWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.stereotype.Component;

@Component
public class ExternalVariableService {

  private final ExternalProcessVariableWriter externalProcessVariableWriter;

  public ExternalVariableService(
      final ExternalProcessVariableWriter externalProcessVariableWriter) {
    this.externalProcessVariableWriter = externalProcessVariableWriter;
  }

  public void storeExternalProcessVariables(
      final List<ExternalProcessVariableDto> externalProcessVariables) {
    externalProcessVariableWriter.writeExternalProcessVariables(
        resolveDuplicateVariableUpdatesPerProcessInstance(externalProcessVariables));
  }

  private List<ExternalProcessVariableDto> resolveDuplicateVariableUpdatesPerProcessInstance(
      final List<ExternalProcessVariableDto> externalProcessVariables) {
    // if we have more than one variable update for the same variable within one process instance,
    // we only import the latest
    // variable in the batch
    final List<ExternalProcessVariableDto> deduplicatedVariables = new ArrayList<>();
    final Map<String, List<ExternalProcessVariableDto>> variablesByProcessInstanceId =
        new HashMap<>();
    for (final ExternalProcessVariableDto variable : externalProcessVariables) {
      variablesByProcessInstanceId.putIfAbsent(variable.getProcessInstanceId(), new ArrayList<>());
      variablesByProcessInstanceId.get(variable.getProcessInstanceId()).add(variable);
    }
    variablesByProcessInstanceId.forEach(
        (id, vars) -> deduplicatedVariables.addAll(resolveDuplicateVariableUpdates(vars)));
    return deduplicatedVariables;
  }

  private Collection<ExternalProcessVariableDto> resolveDuplicateVariableUpdates(
      final List<ExternalProcessVariableDto> externalEntities) {
    return externalEntities.stream()
        .collect(
            toMap(
                ExternalProcessVariableDto::getVariableId,
                Function.identity(),
                // if there is more than one update for the same variable, the last one in the batch
                // wins
                (var1, var2) -> var2))
        .values();
  }
}
