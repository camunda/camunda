/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest;

import static io.camunda.operate.webapp.rest.ProcessInstanceRestService.PROCESS_INSTANCE_URL;

import io.camunda.operate.Metrics;
import io.camunda.operate.store.SequenceFlowStore;
import io.camunda.operate.util.rest.ValidLongId;
import io.camunda.operate.webapp.InternalAPIErrorController;
import io.camunda.operate.webapp.elasticsearch.reader.ProcessInstanceReader;
import io.camunda.operate.webapp.reader.FlowNodeInstanceReader;
import io.camunda.operate.webapp.reader.FlowNodeStatisticsReader;
import io.camunda.operate.webapp.reader.IncidentReader;
import io.camunda.operate.webapp.reader.ListViewReader;
import io.camunda.operate.webapp.reader.ListenerReader;
import io.camunda.operate.webapp.reader.VariableReader;
import io.camunda.operate.webapp.rest.dto.*;
import io.camunda.operate.webapp.rest.dto.activity.FlowNodeStateDto;
import io.camunda.operate.webapp.rest.dto.incidents.IncidentResponseDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewProcessInstanceDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewQueryDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewRequestDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewResponseDto;
import io.camunda.operate.webapp.rest.dto.metadata.FlowNodeMetadataDto;
import io.camunda.operate.webapp.rest.dto.metadata.FlowNodeMetadataRequestDto;
import io.camunda.operate.webapp.rest.dto.operation.CreateBatchOperationRequestDto;
import io.camunda.operate.webapp.rest.dto.operation.CreateOperationRequestDto;
import io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto;
import io.camunda.operate.webapp.rest.exception.InvalidRequestException;
import io.camunda.operate.webapp.rest.exception.NotAuthorizedException;
import io.camunda.operate.webapp.rest.validation.ModifyProcessInstanceRequestValidator;
import io.camunda.operate.webapp.rest.validation.ProcessInstanceRequestValidator;
import io.camunda.operate.webapp.security.permission.PermissionsService;
import io.camunda.operate.webapp.writer.BatchOperationWriter;
import io.camunda.webapps.schema.entities.SequenceFlowEntity;
import io.camunda.webapps.schema.entities.operation.BatchOperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.micrometer.core.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.ConstraintViolationException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = PROCESS_INSTANCE_URL)
@Validated
public class ProcessInstanceRestService extends InternalAPIErrorController {

  public static final String PROCESS_INSTANCE_URL = "/api/process-instances";

  private final PermissionsService permissionsService;
  private final ProcessInstanceRequestValidator processInstanceRequestValidator;
  private final ModifyProcessInstanceRequestValidator modifyProcessInstanceRequestValidator;
  private final BatchOperationWriter batchOperationWriter;
  private final ProcessInstanceReader processInstanceReader;
  private final ListenerReader listenerReader;
  private final ListViewReader listViewReader;
  private final IncidentReader incidentReader;
  private final VariableReader variableReader;
  private final FlowNodeInstanceReader flowNodeInstanceReader;
  private final FlowNodeStatisticsReader flowNodeStatisticsReader;
  private final SequenceFlowStore sequenceFlowStore;

  public ProcessInstanceRestService(
      final PermissionsService permissionsService,
      final ProcessInstanceRequestValidator processInstanceRequestValidator,
      final ModifyProcessInstanceRequestValidator modifyProcessInstanceRequestValidator,
      final BatchOperationWriter batchOperationWriter,
      final ProcessInstanceReader processInstanceReader,
      final ListenerReader listenerReader,
      final ListViewReader listViewReader,
      final IncidentReader incidentReader,
      final VariableReader variableReader,
      final FlowNodeInstanceReader flowNodeInstanceReader,
      final FlowNodeStatisticsReader flowNodeStatisticsReader,
      final SequenceFlowStore sequenceFlowStore) {
    this.permissionsService = permissionsService;
    this.processInstanceRequestValidator = processInstanceRequestValidator;
    this.modifyProcessInstanceRequestValidator = modifyProcessInstanceRequestValidator;
    this.batchOperationWriter = batchOperationWriter;
    this.processInstanceReader = processInstanceReader;
    this.listenerReader = listenerReader;
    this.listViewReader = listViewReader;
    this.incidentReader = incidentReader;
    this.variableReader = variableReader;
    this.flowNodeInstanceReader = flowNodeInstanceReader;
    this.flowNodeStatisticsReader = flowNodeStatisticsReader;
    this.sequenceFlowStore = sequenceFlowStore;
  }

