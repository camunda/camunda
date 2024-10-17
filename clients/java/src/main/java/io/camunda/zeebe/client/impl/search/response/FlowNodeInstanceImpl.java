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
package io.camunda.zeebe.client.impl.search.response;

import io.camunda.zeebe.client.api.search.response.FlowNodeInstance;
import io.camunda.zeebe.client.protocol.rest.FlowNodeInstanceItem;

public final class FlowNodeInstanceImpl implements FlowNodeInstance {

  private final Long flowNodeInstanceKey;
  private final Long processDefinitionKey;
  private final Long processInstanceKey;
  private final String flowNodeId;
  private final String startDate;
  private final String endDate;
  private final Boolean incident;
  private final Long incidentKey;
  private final FlowNodeInstanceItem.StateEnum state;
  private final String tenantId;
  private final String treePath;
  private final FlowNodeInstanceItem.TypeEnum type;

  public FlowNodeInstanceImpl(final FlowNodeInstanceItem item) {
    flowNodeInstanceKey = item.getFlowNodeInstanceKey();
    processDefinitionKey = item.getProcessDefinitionKey();
    processInstanceKey = item.getProcessInstanceKey();
    flowNodeId = item.getFlowNodeId();
    startDate = item.getStartDate();
    endDate = item.getEndDate();
    incident = item.getHasIncident();
    incidentKey = item.getIncidentKey();
    state = item.getState();
    tenantId = item.getTenantId();
    treePath = item.getTreePath();
    type = item.getType();
  }

  @Override
  public Long getFlowNodeInstanceKey() {
    return flowNodeInstanceKey;
  }

  @Override
  public Long getProcessDefinitionKey() {
    return processDefinitionKey;
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
  public String getState() {
    return state.getValue();
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  @Override
  public String getTreePath() {
    return treePath;
  }

  @Override
  public String getType() {
    return type.getValue();
  }
}
