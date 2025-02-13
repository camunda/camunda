/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.process.test.impl.client;

import io.camunda.zeebe.client.api.search.response.ProcessInstance;
import io.camunda.zeebe.client.impl.search.response.OperationImpl;
import io.camunda.zeebe.client.impl.search.response.ProcessInstanceReferenceImpl;
import java.util.Collections;
import java.util.List;

public class ProcessInstanceDto implements ProcessInstance {

  private Long key;
  private String bpmnProcessId;
  private String processDefinitionName;
  private int processDefinitionVersion;
  private long parentKey;
  private Long parentFlowNodeInstanceKey;
  private String startDate;
  private String endDate;
  private String state;
  private Long processDefinitionKey;
  private String tenantId;
  private Long parentProcessInstanceKey;
  private String rootInstanceId;

  @Override
  public Long getKey() {
    return key;
  }

  public void setKey(final long processInstanceKey) {
    key = processInstanceKey;
  }

  @Override
  public String getProcessName() {
    return processDefinitionName;
  }

  @Override
  public Integer getProcessVersion() {
    return processDefinitionVersion;
  }

  @Override
  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public void setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
  }

  @Override
  public Long getParentProcessInstanceKey() {
    return parentProcessInstanceKey;
  }

  @Override
  public Long getParentFlowNodeInstanceKey() {
    return parentFlowNodeInstanceKey;
  }

  public void setParentFlowNodeInstanceKey(final long parentFlowNodeInstanceKey) {
    this.parentFlowNodeInstanceKey = parentFlowNodeInstanceKey;
  }

  @Override
  public String getStartDate() {
    return startDate;
  }

  public void setStartDate(final String startDate) {
    this.startDate = startDate;
  }

  @Override
  public String getEndDate() {
    return endDate;
  }

  public void setEndDate(final String endDate) {
    this.endDate = endDate;
  }

  @Override
  public String getState() {
    return state;
  }

  public void setState(final String state) {
    this.state = state;
  }

  public void setState(final ProcessInstanceState state) {
    this.state = state.name();
  }

  @Override
  public Boolean getIncident() {
    return false;
  }

  @Override
  public Boolean getHasActiveOperation() {
    return false;
  }

  @Override
  public Long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public void setProcessDefinitionKey(final long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(final String tenantId) {
    this.tenantId = tenantId;
  }

  @Override
  public String getRootInstanceId() {
    return rootInstanceId;
  }

  public void setRootInstanceId(final String rootInstanceId) {
    this.rootInstanceId = rootInstanceId;
  }

  @Override
  public List<OperationImpl> getOperations() {
    return Collections.emptyList();
  }

  @Override
  public List<ProcessInstanceReferenceImpl> getCallHierarchy() {
    return Collections.emptyList();
  }

  public void setParentProcessInstanceKey(final long parentProcessInstanceKey) {
    this.parentProcessInstanceKey = parentProcessInstanceKey;
  }

  public void setProcessDefinitionName(final String processDefinitionName) {
    this.processDefinitionName = processDefinitionName;
  }

  public void setProcessDefinitionVersion(final int processDefinitionVersion) {
    this.processDefinitionVersion = processDefinitionVersion;
  }

  public long getParentKey() {
    return parentKey;
  }

  public void setParentKey(final long parentKey) {
    this.parentKey = parentKey;
  }

  public ProcessInstanceState getProcessInstanceState() {
    return state != null ? ProcessInstanceState.valueOf(state) : null;
  }
}
