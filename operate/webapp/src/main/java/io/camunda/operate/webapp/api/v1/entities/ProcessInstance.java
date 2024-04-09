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
package io.camunda.operate.webapp.api.v1.entities;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.operate.entities.listview.ProcessInstanceState;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class ProcessInstance {

  public static final String KEY = ListViewTemplate.PROCESS_INSTANCE_KEY,
      VERSION = ListViewTemplate.PROCESS_VERSION,
      BPMN_PROCESS_ID = ListViewTemplate.BPMN_PROCESS_ID,
      PROCESS_DEFINITION_KEY = ListViewTemplate.PROCESS_KEY,
      PARENT_KEY = ListViewTemplate.PARENT_PROCESS_INSTANCE_KEY,
      PARENT_FLOW_NODE_INSTANCE_KEY = ListViewTemplate.PARENT_FLOW_NODE_INSTANCE_KEY,
      START_DATE = ListViewTemplate.START_DATE,
      END_DATE = ListViewTemplate.END_DATE,
      STATE = ListViewTemplate.STATE,
      INCIDENT = ListViewTemplate.INCIDENT,
      TENANT_ID = ListViewTemplate.TENANT_ID;

  private Long key;
  private Integer processVersion;
  private String bpmnProcessId;
  private Long parentKey;
  private Long parentFlowNodeInstanceKey;
  private String startDate;
  private String endDate;

  @Schema(implementation = ProcessInstanceState.class)
  private String state;

  private Boolean incident;

  private Long processDefinitionKey;
  private String tenantId;

  public Long getKey() {
    return key;
  }

  public ProcessInstance setKey(final long key) {
    this.key = key;
    return this;
  }

  public Integer getProcessVersion() {
    return processVersion;
  }

  public ProcessInstance setProcessVersion(final int processVersion) {
    this.processVersion = processVersion;
    return this;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public ProcessInstance setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  public Long getParentKey() {
    return parentKey;
  }

  public ProcessInstance setParentKey(final Long parentKey) {
    this.parentKey = parentKey;
    return this;
  }

  @JsonProperty("parentProcessInstanceKey")
  public ProcessInstance setParentProcessInstanceKey(final Long parentProcessInstanceKey) {
    this.parentKey = parentProcessInstanceKey;
    return this;
  }

  public Long getParentFlowNodeInstanceKey() {
    return parentFlowNodeInstanceKey;
  }

  public ProcessInstance setParentFlowNodeInstanceKey(Long parentFlowNodeInstanceKey) {
    this.parentFlowNodeInstanceKey = parentFlowNodeInstanceKey;
    return this;
  }

  public String getStartDate() {
    return startDate;
  }

  public ProcessInstance setStartDate(final String startDate) {
    this.startDate = startDate;
    return this;
  }

  public String getEndDate() {
    return endDate;
  }

  public ProcessInstance setEndDate(final String endDate) {
    this.endDate = endDate;
    return this;
  }

  public String getState() {
    return state;
  }

  public ProcessInstance setState(final String state) {
    this.state = state;
    return this;
  }

  public Boolean getIncident() {
    return incident;
  }

  public ProcessInstance setIncident(final Boolean incident) {
    this.incident = incident;
    return this;
  }

  public Long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public ProcessInstance setProcessDefinitionKey(final long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  public String getTenantId() {
    return tenantId;
  }

  public ProcessInstance setTenantId(String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ProcessInstance that = (ProcessInstance) o;
    return Objects.equals(key, that.key)
        && Objects.equals(processVersion, that.processVersion)
        && Objects.equals(bpmnProcessId, that.bpmnProcessId)
        && Objects.equals(parentKey, that.parentKey)
        && Objects.equals(parentFlowNodeInstanceKey, that.parentFlowNodeInstanceKey)
        && Objects.equals(startDate, that.startDate)
        && Objects.equals(endDate, that.endDate)
        && Objects.equals(state, that.state)
        && Objects.equals(incident, that.incident)
        && Objects.equals(processDefinitionKey, that.processDefinitionKey)
        && Objects.equals(tenantId, that.tenantId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        key,
        processVersion,
        bpmnProcessId,
        parentKey,
        parentFlowNodeInstanceKey,
        startDate,
        endDate,
        state,
        incident,
        processDefinitionKey,
        tenantId);
  }

  @Override
  public String toString() {
    return "ProcessInstance{"
        + "key="
        + key
        + ", processVersion="
        + processVersion
        + ", bpmnProcessId='"
        + bpmnProcessId
        + '\''
        + ", parentKey="
        + parentKey
        + ", parentFlowNodeInstanceKey="
        + parentFlowNodeInstanceKey
        + ", startDate='"
        + startDate
        + '\''
        + ", endDate='"
        + endDate
        + '\''
        + ", state='"
        + state
        + '\''
        + ", incident="
        + incident
        + ", processDefinitionKey="
        + processDefinitionKey
        + ", tenantId='"
        + tenantId
        + '\''
        + '}';
  }
}
