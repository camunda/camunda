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

import io.camunda.zeebe.client.api.search.response.DecisionDefinitionType;
import io.camunda.zeebe.client.api.search.response.DecisionInstance;
import io.camunda.zeebe.client.api.search.response.DecisionInstanceState;
import io.camunda.zeebe.client.protocol.rest.DecisionDefinitionTypeEnum;
import io.camunda.zeebe.client.protocol.rest.DecisionInstanceItem;
import io.camunda.zeebe.client.protocol.rest.DecisionInstanceStateEnum;
import java.util.Objects;

public class DecisionInstanceImpl implements DecisionInstance {

  private final long decisionInstanceKey;
  private final DecisionInstanceState state;
  private final String evaluationDate;
  private final String evaluationFailure;
  private final Long processDefinitionKey;
  private final Long processInstanceKey;
  private final long decisionKey;
  private final String decisionDefinitionId;
  private final String decisionDefinitionName;
  private final int decisionDefinitionVersion;
  private final DecisionDefinitionType decisionDefinitionType;
  private final String tenantId;

  public DecisionInstanceImpl(final DecisionInstanceItem item) {
    this(
        item.getDecisionInstanceKey(),
        toDecisionInstanceState(item.getState()),
        item.getEvaluationDate(),
        item.getEvaluationFailure(),
        item.getProcessDefinitionKey(),
        item.getProcessInstanceKey(),
        item.getDecisionDefinitionKey(),
        item.getDecisionDefinitionId(),
        item.getDecisionDefinitionName(),
        item.getDecisionDefinitionVersion(),
        toDecisionDefinitionType(item.getDecisionDefinitionType()),
        item.getTenantId());
  }

  public DecisionInstanceImpl(
      final long decisionInstanceKey,
      final DecisionInstanceState state,
      final String evaluationDate,
      final String evaluationFailure,
      final Long processDefinitionKey,
      final Long processInstanceKey,
      final long decisionKey,
      final String decisionDefinitionId,
      final String decisionDefinitionName,
      final int decisionDefinitionVersion,
      final DecisionDefinitionType decisionDefinitionType,
      final String tenantId) {
    this.decisionInstanceKey = decisionInstanceKey;
    this.state = state;
    this.evaluationDate = evaluationDate;
    this.evaluationFailure = evaluationFailure;
    this.processDefinitionKey = processDefinitionKey;
    this.processInstanceKey = processInstanceKey;
    this.decisionKey = decisionKey;
    this.decisionDefinitionId = decisionDefinitionId;
    this.decisionDefinitionName = decisionDefinitionName;
    this.decisionDefinitionVersion = decisionDefinitionVersion;
    this.decisionDefinitionType = decisionDefinitionType;
    this.tenantId = tenantId;
  }

  private static DecisionDefinitionType toDecisionDefinitionType(
      final DecisionDefinitionTypeEnum decisionDefinitionType) {
    if (decisionDefinitionType == null) {
      return null;
    }
    switch (decisionDefinitionType) {
      case DECISION_TABLE:
        return DecisionDefinitionType.DECISION_TABLE;
      case LITERAL_EXPRESSION:
        return DecisionDefinitionType.LITERAL_EXPRESSION;
      case UNSPECIFIED:
        return DecisionDefinitionType.UNSPECIFIED;
      case UNKNOWN:
      case UNKNOWN_DEFAULT_OPEN_API:
      default:
        return DecisionDefinitionType.UNKNOWN;
    }
  }

  private static DecisionInstanceState toDecisionInstanceState(
      final DecisionInstanceStateEnum decisionInstanceState) {
    if (decisionInstanceState == null) {
      return null;
    }
    switch (decisionInstanceState) {
      case EVALUATED:
        return DecisionInstanceState.EVALUATED;
      case FAILED:
        return DecisionInstanceState.FAILED;
      case UNSPECIFIED:
        return DecisionInstanceState.UNSPECIFIED;
      case UNKNOWN:
      case UNKNOWN_DEFAULT_OPEN_API:
      default:
        return DecisionInstanceState.UNKNOWN;
    }
  }

  @Override
  public long getDecisionInstanceKey() {
    return decisionInstanceKey;
  }

  @Override
  public DecisionInstanceState getState() {
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
  public long getDecisionDefinitionKey() {
    return decisionKey;
  }

  @Override
  public String getDecisionDefinitionId() {
    return decisionDefinitionId;
  }

  @Override
  public String getDecisionDefinitionName() {
    return decisionDefinitionName;
  }

  @Override
  public int getDecisionDefinitionVersion() {
    return decisionDefinitionVersion;
  }

  @Override
  public DecisionDefinitionType getDecisionDefinitionType() {
    return decisionDefinitionType;
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
        decisionDefinitionId,
        decisionDefinitionName,
        decisionDefinitionVersion,
        decisionDefinitionType,
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
        && Objects.equals(decisionDefinitionId, that.decisionDefinitionId)
        && Objects.equals(decisionDefinitionName, that.decisionDefinitionName)
        && Objects.equals(decisionDefinitionVersion, that.decisionDefinitionVersion)
        && decisionDefinitionType == that.decisionDefinitionType
        && Objects.equals(tenantId, that.tenantId);
  }
}
