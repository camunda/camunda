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
package io.camunda.client.impl.search.filter;

import io.camunda.client.api.search.filter.DecisionInstanceFilter;
import io.camunda.client.api.search.filter.builder.BasicLongProperty;
import io.camunda.client.api.search.filter.builder.BasicStringProperty;
import io.camunda.client.api.search.filter.builder.DateTimeProperty;
import io.camunda.client.api.search.filter.builder.DecisionInstanceStateProperty;
import io.camunda.client.api.search.response.DecisionDefinitionType;
import io.camunda.client.api.search.response.DecisionInstanceState;
import io.camunda.client.impl.search.filter.builder.BasicLongPropertyImpl;
import io.camunda.client.impl.search.filter.builder.BasicStringPropertyImpl;
import io.camunda.client.impl.search.filter.builder.DateTimePropertyImpl;
import io.camunda.client.impl.search.filter.builder.DecisionInstanceStatePropertyImpl;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import io.camunda.client.impl.util.ParseUtil;
import io.camunda.client.protocol.rest.DecisionDefinitionTypeEnum;
import java.time.OffsetDateTime;
import java.util.function.Consumer;

public class DecisionInstanceFilterImpl
    extends TypedSearchRequestPropertyProvider<
        io.camunda.client.protocol.rest.DecisionInstanceFilter>
    implements DecisionInstanceFilter {

  private final io.camunda.client.protocol.rest.DecisionInstanceFilter filter;

  public DecisionInstanceFilterImpl() {
    filter = new io.camunda.client.protocol.rest.DecisionInstanceFilter();
  }

  @Override
  public DecisionInstanceFilter decisionInstanceKey(final long decisionInstanceKey) {
    filter.setDecisionEvaluationKey(ParseUtil.keyToString(decisionInstanceKey));
    return this;
  }

  @Override
  public DecisionInstanceFilter decisionInstanceId(final String decisionInstanceId) {
    return decisionInstanceId(b -> b.eq(decisionInstanceId));
  }

  @Override
  public DecisionInstanceFilter decisionInstanceId(final Consumer<BasicStringProperty> fn) {
    final BasicStringProperty property = new BasicStringPropertyImpl();
    fn.accept(property);
    filter.setDecisionEvaluationInstanceKey(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public DecisionInstanceFilter state(final DecisionInstanceState state) {
    return state(b -> b.eq(state));
  }

  @Override
  public DecisionInstanceFilter state(final Consumer<DecisionInstanceStateProperty> fn) {
    final DecisionInstanceStateProperty property = new DecisionInstanceStatePropertyImpl();
    fn.accept(property);
    filter.setState(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public DecisionInstanceFilter evaluationFailure(final String evaluationFailure) {
    filter.setEvaluationFailure(evaluationFailure);
    return this;
  }

  @Override
  public DecisionInstanceFilter evaluationDate(final OffsetDateTime evaluationDate) {
    return evaluationDate(b -> b.eq(evaluationDate));
  }

  @Override
  public DecisionInstanceFilter evaluationDate(final Consumer<DateTimeProperty> fn) {
    final DateTimeProperty property = new DateTimePropertyImpl();
    fn.accept(property);
    filter.setEvaluationDate(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public DecisionInstanceFilter processDefinitionKey(final long processDefinitionKey) {
    filter.setProcessDefinitionKey(ParseUtil.keyToString(processDefinitionKey));
    return this;
  }

  @Override
  public DecisionInstanceFilter processInstanceKey(final long processInstanceKey) {
    filter.setProcessInstanceKey(ParseUtil.keyToString(processInstanceKey));
    return this;
  }

  @Override
  public DecisionInstanceFilter elementInstanceKey(final long elementInstanceKey) {
    return elementInstanceKey(b -> b.eq(elementInstanceKey));
  }

  @Override
  public DecisionInstanceFilter elementInstanceKey(final Consumer<BasicLongProperty> fn) {
    final BasicLongProperty property = new BasicLongPropertyImpl();
    fn.accept(property);
    filter.setElementInstanceKey(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public DecisionInstanceFilter rootDecisionDefinitionKey(final long rootDecisionDefinitionKey) {
    return rootDecisionDefinitionKey(b -> b.eq(rootDecisionDefinitionKey));
  }

  @Override
  public DecisionInstanceFilter rootDecisionDefinitionKey(final Consumer<BasicLongProperty> fn) {
    final BasicLongProperty property = new BasicLongPropertyImpl();
    fn.accept(property);
    filter.setRootDecisionDefinitionKey(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public DecisionInstanceFilter decisionDefinitionKey(final long decisionDefinitionKey) {
    return decisionDefinitionKey(b -> b.eq(decisionDefinitionKey));
  }

  @Override
  public DecisionInstanceFilter decisionDefinitionKey(final Consumer<BasicLongProperty> fn) {
    final BasicLongProperty property = new BasicLongPropertyImpl();
    fn.accept(property);
    filter.setDecisionDefinitionKey(provideSearchRequestProperty(property));
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
  protected io.camunda.client.protocol.rest.DecisionInstanceFilter getSearchRequestProperty() {
    return filter;
  }
}
