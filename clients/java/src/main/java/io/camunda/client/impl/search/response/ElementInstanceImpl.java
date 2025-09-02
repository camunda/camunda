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

import io.camunda.client.api.search.enums.ElementInstanceState;
import io.camunda.client.api.search.enums.ElementInstanceType;
import io.camunda.client.api.search.response.ElementInstance;
import io.camunda.client.impl.util.EnumUtil;
import java.time.OffsetDateTime;
import io.camunda.client.impl.util.ParseUtil;
import java.time.OffsetDateTime;
import io.camunda.client.protocol.rest.ElementInstanceResult;
import java.util.Objects;
import java.time.OffsetDateTime;

public final class ElementInstanceImpl implements ElementInstance {

  private final Long elementInstanceKey;
  private final Long processDefinitionKey;
  private final String processDefinitionId;
  private final Long processInstanceKey;
  private final String elementId;
  private final String elementName;
  private final OffsetDateTime startDate;
  private final OffsetDateTime endDate;
  private final Boolean incident;
  private final Long incidentKey;
  private final ElementInstanceState state;
  private final String tenantId;
  private final ElementInstanceType type;

  public ElementInstanceImpl(final ElementInstanceResult item) {
    elementInstanceKey = ParseUtil.parseLongOrNull(item.getElementInstanceKey());
    processDefinitionKey = ParseUtil.parseLongOrNull(item.getProcessDefinitionKey());
    processDefinitionId = item.getProcessDefinitionId();
    processInstanceKey = ParseUtil.parseLongOrNull(item.getProcessInstanceKey());
    elementId = item.getElementId();
    elementName = item.getElementName();
    startDate = item.getStartDate();
    endDate = item.getEndDate();
    incident = item.getHasIncident();
    incidentKey = ParseUtil.parseLongOrNull(item.getIncidentKey());
    state = EnumUtil.convert(item.getState(), ElementInstanceState.class);
    tenantId = item.getTenantId();
    type = EnumUtil.convert(item.getType(), ElementInstanceType.class);
  }

  @Override
  public Long getElementInstanceKey() {
    return elementInstanceKey;
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
  public String getElementId() {
    return elementId;
  }

  @Override
  public String getElementName() {
    return elementName;
  }

  @Override
  public OffsetDateTime getStartDate() {
    return startDate;
  }

  @Override
  public OffsetDateTime getEndDate() {
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
  public ElementInstanceState getState() {
    return state;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  @Override
  public ElementInstanceType getType() {
    return type;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        elementInstanceKey,
        processDefinitionKey,
        processInstanceKey,
        processDefinitionId,
        elementId,
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
    final ElementInstanceImpl that = (ElementInstanceImpl) o;
    return Objects.equals(elementInstanceKey, that.elementInstanceKey)
        && Objects.equals(processDefinitionKey, that.processDefinitionKey)
        && Objects.equals(processInstanceKey, that.processInstanceKey)
        && Objects.equals(processDefinitionId, that.processDefinitionId)
        && Objects.equals(elementId, that.elementId)
        && Objects.equals(elementName, that.elementName)
        && Objects.equals(startDate, that.startDate)
        && Objects.equals(endDate, that.endDate)
        && Objects.equals(incident, that.incident)
        && Objects.equals(incidentKey, that.incidentKey)
        && state == that.state
        && Objects.equals(tenantId, that.tenantId)
        && type == that.type;
  }
}
