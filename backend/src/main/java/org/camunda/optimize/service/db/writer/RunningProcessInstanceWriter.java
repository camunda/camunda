/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.writer;

import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.BUSINESS_KEY;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.DATA_SOURCE;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.PROCESS_DEFINITION_ID;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.PROCESS_DEFINITION_KEY;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.PROCESS_DEFINITION_VERSION;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.START_DATE;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.STATE;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.TENANT_ID;

public interface RunningProcessInstanceWriter {

  Set<String> UPDATABLE_FIELDS = Set.of(
    PROCESS_DEFINITION_KEY, PROCESS_DEFINITION_VERSION, PROCESS_DEFINITION_ID,
    BUSINESS_KEY, START_DATE, STATE,
    DATA_SOURCE, TENANT_ID
  );
  String IMPORT_ITEM_NAME = "running process instances";

  List<ImportRequestDto> generateProcessInstanceImports(List<ProcessInstanceDto> processInstanceDtos);

  void importProcessInstancesFromUserOperationLogs(final List<ProcessInstanceDto> processInstanceDtos);

  void importProcessInstancesForProcessDefinitionIds(
    final Map<String, Map<String, String>> definitionKeyToIdToStateMap);

  void importProcessInstancesForProcessDefinitionKeys(
    final Map<String, String> definitionKeyToNewStateMap);

}
