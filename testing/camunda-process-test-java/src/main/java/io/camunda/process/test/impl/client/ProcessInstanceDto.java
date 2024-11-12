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

public class ProcessInstanceDto {

  private long key;
  private int processVersion;
  private String bpmnProcessId;
  private long parentKey;
  private long parentFlowNodeInstanceKey;
  private String startDate;
  private String endDate;
  private ProcessInstanceState state;
  private long processDefinitionKey;
  private String tenantId;
  private long parentProcessInstanceKey;

  public long getKey() {
    return key;
  }

  public void setKey(final long key) {
    this.key = key;
  }

  public int getProcessVersion() {
    return processVersion;
  }

  public void setProcessVersion(final int processVersion) {
    this.processVersion = processVersion;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public void setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
  }

  public long getParentKey() {
    return parentKey;
  }

  public void setParentKey(final long parentKey) {
    this.parentKey = parentKey;
  }

  public long getParentFlowNodeInstanceKey() {
    return parentFlowNodeInstanceKey;
  }

  public void setParentFlowNodeInstanceKey(final long parentFlowNodeInstanceKey) {
    this.parentFlowNodeInstanceKey = parentFlowNodeInstanceKey;
  }

  public String getStartDate() {
    return startDate;
  }

  public void setStartDate(final String startDate) {
    this.startDate = startDate;
  }

  public String getEndDate() {
    return endDate;
  }

  public void setEndDate(final String endDate) {
    this.endDate = endDate;
  }

  public ProcessInstanceState getState() {
    return state;
  }

  public void setState(final ProcessInstanceState state) {
    this.state = state;
  }

  public long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public void setProcessDefinitionKey(final long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(final String tenantId) {
    this.tenantId = tenantId;
  }

  public long getParentProcessInstanceKey() {
    return parentProcessInstanceKey;
  }

  public void setParentProcessInstanceKey(final long parentProcessInstanceKey) {
    this.parentProcessInstanceKey = parentProcessInstanceKey;
  }
}
