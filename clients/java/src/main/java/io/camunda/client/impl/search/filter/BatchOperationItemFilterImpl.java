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
import io.camunda.client.api.search.enums.BatchOperationType;
import io.camunda.client.api.search.filter.builder.BasicLongProperty;
import io.camunda.client.api.search.filter.builder.BasicStringProperty;
import io.camunda.client.api.search.filter.builder.BatchOperationItemStateProperty;
import io.camunda.client.api.search.filter.builder.BatchOperationTypeProperty;
import io.camunda.client.impl.search.filter.builder.BasicLongPropertyImpl;
import io.camunda.client.impl.search.filter.builder.BasicStringPropertyImpl;
import io.camunda.client.impl.search.filter.builder.BatchOperationItemStatePropertyImpl;
import io.camunda.client.impl.search.filter.builder.BatchOperationTypePropertyImpl;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import io.camunda.client.protocol.rest.BatchOperationItemFilter;
import java.util.function.Consumer;

public class BatchOperationItemFilterImpl
    extends TypedSearchRequestPropertyProvider<BatchOperationItemFilter>
    implements io.camunda.client.api.search.filter.BatchOperationItemFilter {

  private final BatchOperationItemFilter filter;

  public BatchOperationItemFilterImpl() {
    filter = new BatchOperationItemFilter();
  }

  @Override
  public io.camunda.client.api.search.filter.BatchOperationItemFilter batchOperationKey(
      final String batchOperationKey) {
    batchOperationKey(b -> b.eq(batchOperationKey));
    return this;
  }

  @Override
  public io.camunda.client.api.search.filter.BatchOperationItemFilter batchOperationKey(
      final Consumer<BasicStringProperty> fn) {
    final BasicStringProperty property = new BasicStringPropertyImpl();
    fn.accept(property);
    filter.setBatchOperationKey(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public io.camunda.client.api.search.filter.BatchOperationItemFilter itemKey(final long itemKey) {
    itemKey(b -> b.eq(itemKey));
    return this;
  }

  @Override
  public io.camunda.client.api.search.filter.BatchOperationItemFilter itemKey(
      final Consumer<BasicLongProperty> fn) {
    final BasicLongProperty property = new BasicLongPropertyImpl();
    fn.accept(property);
    filter.setItemKey(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public io.camunda.client.api.search.filter.BatchOperationItemFilter processInstanceKey(
      final long processInstanceKey) {
    processInstanceKey(b -> b.eq(processInstanceKey));
    return this;
  }

  @Override
  public io.camunda.client.api.search.filter.BatchOperationItemFilter processInstanceKey(
      final Consumer<BasicLongProperty> fn) {
    final BasicLongProperty property = new BasicLongPropertyImpl();
    fn.accept(property);
    filter.setProcessInstanceKey(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public io.camunda.client.api.search.filter.BatchOperationItemFilter state(
      final BatchOperationItemState state) {
    if (state == null) {
      filter.setState(null);
      return this;
    }

    return state(b -> b.eq(state));
  }

  @Override
  public io.camunda.client.api.search.filter.BatchOperationItemFilter state(
      final Consumer<BatchOperationItemStateProperty> fn) {
    final BatchOperationItemStateProperty property = new BatchOperationItemStatePropertyImpl();
    fn.accept(property);
    filter.setState(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public io.camunda.client.api.search.filter.BatchOperationItemFilter operationType(
      final BatchOperationType operationType) {
    if (operationType == null) {
      filter.setOperationType(null);
      return this;
    }

    return operationType(b -> b.eq(operationType));
  }

  @Override
  public io.camunda.client.api.search.filter.BatchOperationItemFilter operationType(
      final Consumer<BatchOperationTypeProperty> fn) {
    final BatchOperationTypeProperty property = new BatchOperationTypePropertyImpl();
    fn.accept(property);
    filter.setOperationType(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  protected BatchOperationItemFilter getSearchRequestProperty() {
    return filter;
  }
}
