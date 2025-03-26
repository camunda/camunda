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

import io.camunda.client.api.search.request.TypedSearchRequest.SearchRequestFilter;

public interface DecisionDefinitionFilter extends SearchRequestFilter {

  /** Filter by decision key. */
  DecisionDefinitionFilter decisionDefinitionKey(final long value);

  /** Filter by dmn decision id. */
  DecisionDefinitionFilter decisionDefinitionId(final String value);

  /** Filter by dmn decision name. */
  DecisionDefinitionFilter name(final String value);

  /** Filter by version. */
  DecisionDefinitionFilter version(final int value);

  /** Filter by dmn decision requirements id. */
  DecisionDefinitionFilter decisionRequirementsId(final String value);

  /** Filter by decision requirements key. */
  DecisionDefinitionFilter decisionRequirementsKey(final long value);

  /** Filter by tenant id. */
  DecisionDefinitionFilter tenantId(final String value);
}
