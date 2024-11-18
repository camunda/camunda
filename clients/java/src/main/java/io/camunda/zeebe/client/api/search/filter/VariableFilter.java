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
import io.camunda.zeebe.client.protocol.rest.LongFilterProperty;
import io.camunda.zeebe.client.protocol.rest.StringFilterProperty;

public interface VariableFilter extends SearchRequestFilter {

  /**
   * Filters variables by the specified key.
   *
   * @param key the key of the variable
   * @return the updated filter
   */
  VariableFilter variableKey(final Long key);

  /**
   * Filters variables by the specified value.
   *
   * @param value the value of the variable
   * @return the updated filter
   */
  VariableFilter value(final String value);

  /**
   * Filters variables by the specified value using {@link StringFilterProperty}.
   *
   * @param value the value {@link StringFilterProperty} of the variable
   * @return the updated filter
   */
  VariableFilter value(final StringFilterProperty value);

  /**
   * Filters variables by the specified name.
   *
   * @param name the name of the variable
   * @return the updated filter
   */
  VariableFilter name(final String name);

  /**
   * Filters variables by the specified name using {@link StringFilterProperty}.
   *
   * @param name the name {@link StringFilterProperty} of the variable
   * @return the updated filter
   */
  VariableFilter name(final StringFilterProperty name);

  /**
   * Filters variables by the specified scope key.
   *
   * @param scopeKey
   * @return
   * @param scopeKey the scope key of the variable
   * @return the updated filter
   */
  VariableFilter scopeKey(final Long scopeKey);

  /**
   * Filters variables by the specified scope key using {@link LongFilterProperty}.
   *
   * @param scopeKey
   * @return
   * @param scopeKey the scope key {@link LongFilterProperty} of the variable
   * @return the updated filter
   */
  VariableFilter scopeKey(final LongFilterProperty scopeKey);

  /**
   * Filters variables by the specified process instance key.
   *
   * @param processInstanceKey the process instance key of the variable
   * @return the updated filter
   */
  VariableFilter processInstanceKey(final Long processInstanceKey);

  /**
   * Filters variables by the specified process instance key using {@link LongFilterProperty}.
   *
   * @param processInstanceKey the process instance key {@link LongFilterProperty} of the variable
   * @return the updated filter
   */
  VariableFilter processInstanceKey(final LongFilterProperty processInstanceKey);

  /**
   * Filters variables by the specified tenant id.
   *
   * @param tenantId the tenant id of the variable
   * @return the updated filter
   */
  VariableFilter tenantId(final String tenantId);

  /**
   * Filters variables by the specified isTruncated.
   *
   * @param isTruncated the isTruncated of the variable
   * @return the updated filter
   */
  VariableFilter isTruncated(final Boolean isTruncated);
}
