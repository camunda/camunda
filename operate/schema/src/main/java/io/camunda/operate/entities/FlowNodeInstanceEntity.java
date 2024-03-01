/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
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
package io.camunda.operate.entities;

import static io.camunda.operate.schema.indices.IndexDescriptor.DEFAULT_TENANT_ID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Objects;

public class FlowNodeInstanceEntity extends OperateZeebeEntity<FlowNodeInstanceEntity> {

  private String flowNodeId;
  private OffsetDateTime startDate;
  private OffsetDateTime endDate;
  private FlowNodeState state;
  private FlowNodeType type;
  private Long incidentKey;
  private Long processInstanceKey;

  /** Attention! This field will be filled in only for data imported after v. 8.2.0. */
  private Long processDefinitionKey;

  /** Attention! This field will be filled in only for data imported after v. 8.2.0. */
  private String bpmnProcessId;

  private String treePath;
  private int level;
  private Long position;
  private boolean incident;
  private String tenantId = DEFAULT_TENANT_ID;
  ;
  @JsonIgnore private Object[] sortValues;

  public String getFlowNodeId() {
    return flowNodeId;
  }

  public FlowNodeInstanceEntity setFlowNodeId(String flowNodeId) {
    this.flowNodeId = flowNodeId;
    return this;
  }

  public OffsetDateTime getStartDate() {
    return startDate;
  }

  public FlowNodeInstanceEntity setStartDate(OffsetDateTime startDate) {
    this.startDate = startDate;
    return this;
  }

  public OffsetDateTime getEndDate() {
    return endDate;
  }

  public FlowNodeInstanceEntity setEndDate(OffsetDateTime endDate) {
    this.endDate = endDate;
    return this;
  }

  public FlowNodeState getState() {
    return state;
  }

  public FlowNodeInstanceEntity setState(FlowNodeState state) {
    this.state = state;
    return this;
  }

  public FlowNodeType getType() {
    return type;
  }

  public FlowNodeInstanceEntity setType(FlowNodeType type) {
    this.type = type;
    return this;
  }

  public Long getIncidentKey() {
    return incidentKey;
  }

  public FlowNodeInstanceEntity setIncidentKey(Long incidentKey) {
    this.incidentKey = incidentKey;
    return this;
  }

  public Long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public FlowNodeInstanceEntity setProcessInstanceKey(Long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  public Long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public FlowNodeInstanceEntity setProcessDefinitionKey(Long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public FlowNodeInstanceEntity setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  public String getTreePath() {
    return treePath;
  }

  public FlowNodeInstanceEntity setTreePath(String treePath) {
    this.treePath = treePath;
    return this;
  }

  public int getLevel() {
    return level;
  }

  public FlowNodeInstanceEntity setLevel(int level) {
    this.level = level;
    return this;
  }

  public Long getPosition() {
    return position;
  }

  public FlowNodeInstanceEntity setPosition(Long position) {
    this.position = position;
    return this;
  }

  public Object[] getSortValues() {
    return sortValues;
  }

  public FlowNodeInstanceEntity setSortValues(Object[] sortValues) {
    this.sortValues = sortValues;
    return this;
  }

  public boolean isIncident() {
    return incident;
  }

  public FlowNodeInstanceEntity setIncident(final boolean incident) {
    this.incident = incident;
    return this;
  }

  public String getTenantId() {
    return tenantId;
  }

  public FlowNodeInstanceEntity setTenantId(String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  @Override
  public int hashCode() {
    int result =
        Objects.hash(
            super.hashCode(),
            flowNodeId,
            startDate,
            endDate,
            state,
            type,
            incidentKey,
            processInstanceKey,
            processDefinitionKey,
            bpmnProcessId,
            treePath,
            level,
            position,
            incident,
            tenantId);
    result = 31 * result + Arrays.hashCode(sortValues);
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    FlowNodeInstanceEntity that = (FlowNodeInstanceEntity) o;
    return level == that.level
        && incident == that.incident
        && Objects.equals(flowNodeId, that.flowNodeId)
        && Objects.equals(startDate, that.startDate)
        && Objects.equals(endDate, that.endDate)
        && state == that.state
        && type == that.type
        && Objects.equals(incidentKey, that.incidentKey)
        && Objects.equals(processInstanceKey, that.processInstanceKey)
        && Objects.equals(processDefinitionKey, that.processDefinitionKey)
        && Objects.equals(bpmnProcessId, that.bpmnProcessId)
        && Objects.equals(treePath, that.treePath)
        && Objects.equals(position, that.position)
        && Objects.equals(tenantId, that.tenantId)
        && Arrays.equals(sortValues, that.sortValues);
  }
}
