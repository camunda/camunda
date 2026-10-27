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

import io.camunda.client.api.search.enums.ElementInstanceState;
import io.camunda.client.api.search.enums.ElementInstanceType;
import io.camunda.client.api.search.response.ElementInstance;
import java.time.OffsetDateTime;

public class ElementInstanceBuilder implements ElementInstance {

  private static final OffsetDateTime START_DATE = OffsetDateTime.parse("2024-01-01T10:00:00Z");
  private static final OffsetDateTime END_DATE = OffsetDateTime.parse("2024-01-02T15:00:00Z");

  private Long elementInstanceKey;
  private Long processDefinitionKey;
  private String processDefinitionId;
  private Long processInstanceKey;
  private Long rootProcessInstanceKey;
  private String elementId;
  private String elementName;
  private OffsetDateTime startDate;
  private OffsetDateTime endDate;
  private Boolean incident;
  private Long incidentKey;
  private ElementInstanceState state;
  private String tenantId;
  private ElementInstanceType type;

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
  public Long getRootProcessInstanceKey() {
    return rootProcessInstanceKey;
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

  public ElementInstanceBuilder setType(final ElementInstanceType type) {
    this.type = type;
    return this;
  }

  public ElementInstanceBuilder setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public ElementInstanceBuilder setState(final ElementInstanceState state) {
    this.state = state;
    return this;
  }

  public ElementInstanceBuilder setIncidentKey(final Long incidentKey) {
    this.incidentKey = incidentKey;
    return this;
  }

  public ElementInstanceBuilder setIncident(final Boolean incident) {
    this.incident = incident;
    return this;
  }

  public ElementInstanceBuilder setEndDate(final OffsetDateTime endDate) {
    this.endDate = endDate;
    return this;
  }

  public ElementInstanceBuilder setStartDate(final OffsetDateTime startDate) {
    this.startDate = startDate;
    return this;
  }

  public ElementInstanceBuilder setElementName(final String elementName) {
    this.elementName = elementName;
    return this;
  }

  public ElementInstanceBuilder setElementId(final String elementId) {
    this.elementId = elementId;
    return this;
  }

  public ElementInstanceBuilder setRootProcessInstanceKey(final Long rootProcessInstanceKey) {
    this.rootProcessInstanceKey = rootProcessInstanceKey;
    return this;
  }

  public ElementInstanceBuilder setProcessInstanceKey(final Long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  public ElementInstanceBuilder setProcessDefinitionId(final String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
    return this;
  }

  public ElementInstanceBuilder setProcessDefinitionKey(final Long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  public ElementInstanceBuilder setElementInstanceKey(final Long elementInstanceKey) {
    this.elementInstanceKey = elementInstanceKey;
    return this;
  }

  public ElementInstance build() {
    return this;
  }

  public static ElementInstanceBuilder newActiveElementInstance(
      final String elementId, final long processInstanceKey) {
    return new ElementInstanceBuilder()
        .setElementId(elementId)
        .setElementName("element_" + elementId)
        .setProcessInstanceKey(processInstanceKey)
        .setState(ElementInstanceState.ACTIVE)
        .setStartDate(START_DATE);
  }

  public static ElementInstanceBuilder newCompletedElementInstance(
      final String elementId, final long processInstanceKey) {
    return newActiveElementInstance(elementId, processInstanceKey)
        .setState(ElementInstanceState.COMPLETED)
        .setEndDate(END_DATE);
  }

  public static ElementInstanceBuilder newTerminatedElementInstance(
      final String elementId, final long processInstanceKey) {
    return newActiveElementInstance(elementId, processInstanceKey)
        .setState(ElementInstanceState.TERMINATED)
        .setEndDate(END_DATE);
  }
}
