/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.reader;

import static io.camunda.optimize.dto.optimize.DefinitionType.PROCESS;

import io.camunda.optimize.dto.optimize.query.PageResultDto;
import io.camunda.optimize.service.db.repository.ProcessInstanceRepository;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

@Component
public class ProcessInstanceReader {

  private static final Logger log = org.slf4j.LoggerFactory.getLogger(ProcessInstanceReader.class);
  private final ProcessInstanceRepository processInstanceRepository;
  private final DefinitionInstanceReader definitionInstanceReader;

  public ProcessInstanceReader(
      final ProcessInstanceRepository processInstanceRepository,
      final DefinitionInstanceReader definitionInstanceReader) {
    this.processInstanceRepository = processInstanceRepository;
    this.definitionInstanceReader = definitionInstanceReader;
  }

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
