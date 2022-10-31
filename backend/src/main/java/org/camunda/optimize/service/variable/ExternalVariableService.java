/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.variable;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.query.variable.ExternalProcessVariableDto;
import org.camunda.optimize.service.es.writer.variable.ExternalProcessVariableWriter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static java.util.stream.Collectors.toMap;

@Component
@RequiredArgsConstructor
public class ExternalVariableService {

  private final ExternalProcessVariableWriter externalProcessVariableWriter;

  public void storeExternalProcessVariables(final List<ExternalProcessVariableDto> externalProcessVariables) {
    externalProcessVariableWriter.writeExternalProcessVariables(
      resolveDuplicateVariableUpdatesPerProcessInstance(externalProcessVariables)
    );
  }

  private List<ExternalProcessVariableDto> resolveDuplicateVariableUpdatesPerProcessInstance(
    final List<ExternalProcessVariableDto> externalProcessVariables) {
    // if we have more than one variable update for the same variable within one process instance, we only import the latest
    // variable in the batch
    List<ExternalProcessVariableDto> deduplicatedVariables = new ArrayList<>();
    Map<String, List<ExternalProcessVariableDto>> variablesByProcessInstanceId = new HashMap<>();
    for (ExternalProcessVariableDto variable : externalProcessVariables) {
      variablesByProcessInstanceId.putIfAbsent(variable.getProcessInstanceId(), new ArrayList<>());
      variablesByProcessInstanceId.get(variable.getProcessInstanceId()).add(variable);
    }
    variablesByProcessInstanceId
      .forEach((id, vars) -> deduplicatedVariables.addAll(resolveDuplicateVariableUpdates(vars)));
    return deduplicatedVariables;
  }

  private Collection<ExternalProcessVariableDto> resolveDuplicateVariableUpdates(final List<ExternalProcessVariableDto> externalEntities) {
    return externalEntities.stream().collect(toMap(
        ExternalProcessVariableDto::getVariableId,
        Function.identity(),
        // if there is more than one update for the same variable, the last one in the batch wins
        (var1, var2) -> var2
      )).values();
  }
}
