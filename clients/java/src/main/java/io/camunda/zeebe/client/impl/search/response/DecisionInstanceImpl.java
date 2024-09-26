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

import io.camunda.zeebe.client.api.search.response.DecisionInstance;
import io.camunda.zeebe.client.protocol.rest.DecisionInstanceItem;
import io.camunda.zeebe.client.protocol.rest.DecisionInstanceStateEnum;
import io.camunda.zeebe.client.protocol.rest.DecisionInstanceTypeEnum;
import java.util.Objects;

public class DecisionInstanceImpl implements DecisionInstance {

  private final long decisionInstanceKey;
  private final DecisionInstanceStateEnum state;
  private final String evaluationDate;
  private final String evaluationFailure;
  private final Long processDefinitionKey;
  private final Long processInstanceKey;
  private final long decisionKey;
  private final String dmnDecisionId;
  private final String dmnDecisionName;
  private final int decisionVersion;
  private final DecisionInstanceTypeEnum decisionType;
  private final String tenantId;

  public DecisionInstanceImpl(final DecisionInstanceItem item) {
    this(
        item.getDecisionInstanceKey(),
        item.getState(),
        item.getEvaluationDate(),
        item.getEvaluationFailure(),
        item.getProcessDefinitionKey(),
        item.getProcessInstanceKey(),
        item.getDecisionDefinitionKey(),
        item.getDmnDecisionId(),
        item.getDmnDecisionName(),
        item.getDecisionVersion(),
        item.getDecisionType(),
        item.getTenantId());
  }

  public DecisionInstanceImpl(
      final long decisionInstanceKey,
      final DecisionInstanceStateEnum state,
      final String evaluationDate,
      final String evaluationFailure,
      final Long processDefinitionKey,
      final Long processInstanceKey,
      final long decisionKey,
      final String dmnDecisionId,
      final String dmnDecisionName,
      final int decisionVersion,
      final DecisionInstanceTypeEnum decisionType,
      final String tenantId) {
    this.decisionInstanceKey = decisionInstanceKey;
    this.state = state;
    this.evaluationDate = evaluationDate;
    this.evaluationFailure = evaluationFailure;
    this.processDefinitionKey = processDefinitionKey;
    this.processInstanceKey = processInstanceKey;
    this.decisionKey = decisionKey;
    this.dmnDecisionId = dmnDecisionId;
    this.dmnDecisionName = dmnDecisionName;
    this.decisionVersion = decisionVersion;
    this.decisionType = decisionType;
    this.tenantId = tenantId;
  }

  @Override
  public long getDecisionInstanceKey() {
    return decisionInstanceKey;
  }

  @Override
  public DecisionInstanceStateEnum getState() {
    return state;
  }

  @Override
  public String getEvaluationDate() {
    return evaluationDate;
  }

  @Override
  public String getEvaluationFailure() {
    return evaluationFailure;
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
  public long getDecisionKey() {
    return decisionKey;
  }

  @Override
  public String getDmnDecisionId() {
    return dmnDecisionId;
  }

  @Override
  public String getDmnDecisionName() {
    return dmnDecisionName;
  }

  @Override
  public int getDecisionVersion() {
    return decisionVersion;
  }

  @Override
  public DecisionInstanceTypeEnum getDecisionType() {
    return decisionType;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        decisionInstanceKey,
        state,
        evaluationDate,
        evaluationFailure,
        processDefinitionKey,
        processInstanceKey,
        decisionKey,
        dmnDecisionId,
        dmnDecisionName,
        decisionVersion,
        decisionType,
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
    final DecisionInstanceImpl that = (DecisionInstanceImpl) o;
    return Objects.equals(decisionInstanceKey, that.decisionInstanceKey)
        && state == that.state
        && Objects.equals(evaluationDate, that.evaluationDate)
        && Objects.equals(evaluationFailure, that.evaluationFailure)
        && Objects.equals(processDefinitionKey, that.processDefinitionKey)
        && Objects.equals(processInstanceKey, that.processInstanceKey)
        && Objects.equals(decisionKey, that.decisionKey)
        && Objects.equals(dmnDecisionId, that.dmnDecisionId)
        && Objects.equals(dmnDecisionName, that.dmnDecisionName)
        && Objects.equals(decisionVersion, that.decisionVersion)
        && decisionType == that.decisionType
        && Objects.equals(tenantId, that.tenantId);
  }
}