  @Operation(summary = "Query process instances by different parameters")
  @PostMapping
  @Timed(
      value = Metrics.TIMER_NAME_QUERY,
      extraTags = {Metrics.TAG_KEY_NAME, Metrics.TAG_VALUE_PROCESSINSTANCES},
      description = "How long does it take to retrieve the processinstances by query.")
  public ListViewResponseDto queryProcessInstances(
      @RequestBody final ListViewRequestDto processInstanceRequest) {
    if (processInstanceRequest.getQuery() == null) {
      throw new InvalidRequestException("Query must be provided.");
    }
    if (processInstanceRequest.getQuery().getProcessVersion() != null
        && processInstanceRequest.getQuery().getBpmnProcessId() == null) {
      throw new InvalidRequestException(
          "BpmnProcessId must be provided in request, when process version is not null.");
    }
    return listViewReader.queryProcessInstances(processInstanceRequest);
  }

  @Operation(summary = "Perform single operation on an instance (async)")
  @PostMapping("/{id}/operation")
  public BatchOperationEntity operation(
      @PathVariable @ValidLongId final String id,
      @RequestBody final CreateOperationRequestDto operationRequest) {
    processInstanceRequestValidator.validateCreateOperationRequest(operationRequest, id);
    if (operationRequest.getOperationType() == OperationType.DELETE_PROCESS_INSTANCE) {
      checkIdentityPermission(Long.valueOf(id), PermissionType.DELETE_PROCESS_INSTANCE);
    } else {
      checkIdentityPermission(Long.valueOf(id), PermissionType.UPDATE_PROCESS_INSTANCE);
    }
    return batchOperationWriter.scheduleSingleOperation(Long.parseLong(id), operationRequest);
  }

  @Operation(summary = "Perform modify process instance operation")
  @PostMapping("/{id}/modify")
  public BatchOperationEntity modify(
      @PathVariable @ValidLongId final String id,
      @RequestBody final ModifyProcessInstanceRequestDto modifyRequest) {
    modifyRequest.setProcessInstanceKey(id);
    modifyProcessInstanceRequestValidator.validate(modifyRequest);
    checkIdentityPermission(Long.valueOf(id), PermissionType.UPDATE_PROCESS_INSTANCE);
    return batchOperationWriter.scheduleModifyProcessInstance(modifyRequest);
  }

  @Operation(summary = "Create batch operation based on filter")
  @PostMapping("/batch-operation")
  public BatchOperationEntity createBatchOperation(
      @RequestBody final CreateBatchOperationRequestDto batchOperationRequest) {
    // TODO: Check permissions https://github.com/camunda/camunda/issues/32409
    processInstanceRequestValidator.validateCreateBatchOperationRequest(batchOperationRequest);
    return batchOperationWriter.scheduleBatchOperation(batchOperationRequest);
  }

  @Operation(summary = "Get process instance by id")
  @GetMapping("/{id}")
  public ListViewProcessInstanceDto queryProcessInstanceById(
      @PathVariable @ValidLongId final String id) {
    checkIdentityReadPermission(Long.parseLong(id));
    return processInstanceReader.getProcessInstanceWithOperationsByKey(Long.valueOf(id));
  }

  @Operation(summary = "Get incidents by process instance id")
  @GetMapping("/{id}/incidents")
  public IncidentResponseDto queryIncidentsByProcessInstanceId(
      @PathVariable @ValidLongId final String id) {
    checkIdentityReadPermission(Long.parseLong(id));
    return incidentReader.getIncidentsByProcessInstanceId(id);
  }

