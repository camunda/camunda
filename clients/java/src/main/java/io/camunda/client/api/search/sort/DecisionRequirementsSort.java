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

public interface DecisionRequirementsSort extends SearchRequestSort<DecisionRequirementsSort> {
  /** Sort by decision requirements key. */
  DecisionRequirementsSort decisionRequirementsKey();

  /** Sort by decision requirements name. */
  DecisionRequirementsSort decisionRequirementsName();

  /** Sort by decision requirement version. */
  DecisionRequirementsSort version();

  /** Sort by decision requirements id. */
  DecisionRequirementsSort decisionRequirementsId();

  /** Sort by resource name. */
  DecisionRequirementsSort tenantId();
}
