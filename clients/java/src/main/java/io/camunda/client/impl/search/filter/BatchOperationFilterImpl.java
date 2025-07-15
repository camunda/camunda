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

import io.camunda.client.api.search.enums.BatchOperationState;
import io.camunda.client.api.search.enums.BatchOperationType;
import io.camunda.client.api.search.filter.builder.BasicStringProperty;
import io.camunda.client.api.search.filter.builder.BatchOperationStateProperty;
import io.camunda.client.api.search.filter.builder.BatchOperationTypeProperty;
import io.camunda.client.impl.search.filter.builder.BasicStringPropertyImpl;
import io.camunda.client.impl.search.filter.builder.BatchOperationStatePropertyImpl;
import io.camunda.client.impl.search.filter.builder.BatchOperationTypePropertyImpl;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import io.camunda.client.protocol.rest.BatchOperationFilter;
import java.util.function.Consumer;

public class BatchOperationFilterImpl
    extends TypedSearchRequestPropertyProvider<BatchOperationFilter>
    implements io.camunda.client.api.search.filter.BatchOperationFilter {

  private final BatchOperationFilter filter;

  public BatchOperationFilterImpl() {
    filter = new BatchOperationFilter();
  }

  @Override
  public io.camunda.client.api.search.filter.BatchOperationFilter batchOperationKey(
      final String batchOperationKey) {
    batchOperationKey(b -> b.eq(batchOperationKey));
    return this;
  }

  @Override
  public io.camunda.client.api.search.filter.BatchOperationFilter batchOperationKey(
      final Consumer<BasicStringProperty> fn) {
    final BasicStringProperty property = new BasicStringPropertyImpl();
    fn.accept(property);
    filter.setBatchOperationKey(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public io.camunda.client.api.search.filter.BatchOperationFilter operationType(
      final BatchOperationType batchOperationType) {
    if (batchOperationType == null) {
      filter.setOperationType(null);
      return this;
    }

    return operationType(b -> b.eq(batchOperationType));
  }

  @Override
  public io.camunda.client.api.search.filter.BatchOperationFilter operationType(
      final Consumer<BatchOperationTypeProperty> fn) {
    final BatchOperationTypeProperty property = new BatchOperationTypePropertyImpl();
    fn.accept(property);
    filter.operationType(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public io.camunda.client.api.search.filter.BatchOperationFilter state(
      final BatchOperationState state) {
    if (state == null) {
      filter.setState(null);
      return this;
    }

    return state(b -> b.eq(state));
  }

  @Override
  public io.camunda.client.api.search.filter.BatchOperationFilter state(
      final Consumer<BatchOperationStateProperty> fn) {
    final BatchOperationStateProperty property = new BatchOperationStatePropertyImpl();
    fn.accept(property);
    filter.setState(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  protected BatchOperationFilter getSearchRequestProperty() {
    return filter;
  }
}
