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
 * Interface for defining Decision Requirmeent in search queries.
 *
 * @deprecated since 8.8 for removal in 8.9, replaced by {@link
 *     io.camunda.client.api.search.filter.DecisionRequirementsFilter}
 */
@Deprecated
public interface DecisionRequirementsFilter extends SearchRequestFilter {

  /**
   * Filters Decision Requirement by the specified key.
   *
   * @param value the key of the decision requirement
   * @return the updated filter
   */
  DecisionRequirementsFilter decisionRequirementsKey(final Long value);

  /**
   * Filters Decision Requirement by the specified name.
   *
   * @param name the name of the decision requirement
   * @return the updated filter
   */
  DecisionRequirementsFilter dmnDecisionRequirementsName(final String name);

  /**
   * Filters Decision Requirement by the specified version.
   *
   * @param version the version of the decision requirement
   * @return the updated filter
   */
  DecisionRequirementsFilter version(final Integer version);

  /**
   * Filters Decision Requirement by the specified decision requirements ID.
   *
   * @param decisionRequirementsId the decision requirements ID of the decision requirement
   * @return the updated filter
   */
  DecisionRequirementsFilter dmnDecisionRequirementsId(final String decisionRequirementsId);

  /**
   * Filters Decision Requirement by the specified tenant ID.
   *
   * @param tenantId the tenant ID of the decision requirement
   * @return the updated filter
   */
  DecisionRequirementsFilter tenantId(final String tenantId);
}
