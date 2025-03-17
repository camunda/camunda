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

import io.camunda.client.api.search.response.FlowNodeInstance;
import io.camunda.client.api.search.response.FlowNodeInstanceState;
import io.camunda.client.api.search.response.FlowNodeInstanceType;

public class FlowNodeInstanceBuilder implements FlowNodeInstance {

  private static final String START_DATE = "2024-01-01T10:00:00";
  private static final String END_DATE = "2024-01-02T15:00:00";

  private Long flowNodeInstanceKey;
  private Long processDefinitionKey;
  private String processDefinitionId;
  private Long processInstanceKey;
  private String flowNodeId;
  private String flowNodeName;
  private String startDate;
  private String endDate;
  private Boolean incident;
  private Long incidentKey;
  private FlowNodeInstanceState state;
  private String tenantId;
  private FlowNodeInstanceType type;

  @Override
  public Long getFlowNodeInstanceKey() {
    return flowNodeInstanceKey;
  }

  @Override
  public Long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  @Override
  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  @Override
  public Long getProcessInstanceKey() {
    return processInstanceKey;
  }

  @Override
  public String getFlowNodeId() {
    return flowNodeId;
  }

  @Override
  public String getFlowNodeName() {
    return flowNodeName;
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
  public Boolean getIncident() {
    return incident;
  }

  @Override
  public Long getIncidentKey() {
    return incidentKey;
  }

  @Override
  public FlowNodeInstanceState getState() {
    return state;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  @Override
  public FlowNodeInstanceType getType() {
    return type;
  }

  public FlowNodeInstanceBuilder setType(final FlowNodeInstanceType type) {
    this.type = type;
    return this;
  }

  public FlowNodeInstanceBuilder setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public FlowNodeInstanceBuilder setState(final FlowNodeInstanceState state) {
    this.state = state;
    return this;
  }

  public FlowNodeInstanceBuilder setIncidentKey(final Long incidentKey) {
    this.incidentKey = incidentKey;
    return this;
  }

  public FlowNodeInstanceBuilder setIncident(final Boolean incident) {
    this.incident = incident;
    return this;
  }

  public FlowNodeInstanceBuilder setEndDate(final String endDate) {
    this.endDate = endDate;
    return this;
  }

  public FlowNodeInstanceBuilder setStartDate(final String startDate) {
    this.startDate = startDate;
    return this;
  }

  public FlowNodeInstanceBuilder setFlowNodeName(final String flowNodeName) {
    this.flowNodeName = flowNodeName;
    return this;
  }

  public FlowNodeInstanceBuilder setFlowNodeId(final String flowNodeId) {
    this.flowNodeId = flowNodeId;
    return this;
  }

  public FlowNodeInstanceBuilder setProcessInstanceKey(final Long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  public FlowNodeInstanceBuilder setProcessDefinitionId(final String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
    return this;
  }

  public FlowNodeInstanceBuilder setProcessDefinitionKey(final Long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  public FlowNodeInstanceBuilder setFlowNodeInstanceKey(final Long flowNodeInstanceKey) {
    this.flowNodeInstanceKey = flowNodeInstanceKey;
    return this;
  }

  public FlowNodeInstance build() {
    return this;
  }

  public static FlowNodeInstanceBuilder newActiveFlowNodeInstance(
      final String elementId, final long processInstanceKey) {
    return new FlowNodeInstanceBuilder()
        .setFlowNodeId(elementId)
        .setFlowNodeName("element_" + elementId)
        .setProcessInstanceKey(processInstanceKey)
        .setState(FlowNodeInstanceState.ACTIVE)
        .setStartDate(START_DATE);
  }

  public static FlowNodeInstanceBuilder newCompletedFlowNodeInstance(
      final String elementId, final long processInstanceKey) {
    return newActiveFlowNodeInstance(elementId, processInstanceKey)
        .setState(FlowNodeInstanceState.COMPLETED)
        .setEndDate(END_DATE);
  }

  public static FlowNodeInstanceBuilder newTerminatedFlowNodeInstance(
      final String elementId, final long processInstanceKey) {
    return newActiveFlowNodeInstance(elementId, processInstanceKey)
        .setState(FlowNodeInstanceState.TERMINATED)
        .setEndDate(END_DATE);
  }
}
