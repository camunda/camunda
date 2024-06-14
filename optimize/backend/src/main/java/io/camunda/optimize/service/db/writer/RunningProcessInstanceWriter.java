/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.service.db.writer;

import static io.camunda.optimize.service.db.DatabaseConstants.PROCESS_INSTANCE_MULTI_ALIAS;
import static io.camunda.optimize.service.db.schema.index.IndexMappingCreatorBuilder.PROCESS_INSTANCE_INDEX;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.BUSINESS_KEY;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.DATA_SOURCE;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.PROCESS_DEFINITION_ID;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.PROCESS_DEFINITION_KEY;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.PROCESS_DEFINITION_VERSION;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.START_DATE;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.STATE;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.TENANT_ID;
import static java.util.stream.Collectors.toSet;

import io.camunda.optimize.dto.optimize.ImportRequestDto;
import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.service.db.helper.ImportRequestDtoFactory;
import io.camunda.optimize.service.db.repository.IndexRepository;
import io.camunda.optimize.service.db.repository.ProcessInstanceRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RunningProcessInstanceWriter {
  Set<String> UPDATABLE_FIELDS =
      Set.of(
          PROCESS_DEFINITION_KEY,
          PROCESS_DEFINITION_VERSION,
          PROCESS_DEFINITION_ID,
          BUSINESS_KEY,
          START_DATE,
          STATE,
          DATA_SOURCE,
          TENANT_ID);
  String IMPORT_ITEM_NAME = "running process instances";
  private static final Set<String> READ_ONLY_ALIASES = Set.of(PROCESS_INSTANCE_MULTI_ALIAS);
  private final ProcessInstanceRepository processInstanceRepository;
  private final IndexRepository indexRepository;
  private final ImportRequestDtoFactory importRequestDtoFactory;

  public List<ImportRequestDto> generateProcessInstanceImports(
      final List<ProcessInstanceDto> processInstanceDtos) {
    log.debug("Creating imports for {} [{}].", processInstanceDtos.size(), IMPORT_ITEM_NAME);
    createMissingIndices(processInstanceDtos);

    return processInstanceDtos.stream()
        .map(
            instance ->
                importRequestDtoFactory.createImportRequestForProcessInstance(
                    instance, UPDATABLE_FIELDS, IMPORT_ITEM_NAME))
        .toList();
  }

  public void importProcessInstancesFromUserOperationLogs(
      final List<ProcessInstanceDto> processInstanceDtos) {
    log.debug(
        "Writing changes from user operation logs to [{}] {} to OS.",
        processInstanceDtos.size(),
        IMPORT_ITEM_NAME);

    final List<ProcessInstanceDto> processInstanceDtoToUpdateList =
        processInstanceDtos.stream()
            .filter(procInst -> procInst.getProcessInstanceId() != null)
            .toList();

    createMissingIndices(processInstanceDtos);

    processInstanceRepository.bulkImportProcessInstances(
        IMPORT_ITEM_NAME, processInstanceDtoToUpdateList);
  }

  public void importProcessInstancesForProcessDefinitionIds(
      final Map<String, Map<String, String>> definitionKeyToIdToStateMap) {
    for (Map.Entry<String, Map<String, String>> definitionKeyEntry :
        definitionKeyToIdToStateMap.entrySet()) {
      final String definitionKey = definitionKeyEntry.getKey();
      for (Map.Entry<String, String> definitionStateEntry :
          definitionKeyEntry.getValue().entrySet()) {
        indexRepository.createMissingIndices(
            PROCESS_INSTANCE_INDEX, READ_ONLY_ALIASES, Set.of(definitionKey));
        processInstanceRepository.updateProcessInstanceStateForProcessDefinitionId(
            IMPORT_ITEM_NAME,
            definitionKey,
            definitionStateEntry.getKey(),
            definitionStateEntry.getValue());
      }
    }
  }

  public void importProcessInstancesForProcessDefinitionKeys(
      final Map<String, String> definitionKeyToNewStateMap) {
    indexRepository.createMissingIndices(
        PROCESS_INSTANCE_INDEX, READ_ONLY_ALIASES, definitionKeyToNewStateMap.keySet());

    for (Map.Entry<String, String> definitionStateEntry : definitionKeyToNewStateMap.entrySet()) {
      processInstanceRepository.updateAllProcessInstancesStates(
          IMPORT_ITEM_NAME, definitionStateEntry.getKey(), definitionStateEntry.getValue());
    }
  }

  private void createMissingIndices(final List<ProcessInstanceDto> processInstanceDtos) {
    indexRepository.createMissingIndices(
        PROCESS_INSTANCE_INDEX,
        READ_ONLY_ALIASES,
        processInstanceDtos.stream()
            .map(ProcessInstanceDto::getProcessDefinitionKey)
            .collect(toSet()));
  }
}
