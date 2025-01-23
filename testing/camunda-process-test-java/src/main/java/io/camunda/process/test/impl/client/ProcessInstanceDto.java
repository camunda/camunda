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

import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.search.response.ProcessInstanceState;

public class ProcessInstanceDto implements ProcessInstance {

  private Long key;
  private int processVersion;
  private String bpmnProcessId;
  private String processDefinitionName;
  private int processDefinitionVersion;
  private String processDefinitionVersionTag;
  private long parentKey;
  private Long parentFlowNodeInstanceKey;
  private String startDate;
  private String endDate;
  private ProcessInstanceState state;
  private Long processDefinitionKey;
  private String tenantId;
  private Long parentProcessInstanceKey;

  @Override
  public Long getProcessInstanceKey() {
    return key;
  }

  @Override
  public String getProcessDefinitionId() {
    return bpmnProcessId;
  }

  @Override
  public String getProcessDefinitionName() {
    return processDefinitionName;
  }

  @Override
  public Integer getProcessDefinitionVersion() {
    return processDefinitionVersion;
  }

  @Override
  public String getProcessDefinitionVersionTag() {
    return processDefinitionVersionTag;
  }

  @Override
  public Long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public void setProcessDefinitionKey(final long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
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
  public ProcessInstanceState getState() {
    return state;
  }

  public void setState(final ProcessInstanceState state) {
    this.state = state;
  }

  @Override
  public Boolean getHasIncident() {
    return false;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(final String tenantId) {
    this.tenantId = tenantId;
  }

  public void setParentProcessInstanceKey(final long parentProcessInstanceKey) {
    this.parentProcessInstanceKey = parentProcessInstanceKey;
  }

  public void setProcessDefinitionVersionTag(final String processDefinitionVersionTag) {
    this.processDefinitionVersionTag = processDefinitionVersionTag;
  }

  public void setProcessDefinitionVersion(final int processDefinitionVersion) {
    this.processDefinitionVersion = processDefinitionVersion;
  }

  public void setProcessDefinitionName(final String processDefinitionName) {
    this.processDefinitionName = processDefinitionName;
  }

  public void setKey(final long processInstanceKey) {
    key = processInstanceKey;
  }

  public void setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
  }

  public ProcessInstanceState getProcessInstanceState() {
    return state;
  }

  public int getProcessVersion() {
    return processVersion;
  }

  public void setProcessVersion(final int processVersion) {
    this.processVersion = processVersion;
  }

  public long getParentKey() {
    return parentKey;
  }

  public void setParentKey(final long parentKey) {
    this.parentKey = parentKey;
  }
}
