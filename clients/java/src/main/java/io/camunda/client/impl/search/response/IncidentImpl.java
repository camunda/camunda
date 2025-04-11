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

import io.camunda.client.api.search.enums.IncidentErrorType;
import io.camunda.client.api.search.enums.IncidentState;
import io.camunda.client.api.search.response.Incident;
import io.camunda.client.impl.util.EnumUtil;
import io.camunda.client.impl.util.ParseUtil;
import io.camunda.client.protocol.rest.IncidentResult;
import java.util.Objects;

public class IncidentImpl implements Incident {

  private final Long incidentKey;
  private final Long processDefinitionKey;
  private final String processDefinitionId;
  private final Long processInstanceKey;
  private final IncidentErrorType errorType;
  private final String errorMessage;
  private final String elementId;
  private final Long elementInstanceKey;
  private final String creationTime;
  private final IncidentState state;
  private final Long jobKey;
  private final String tenantId;

  public IncidentImpl(final IncidentResult item) {
    incidentKey = ParseUtil.parseLongOrNull(item.getIncidentKey());
    processDefinitionKey = ParseUtil.parseLongOrNull(item.getProcessDefinitionKey());
    processDefinitionId = item.getProcessDefinitionId();
    processInstanceKey = ParseUtil.parseLongOrNull(item.getProcessInstanceKey());
    errorType = EnumUtil.convert(item.getErrorType(), IncidentErrorType.class);
    errorMessage = item.getErrorMessage();
    elementId = item.getElementId();
    elementInstanceKey = ParseUtil.parseLongOrNull(item.getElementInstanceKey());
    creationTime = item.getCreationTime();
    state = EnumUtil.convert(item.getState(), IncidentState.class);
    jobKey = ParseUtil.parseLongOrNull(item.getJobKey());
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
  public IncidentErrorType getErrorType() {
    return errorType;
  }

  @Override
  public String getErrorMessage() {
    return errorMessage;
  }

  @Override
  public String getElementId() {
    return elementId;
  }

  @Override
  public Long getElementInstanceKey() {
    return elementInstanceKey;
  }

  @Override
  public String getCreationTime() {
    return creationTime;
  }

  @Override
  public IncidentState getState() {
    return state;
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
  public int hashCode() {
    return Objects.hash(
        incidentKey,
        processDefinitionKey,
        processDefinitionId,
        processInstanceKey,
        errorType,
        errorMessage,
        elementId,
        elementInstanceKey,
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
        && Objects.equals(elementId, incident.elementId)
        && Objects.equals(elementInstanceKey, incident.elementInstanceKey)
        && Objects.equals(creationTime, incident.creationTime)
        && state == incident.state
        && Objects.equals(jobKey, incident.jobKey)
        && Objects.equals(tenantId, incident.tenantId);
  }
}
