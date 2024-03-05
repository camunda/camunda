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
package io.camunda.operate.webapp.rest.dto.listview;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.entities.OperationEntity;
import io.camunda.operate.entities.OperationState;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.entities.listview.ProcessInstanceState;
import io.camunda.operate.util.ConversionUtils;
import io.camunda.operate.util.TreePath;
import io.camunda.operate.webapp.rest.dto.DtoCreator;
import io.camunda.operate.webapp.rest.dto.OperationDto;
import io.camunda.operate.webapp.rest.dto.ProcessInstanceReferenceDto;
import io.camunda.operate.webapp.security.identity.PermissionsService;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class ListViewProcessInstanceDto {

  private String id;

  private String processId;
  private String processName;
  private Integer processVersion;

  private OffsetDateTime startDate;
  private OffsetDateTime endDate;

  private ProcessInstanceStateDto state;

  private String bpmnProcessId;

  private boolean hasActiveOperation = false;

  private List<OperationDto> operations = new ArrayList<>();

  private String parentInstanceId;

  private String rootInstanceId;

  private List<ProcessInstanceReferenceDto> callHierarchy = new ArrayList<>();

  private String tenantId;

  /**
   * Sort values, define the position of process instance in the list and may be used to search for
   * previous or following page.
   */
  private SortValuesWrapper[] sortValues;

  private Set<String> permissions;

  public static ListViewProcessInstanceDto createFrom(
      ProcessInstanceForListViewEntity processInstanceEntity,
      List<OperationEntity> operations,
      ObjectMapper objectMapper) {
    return createFrom(processInstanceEntity, operations, null, null, objectMapper);
  }

  public static ListViewProcessInstanceDto createFrom(
      ProcessInstanceForListViewEntity processInstanceEntity,
      List<OperationEntity> operations,
      List<ProcessInstanceReferenceDto> callHierarchy,
      ObjectMapper objectMapper) {
    return createFrom(processInstanceEntity, operations, callHierarchy, null, objectMapper);
  }

  public static ListViewProcessInstanceDto createFrom(
      ProcessInstanceForListViewEntity processInstanceEntity,
      List<OperationEntity> operations,
      List<ProcessInstanceReferenceDto> callHierarchy,
      PermissionsService permissionsService,
      ObjectMapper objectMapper) {
    if (processInstanceEntity == null) {
      return null;
    }
    ListViewProcessInstanceDto processInstance = new ListViewProcessInstanceDto();
    processInstance
        .setId(processInstanceEntity.getId())
        .setStartDate(processInstanceEntity.getStartDate())
        .setEndDate(processInstanceEntity.getEndDate());
    if (processInstanceEntity.getState() == ProcessInstanceState.ACTIVE
        && processInstanceEntity.isIncident()) {
      processInstance.setState(ProcessInstanceStateDto.INCIDENT);
    } else {
      processInstance.setState(ProcessInstanceStateDto.getState(processInstanceEntity.getState()));
    }

    processInstance
        .setProcessId(
            ConversionUtils.toStringOrNull(processInstanceEntity.getProcessDefinitionKey()))
        .setBpmnProcessId(processInstanceEntity.getBpmnProcessId())
        .setProcessName(processInstanceEntity.getProcessName())
        .setProcessVersion(processInstanceEntity.getProcessVersion())
        .setOperations(DtoCreator.create(operations, OperationDto.class))
        .setTenantId(processInstanceEntity.getTenantId());
    if (operations != null) {
      processInstance.setHasActiveOperation(
          operations.stream()
              .anyMatch(
                  o ->
                      o.getState().equals(OperationState.SCHEDULED)
                          || o.getState().equals(OperationState.LOCKED)
                          || o.getState().equals(OperationState.SENT)));
    }
    if (processInstanceEntity.getParentProcessInstanceKey() != null) {
      processInstance.setParentInstanceId(
          String.valueOf(processInstanceEntity.getParentProcessInstanceKey()));
    }
    // convert to String[]
    if (processInstanceEntity.getSortValues() != null) {
      processInstance.setSortValues(
          SortValuesWrapper.createFrom(processInstanceEntity.getSortValues(), objectMapper));
    }

    if (processInstanceEntity.getTreePath() != null) {
      final String rootInstanceId =
          new TreePath(processInstanceEntity.getTreePath()).extractRootInstanceId();
      if (!processInstanceEntity.getId().equals(rootInstanceId)) {
        processInstance.setRootInstanceId(rootInstanceId);
      }
    }
    processInstance.setCallHierarchy(callHierarchy);
    processInstance.setPermissions(
        permissionsService == null
            ? new HashSet<>()
            : permissionsService.getProcessDefinitionPermission(
                processInstanceEntity.getBpmnProcessId()));
    return processInstance;
  }

  public static List<ListViewProcessInstanceDto> createFrom(
      List<ProcessInstanceForListViewEntity> processInstanceEntities,
      Map<Long, List<OperationEntity>> operationsPerProcessInstance,
      ObjectMapper objectMapper) {
    if (processInstanceEntities == null) {
      return new ArrayList<>();
    }
    return processInstanceEntities.stream()
        .filter(item -> item != null)
        .map(
            item ->
                createFrom(
                    item,
                    operationsPerProcessInstance.get(item.getProcessInstanceKey()),
                    objectMapper))
        .collect(Collectors.toList());
  }

  public String getId() {
    return id;
  }

  public ListViewProcessInstanceDto setId(String id) {
    this.id = id;
    return this;
  }

  public String getProcessId() {
    return processId;
  }

  public ListViewProcessInstanceDto setProcessId(String processId) {
    this.processId = processId;
    return this;
  }

  public String getProcessName() {
    return processName;
  }

  public ListViewProcessInstanceDto setProcessName(String processName) {
    this.processName = processName;
    return this;
  }

  public Integer getProcessVersion() {
    return processVersion;
  }

  public ListViewProcessInstanceDto setProcessVersion(Integer processVersion) {
    this.processVersion = processVersion;
    return this;
  }

  public OffsetDateTime getStartDate() {
    return startDate;
  }

  public ListViewProcessInstanceDto setStartDate(OffsetDateTime startDate) {
    this.startDate = startDate;
    return this;
  }

  public OffsetDateTime getEndDate() {
    return endDate;
  }

  public ListViewProcessInstanceDto setEndDate(OffsetDateTime endDate) {
    this.endDate = endDate;
    return this;
  }

  public ProcessInstanceStateDto getState() {
    return state;
  }

  public ListViewProcessInstanceDto setState(ProcessInstanceStateDto state) {
    this.state = state;
    return this;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public ListViewProcessInstanceDto setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  public boolean isHasActiveOperation() {
    return hasActiveOperation;
  }

  public ListViewProcessInstanceDto setHasActiveOperation(boolean hasActiveOperation) {
    this.hasActiveOperation = hasActiveOperation;
    return this;
  }

  public List<OperationDto> getOperations() {
    return operations;
  }

  public ListViewProcessInstanceDto setOperations(List<OperationDto> operations) {
    this.operations = operations;
    return this;
  }

  public String getParentInstanceId() {
    return parentInstanceId;
  }

  public ListViewProcessInstanceDto setParentInstanceId(final String parentInstanceId) {
    this.parentInstanceId = parentInstanceId;
    return this;
  }

  public List<ProcessInstanceReferenceDto> getCallHierarchy() {
    return callHierarchy;
  }

  public ListViewProcessInstanceDto setCallHierarchy(
      final List<ProcessInstanceReferenceDto> callHierarchy) {
    this.callHierarchy = callHierarchy;
    return this;
  }

  public String getRootInstanceId() {
    return rootInstanceId;
  }

  public ListViewProcessInstanceDto setRootInstanceId(final String rootInstanceId) {
    this.rootInstanceId = rootInstanceId;
    return this;
  }

  public String getTenantId() {
    return tenantId;
  }

  public ListViewProcessInstanceDto setTenantId(String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public SortValuesWrapper[] getSortValues() {
    return sortValues;
  }

  public ListViewProcessInstanceDto setSortValues(SortValuesWrapper[] sortValues) {
    this.sortValues = sortValues;
    return this;
  }

  public Set<String> getPermissions() {
    return permissions;
  }

  public void setPermissions(Set<String> permissions) {
    this.permissions = permissions;
  }

  @Override
  public int hashCode() {
    int result =
        Objects.hash(
            id,
            processId,
            processName,
            processVersion,
            startDate,
            endDate,
            state,
            bpmnProcessId,
            hasActiveOperation,
            operations,
            parentInstanceId,
            rootInstanceId,
            callHierarchy,
            tenantId,
            permissions);
    result = 31 * result + Arrays.hashCode(sortValues);
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ListViewProcessInstanceDto that = (ListViewProcessInstanceDto) o;
    return hasActiveOperation == that.hasActiveOperation
        && Objects.equals(id, that.id)
        && Objects.equals(processId, that.processId)
        && Objects.equals(processName, that.processName)
        && Objects.equals(processVersion, that.processVersion)
        && Objects.equals(startDate, that.startDate)
        && Objects.equals(endDate, that.endDate)
        && state == that.state
        && Objects.equals(bpmnProcessId, that.bpmnProcessId)
        && Objects.equals(operations, that.operations)
        && Objects.equals(parentInstanceId, that.parentInstanceId)
        && Objects.equals(rootInstanceId, that.rootInstanceId)
        && Objects.equals(callHierarchy, that.callHierarchy)
        && Objects.equals(tenantId, that.tenantId)
        && Arrays.equals(sortValues, that.sortValues)
        && Objects.equals(permissions, that.permissions);
  }

  @Override
  public String toString() {
    return String.format("ListViewProcessInstanceDto %s (%s)", processName, processId);
  }
}
