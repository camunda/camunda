/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.writer;

import static org.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.camunda.optimize.service.db.DatabaseConstants.OPTIMIZE_DATE_FORMAT;
import static org.camunda.optimize.service.db.DatabaseConstants.PROCESS_INSTANCE_MULTI_ALIAS;
import static org.camunda.optimize.service.db.repository.script.ZeebeProcessInstanceScriptFactory.createProcessInstanceUpdateScript;
import static org.camunda.optimize.service.db.schema.index.IndexMappingCreatorBuilder.PROCESS_INSTANCE_INDEX;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;
import static org.camunda.optimize.service.util.InstanceIndexUtil.getProcessInstanceIndexAliasName;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.RequestType;
import org.camunda.optimize.service.db.repository.IndexRepository;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@AllArgsConstructor
public class ZeebeProcessInstanceWriter {
  private static final String NEW_INSTANCE = "instance";
  private static final String FORMATTER = "dateFormatPattern";
  private static final String SOURCE_EXPORT_INDEX = "sourceExportIndex";
  private final IndexRepository indexRepository;
  private final ObjectMapper objectMapper;

  public List<ImportRequestDto> generateProcessInstanceImports(
      final List<ProcessInstanceDto> processInstances, final String sourceExportIndex) {
    final String importItemName = "zeebe process instances";
    log.debug("Creating imports for {} [{}].", processInstances.size(), importItemName);
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
}
