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
package io.camunda.process.test.utils;

import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.client.api.search.response.ProcessInstance;

public class ProcessInstanceBuilder implements ProcessInstance {

  private static final String PROCESS_DEFINITION_ID = "process";
  private static final String START_DATE = "2024-01-01T09:00:00";
  private static final String END_DATE = "2024-01-02T16:00:00";

  private Long processInstanceKey;
  private String processDefinitionId;
  private String processDefinitionName;
  private Integer processDefinitionVersion;
  private String processDefinitionVersionTag;
  private Long processDefinitionKey;
  private Long parentProcessInstanceKey;
  private Long parentFlowNodeInstanceKey;
  private String startDate;
  private String endDate;
  private ProcessInstanceState state;
  private Boolean hasIncident;
  private String tenantId;

  @Override
  public Long getProcessInstanceKey() {
    return processInstanceKey;
  }

  @Override
  public String getProcessDefinitionId() {
    return processDefinitionId;
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

  @Override
  public Long getParentProcessInstanceKey() {
    return parentProcessInstanceKey;
  }

  @Override
  public Long getParentFlowNodeInstanceKey() {
    return parentFlowNodeInstanceKey;
  }

  @Override
  public String getStartDate() {
    return startDate;
  }

  @Override
  public String getEndDate() {
    return endDate;
  }

  @Override
  public ProcessInstanceState getState() {
    return state;
  }

  @Override
  public Boolean getHasIncident() {
    return hasIncident;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  public ProcessInstanceBuilder setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public ProcessInstanceBuilder setHasIncident(final Boolean hasIncident) {
    this.hasIncident = hasIncident;
    return this;
  }

  public ProcessInstanceBuilder setState(final ProcessInstanceState state) {
    this.state = state;
    return this;
  }

  public ProcessInstanceBuilder setEndDate(final String endDate) {
    this.endDate = endDate;
    return this;
  }

  public ProcessInstanceBuilder setStartDate(final String startDate) {
    this.startDate = startDate;
    return this;
  }

  public ProcessInstanceBuilder setParentFlowNodeInstanceKey(final Long parentFlowNodeInstanceKey) {
    this.parentFlowNodeInstanceKey = parentFlowNodeInstanceKey;
    return this;
  }

  public ProcessInstanceBuilder setParentProcessInstanceKey(final Long parentProcessInstanceKey) {
    this.parentProcessInstanceKey = parentProcessInstanceKey;
    return this;
  }

  public ProcessInstanceBuilder setProcessDefinitionKey(final Long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  public ProcessInstanceBuilder setProcessDefinitionVersionTag(
      final String processDefinitionVersionTag) {
    this.processDefinitionVersionTag = processDefinitionVersionTag;
    return this;
  }

  public ProcessInstanceBuilder setProcessDefinitionVersion(
      final Integer processDefinitionVersion) {
    this.processDefinitionVersion = processDefinitionVersion;
    return this;
  }

  public ProcessInstanceBuilder setProcessDefinitionName(final String processDefinitionName) {
    this.processDefinitionName = processDefinitionName;
    return this;
  }

  public ProcessInstanceBuilder setProcessDefinitionId(final String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
    return this;
  }

  public ProcessInstanceBuilder setProcessInstanceKey(final Long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  public ProcessInstance build() {
    return this;
  }

  public static ProcessInstanceBuilder newActiveProcessInstance(final long processInstanceKey) {
    return new ProcessInstanceBuilder()
        .setProcessInstanceKey(processInstanceKey)
        .setProcessDefinitionId(PROCESS_DEFINITION_ID)
        .setState(ProcessInstanceState.ACTIVE)
        .setStartDate(START_DATE);
  }

  public static ProcessInstanceBuilder newCompletedProcessInstance(final long processInstanceKey) {
    return newActiveProcessInstance(processInstanceKey)
        .setState(ProcessInstanceState.COMPLETED)
        .setStartDate(START_DATE)
        .setEndDate(END_DATE);
  }

  public static ProcessInstanceBuilder newTerminatedProcessInstance(final long processInstanceKey) {
    return newActiveProcessInstance(processInstanceKey)
        .setState(ProcessInstanceState.TERMINATED)
        .setStartDate(START_DATE)
        .setEndDate(END_DATE);
  }
}
