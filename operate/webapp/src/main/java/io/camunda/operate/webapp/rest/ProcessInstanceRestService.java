/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.webapp.rest;

import static io.camunda.operate.webapp.rest.ProcessInstanceRestService.PROCESS_INSTANCE_URL;

import io.camunda.operate.Metrics;
import io.camunda.operate.entities.BatchOperationEntity;
import io.camunda.operate.entities.OperationType;
import io.camunda.operate.entities.SequenceFlowEntity;
import io.camunda.operate.store.SequenceFlowStore;
import io.camunda.operate.util.CollectionUtil;
import io.camunda.operate.util.rest.ValidLongId;
import io.camunda.operate.webapp.InternalAPIErrorController;
import io.camunda.operate.webapp.elasticsearch.reader.ProcessInstanceReader;
import io.camunda.operate.webapp.reader.FlowNodeInstanceReader;
import io.camunda.operate.webapp.reader.FlowNodeStatisticsReader;
import io.camunda.operate.webapp.reader.IncidentReader;
import io.camunda.operate.webapp.reader.ListViewReader;
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
import io.camunda.operate.webapp.security.identity.IdentityPermission;
import io.camunda.operate.webapp.security.identity.PermissionsService;
import io.camunda.operate.webapp.writer.BatchOperationWriter;
import io.micrometer.core.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.ConstraintViolationException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = PROCESS_INSTANCE_URL)
@Validated
public class ProcessInstanceRestService extends InternalAPIErrorController {

  public static final String PROCESS_INSTANCE_URL = "/api/process-instances";

  protected final PermissionsService permissionsService;
  private final ProcessInstanceRequestValidator processInstanceRequestValidator;
  private final ModifyProcessInstanceRequestValidator modifyProcessInstanceRequestValidator;
  private final BatchOperationWriter batchOperationWriter;
  private final ProcessInstanceReader processInstanceReader;
  private final ListViewReader listViewReader;
  private final IncidentReader incidentReader;
  private final VariableReader variableReader;
  private final FlowNodeInstanceReader flowNodeInstanceReader;
  private final FlowNodeStatisticsReader flowNodeStatisticsReader;
  private final SequenceFlowStore sequenceFlowStore;

  public ProcessInstanceRestService(
      @Nullable final PermissionsService permissionsService,
      final ProcessInstanceRequestValidator processInstanceRequestValidator,
      final ModifyProcessInstanceRequestValidator modifyProcessInstanceRequestValidator,
      final BatchOperationWriter batchOperationWriter,
      final ProcessInstanceReader processInstanceReader,
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
  @PreAuthorize("hasPermission('write')")
  public BatchOperationEntity operation(
      @PathVariable @ValidLongId final String id,
      @RequestBody final CreateOperationRequestDto operationRequest) {
    processInstanceRequestValidator.validateCreateOperationRequest(operationRequest, id);
    if (operationRequest.getOperationType() == OperationType.DELETE_PROCESS_INSTANCE) {
      checkIdentityPermission(Long.valueOf(id), IdentityPermission.DELETE_PROCESS_INSTANCE);
    } else {
      checkIdentityPermission(Long.valueOf(id), IdentityPermission.UPDATE_PROCESS_INSTANCE);
    }
    return batchOperationWriter.scheduleSingleOperation(Long.parseLong(id), operationRequest);
  }

  @Operation(summary = "Perform modify process instance operation")
  @PostMapping("/{id}/modify")
  @PreAuthorize("hasPermission('write')")
  public BatchOperationEntity modify(
      @PathVariable @ValidLongId final String id,
      @RequestBody final ModifyProcessInstanceRequestDto modifyRequest) {
    modifyRequest.setProcessInstanceKey(id);
    modifyProcessInstanceRequestValidator.validate(modifyRequest);
    checkIdentityPermission(Long.valueOf(id), IdentityPermission.UPDATE_PROCESS_INSTANCE);
    return batchOperationWriter.scheduleModifyProcessInstance(modifyRequest);
  }

  @Operation(summary = "Create batch operation based on filter")
  @PostMapping("/batch-operation")
  @PreAuthorize("hasPermission('write')")
  public BatchOperationEntity createBatchOperation(
      @RequestBody final CreateBatchOperationRequestDto batchOperationRequest) {
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
    final List<Long> processDefinitionKeys =
        CollectionUtil.toSafeListOfLongs(query.getProcessIds());
    final String bpmnProcessId = query.getBpmnProcessId();
    final Integer processVersion = query.getProcessVersion();

    if ((processDefinitionKeys != null && processDefinitionKeys.size() == 1)
        == (bpmnProcessId != null && processVersion != null)) {
      throw new InvalidRequestException(
          "Exactly one process must be specified in the request (via processIds or bpmnProcessId/version).");
    }
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
    checkIdentityPermission(processInstanceKey, IdentityPermission.READ);
  }

  private void checkIdentityPermission(
      final Long processInstanceKey, final IdentityPermission permission) {
    if (permissionsService != null
        && !permissionsService.hasPermissionForProcess(
            processInstanceReader.getProcessInstanceByKey(processInstanceKey).getBpmnProcessId(),
            permission)) {
      throw new NotAuthorizedException(
          String.format(
              "No %s permission for process instance %s", permission, processInstanceKey));
    }
  }
}
