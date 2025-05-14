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

import io.camunda.client.api.search.enums.BatchOperationItemState;
import io.camunda.client.api.search.request.TypedSearchRequest.SearchRequestFilter;

public interface BatchOperationItemFilter extends SearchRequestFilter {

  /**
   * Filters batch operation items by the specified batchOperationId.
   *
   * @param batchOperationId the ID of the batch operation
   * @return the updated filter
   */
  BatchOperationItemFilter batchOperationId(final String batchOperationId);

  /**
   * Filters batch operation items by the specified itemKey.
   *
   * @param itemKey the itemKey
   * @return the updated filter
   */
  BatchOperationItemFilter itemKey(final long itemKey);

  /**
   * Filters batch operation items by the specified processInstanceKey.
   *
   * @param processInstanceKey the processInstanceKey
   * @return the updated filter
   */
  BatchOperationItemFilter processInstanceKey(final long processInstanceKey);

  /**
   * Filters batch operations by the specified state.
   *
   * @param state the state
   * @return the updated filter
   */
  BatchOperationItemFilter state(final BatchOperationItemState state);
}
