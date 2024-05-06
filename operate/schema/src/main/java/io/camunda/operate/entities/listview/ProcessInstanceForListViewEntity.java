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
package io.camunda.operate.entities.listview;

import static io.camunda.operate.schema.indices.IndexDescriptor.DEFAULT_TENANT_ID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.operate.entities.OperateZeebeEntity;
import io.camunda.operate.schema.templates.ListViewTemplate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

public class ProcessInstanceForListViewEntity
    extends OperateZeebeEntity<ProcessInstanceForListViewEntity> {

  private Long processDefinitionKey;
  private String processName;
  private Integer processVersion;
  private String bpmnProcessId;

  private OffsetDateTime startDate;
  private OffsetDateTime endDate;

  private ProcessInstanceState state;

  private List<String> batchOperationIds;

  private Long parentProcessInstanceKey;

  private Long parentFlowNodeInstanceKey;

  private String treePath;

  private boolean incident;

  private String tenantId = DEFAULT_TENANT_ID;

  private ListViewJoinRelation joinRelation =
      new ListViewJoinRelation(ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION);

  private Long position;

  @JsonIgnore private Object[] sortValues;

  public Long getProcessInstanceKey() {
    return getKey();
  }

  public ProcessInstanceForListViewEntity setProcessInstanceKey(Long processInstanceKey) {
    this.setKey(processInstanceKey);
    return this;
  }

  public Long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public ProcessInstanceForListViewEntity setProcessDefinitionKey(Long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  public String getProcessName() {
    return processName;
  }

  public ProcessInstanceForListViewEntity setProcessName(String processName) {
    this.processName = processName;
    return this;
  }

  public Integer getProcessVersion() {
    return processVersion;
  }

  public ProcessInstanceForListViewEntity setProcessVersion(Integer processVersion) {
    this.processVersion = processVersion;
    return this;
  }

  public OffsetDateTime getStartDate() {
    return startDate;
  }

  public ProcessInstanceForListViewEntity setStartDate(OffsetDateTime startDate) {
    this.startDate = startDate;
    return this;
  }

  public OffsetDateTime getEndDate() {
    return endDate;
  }

  public ProcessInstanceForListViewEntity setEndDate(OffsetDateTime endDate) {
    this.endDate = endDate;
    return this;
  }

  public ProcessInstanceState getState() {
    return state;
  }

  public ProcessInstanceForListViewEntity setState(ProcessInstanceState state) {
    this.state = state;
    return this;
  }

  public List<String> getBatchOperationIds() {
    return batchOperationIds;
  }

  public ProcessInstanceForListViewEntity setBatchOperationIds(List<String> batchOperationIds) {
    this.batchOperationIds = batchOperationIds;
    return this;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public ProcessInstanceForListViewEntity setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  public Long getParentProcessInstanceKey() {
    return parentProcessInstanceKey;
  }

  public ProcessInstanceForListViewEntity setParentProcessInstanceKey(
      Long parentProcessInstanceKey) {
    this.parentProcessInstanceKey = parentProcessInstanceKey;
    return this;
  }

  public Long getParentFlowNodeInstanceKey() {
    return parentFlowNodeInstanceKey;
  }

  public ProcessInstanceForListViewEntity setParentFlowNodeInstanceKey(
      Long parentFlowNodeInstanceKey) {
    this.parentFlowNodeInstanceKey = parentFlowNodeInstanceKey;
    return this;
  }

  public String getTreePath() {
    return treePath;
  }

  public ProcessInstanceForListViewEntity setTreePath(String treePath) {
    this.treePath = treePath;
    return this;
  }

  public boolean isIncident() {
    return incident;
  }

  public ProcessInstanceForListViewEntity setIncident(boolean incident) {
    this.incident = incident;
    return this;
  }

  public String getTenantId() {
    return tenantId;
  }

  public ProcessInstanceForListViewEntity setTenantId(String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public ListViewJoinRelation getJoinRelation() {
    return joinRelation;
  }

  public ProcessInstanceForListViewEntity setJoinRelation(ListViewJoinRelation joinRelation) {
    this.joinRelation = joinRelation;
    return this;
  }

  public Object[] getSortValues() {
    return sortValues;
  }

  public ProcessInstanceForListViewEntity setSortValues(Object[] sortValues) {
    this.sortValues = sortValues;
    return this;
  }

  public Long getPosition() {
    return position;
  }

  public ProcessInstanceForListViewEntity setPosition(final Long position) {
    this.position = position;
    return this;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final ProcessInstanceForListViewEntity that = (ProcessInstanceForListViewEntity) o;
    return incident == that.incident
        && Objects.equals(processDefinitionKey, that.processDefinitionKey)
        && Objects.equals(processName, that.processName)
        && Objects.equals(processVersion, that.processVersion)
        && Objects.equals(bpmnProcessId, that.bpmnProcessId)
        && Objects.equals(startDate, that.startDate)
        && Objects.equals(endDate, that.endDate)
        && state == that.state
        && Objects.equals(batchOperationIds, that.batchOperationIds)
        && Objects.equals(parentProcessInstanceKey, that.parentProcessInstanceKey)
        && Objects.equals(parentFlowNodeInstanceKey, that.parentFlowNodeInstanceKey)
        && Objects.equals(treePath, that.treePath)
        && Objects.equals(tenantId, that.tenantId)
        && Objects.equals(joinRelation, that.joinRelation)
        && Objects.equals(position, that.position);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(),
        processDefinitionKey,
        processName,
        processVersion,
        bpmnProcessId,
        startDate,
        endDate,
        state,
        batchOperationIds,
        parentProcessInstanceKey,
        parentFlowNodeInstanceKey,
        treePath,
        incident,
        tenantId,
        joinRelation,
        position);
  }
}
