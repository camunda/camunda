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
import io.camunda.client.api.search.filter.builder.DateTimeProperty;
import io.camunda.client.api.search.filter.builder.IntegerProperty;
import io.camunda.client.api.search.filter.builder.LongProperty;
import io.camunda.client.api.search.filter.builder.StringProperty;
import io.camunda.client.api.search.response.DecisionDefinitionType;
import io.camunda.client.api.search.response.DecisionInstanceState;
import io.camunda.client.impl.search.TypedSearchRequestPropertyProvider;
import io.camunda.client.impl.search.filter.builder.DateTimePropertyImpl;
import io.camunda.client.impl.search.filter.builder.IntegerPropertyImpl;
import io.camunda.client.impl.search.filter.builder.LongPropertyImpl;
import io.camunda.client.impl.search.filter.builder.StringPropertyImpl;
import io.camunda.client.protocol.rest.DecisionDefinitionTypeEnum;
import io.camunda.client.protocol.rest.DecisionInstanceFilterRequest;
import io.camunda.client.protocol.rest.DecisionInstanceStateEnum;
import java.time.OffsetDateTime;
import java.util.function.Consumer;

public class DecisionInstanceFilterImpl
    extends TypedSearchRequestPropertyProvider<DecisionInstanceFilterRequest>
    implements DecisionInstanceFilter {

  private final DecisionInstanceFilterRequest filter;

  public DecisionInstanceFilterImpl() {
    filter = new DecisionInstanceFilterRequest();
  }

  @Override
  public DecisionInstanceFilter decisionInstanceKey(final long decisionInstanceKey) {
    decisionInstanceKey(b -> b.eq(decisionInstanceKey));
    return this;
  }

  @Override
  public DecisionInstanceFilter decisionInstanceKey(final Consumer<LongProperty> callback) {
    final LongPropertyImpl property = new LongPropertyImpl();
    callback.accept(property);
    filter.setDecisionInstanceKey(property.build());
    return this;
  }

  @Override
  public DecisionInstanceFilter decisionInstanceId(final String decisionInstanceId) {
    decisionInstanceId(b -> b.eq(decisionInstanceId));
    return this;
  }

  @Override
  public DecisionInstanceFilter decisionInstanceId(final Consumer<StringProperty> callback) {
    final StringPropertyImpl property = new StringPropertyImpl();
    callback.accept(property);
    filter.setDecisionInstanceId(property.build());
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
    evaluationFailure(b -> b.eq(evaluationFailure));
    return this;
  }

  @Override
  public DecisionInstanceFilter evaluationFailure(final Consumer<StringProperty> callback) {
    final StringPropertyImpl property = new StringPropertyImpl();
    callback.accept(property);
    filter.setEvaluationFailure(property.build());
    return this;
  }

  @Override
  public DecisionInstanceFilter evaluationDate(final OffsetDateTime evaluationDate) {
    evaluationDate(b -> b.eq(evaluationDate));
    return this;
  }

  @Override
  public DecisionInstanceFilter evaluationDate(final Consumer<DateTimeProperty> callback) {
    final DateTimePropertyImpl property = new DateTimePropertyImpl();
    callback.accept(property);
    filter.setEvaluationDate(property.build());
    return this;
  }

  @Override
  public DecisionInstanceFilter processDefinitionKey(final long processDefinitionKey) {
    processDefinitionKey(b -> b.eq(processDefinitionKey));
    return this;
  }

  @Override
  public DecisionInstanceFilter processDefinitionKey(final Consumer<LongProperty> callback) {
    final LongPropertyImpl property = new LongPropertyImpl();
    callback.accept(property);
    filter.setProcessDefinitionKey(property.build());
    return this;
  }

  @Override
  public DecisionInstanceFilter processInstanceKey(final long processInstanceKey) {
    processInstanceKey(b -> b.eq(processInstanceKey));
    return this;
  }

  @Override
  public DecisionInstanceFilter processInstanceKey(final Consumer<LongProperty> callback) {
    final LongPropertyImpl property = new LongPropertyImpl();
    callback.accept(property);
    filter.setProcessInstanceKey(property.build());
    return this;
  }

  @Override
  public DecisionInstanceFilter decisionDefinitionKey(final long decisionDefinitionKey) {
    decisionDefinitionKey(b -> b.eq(decisionDefinitionKey));
    return this;
  }

  @Override
  public DecisionInstanceFilter decisionDefinitionKey(final Consumer<LongProperty> callback) {
    final LongPropertyImpl property = new LongPropertyImpl();
    callback.accept(property);
    filter.setDecisionDefinitionKey(property.build());
    return this;
  }

  @Override
  public DecisionInstanceFilter decisionDefinitionId(final String decisionDefinitionId) {
    decisionDefinitionId(b -> b.eq(decisionDefinitionId));
    return this;
  }

  @Override
  public DecisionInstanceFilter decisionDefinitionId(final Consumer<StringProperty> callback) {
    final StringPropertyImpl property = new StringPropertyImpl();
    callback.accept(property);
    filter.setDecisionDefinitionId(property.build());
    return this;
  }

  @Override
  public DecisionInstanceFilter decisionDefinitionName(final String decisionDefinitionName) {
    decisionDefinitionName(b -> b.eq(decisionDefinitionName));
    return this;
  }

  @Override
  public DecisionInstanceFilter decisionDefinitionName(final Consumer<StringProperty> callback) {
    final StringPropertyImpl property = new StringPropertyImpl();
    callback.accept(property);
    filter.setDecisionDefinitionName(property.build());
    return this;
  }

  @Override
  public DecisionInstanceFilter decisionDefinitionVersion(final int decisionDefinitionVersion) {
    decisionDefinitionVersion(b -> b.eq(decisionDefinitionVersion));
    return this;
  }

  @Override
  public DecisionInstanceFilter decisionDefinitionVersion(
      final Consumer<IntegerProperty> callback) {
    final IntegerPropertyImpl property = new IntegerPropertyImpl();
    callback.accept(property);
    filter.setDecisionDefinitionVersion(property.build());
    return this;
  }

  @Override
  public DecisionInstanceFilter decisionDefinitionType(
      final DecisionDefinitionType decisionDefinitionType) {
    final DecisionDefinitionTypeEnum typeEnum;
    switch (decisionDefinitionType) {
      case DECISION_TABLE:
        typeEnum = DecisionDefinitionTypeEnum.DECISION_TABLE;
        break;
      case LITERAL_EXPRESSION:
        typeEnum = DecisionDefinitionTypeEnum.LITERAL_EXPRESSION;
        break;
      case UNSPECIFIED:
        typeEnum = DecisionDefinitionTypeEnum.UNSPECIFIED;
        break;
      case UNKNOWN:
        typeEnum = DecisionDefinitionTypeEnum.UNKNOWN;
        break;
      default:
        throw new IllegalArgumentException(
            "Unexpected DecisionDefinitionType value: " + decisionDefinitionType);
    }
    filter.setDecisionDefinitionType(typeEnum);
    return this;
  }

  @Override
  public DecisionInstanceFilter tenantId(final String tenantId) {
    tenantId(b -> b.eq(tenantId));
    return this;
  }

  @Override
  public DecisionInstanceFilter tenantId(final Consumer<StringProperty> callback) {
    final StringPropertyImpl property = new StringPropertyImpl();
    callback.accept(property);
    filter.setTenantId(property.build());
    return this;
  }

  @Override
  protected DecisionInstanceFilterRequest getSearchRequestProperty() {
    return filter;
  }
}
