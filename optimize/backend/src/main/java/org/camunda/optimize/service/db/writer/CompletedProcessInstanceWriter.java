/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.writer;

import static java.util.stream.Collectors.toSet;
import static org.camunda.optimize.service.db.DatabaseConstants.PROCESS_INSTANCE_MULTI_ALIAS;
import static org.camunda.optimize.service.db.schema.index.IndexMappingCreatorBuilder.PROCESS_INSTANCE_INDEX;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.BUSINESS_KEY;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.DATA_SOURCE;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.DURATION;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.END_DATE;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.PROCESS_DEFINITION_ID;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.PROCESS_DEFINITION_KEY;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.PROCESS_DEFINITION_VERSION;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.START_DATE;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.STATE;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.TENANT_ID;
import static org.camunda.optimize.service.util.InstanceIndexUtil.getProcessInstanceIndexAliasName;

import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.service.db.helper.ImportRequestDtoFactory;
import org.camunda.optimize.service.db.repository.IndexRepository;
import org.camunda.optimize.service.db.repository.ProcessInstanceRepository;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@AllArgsConstructor
public class CompletedProcessInstanceWriter {
  private static final Set<String> READ_ONLY_ALIASES = Set.of(PROCESS_INSTANCE_MULTI_ALIAS);
  private static final Set<String> UPDATABLE_FIELDS =
      Set.of(
          PROCESS_DEFINITION_KEY,
          PROCESS_DEFINITION_VERSION,
          PROCESS_DEFINITION_ID,
          BUSINESS_KEY,
          START_DATE,
          END_DATE,
          DURATION,
          STATE,
          DATA_SOURCE,
          TENANT_ID);
  private final IndexRepository indexRepository;
  private final ProcessInstanceRepository processInstanceRepository;
  private final ImportRequestDtoFactory importRequestDtoFactory;

  public List<ImportRequestDto> generateProcessInstanceImports(
      final List<ProcessInstanceDto> processInstances) {
    final String importItemName = "completed process instances";
    log.debug("Creating imports for {} [{}].", processInstances.size(), importItemName);
    indexRepository.createMissingIndices(
        PROCESS_INSTANCE_INDEX,
        READ_ONLY_ALIASES,
        processInstances.stream()
            .map(ProcessInstanceDto::getProcessDefinitionKey)
            .collect(toSet()));
    return processInstances.stream()
        .map(
            processInstanceDto ->
                createImportRequestForProcessInstance(processInstanceDto, importItemName))
        .toList();
  }

  public void deleteByIds(final String definitionKey, final List<String> processInstanceIds) {
    log.debug(
        "Deleting [{}] process instance documents with bulk request.", processInstanceIds.size());
    final String index = getProcessInstanceIndexAliasName(definitionKey);
    processInstanceRepository.deleteByIds(index, index, processInstanceIds);
  }

  private ImportRequestDto createImportRequestForProcessInstance(
      final ProcessInstanceDto processInstanceDto, final String importItemName) {
    if (processInstanceDto.getEndDate() == null) {
      log.warn("End date should not be null for completed process instances!");
    }
    return importRequestDtoFactory.createImportRequestForProcessInstance(
        processInstanceDto, UPDATABLE_FIELDS, importItemName);
  }
}
