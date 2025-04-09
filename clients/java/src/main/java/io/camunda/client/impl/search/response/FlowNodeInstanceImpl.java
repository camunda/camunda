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
package io.camunda.client.impl.search.response;

import io.camunda.client.api.search.enums.FlowNodeInstanceState;
import io.camunda.client.api.search.enums.FlowNodeInstanceType;
import io.camunda.client.api.search.response.FlowNodeInstance;
import io.camunda.client.impl.util.EnumUtil;
import io.camunda.client.impl.util.ParseUtil;
import io.camunda.client.protocol.rest.ElementInstanceResult;
import java.util.Objects;

public final class FlowNodeInstanceImpl implements FlowNodeInstance {

  private final Long flowNodeInstanceKey;
  private final Long processDefinitionKey;
  private final String processDefinitionId;
  private final Long processInstanceKey;
  private final String flowNodeId;
  private final String flowNodeName;
  private final String startDate;
  private final String endDate;
  private final Boolean incident;
  private final Long incidentKey;
  private final FlowNodeInstanceState state;
  private final String tenantId;
  private final FlowNodeInstanceType type;

  public FlowNodeInstanceImpl(final ElementInstanceResult item) {
    flowNodeInstanceKey = ParseUtil.parseLongOrNull(item.getElementInstanceKey());
    processDefinitionKey = ParseUtil.parseLongOrNull(item.getProcessDefinitionKey());
    processDefinitionId = item.getProcessDefinitionId();
    processInstanceKey = ParseUtil.parseLongOrNull(item.getProcessInstanceKey());
    flowNodeId = item.getElementId();
    flowNodeName = item.getElementName();
    startDate = item.getStartDate();
    endDate = item.getEndDate();
    incident = item.getHasIncident();
    incidentKey = ParseUtil.parseLongOrNull(item.getIncidentKey());
    state = EnumUtil.convert(item.getState(), FlowNodeInstanceState.class);
    tenantId = item.getTenantId();
    type = EnumUtil.convert(item.getType(), FlowNodeInstanceType.class);
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

  @Override
  public int hashCode() {
    return Objects.hash(
        flowNodeInstanceKey,
        processDefinitionKey,
        processInstanceKey,
        processDefinitionId,
        flowNodeId,
        startDate,
        endDate,
        incident,
        incidentKey,
        state,
        tenantId,
        type);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final FlowNodeInstanceImpl that = (FlowNodeInstanceImpl) o;
    return Objects.equals(flowNodeInstanceKey, that.flowNodeInstanceKey)
        && Objects.equals(processDefinitionKey, that.processDefinitionKey)
        && Objects.equals(processInstanceKey, that.processInstanceKey)
        && Objects.equals(processDefinitionId, that.processDefinitionId)
        && Objects.equals(flowNodeId, that.flowNodeId)
        && Objects.equals(flowNodeName, that.flowNodeName)
        && Objects.equals(startDate, that.startDate)
        && Objects.equals(endDate, that.endDate)
        && Objects.equals(incident, that.incident)
        && Objects.equals(incidentKey, that.incidentKey)
        && state == that.state
        && Objects.equals(tenantId, that.tenantId)
        && type == that.type;
  }
}
