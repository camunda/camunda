/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.goals;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionResponseDto;
import org.camunda.optimize.dto.optimize.query.goals.ProcessDurationGoalDto;
import org.camunda.optimize.dto.optimize.query.goals.ProcessDurationGoalResultDto;
import org.camunda.optimize.dto.optimize.query.goals.ProcessDurationGoalsAndResultsDto;
import org.camunda.optimize.dto.optimize.query.goals.ProcessGoalsDto;
import org.camunda.optimize.dto.optimize.query.goals.ProcessGoalsResponseDto;
import org.camunda.optimize.service.DefinitionService;
import org.camunda.optimize.service.es.reader.ProcessGoalsReader;
import org.camunda.optimize.service.es.writer.ProcessGoalsWriter;
import org.camunda.optimize.service.exceptions.OptimizeValidationException;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.camunda.optimize.dto.optimize.DefinitionType.PROCESS;

@AllArgsConstructor
@Component
@Slf4j
public class ProcessGoalsService {

  private final DefinitionService definitionService;
  private final ProcessGoalsWriter processGoalsWriter;
  private final ProcessGoalsReader processGoalsReader;
  private final ProcessGoalsEvaluator processGoalsEvaluator;

  public List<ProcessGoalsResponseDto> getProcessDefinitionGoals(final String userId) {
    final Map<String, String> procDefKeysAndName = new HashMap<>();
    definitionService.getFullyImportedDefinitions(PROCESS, userId, false)
      .forEach(definition -> procDefKeysAndName.put(definition.getKey(), definition.getName()));
    final Map<String, ProcessGoalsDto> goalsForProcessesByKey =
      processGoalsReader.getGoalsForProcessesByKey(procDefKeysAndName.keySet());
    final Map<String, List<ProcessDurationGoalResultDto>> goalResultsByKey =
      processGoalsEvaluator.evaluateGoals(goalsForProcessesByKey);
    return procDefKeysAndName.entrySet()
      .stream()
      .map(entry -> {
        final String procDefKey = entry.getKey();
        final Optional<ProcessGoalsDto> goalsForKey = Optional.ofNullable(goalsForProcessesByKey.get(procDefKey));
        return new ProcessGoalsResponseDto(
          StringUtils.isEmpty(entry.getValue()) ? procDefKey : entry.getValue(),
          procDefKey,
          null,
          goalsForKey.map(
            goals -> {
              ProcessDurationGoalsAndResultsDto goalsAndResults = new ProcessDurationGoalsAndResultsDto();
              goalsAndResults.setGoals(goals.getDurationGoals());
              goalsAndResults.setResults(
                Optional.ofNullable(goalResultsByKey.get(procDefKey)).orElse(Collections.emptyList()));
              return goalsAndResults;
            }
          ).orElseGet(ProcessDurationGoalsAndResultsDto::new)
        );
      }).collect(Collectors.toList());
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
    processGoalsWriter.updateProcessGoals(processGoalsDto);
  }

}
