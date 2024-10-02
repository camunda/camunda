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
package io.camunda.zeebe.client.impl.search.sort;

import io.camunda.zeebe.client.api.search.sort.DecisionRequirementsSort;
import io.camunda.zeebe.client.impl.search.query.SearchQuerySortBase;

public class DecisionRequirementsSortImpl extends SearchQuerySortBase<DecisionRequirementsSort>
    implements DecisionRequirementsSort {

  @Override
  protected DecisionRequirementsSort self() {
    return this;
  }

  @Override
  public DecisionRequirementsSort decisionRequirementsKey() {
    return field("decisionRequirementsKey");
  }

  @Override
  public DecisionRequirementsSort name() {
    return field("name");
  }

  @Override
  public DecisionRequirementsSort version() {
    return field("version");
  }

  @Override
  public DecisionRequirementsSort decisionRequirementsId() {
    return field("decisionRequirementsId");
  }

  @Override
  public DecisionRequirementsSort tenantId() {
    return field("tenantId");
  }
}