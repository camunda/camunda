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
package io.camunda.client.api.search.filter;

import io.camunda.client.api.search.filter.builder.BasicLongProperty;
import io.camunda.client.api.search.filter.builder.DateTimeProperty;
import io.camunda.client.api.search.query.TypedSearchRequest.SearchRequestFilter;
import io.camunda.client.api.search.response.DecisionDefinitionType;
import io.camunda.client.api.search.response.DecisionInstanceState;
import java.time.OffsetDateTime;
import java.util.function.Consumer;

public interface DecisionInstanceFilter extends SearchRequestFilter {

  /** Filter by decisionInstanceKey */
  DecisionInstanceFilter decisionInstanceKey(long decisionInstanceKey);

  /** Filter by decisionInstanceId */
  DecisionInstanceFilter decisionInstanceId(String decisionInstanceId);

  /** Filter by state */
  DecisionInstanceFilter state(DecisionInstanceState state);

  /** Filter by evaluationFailure */
  DecisionInstanceFilter evaluationFailure(String evaluationFailure);

  /** Filter by evaluationDate */
  DecisionInstanceFilter evaluationDate(OffsetDateTime evaluationDate);

  /** Filter by evaluationDate using {@link DateTimeProperty} consumer */
  DecisionInstanceFilter evaluationDate(Consumer<DateTimeProperty> callback);

  /** Filter by processDefinitionKey */
  DecisionInstanceFilter processDefinitionKey(long processDefinitionKey);

  /** Filter by processInstanceKey */
  DecisionInstanceFilter processInstanceKey(long processInstanceKey);

  /** Filter by decisionDefinitionKey */
  DecisionInstanceFilter decisionDefinitionKey(long decisionDefinitionKey);

  /** Filter by decisionDefinitionKey using {@link BasicLongProperty} consumer */
  DecisionInstanceFilter decisionDefinitionKey(Consumer<BasicLongProperty> fn);

  /** Filter by decisionDefinitionId */
  DecisionInstanceFilter decisionDefinitionId(String decisionDefinitionId);

  /** Filter by decisionDefinitionName */
  DecisionInstanceFilter decisionDefinitionName(String decisionDefinitionName);

  /** Filter by decisionDefinitionVersion */
  DecisionInstanceFilter decisionDefinitionVersion(int decisionDefinitionVersion);

  /** Filter by decisionDefinitionType */
  DecisionInstanceFilter decisionDefinitionType(DecisionDefinitionType decisionDefinitionType);

  /** Filter by tenantId */
  DecisionInstanceFilter tenantId(String tenantId);
}
