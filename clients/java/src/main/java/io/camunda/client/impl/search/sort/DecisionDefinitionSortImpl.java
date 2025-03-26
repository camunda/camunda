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

import io.camunda.client.api.search.sort.DecisionDefinitionSort;
import io.camunda.client.impl.search.query.SearchRequestSortBase;

public class DecisionDefinitionSortImpl extends SearchRequestSortBase<DecisionDefinitionSort>
    implements DecisionDefinitionSort {

  @Override
  public DecisionDefinitionSort decisionDefinitionKey() {
    return field("decisionDefinitionKey");
  }

  @Override
  public DecisionDefinitionSort decisionDefinitionId() {
    return field("decisionDefinitionId");
  }

  @Override
  public DecisionDefinitionSort name() {
    return field("name");
  }

  @Override
  public DecisionDefinitionSort version() {
    return field("version");
  }

  @Override
  public DecisionDefinitionSort decisionRequirementsId() {
    return field("decisionRequirementsId");
  }

  @Override
  public DecisionDefinitionSort decisionRequirementsKey() {
    return field("decisionRequirementsKey");
  }

  @Override
  public DecisionDefinitionSort tenantId() {
    return field("tenantId");
  }

  @Override
  protected DecisionDefinitionSort self() {
    return this;
  }
}
