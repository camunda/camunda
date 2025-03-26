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
package io.camunda.client.api.search.sort;

import io.camunda.client.api.search.query.TypedSearchRequest.SearchRequestSort;

public interface DecisionInstanceSort extends SearchRequestSort<DecisionInstanceSort> {

  /** Sort by decisionInstanceKey */
  DecisionInstanceSort decisionInstanceKey();

  /** Sort by decisionInstanceId */
  DecisionInstanceSort decisionInstanceId();

  /** Sort by state */
  DecisionInstanceSort state();

  /** Sort by evaluationDate */
  DecisionInstanceSort evaluationDate();

  /** Sort by evaluationFailure */
  DecisionInstanceSort evaluationFailure();

  /** Sort by processDefinitionKey */
  DecisionInstanceSort processDefinitionKey();

  /** Sort by processInstanceKey */
  DecisionInstanceSort processInstanceId();

  /** Sort by decisionDefinitionKey */
  DecisionInstanceSort decisionDefinitionKey();

  /** Sort by decisionDefinitionId */
  DecisionInstanceSort decisionDefinitionId();

  /** Sort by dmnDecisionName */
  DecisionInstanceSort decisionDefinitionName();

  /** Sort by decisionVersion */
  DecisionInstanceSort decisionDefinitionVersion();

  /** Sort by decisionType */
  DecisionInstanceSort decisionDefinitionType();

  /** Sort by tenantId */
  DecisionInstanceSort tenantId();
}
