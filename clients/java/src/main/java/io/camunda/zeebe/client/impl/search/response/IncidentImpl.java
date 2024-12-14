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

import io.camunda.zeebe.client.api.search.response.Incident;
import io.camunda.zeebe.client.protocol.rest.IncidentItem;
import io.camunda.zeebe.client.protocol.rest.IncidentItem.ErrorTypeEnum;
import io.camunda.zeebe.client.protocol.rest.IncidentItem.StateEnum;
import java.util.Objects;

public class IncidentImpl implements Incident {

  private final Long incidentKey;
  private final Long processDefinitionKey;
  private final String processDefinitionId;
  private final Long processInstanceKey;
  private final ErrorTypeEnum errorType;
  private final String errorMessage;
  private final String flowNodeId;
  private final Long flowNodeInstanceKey;
  private final String creationTime;
  private final StateEnum state;
  private final Long jobKey;
  private final String tenantId;

  public IncidentImpl(final IncidentItem item) {
    incidentKey = item.getIncidentKey();
    processDefinitionKey = item.getProcessDefinitionKey();
    processDefinitionId = item.getProcessDefinitionId();
    processInstanceKey = item.getProcessInstanceKey();
    errorType = item.getErrorType();
    errorMessage = item.getErrorMessage();
    flowNodeId = item.getFlowNodeId();
    flowNodeInstanceKey = item.getFlowNodeInstanceKey();
    creationTime = item.getCreationTime();
    state = item.getState();
    jobKey = item.getJobKey();
    tenantId = item.getTenantId();
  }

  @Override
  public Long getIncidentKey() {
    return incidentKey;
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
  public String getErrorMessage() {
    return errorMessage;
  }

  @Override
  public String getFlowNodeId() {
    return flowNodeId;
  }

  @Override
  public Long getFlowNodeInstanceKey() {
    return flowNodeInstanceKey;
  }

  @Override
  public String getCreationTime() {
    return creationTime;
  }

  @Override
  public String getState() {
    return state.getValue();
  }

  @Override
  public Long getJobKey() {
    return jobKey;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  @Override
  public String getErrorType() {
    return errorType.getValue();
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        incidentKey,
        processDefinitionKey,
        processDefinitionId,
        processInstanceKey,
        errorType,
        errorMessage,
        flowNodeId,
        flowNodeInstanceKey,
        creationTime,
        state,
        jobKey,
        tenantId);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final IncidentImpl incident = (IncidentImpl) o;
    return Objects.equals(incidentKey, incident.incidentKey)
        && Objects.equals(processDefinitionKey, incident.processDefinitionKey)
        && Objects.equals(processDefinitionId, incident.processDefinitionId)
        && Objects.equals(processInstanceKey, incident.processInstanceKey)
        && errorType == incident.errorType
        && Objects.equals(errorMessage, incident.errorMessage)
        && Objects.equals(flowNodeId, incident.flowNodeId)
        && Objects.equals(flowNodeInstanceKey, incident.flowNodeInstanceKey)
        && Objects.equals(creationTime, incident.creationTime)
        && state == incident.state
        && Objects.equals(jobKey, incident.jobKey)
        && Objects.equals(tenantId, incident.tenantId);
  }
}
