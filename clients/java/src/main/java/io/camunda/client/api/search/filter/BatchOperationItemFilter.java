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
import io.camunda.client.api.search.enums.BatchOperationType;
import io.camunda.client.api.search.filter.builder.BasicLongProperty;
import io.camunda.client.api.search.filter.builder.BasicStringProperty;
import io.camunda.client.api.search.filter.builder.BatchOperationItemStateProperty;
import io.camunda.client.api.search.filter.builder.BatchOperationTypeProperty;
import io.camunda.client.api.search.filter.builder.StringProperty;
import io.camunda.client.api.search.request.TypedFilterableRequest.SearchRequestFilter;
import java.util.function.Consumer;

public interface BatchOperationItemFilter extends SearchRequestFilter {

  /**
   * Filters batch operation items by the specified batchOperationKey.
   *
   * @param batchOperationKey the key of the batch operation
   * @return the updated filter
   */
  BatchOperationItemFilter batchOperationKey(final String batchOperationKey);

  /**
   * Filter by batchOperationKey using {@link StringProperty} consumer
   *
   * @param fn the consumer to apply to the StringProperty
   * @return the updated filter
   */
  BatchOperationItemFilter batchOperationKey(final Consumer<BasicStringProperty> fn);

  /**
   * Filters batch operation items by the specified itemKey.
   *
   * @param itemKey the itemKey
   * @return the updated filter
   */
  BatchOperationItemFilter itemKey(final long itemKey);

  /**
   * Filter by itemKey using {@link BasicLongProperty} consumer
   *
   * @param fn the consumer to apply to the BasicLongProperty
   * @return the updated filter
   */
  BatchOperationItemFilter itemKey(final Consumer<BasicLongProperty> fn);

  /**
   * Filters batch operation items by the specified processInstanceKey.
   *
   * @param processInstanceKey the processInstanceKey
   * @return the updated filter
   */
  BatchOperationItemFilter processInstanceKey(final long processInstanceKey);

  /**
   * Filter by processInstanceKey using {@link BasicLongProperty} consumer
   *
   * @param fn the consumer to apply to the BasicLongProperty
   * @return the updated filter
   */
  BatchOperationItemFilter processInstanceKey(final Consumer<BasicLongProperty> fn);

  /**
   * Filters batch operations by the specified state.
   *
   * @param state the state
   * @return the updated filter
   */
  BatchOperationItemFilter state(final BatchOperationItemState state);

  /**
   * Filter by state using {@link BatchOperationItemState} consumer
   *
   * @param fn the consumer to apply to the BatchOperationItemState
   * @return the updated filter
   */
  BatchOperationItemFilter state(final Consumer<BatchOperationItemStateProperty> fn);

  /**
   * Filters batch operation items by the specified operationType.
   *
   * @param operationType the operationType
   * @return the updated filter
   */
  BatchOperationItemFilter operationType(final BatchOperationType operationType);

  /**
   * Filter by operationType using {@link BatchOperationTypeProperty} consumer
   *
   * @param fn the consumer to apply to the BatchOperationTypeProperty
   * @return the updated filter
   */
  BatchOperationItemFilter operationType(Consumer<BatchOperationTypeProperty> fn);
}
