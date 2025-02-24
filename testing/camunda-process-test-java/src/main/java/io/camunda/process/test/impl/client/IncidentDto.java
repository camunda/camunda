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

import io.camunda.client.api.search.response.Incident;
import io.camunda.client.api.search.response.IncidentErrorType;
import io.camunda.client.api.search.response.IncidentState;

public class IncidentDto implements Incident {

  private long incidentKey;
  private IncidentErrorType errorType;
  private String errorMessage;

  @Override
  public Long getIncidentKey() {
    return incidentKey;
  }

  public void setIncidentKey(final long incidentKey) {
    this.incidentKey = incidentKey;
  }

  @Override
  public Long getProcessDefinitionKey() {
    return 0L;
  }

  @Override
  public String getProcessDefinitionId() {
    return "";
  }

  @Override
  public Long getProcessInstanceKey() {
    return 0L;
  }

  @Override
  public IncidentErrorType getErrorType() {
    return errorType;
  }

  public void setErrorType(final IncidentErrorType errorType) {
    this.errorType = errorType;
  }

  @Override
  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(final String errorMessage) {
    this.errorMessage = errorMessage;
  }

  @Override
  public String getFlowNodeId() {
    return "";
  }

  @Override
  public Long getFlowNodeInstanceKey() {
    return 0L;
  }

  @Override
  public String getCreationTime() {
    return "";
  }

  @Override
  public IncidentState getState() {
    return null;
  }

  @Override
  public Long getJobKey() {
    return 0L;
  }

  @Override
  public String getTenantId() {
    return "";
  }
}
