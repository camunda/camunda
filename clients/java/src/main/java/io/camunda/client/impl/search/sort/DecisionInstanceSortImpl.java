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
package io.camunda.client.impl.search.sort;

import io.camunda.client.api.search.sort.DecisionInstanceSort;
import io.camunda.client.impl.search.request.SearchRequestSortBase;

public class DecisionInstanceSortImpl extends SearchRequestSortBase<DecisionInstanceSort>
    implements DecisionInstanceSort {

  @Override
  protected DecisionInstanceSort self() {
    return this;
  }

  @Override
  public DecisionInstanceSort decisionInstanceKey() {
    return field("decisionInstanceKey");
  }

  @Override
  public DecisionInstanceSort decisionInstanceId() {
    return field("decisionInstanceId");
  }

  @Override
  public DecisionInstanceSort state() {
    return field("state");
  }

  @Override
  public DecisionInstanceSort evaluationDate() {
    return field("evaluationDate");
  }

  @Override
  public DecisionInstanceSort evaluationFailure() {
    return field("evaluationFailure");
  }

  @Override
  public DecisionInstanceSort processDefinitionKey() {
    return field("processDefinitionKey");
  }

  @Override
  public DecisionInstanceSort processInstanceKey() {
    return field("processInstanceKey");
  }

  @Override
  public DecisionInstanceSort elementInstanceKey() {
    return field("elementInstanceKey");
  }

  @Override
  public DecisionInstanceSort decisionDefinitionKey() {
    return field("decisionDefinitionKey");
  }

  @Override
  public DecisionInstanceSort decisionDefinitionId() {
    return field("decisionDefinitionId");
  }

  @Override
  public DecisionInstanceSort decisionDefinitionName() {
    return field("decisionDefinitionName");
  }

  @Override
  public DecisionInstanceSort decisionDefinitionVersion() {
    return field("decisionDefinitionVersion");
  }

  @Override
  public DecisionInstanceSort decisionDefinitionType() {
    return field("decisionDefinitionType");
  }

  @Override
  public DecisionInstanceSort rootDecisionDefinitionKey() {
    return field("rootDecisionDefinitionKey");
  }

  @Override
  public DecisionInstanceSort tenantId() {
    return field("tenantId");
  }
}
