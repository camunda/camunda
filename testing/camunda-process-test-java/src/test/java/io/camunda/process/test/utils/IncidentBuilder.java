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

import io.camunda.client.api.search.response.Incident;
import io.camunda.client.wrappers.IncidentResult;

public class IncidentBuilder implements Incident {

  private Long incidentKey;
  private Long processDefinitionKey;
  private String processDefinitionId;
  private Long processInstanceKey;
  private IncidentResult.ErrorType errorType;
  private String errorMessage;
  private String flowNodeId;
  private Long flowNodeInstanceKey;
  private String creationTime;
  private IncidentResult.State state;
  private Long jobKey;
  private String tenantId;

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
  public IncidentResult.ErrorType getErrorType() {
    return errorType;
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
  public IncidentResult.State getState() {
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

  public IncidentBuilder setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public IncidentBuilder setJobKey(final Long jobKey) {
    this.jobKey = jobKey;
    return this;
  }

  public IncidentBuilder setState(final IncidentResult.State state) {
    this.state = state;
    return this;
  }

  public IncidentBuilder setCreationTime(final String creationTime) {
    this.creationTime = creationTime;
    return this;
  }

  public IncidentBuilder setFlowNodeInstanceKey(final Long flowNodeInstanceKey) {
    this.flowNodeInstanceKey = flowNodeInstanceKey;
    return this;
  }

  public IncidentBuilder setFlowNodeId(final String flowNodeId) {
    this.flowNodeId = flowNodeId;
    return this;
  }

  public IncidentBuilder setErrorMessage(final String errorMessage) {
    this.errorMessage = errorMessage;
    return this;
  }

  public IncidentBuilder setErrorType(final IncidentResult.ErrorType errorType) {
    this.errorType = errorType;
    return this;
  }

  public IncidentBuilder setProcessInstanceKey(final Long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  public IncidentBuilder setProcessDefinitionId(final String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
    return this;
  }

  public IncidentBuilder setProcessDefinitionKey(final Long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  public IncidentBuilder setIncidentKey(final Long incidentKey) {
    this.incidentKey = incidentKey;
    return this;
  }

  public Incident build() {
    return this;
  }

  public static IncidentBuilder newActiveIncident(
      final IncidentResult.ErrorType errorType, final String errorMessage) {
    return new IncidentBuilder()
        .setErrorType(errorType)
        .setErrorMessage(errorMessage)
        .setState(IncidentResult.State.ACTIVE);
  }
}
