/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.repository;

import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;

import java.util.List;

public interface ProcessInstanceRepository {
  void doImportBulkRequestWithList(final String importItemName, final List<ProcessInstanceDto> processInstanceDtos);

  void updateProcessInstanceStateForProcessDefinitionId(
    final String importItemName,
    final String definitionKey,
    final String processDefinitionId,
    final String state);

  void updateAllProcessInstancesStates(
    final String importItemName,
    final String definitionKey,
    final String state
  );

  void deleteByIds(final String index, final String itemName, final List<String> processInstanceIds);

  void bulkImport(final String bulkRequestName, final List<ImportRequestDto> importRequests);
}
