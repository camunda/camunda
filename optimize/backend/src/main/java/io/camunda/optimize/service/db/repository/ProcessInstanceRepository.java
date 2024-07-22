/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository;

import io.camunda.optimize.dto.optimize.ImportRequestDto;
import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.dto.optimize.query.PageResultDto;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.Supplier;

public interface ProcessInstanceRepository {
  void bulkImportProcessInstances(
      final String importItemName, final List<ProcessInstanceDto> processInstanceDtos);

  void updateProcessInstanceStateForProcessDefinitionId(
      final String importItemName,
      final String definitionKey,
      final String processDefinitionId,
      final String state);

  void updateAllProcessInstancesStates(
      final String importItemName, final String definitionKey, final String state);

  void deleteByIds(
      final String index, final String itemName, final List<String> processInstanceIds);

  void bulkImport(final String bulkRequestName, final List<ImportRequestDto> importRequests);

  void deleteEndedBefore(String index, OffsetDateTime endDate, String deletedItemIdentifier);

  void deleteVariablesOfInstancesThatEndedBefore(
      String index, OffsetDateTime endDate, String updateItem);

  boolean processDefinitionHasStartedInstances(String processDefinitionKey);

  PageResultDto<String> getNextPageOfProcessInstanceIds(
      PageResultDto<String> previousPage, Supplier<PageResultDto<String>> firstPageFetchFunction);

  PageResultDto<String> getFirstPageOfProcessInstanceIdsThatHaveVariablesAndEndedBefore(
      String processDefinitionKey, OffsetDateTime endDate, Integer limit);

  PageResultDto<String> getFirstPageOfProcessInstanceIdsThatEndedBefore(
      String processDefinitionKey, OffsetDateTime endDate, Integer limit);
}
