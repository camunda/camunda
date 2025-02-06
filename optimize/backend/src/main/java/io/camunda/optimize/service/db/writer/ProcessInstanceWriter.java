/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.writer;

import static io.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static io.camunda.optimize.service.db.DatabaseConstants.OPTIMIZE_DATE_FORMAT;
import static io.camunda.optimize.service.db.DatabaseConstants.PROCESS_INSTANCE_MULTI_ALIAS;
import static io.camunda.optimize.service.db.repository.script.ZeebeProcessInstanceScriptFactory.createProcessInstanceUpdateScript;
import static io.camunda.optimize.service.db.schema.index.IndexMappingCreatorBuilder.PROCESS_INSTANCE_INDEX;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.BUSINESS_KEY;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.DATA_SOURCE;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.DURATION;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.END_DATE;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.PROCESS_DEFINITION_ID;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.PROCESS_DEFINITION_KEY;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.PROCESS_DEFINITION_VERSION;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.START_DATE;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.STATE;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.TENANT_ID;
import static io.camunda.optimize.service.util.InstanceIndexUtil.getProcessInstanceIndexAliasName;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.ImportRequestDto;
import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.dto.optimize.RequestType;
import io.camunda.optimize.service.db.helper.ImportRequestDtoFactory;
import io.camunda.optimize.service.db.repository.IndexRepository;
import io.camunda.optimize.service.db.repository.ProcessInstanceRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class ProcessInstanceWriter {

  private static final String NEW_INSTANCE = "instance";
  private static final String FORMATTER = "dateFormatPattern";
  private static final String SOURCE_EXPORT_INDEX = "sourceExportIndex";
  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(ProcessInstanceWriter.class);
  private final IndexRepository indexRepository;
  private final ObjectMapper objectMapper;
  private final ImportRequestDtoFactory importRequestDtoFactory;
  private final ProcessInstanceRepository processInstanceRepository;

  public ProcessInstanceWriter(
      final IndexRepository indexRepository,
      final @Qualifier("optimizeObjectMapper") ObjectMapper objectMapper,
      final ImportRequestDtoFactory importRequestDtoFactory,
      final ProcessInstanceRepository processInstanceRepository) {
    this.indexRepository = indexRepository;
    this.objectMapper = objectMapper;
    this.importRequestDtoFactory = importRequestDtoFactory;
    this.processInstanceRepository = processInstanceRepository;
  }

  public List<ImportRequestDto> generateProcessInstanceImports(
      final List<ProcessInstanceDto> processInstances, final String sourceExportIndex) {
    final String importItemName = "zeebe process instances";
    LOG.debug("Creating imports for {} [{}].", processInstances.size(), importItemName);
    indexRepository.createMissingIndices(
        PROCESS_INSTANCE_INDEX,
        Set.of(PROCESS_INSTANCE_MULTI_ALIAS),
        processInstances.stream()
            .map(ProcessInstanceDto::getProcessDefinitionKey)
            .collect(Collectors.toSet()));

    return processInstances.stream()
        .map(
            procInst -> {
              final Map<String, Object> params = new HashMap<>();
              params.put(NEW_INSTANCE, procInst);
              params.put(FORMATTER, OPTIMIZE_DATE_FORMAT);
              params.put(SOURCE_EXPORT_INDEX, sourceExportIndex);
              params.put(FLOW_NODE_INSTANCES, procInst.getFlowNodeInstances());
              return ImportRequestDto.builder()
                  .importName(importItemName)
                  .type(RequestType.UPDATE)
                  .id(procInst.getProcessInstanceId())
                  .indexName(getProcessInstanceIndexAliasName(procInst.getProcessDefinitionKey()))
                  .source(procInst)
                  .retryNumberOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT)
                  .scriptData(
                      DatabaseWriterUtil.createScriptData(
                          createProcessInstanceUpdateScript(), params, objectMapper))
                  .build();
            })
        .collect(Collectors.toList());
  }

  public void deleteByIds(final String definitionKey, final List<String> processInstanceIds) {
    LOG.debug(
        "Deleting [{}] process instance documents with bulk request.", processInstanceIds.size());
    final String index = getProcessInstanceIndexAliasName(definitionKey);
    processInstanceRepository.deleteByIds(index, index, processInstanceIds);
  }

  public List<ImportRequestDto> generateRunningProcessInstanceImports(
      final List<ProcessInstanceDto> processInstances) {
    final String importItemName = "running process instances";
    LOG.debug("Creating imports for {} [{}].", processInstances.size(), importItemName);
    indexRepository.createMissingIndices(
        PROCESS_INSTANCE_INDEX,
        Set.of(PROCESS_INSTANCE_MULTI_ALIAS),
        processInstances.stream()
            .map(ProcessInstanceDto::getProcessDefinitionKey)
            .collect(Collectors.toSet()));

    return processInstances.stream()
        .map(
            instance ->
                importRequestDtoFactory.createImportRequestForProcessInstance(
                    instance,
                    Set.of(
                        PROCESS_DEFINITION_KEY,
                        PROCESS_DEFINITION_VERSION,
                        PROCESS_DEFINITION_ID,
                        BUSINESS_KEY,
                        START_DATE,
                        STATE,
                        DATA_SOURCE,
                        TENANT_ID),
                    "running process instances"))
        .toList();
  }

  public List<ImportRequestDto> generateCompletedProcessInstanceImports(
      final List<ProcessInstanceDto> processInstances) {
    final String importItemName = "completed process instances";
    LOG.debug("Creating imports for {} [{}].", processInstances.size(), importItemName);
    indexRepository.createMissingIndices(
        PROCESS_INSTANCE_INDEX,
        Set.of(PROCESS_INSTANCE_MULTI_ALIAS),
        processInstances.stream()
            .map(ProcessInstanceDto::getProcessDefinitionKey)
            .collect(Collectors.toSet()));
    return processInstances.stream()
        .map(
            processInstanceDto ->
                importRequestDtoFactory.createImportRequestForProcessInstance(
                    processInstanceDto,
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
                        TENANT_ID),
                    importItemName))
        .toList();
  }
}
