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

/**
 * @deprecated since 8.8 for removal in 8.9, replaced by {@link
 *     io.camunda.client.api.search.filter.DecisionDefinitionFilter}
 */
@Deprecated
public interface DecisionDefinitionFilter extends SearchRequestFilter {

  /** Filter by decision key. */
  DecisionDefinitionFilter decisionKey(final Long value);

  /** Filter by dmn decision id. */
  DecisionDefinitionFilter dmnDecisionId(final String value);

  /** Filter by dmn decision name. */
  DecisionDefinitionFilter dmnDecisionName(final String value);

  /** Filter by version. */
  DecisionDefinitionFilter version(final Integer value);

  /** Filter by dmn decision requirements id. */
  DecisionDefinitionFilter dmnDecisionRequirementsId(final String value);

  /** Filter by decision requirements key. */
  DecisionDefinitionFilter decisionRequirementsKey(final Long value);

  /** Filter by tenant id. */
  DecisionDefinitionFilter tenantId(final String value);
}
