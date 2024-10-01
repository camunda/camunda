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
package io.camunda.zeebe.client.api.search.filter;

import io.camunda.zeebe.client.api.search.query.TypedSearchQueryRequest.SearchRequestFilter;
import io.camunda.zeebe.client.api.search.response.DecisionDefinitionType;
import io.camunda.zeebe.client.api.search.response.DecisionInstanceState;

public interface DecisionInstanceFilter extends SearchRequestFilter {

  /** Filter by decisionInstanceKey */
  DecisionInstanceFilter decisionInstanceKey(long decisionInstanceKey);

  /** Filter by state */
  DecisionInstanceFilter state(DecisionInstanceState state);

  /** Filter by evaluationFailure */
  DecisionInstanceFilter evaluationFailure(String evaluationFailure);

  /** Filter by processDefinitionKey */
  DecisionInstanceFilter processDefinitionKey(long processDefinitionKey);

  /** Filter by processInstanceKey */
  DecisionInstanceFilter processInstanceKey(long processInstanceKey);

  /** Filter by decisionDefinitionKey */
  DecisionInstanceFilter decisionDefinitionKey(long decisionDefinitionKey);

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
