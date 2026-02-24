/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest;

import static io.camunda.operate.webapp.rest.ProcessInstanceRestService.PROCESS_INSTANCE_URL;

import io.camunda.operate.util.rest.ValidLongId;
import io.camunda.operate.webapp.InternalAPIErrorController;
import io.camunda.operate.webapp.elasticsearch.reader.ProcessInstanceReader;
import io.camunda.operate.webapp.reader.FlowNodeInstanceReader;
import io.camunda.operate.webapp.rest.dto.activity.FlowNodeStateDto;
import io.camunda.operate.webapp.rest.exception.NotAuthorizedException;
import io.camunda.operate.webapp.security.permission.PermissionsService;
import io.camunda.spring.utils.ConditionalOnRdbmsDisabled;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.ConstraintViolationException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = PROCESS_INSTANCE_URL)
@Validated
@ConditionalOnRdbmsDisabled
public class ProcessInstanceRestService extends InternalAPIErrorController {

  public static final String PROCESS_INSTANCE_URL = "/api/process-instances";

  private final PermissionsService permissionsService;
  private final ProcessInstanceReader processInstanceReader;
  private final FlowNodeInstanceReader flowNodeInstanceReader;

  public ProcessInstanceRestService(
      final PermissionsService permissionsService,
      final ProcessInstanceReader processInstanceReader,
      final FlowNodeInstanceReader flowNodeInstanceReader) {
    this.permissionsService = permissionsService;
    this.processInstanceReader = processInstanceReader;
    this.flowNodeInstanceReader = flowNodeInstanceReader;
  }

  @Operation(summary = "Get flow node states by process instance id")
  @GetMapping("/{processInstanceId}/flow-node-states")
  public Map<String, FlowNodeStateDto> getFlowNodeStates(
      @PathVariable @ValidLongId final String processInstanceId) {
    checkIdentityReadPermission(Long.parseLong(processInstanceId));
    return flowNodeInstanceReader.getFlowNodeStates(processInstanceId);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<String> handleConstraintViolation(
      final ConstraintViolationException exception) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(exception.getMessage());
  }

  private void checkIdentityReadPermission(final Long processInstanceKey) {
    checkIdentityPermission(processInstanceKey, PermissionType.READ_PROCESS_INSTANCE);
  }

  private void checkIdentityPermission(
      final Long processInstanceKey, final PermissionType permission) {
    if (!permissionsService.hasPermissionForProcess(
        processInstanceReader.getProcessInstanceByKey(processInstanceKey).getBpmnProcessId(),
        permission)) {
      throw new NotAuthorizedException(
          String.format(
              "No %s permission for process instance %s", permission, processInstanceKey));
    }
  }
}
