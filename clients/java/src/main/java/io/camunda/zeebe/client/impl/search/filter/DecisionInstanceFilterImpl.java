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
package io.camunda.zeebe.client.impl.search.filter;

import io.camunda.zeebe.client.api.search.filter.DecisionInstanceFilter;
import io.camunda.zeebe.client.api.search.response.DecisionDefinitionType;
import io.camunda.zeebe.client.api.search.response.DecisionInstanceState;
import io.camunda.zeebe.client.impl.search.TypedSearchRequestPropertyProvider;
import io.camunda.zeebe.client.protocol.rest.DecisionDefinitionTypeEnum;
import io.camunda.zeebe.client.protocol.rest.DecisionInstanceFilterRequest;
import io.camunda.zeebe.client.protocol.rest.DecisionInstanceStateEnum;

public class DecisionInstanceFilterImpl
    extends TypedSearchRequestPropertyProvider<DecisionInstanceFilterRequest>
    implements DecisionInstanceFilter {

  private final DecisionInstanceFilterRequest filter;

  public DecisionInstanceFilterImpl() {
    filter = new DecisionInstanceFilterRequest();
  }

  @Override
  public DecisionInstanceFilter decisionInstanceKey(final long decisionInstanceKey) {
    filter.setDecisionInstanceKey(decisionInstanceKey);
    return this;
  }

  @Override
  public DecisionInstanceFilter state(final DecisionInstanceState state) {
    final DecisionInstanceStateEnum stateEnum;
    switch (state) {
      case EVALUATED:
        stateEnum = DecisionInstanceStateEnum.EVALUATED;
        break;
      case FAILED:
        stateEnum = DecisionInstanceStateEnum.FAILED;
        break;
      case UNSPECIFIED:
        stateEnum = DecisionInstanceStateEnum.UNSPECIFIED;
        break;
      case UNKNOWN:
        stateEnum = DecisionInstanceStateEnum.UNKNOWN;
        break;
      default:
        throw new IllegalArgumentException("Unexpected DecisionInstanceState value: " + state);
    }
    filter.setState(stateEnum);
    return this;
  }

  @Override
  public DecisionInstanceFilter evaluationFailure(final String evaluationFailure) {
    filter.setEvaluationFailure(evaluationFailure);
    return this;
  }

  @Override
  public DecisionInstanceFilter processDefinitionKey(final long processDefinitionKey) {
    filter.setProcessDefinitionKey(processDefinitionKey);
    return this;
  }

  @Override
  public DecisionInstanceFilter processInstanceKey(final long processInstanceKey) {
    filter.setProcessInstanceKey(processInstanceKey);
    return this;
  }

  @Override
  public DecisionInstanceFilter decisionDefinitionKey(final long decisionDefinitionKey) {
    filter.setDecisionDefinitionKey(decisionDefinitionKey);
    return this;
  }

  @Override
  public DecisionInstanceFilter decisionDefinitionId(final String decisionDefinitionId) {
    filter.setDecisionDefinitionId(decisionDefinitionId);
    return this;
  }

  @Override
  public DecisionInstanceFilter decisionDefinitionName(final String decisionDefinitionName) {
    filter.setDecisionDefinitionName(decisionDefinitionName);
    return this;
  }

  @Override
  public DecisionInstanceFilter decisionDefinitionVersion(final int decisionDefinitionVersion) {
    filter.setDecisionDefinitionVersion(decisionDefinitionVersion);
    return this;
  }

  @Override
  public DecisionInstanceFilter decisionDefinitionType(
      final DecisionDefinitionType decisionDefinitionType) {
    final DecisionDefinitionTypeEnum decisionDefinitionTypeEnum;
    switch (decisionDefinitionType) {
      case DECISION_TABLE:
        decisionDefinitionTypeEnum = DecisionDefinitionTypeEnum.DECISION_TABLE;
        break;
      case LITERAL_EXPRESSION:
        decisionDefinitionTypeEnum = DecisionDefinitionTypeEnum.LITERAL_EXPRESSION;
        break;
      case UNSPECIFIED:
        decisionDefinitionTypeEnum = DecisionDefinitionTypeEnum.UNSPECIFIED;
        break;
      case UNKNOWN:
        decisionDefinitionTypeEnum = DecisionDefinitionTypeEnum.UNKNOWN;
        break;
      default:
        throw new IllegalArgumentException(
            "Unexpected DecisionDefinitionType value: " + decisionDefinitionType);
    }
    filter.setDecisionDefinitionType(decisionDefinitionTypeEnum);
    return this;
  }

  @Override
  public DecisionInstanceFilter tenantId(final String tenantId) {
    filter.setTenantId(tenantId);
    return this;
  }

  @Override
  protected DecisionInstanceFilterRequest getSearchRequestProperty() {
    return filter;
  }
}