  @Operation(summary = "Get sequence flows by process instance id")
  @GetMapping("/{id}/sequence-flows")
  public List<SequenceFlowDto> querySequenceFlowsByProcessInstanceId(
      @PathVariable @ValidLongId final String id) {
    checkIdentityReadPermission(Long.parseLong(id));
    final List<SequenceFlowEntity> sequenceFlows =
        sequenceFlowStore.getSequenceFlowsByProcessInstanceKey(Long.valueOf(id));
    return DtoCreator.create(sequenceFlows, SequenceFlowDto.class);
  }

  @Operation(summary = "Get variables by process instance id and scope id")
  @PostMapping("/{processInstanceId}/variables")
  public List<VariableDto> getVariables(
      @PathVariable @ValidLongId final String processInstanceId,
      @RequestBody final VariableRequestDto variableRequest) {
    checkIdentityReadPermission(Long.parseLong(processInstanceId));
    processInstanceRequestValidator.validateVariableRequest(variableRequest);
    return variableReader.getVariables(processInstanceId, variableRequest);
  }

  @Operation(summary = "Get full variable by id")
  @GetMapping("/{processInstanceId}/variables/{variableId}")
  public VariableDto getVariable(
      @PathVariable @ValidLongId final String processInstanceId,
      @PathVariable final String variableId) {
    checkIdentityReadPermission(Long.parseLong(processInstanceId));
    return variableReader.getVariable(variableId);
  }

  @Operation(summary = "Get listeners by process instance id")
  @PostMapping("/{processInstanceId}/listeners")
  public ListenerResponseDto getListeners(
      @PathVariable @ValidLongId final String processInstanceId,
      @RequestBody final ListenerRequestDto request) {
    checkIdentityReadPermission(Long.parseLong(processInstanceId));
    processInstanceRequestValidator.validateListenerRequest(request);
    return listenerReader.getListenerExecutions(processInstanceId, request);
  }

  @Operation(summary = "Get flow node states by process instance id")
  @GetMapping("/{processInstanceId}/flow-node-states")
  public Map<String, FlowNodeStateDto> getFlowNodeStates(
      @PathVariable @ValidLongId final String processInstanceId) {
    checkIdentityReadPermission(Long.parseLong(processInstanceId));
    return flowNodeInstanceReader.getFlowNodeStates(processInstanceId);
  }

  @Operation(summary = "Get flow node statistic by process instance id")
  @GetMapping("/{processInstanceId}/statistics")
  public Collection<FlowNodeStatisticsDto> getStatistics(
      @PathVariable @ValidLongId final String processInstanceId) {
    checkIdentityReadPermission(Long.parseLong(processInstanceId));
    return flowNodeInstanceReader.getFlowNodeStatisticsForProcessInstance(
        Long.parseLong(processInstanceId));
  }

  @Operation(summary = "Get flow node metadata.")
  @PostMapping("/{processInstanceId}/flow-node-metadata")
  public FlowNodeMetadataDto getFlowNodeMetadata(
      @PathVariable @ValidLongId final String processInstanceId,
      @RequestBody final FlowNodeMetadataRequestDto request) {
    checkIdentityReadPermission(Long.parseLong(processInstanceId));
    processInstanceRequestValidator.validateFlowNodeMetadataRequest(request);
    return flowNodeInstanceReader.getFlowNodeMetadata(processInstanceId, request);
  }

  @Operation(summary = "Get activity instance statistics")
  @PostMapping(path = "/statistics")
  public Collection<FlowNodeStatisticsDto> getStatistics(
      @RequestBody final ListViewQueryDto query) {
    processInstanceRequestValidator.validateFlowNodeStatisticsRequest(query);
    return flowNodeStatisticsReader.getFlowNodeStatistics(query);
  }

  @Operation(summary = "Get process instance core statistics (aggregations)")
  @GetMapping(path = "/core-statistics")
  @Timed(
      value = Metrics.TIMER_NAME_QUERY,
      extraTags = {Metrics.TAG_KEY_NAME, Metrics.TAG_VALUE_CORESTATISTICS},
      description = "How long does it take to retrieve the core statistics.")
  public ProcessInstanceCoreStatisticsDto getCoreStatistics() {
    return processInstanceReader.getCoreStatistics();
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
