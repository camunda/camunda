/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.importing.UserOperationLogEntryDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.elasticsearch.script.Script;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

import static java.util.stream.Collectors.toList;
import static org.camunda.optimize.dto.optimize.ProcessInstanceConstants.ACTIVE_STATE;
import static org.camunda.optimize.dto.optimize.ProcessInstanceConstants.SUSPENDED_STATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.BUSINESS_KEY;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.ENGINE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.PROCESS_DEFINITION_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.PROCESS_DEFINITION_KEY;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.PROCESS_DEFINITION_VERSION;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.START_DATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.STATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.TENANT_ID;
import static org.camunda.optimize.service.es.writer.ElasticsearchWriterUtil.createDefaultScript;

@Component
@Slf4j
public class RunningProcessInstanceWriter extends AbstractProcessInstanceWriter {
  private static final Set<String> PRIMITIVE_UPDATABLE_FIELDS = ImmutableSet.of(
    PROCESS_DEFINITION_KEY, PROCESS_DEFINITION_VERSION, PROCESS_DEFINITION_ID,
    BUSINESS_KEY, START_DATE, STATE,
    ENGINE, TENANT_ID
  );

  private final OptimizeElasticsearchClient esClient;

  public RunningProcessInstanceWriter(final OptimizeElasticsearchClient esClient,
                                      final ObjectMapper objectMapper) {
    super(objectMapper);
    this.esClient = esClient;
  }

  public void importProcessInstances(List<ProcessInstanceDto> processInstanceDtos) {
    String importItemName = "running process instances";
    log.debug("Writing [{}] {} to ES.", processInstanceDtos.size(), importItemName);

    ElasticsearchWriterUtil.doBulkRequestWithList(
      esClient,
      importItemName,
      processInstanceDtos,
      (request, dto) -> addImportProcessInstanceRequest(
        request,
        dto,
        PRIMITIVE_UPDATABLE_FIELDS,
        objectMapper
      )
    );
  }

  public void importProcessInstancesFromUserOperationLogs(List<UserOperationLogEntryDto> userOperationLogEntryDtos) {
    final String importItemName = "running process instances";
    log.debug(
      "Writing changes from [{}] user operation logs to {} to ES.",
      userOperationLogEntryDtos.size(),
      importItemName
    );

    final List<ProcessInstanceDto> processInstanceDtos =
      mapUserOperationsLogsToProcessInstanceDtos(userOperationLogEntryDtos);

    if (processInstanceDtos.isEmpty()) {
      log.debug("No {} to udpate", importItemName);
      return;
    }

    ElasticsearchWriterUtil.doBulkRequestWithList(
      esClient,
      importItemName,
      processInstanceDtos,
      (request, dto) -> addImportProcessInstanceRequest(
        request,
        dto,
        createUpdateStatusScript(dto),
        objectMapper
      )
    );
  }

  private List<ProcessInstanceDto> mapUserOperationsLogsToProcessInstanceDtos(
    final List<UserOperationLogEntryDto> userOperationLogEntryDtos) {
    return userOperationLogEntryDtos.stream()
      .filter(this::isProcessInstanceStatusChangeOperation)
      .map(userOpLog -> ProcessInstanceDto.builder()
        .processInstanceId(userOpLog.getProcessInstanceId())
        .state(userOpLog.getNewValue())
        .build()
      )
      .collect(toList());
  }

  private boolean isProcessInstanceStatusChangeOperation(final UserOperationLogEntryDto userOperationLogEntryDto) {
    return userOperationLogEntryDto.getProcessInstanceId() != null
      && userOperationLogEntryDto.getProperty().equalsIgnoreCase(ProcessInstanceDto.Fields.state);
  }

  private Script createUpdateStatusScript(final ProcessInstanceDto processInstanceDto) {
    final ImmutableMap<String, Object> scriptParameters = createScriptParamsMap(processInstanceDto);
    return createDefaultScript(createInlineUpdateScript(), scriptParameters);
  }

  private String createInlineUpdateScript() {
    // @formatter:off
    return
      "if ((ctx._source.state == params.activeState || ctx_.source.state == params.suspendedState)" +
        "&& (params.newState.equals(params.activeState) || params.newState.equals(params.suspendedState)) {" +
        "ctx_.source.state = params.newState;" +
        "}\n"
      ;
    // @formatter:on
  }

  private ImmutableMap<String, Object> createScriptParamsMap(final ProcessInstanceDto processInstanceDto) {
    return ImmutableMap.of(
      "activeState", ACTIVE_STATE,
      "suspendedState", SUSPENDED_STATE,
      "newState", processInstanceDto.getState()
    );
  }
}