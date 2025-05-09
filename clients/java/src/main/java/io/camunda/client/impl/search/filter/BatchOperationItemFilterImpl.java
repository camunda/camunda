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
package io.camunda.client.impl.search.filter;

import io.camunda.client.api.search.enums.BatchOperationItemState;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import io.camunda.client.protocol.rest.BatchOperationItemFilter;
import io.camunda.client.protocol.rest.BatchOperationItemFilter.StateEnum;

public class BatchOperationItemFilterImpl
    extends TypedSearchRequestPropertyProvider<BatchOperationItemFilter>
    implements io.camunda.client.api.search.filter.BatchOperationItemFilter {

  private final BatchOperationItemFilter filter;

  public BatchOperationItemFilterImpl() {
    filter = new BatchOperationItemFilter();
  }

  @Override
  public io.camunda.client.api.search.filter.BatchOperationItemFilter batchOperationId(
      final String batchOperationId) {
    filter.setBatchOperationId(batchOperationId);
    return this;
  }

  @Override
  public io.camunda.client.api.search.filter.BatchOperationItemFilter itemKey(final long itemKey) {
    filter.itemKey(Long.toString(itemKey));
    return this;
  }

  @Override
  public io.camunda.client.api.search.filter.BatchOperationItemFilter processInstanceKey(
      final long processInstanceKey) {
    filter.processInstanceKey(Long.toString(processInstanceKey));
    return this;
  }

  @Override
  public io.camunda.client.api.search.filter.BatchOperationItemFilter state(
      final BatchOperationItemState state) {
    if (state == null) {
      filter.setState(null);
      return this;
    }

    filter.setState(StateEnum.valueOf(state.name()));
    return this;
  }

  @Override
  protected BatchOperationItemFilter getSearchRequestProperty() {
    return filter;
  }
}
