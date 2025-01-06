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

import io.camunda.client.api.search.response.FlowNodeInstance;
import io.camunda.client.api.search.response.FlowNodeInstanceState;
import io.camunda.client.api.search.response.FlowNodeInstanceType;

public class FlowNodeInstanceDto implements FlowNodeInstance {

  private long key;
  private long processInstanceKey;
  private long processDefinitionKey;
  private String startDate;
  private String endDate;
  private String flowNodeId;
  private String flowNodeName;
  private long incidentKey;
  private FlowNodeInstanceType type;
  private FlowNodeInstanceState state;
  private boolean incident;
  private String tenantId;

  @Override
  public Long getFlowNodeInstanceKey() {
    return key;
  }

  @Override
  public Long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  @Override
  public String getProcessDefinitionId() {
    return "";
  }

  @Override
  public Long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public void setProcessInstanceKey(final long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
  }

  @Override
  public String getFlowNodeId() {
    return flowNodeId;
  }

  public void setFlowNodeId(final String flowNodeId) {
    this.flowNodeId = flowNodeId;
  }

  @Override
  public String getFlowNodeName() {
    return flowNodeName;
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

  @Override
  public Boolean getIncident() {
    return incident;
  }

  @Override
  public Long getIncidentKey() {
    return incidentKey;
  }

  public void setIncidentKey(final long incidentKey) {
    this.incidentKey = incidentKey;
  }

  @Override
  public FlowNodeInstanceState getState() {
    return state;
  }

  public void setState(final FlowNodeInstanceState state) {
    this.state = state;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  @Override
  public FlowNodeInstanceType getType() {
    return type;
  }

  public void setType(final FlowNodeInstanceType type) {
    this.type = type;
  }

  public void setTenantId(final String tenantId) {
    this.tenantId = tenantId;
  }

  public void setEndDate(final String endDate) {
    this.endDate = endDate;
  }

  public void setFlowNodeName(final String flowNodeName) {
    this.flowNodeName = flowNodeName;
  }

  public void setProcessDefinitionKey(final long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public long getKey() {
    return key;
  }

  public void setKey(final long key) {
    this.key = key;
  }

  public boolean isIncident() {
    return incident;
  }

  public void setIncident(final boolean incident) {
    this.incident = incident;
  }
}
