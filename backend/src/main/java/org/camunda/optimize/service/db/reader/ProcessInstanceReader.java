/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.reader;

import static org.camunda.optimize.dto.optimize.DefinitionType.PROCESS;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.PageResultDto;
import org.camunda.optimize.service.db.repository.ProcessInstanceRepository;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
@Slf4j
public class ProcessInstanceReader {
  private final ProcessInstanceRepository processInstanceRepository;
  private final DefinitionInstanceReader definitionInstanceReader;

  public PageResultDto<String> getFirstPageOfProcessInstanceIdsThatHaveVariablesAndEndedBefore(
      final String processDefinitionKey, final OffsetDateTime endDate, final Integer limit) {
    return processInstanceRepository
        .getFirstPageOfProcessInstanceIdsThatHaveVariablesAndEndedBefore(
            processDefinitionKey, endDate, limit);
  }

  public PageResultDto<String> getNextPageOfProcessInstanceIdsThatHaveVariablesAndEndedBefore(
      final String processDefinitionKey,
      final OffsetDateTime endDate,
      final Integer limit,
      final PageResultDto<String> previousPage) {
    return getNextPageOfProcessInstanceIds(
        previousPage,
        () ->
            getFirstPageOfProcessInstanceIdsThatHaveVariablesAndEndedBefore(
                processDefinitionKey, endDate, limit));
  }

  public PageResultDto<String> getFirstPageOfProcessInstanceIdsThatEndedBefore(
      final String processDefinitionKey, final OffsetDateTime endDate, final Integer limit) {
    return processInstanceRepository.getFirstPageOfProcessInstanceIdsThatEndedBefore(
        processDefinitionKey, endDate, limit);
  }

  public PageResultDto<String> getNextPageOfProcessInstanceIdsThatEndedBefore(
      final String processDefinitionKey,
      final OffsetDateTime endDate,
      final Integer limit,
      final PageResultDto<String> previousPage) {
    return getNextPageOfProcessInstanceIds(
        previousPage,
        () ->
            getFirstPageOfProcessInstanceIdsThatEndedBefore(processDefinitionKey, endDate, limit));
  }

  public Set<String> getExistingProcessDefinitionKeysFromInstances() {
    return definitionInstanceReader.getAllExistingDefinitionKeys(PROCESS);
  }

  public Optional<String> getProcessDefinitionKeysForInstanceId(final String instanceId) {
    return definitionInstanceReader
        .getAllExistingDefinitionKeys(PROCESS, Collections.singleton(instanceId))
        .stream()
        .findFirst();
  }

  public boolean processDefinitionHasStartedInstances(final String processDefinitionKey) {
    return processInstanceRepository.processDefinitionHasStartedInstances(processDefinitionKey);
  }

  private PageResultDto<String> getNextPageOfProcessInstanceIds(
      final PageResultDto<String> previousPage,
      final Supplier<PageResultDto<String>> firstPageFetchFunction) {
    return processInstanceRepository.getNextPageOfProcessInstanceIds(
        previousPage, firstPageFetchFunction);
  }
}
