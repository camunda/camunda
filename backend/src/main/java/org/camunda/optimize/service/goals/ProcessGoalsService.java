/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.goals;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionWithTenantIdsDto;
import org.camunda.optimize.dto.optimize.query.goals.ProcessDurationGoalDto;
import org.camunda.optimize.dto.optimize.query.goals.ProcessDurationGoalResultDto;
import org.camunda.optimize.dto.optimize.query.goals.ProcessDurationGoalsAndResultsDto;
import org.camunda.optimize.dto.optimize.query.goals.ProcessGoalsDto;
import org.camunda.optimize.dto.optimize.query.goals.ProcessGoalsResponseDto;
import org.camunda.optimize.service.DefinitionService;
import org.camunda.optimize.service.es.reader.ProcessGoalsReader;
import org.camunda.optimize.service.es.writer.ProcessGoalsWriter;
import org.camunda.optimize.service.exceptions.OptimizeValidationException;
import org.camunda.optimize.service.identity.AbstractIdentityService;
import org.camunda.optimize.service.security.util.definition.DataSourceDefinitionAuthorizationService;
import org.springframework.stereotype.Component;

import javax.ws.rs.ForbiddenException;
import javax.ws.rs.NotFoundException;
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
  private final DataSourceDefinitionAuthorizationService definitionAuthorizationService;
  private final ProcessGoalsWriter processGoalsWriter;
  private final ProcessGoalsReader processGoalsReader;
  private final ProcessGoalsEvaluator processGoalsEvaluator;
  private final AbstractIdentityService identityService;

  public List<ProcessGoalsResponseDto> getProcessDefinitionGoals(final String userId) {
    final Map<String, String> procDefKeysAndName = new HashMap<>();
    definitionService.getAllDefinitionsWithTenants(PROCESS)
      .stream()
      .filter(def ->
                definitionAuthorizationService.isAuthorizedToAccessDefinition(
                  userId,
                  PROCESS,
                  def.getKey(),
                  def.getTenantIds()
                )
      )
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
          goalsForKey.flatMap(goals -> Optional.ofNullable(goals.getOwner())
            .map(owner -> identityService.getIdentityNameById(owner).orElse(owner))).orElse(null),
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
    validateGoals(durationGoals);
    checkAuthorizationToProcessDefinition(userId, processDefKey);
    final ProcessGoalsDto processGoalsDto = new ProcessGoalsDto();
    processGoalsDto.setProcessDefinitionKey(processDefKey);
    processGoalsDto.setOwner(null);
    processGoalsDto.setDurationGoals(durationGoals);
    processGoalsWriter.updateProcessGoals(processGoalsDto);
  }

  public List<ProcessDurationGoalResultDto> evaluateGoalsForProcess(final String userId,
                                                                    final String processDefKey,
                                                                    final List<ProcessDurationGoalDto> goals) {
    validateGoals(goals);
    checkAuthorizationToProcessDefinition(userId, processDefKey);
    return processGoalsEvaluator.evaluateGoalsForProcess(new ProcessGoalsDto(processDefKey, null, goals));
  }

  public void updateProcessGoalsOwner(final String userId, final String processDefKey, String ownerId) {
    checkAuthorizationToProcessDefinition(userId, processDefKey);
    String ownerIdToSave = null;
    if (ownerId != null) {
      if (!identityService.isUserAuthorizedToAccessIdentity(userId, new IdentityDto(ownerId, IdentityType.USER))) {
        throw new ForbiddenException(String.format(
          "User with ID %s is not permitted to set process goal owner to user with ID: %s", userId, ownerId));
      }
      ownerIdToSave = identityService.getUserById(ownerId).map(IdentityDto::getId)
        .orElseThrow(() -> new NotFoundException("User with ID does not exist: " + ownerId));
    }
    processGoalsWriter.updateProcessOwner(processDefKey, ownerIdToSave);
  }

  private void validateGoals(final List<ProcessDurationGoalDto> durationGoals) {
    final boolean containsDuplicateTypes = durationGoals.stream()
      .collect(Collectors.groupingBy(ProcessDurationGoalDto::getType, Collectors.counting()))
      .values().stream()
      .anyMatch(count -> count > 1);
    if (containsDuplicateTypes) {
      throw new OptimizeValidationException("Goal types must be unique for each process definition key");
    }
  }

  private void checkAuthorizationToProcessDefinition(final String userId, final String processDefKey) {
    final Optional<DefinitionWithTenantIdsDto> definitionForKey =
      definitionService.getProcessDefinitionWithTenants(processDefKey);
    if (definitionForKey.isEmpty()) {
      throw new NotFoundException("Process definition with key " + processDefKey + "does not exist");
    }
    if (!definitionAuthorizationService.isAuthorizedToAccessDefinition(
      userId, PROCESS, definitionForKey.get().getKey(), definitionForKey.get().getTenantIds())) {
      throw new ForbiddenException("User is not authorized to goals for process definition with key " + processDefKey);
    }
  }
}
