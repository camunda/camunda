/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionResponseDto;
import org.camunda.optimize.dto.optimize.query.goals.ProcessDurationGoalDto;
import org.camunda.optimize.dto.optimize.query.goals.ProcessGoalsDto;
import org.camunda.optimize.dto.optimize.query.goals.ProcessGoalsResponseDto;
import org.camunda.optimize.service.es.reader.ProcessDefinitionReader;
import org.camunda.optimize.service.es.writer.ProcessGoalsWriter;
import org.camunda.optimize.service.exceptions.OptimizeValidationException;
import org.camunda.optimize.service.security.util.definition.DataSourceDefinitionAuthorizationService;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.camunda.optimize.dto.optimize.DefinitionType.PROCESS;

@AllArgsConstructor
@Component
@Slf4j
public class ProcessGoalsService {

  private final ProcessDefinitionReader processDefinitionReader;
  private final DefinitionService definitionService;
  private final DataSourceDefinitionAuthorizationService dataSourceDefinitionAuthorizationService;
  private final ProcessGoalsWriter processGoalsWriter;

  public List<ProcessGoalsResponseDto> getProcessDefinitionGoals(final String userId) {
    return processDefinitionReader.getAllProcessDefinitions().stream()
      .filter(processDefinition -> dataSourceDefinitionAuthorizationService
        .isAuthorizedToAccessDefinition(userId, processDefinition))
      .map(processDefinition -> ProcessGoalsResponseDto.from(
             new ProcessGoalsDto(
               processDefinition.getKey(),
               null,
               Collections.emptyList()
             ),
             StringUtils.isEmpty(processDefinition.getName()) ? processDefinition.getKey() : processDefinition.getName()
           )
      ).distinct().collect(Collectors.toList());
  }

  public void updateProcessGoals(final String userId,
                                 final String processDefKey,
                                 final List<ProcessDurationGoalDto> durationGoals) {
    final Optional<DefinitionResponseDto> definitionForKey =
      definitionService.getDefinitionWithAvailableTenants(PROCESS, processDefKey, userId);
    if (definitionForKey.isEmpty()) {
      throw new OptimizeValidationException(
        "User is not authorized to create goals for process definition with key " + processDefKey
          + ", or it does not exist");
    }
    final boolean containsDuplicateTypes = durationGoals.stream()
      .collect(Collectors.groupingBy(ProcessDurationGoalDto::getType, Collectors.counting()))
      .values().stream()
      .anyMatch(count -> count > 1);
    if (containsDuplicateTypes) {
      throw new OptimizeValidationException("Goal types must be unique for each process definition key");
    }
    final ProcessGoalsDto processGoalsDto = new ProcessGoalsDto();
    processGoalsDto.setProcessDefinitionKey(processDefKey);
    processGoalsDto.setOwner(null);
    processGoalsDto.setDurationGoals(durationGoals);
    processGoalsWriter.createProcessGoals(processGoalsDto);
  }

}
