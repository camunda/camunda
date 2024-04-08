/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.Supplier;
import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.importing.EventProcessGatewayDto;
import org.camunda.optimize.dto.optimize.query.PageResultDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessInstanceDto;

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

  void bulkImportEvents(
      String index,
      String importItemName,
      List<EventProcessInstanceDto> processInstanceDtos,
      List<EventProcessGatewayDto> gatewayLookup);

  void deleteEndedBefore(String index, OffsetDateTime endDate, String deletedItemIdentifier);

  void deleteVariablesOfInstancesThatEndedBefore(
      String index, OffsetDateTime endDate, String updateItem);

  void deleteEventsWithIdsInFromAllInstances(
      String index, List<String> eventIdsToDelete, String updateItem);

  boolean processDefinitionHasStartedInstances(String processDefinitionKey);

  PageResultDto<String> getNextPageOfProcessInstanceIds(
      PageResultDto<String> previousPage, Supplier<PageResultDto<String>> firstPageFetchFunction);

  PageResultDto<String> getFirstPageOfProcessInstanceIdsThatHaveVariablesAndEndedBefore(
      String processDefinitionKey, OffsetDateTime endDate, Integer limit);

  PageResultDto<String> getFirstPageOfProcessInstanceIdsThatEndedBefore(
      String processDefinitionKey, OffsetDateTime endDate, Integer limit);
}
