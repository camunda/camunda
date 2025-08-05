/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest;

import static io.camunda.operate.webapp.rest.ProcessRestService.PROCESS_URL;

import io.camunda.operate.util.rest.ValidLongId;
import io.camunda.operate.webapp.InternalAPIErrorController;
import io.camunda.operate.webapp.elasticsearch.reader.ProcessInstanceReader;
import io.camunda.operate.webapp.reader.ProcessReader;
import io.camunda.operate.webapp.rest.dto.DtoCreator;
import io.camunda.operate.webapp.rest.dto.ProcessDto;
import io.camunda.operate.webapp.rest.dto.ProcessGroupDto;
import io.camunda.operate.webapp.rest.dto.ProcessRequestDto;
import io.camunda.operate.webapp.rest.exception.NotAuthorizedException;
import io.camunda.operate.webapp.security.permission.PermissionsService;
import io.camunda.operate.webapp.writer.BatchOperationWriter;
import io.camunda.webapps.schema.entities.ProcessEntity;
import io.camunda.webapps.schema.entities.operation.BatchOperationEntity;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Processes")
@RestController
@RequestMapping(value = PROCESS_URL)
public class ProcessRestService extends InternalAPIErrorController {

  public static final String PROCESS_URL = "/api/processes";

  @Autowired private ProcessReader processReader;
  @Autowired private ProcessInstanceReader processInstanceReader;
  @Autowired private PermissionsService permissionsService;
  @Autowired private BatchOperationWriter batchOperationWriter;

  @Operation(summary = "Get process BPMN XML")
  @GetMapping(path = "/{id}/xml")
  public String getProcessDiagram(@PathVariable("id") final String processId) {
    final Long processDefinitionKey = Long.valueOf(processId);
    final ProcessEntity processEntity = processReader.getProcess(processDefinitionKey);
    checkReadPermission(processEntity.getBpmnProcessId(), PermissionType.READ_PROCESS_DEFINITION);
    return processReader.getDiagram(processDefinitionKey);
  }

  @Operation(summary = "Get process by id")
  @GetMapping(path = "/{id}")
  public ProcessDto getProcess(@PathVariable("id") final String processId) {
    final ProcessEntity processEntity = processReader.getProcess(Long.valueOf(processId));
    checkReadPermission(processEntity.getBpmnProcessId(), PermissionType.READ_PROCESS_INSTANCE);
    return DtoCreator.create(processEntity, ProcessDto.class);
  }

  @Operation(summary = "List processes grouped by bpmnProcessId")
  @GetMapping(path = "/grouped")
  @Deprecated
  public List<ProcessGroupDto> getProcessesGrouped() {
    final var processesGrouped = processReader.getProcessesGrouped(new ProcessRequestDto());
    return ProcessGroupDto.createFrom(processesGrouped, permissionsService);
  }

  @Operation(summary = "List processes grouped by bpmnProcessId")
  @PostMapping(path = "/grouped")
  public List<ProcessGroupDto> getProcessesGrouped(@RequestBody final ProcessRequestDto request) {
    final var processesGrouped = processReader.getProcessesGrouped(request);
    return ProcessGroupDto.createFrom(processesGrouped, permissionsService);
  }

  @Operation(summary = "Delete process definition and dependant resources")
  @DeleteMapping(path = "/{id}")
  public BatchOperationEntity deleteProcessDefinition(
      @ValidLongId @PathVariable("id") final String processId) {
    final ProcessEntity processEntity = processReader.getProcess(Long.valueOf(processId));
    checkDeletePermission(processEntity.getKey());
    return batchOperationWriter.scheduleDeleteProcessDefinition(processEntity);
  }

  private void checkReadPermission(
      final String bpmnProcessId, final PermissionType permissionType) {
    if (!permissionsService.hasPermissionForProcess(bpmnProcessId, permissionType)) {
      throw new NotAuthorizedException(
          String.format("No read permission for process %s", bpmnProcessId));
    }
  }

  private void checkDeletePermission(final Long processDefinitionKey) {
    if (!permissionsService.hasPermissionForResource(
        processDefinitionKey, PermissionType.DELETE_PROCESS)) {
      throw new NotAuthorizedException(
          String.format("No delete permission for process definition %s", processDefinitionKey));
    }
  }
}
