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
import io.camunda.zeebe.client.impl.search.TypedSearchRequestPropertyProvider;
import io.camunda.zeebe.client.protocol.rest.DecisionInstanceFilterRequest;
import io.camunda.zeebe.client.protocol.rest.DecisionInstanceStateEnum;
import io.camunda.zeebe.client.protocol.rest.DecisionInstanceTypeEnum;

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
  public DecisionInstanceFilter state(final DecisionInstanceStateEnum state) {
    filter.setState(state);
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
  public DecisionInstanceFilter decisionKey(final long decisionKey) {
    filter.setDecisionDefinitionKey(decisionKey);
    return this;
  }

  @Override
  public DecisionInstanceFilter dmnDecisionId(final String dmnDecisionId) {
    filter.setDecisionDefinitionId(dmnDecisionId);
    return this;
  }

  @Override
  public DecisionInstanceFilter dmnDecisionName(final String dmnDecisionName) {
    filter.setDecisionDefinitionName(dmnDecisionName);
    return this;
  }

  @Override
  public DecisionInstanceFilter decisionVersion(final int decisionVersion) {
    filter.setDecisionDefinitionVersion(decisionVersion);
    return this;
  }

  @Override
  public DecisionInstanceFilter decisionType(final DecisionInstanceTypeEnum decisionType) {
    filter.setDecisionDefinitionType(decisionType);
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
