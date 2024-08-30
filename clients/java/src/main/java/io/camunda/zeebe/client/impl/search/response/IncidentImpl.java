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

import io.camunda.zeebe.client.api.search.response.DecisionInstanceReference;
import io.camunda.zeebe.client.api.search.response.Incident;
import io.camunda.zeebe.client.api.search.response.Operation;
import io.camunda.zeebe.client.api.search.response.ProcessInstanceReference;
import io.camunda.zeebe.client.protocol.rest.IncidentItem;

public class IncidentImpl implements Incident {

  private final Long key;
  private final Long processDefinitionKey;
  private final Long processInstanceKey;
  private final String type;
  private final String flowNodeId;
  private final String flowNodeInstanceId;
  private final String creationTime;
  private final String state;
  private final Long jobKey;
  private final Boolean hasActiveOperation;
  private Operation operation;
  private ProcessInstanceReference processInstanceReference;
  private DecisionInstanceReference decisionInstanceReference;

  public IncidentImpl(final IncidentItem item) {
    key = item.getKey();
    processDefinitionKey = item.getProcessDefinitionKey();
    processInstanceKey = item.getProcessInstanceKey();
    type = item.getType();
    flowNodeId = item.getFlowNodeId();
    flowNodeInstanceId = item.getFlowNodeInstanceId();
    creationTime = item.getCreationTime();
    state = item.getState();
    jobKey = item.getJobKey();
    hasActiveOperation = item.getHasActiveOperation();
    if (item.getLastOperation() != null) {
      operation = new OperationImpl(item.getLastOperation());
    }
    if (item.getRootCauseInstance() != null) {
      processInstanceReference = new ProcessInstanceReferenceImpl(item.getRootCauseInstance());
    }
    if (item.getRootCauseDecision() != null) {
      decisionInstanceReference = new DecisionInstanceReferenceImpl(item.getRootCauseDecision());
    }
  }

  @Override
  public Long getKey() {
    return key;
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
  public String getType() {
    return type;
  }

  @Override
  public String getFlowNodeId() {
    return flowNodeId;
  }

  @Override
  public String getFlowNodeInstanceId() {
    return flowNodeInstanceId;
  }

  @Override
  public String getCreationTime() {
    return creationTime;
  }

  @Override
  public String getState() {
    return state;
  }

  @Override
  public Long getJobKey() {
    return jobKey;
  }

  @Override
  public Boolean getHasActiveOperation() {
    return hasActiveOperation;
  }

  @Override
  public Operation getOperation() {
    return operation;
  }

  @Override
  public ProcessInstanceReference getProcessInstanceReference() {
    return processInstanceReference;
  }

  @Override
  public DecisionInstanceReference getDecisionInstanceReference() {
    return decisionInstanceReference;
  }
}
