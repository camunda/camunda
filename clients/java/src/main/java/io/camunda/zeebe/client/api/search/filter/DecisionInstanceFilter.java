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
import io.camunda.zeebe.client.protocol.rest.DecisionInstanceStateEnum;
import io.camunda.zeebe.client.protocol.rest.DecisionInstanceTypeEnum;

public interface DecisionInstanceFilter extends SearchRequestFilter {

  /** Filter by decisionInstanceKey */
  DecisionInstanceFilter decisionInstanceKey(long decisionInstanceKey);

  /** Filter by state */
  DecisionInstanceFilter state(DecisionInstanceStateEnum state);

  /** Filter by evaluationFailure */
  DecisionInstanceFilter evaluationFailure(String evaluationFailure);

  /** Filter by processDefinitionKey */
  DecisionInstanceFilter processDefinitionKey(long processDefinitionKey);

  /** Filter by processInstanceKey */
  DecisionInstanceFilter processInstanceKey(long processInstanceKey);

  /** Filter by decisionKey */
  DecisionInstanceFilter decisionKey(long decisionKey);

  /** Filter by dmnDecisionId */
  DecisionInstanceFilter dmnDecisionId(String dmnDecisionId);

  /** Filter by dmnDecisionName */
  DecisionInstanceFilter dmnDecisionName(String dmnDecisionName);

  /** Filter by decisionVersion */
  DecisionInstanceFilter decisionVersion(int decisionVersion);

  /** Filter by decisionType */
  DecisionInstanceFilter decisionType(DecisionInstanceTypeEnum decisionType);

  /** Filter by tenantId */
  DecisionInstanceFilter tenantId(String tenantId);
}
