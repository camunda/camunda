/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.filter;

import static io.camunda.util.CollectionUtil.addValuesToList;
import static io.camunda.util.CollectionUtil.collectValues;

import io.camunda.util.FilterUtil;
import io.camunda.util.ObjectBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public record BatchOperationItemFilter(
    List<Operation<String>> batchOperationIdOperations,
    List<Operation<Long>> itemKeyOperations,
    List<Operation<Long>> processInstanceKeyOperations,
    List<Operation<String>> stateOperations)
    implements FilterBase {

  public static final class Builder implements ObjectBuilder<BatchOperationItemFilter> {

    private List<Operation<String>> batchOperationIdOperations;
    private List<Operation<Long>> itemKeyOperations;
    private List<Operation<Long>> processInstanceKeyOperations;
    private List<Operation<String>> stateOperations;

    public Builder batchOperationIdOperations(final List<Operation<String>> operations) {
      batchOperationIdOperations = addValuesToList(batchOperationIdOperations, operations);
      return this;
    }

    public Builder batchOperationIds(final String value, final String... values) {
      return batchOperationIdOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder replaceBatchOperationIdOperations(final List<Operation<String>> operations) {
      batchOperationIdOperations = new ArrayList<>(operations);
      return this;
    }

    @SafeVarargs
    public final Builder batchOperationIdOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return batchOperationIdOperations(collectValues(operation, operations));
    }

    public Builder itemKeyOperations(final List<Operation<Long>> operations) {
      itemKeyOperations = addValuesToList(itemKeyOperations, operations);
      return this;
    }

    public Builder itemKeys(final Long value, final Long... values) {
      return itemKeyOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder replaceItemKeyOperations(final List<Operation<Long>> operations) {
      itemKeyOperations = new ArrayList<>(operations);
      return this;
    }

    @SafeVarargs
    public final Builder itemKeyOperations(
        final Operation<Long> operation, final Operation<Long>... operations) {
      return itemKeyOperations(collectValues(operation, operations));
    }

    public Builder processInstanceKeyOperations(final List<Operation<Long>> operations) {
      processInstanceKeyOperations = addValuesToList(processInstanceKeyOperations, operations);
      return this;
    }

    public Builder processInstanceKeys(final Long value, final Long... values) {
      return processInstanceKeyOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder replaceProcessInstanceKeyOperations(final List<Operation<Long>> operations) {
      processInstanceKeyOperations = new ArrayList<>(operations);
      return this;
    }

    @SafeVarargs
    public final Builder processInstanceKeyOperations(
        final Operation<Long> operation, final Operation<Long>... operations) {
      return processInstanceKeyOperations(collectValues(operation, operations));
    }

    public Builder stateOperations(final List<Operation<String>> operations) {
      stateOperations = addValuesToList(stateOperations, operations);
      return this;
    }

    public Builder states(final String value, final String... values) {
      return stateOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder replaceStateOperationsOperations(final List<Operation<String>> operations) {
      stateOperations = new ArrayList<>(operations);
      return this;
    }

    @SafeVarargs
    public final Builder stateOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return stateOperations(collectValues(operation, operations));
    }

    @Override
    public BatchOperationItemFilter build() {
      return new BatchOperationItemFilter(
          Objects.requireNonNullElse(batchOperationIdOperations, Collections.emptyList()),
          Objects.requireNonNullElse(itemKeyOperations, Collections.emptyList()),
          Objects.requireNonNullElse(processInstanceKeyOperations, Collections.emptyList()),
          Objects.requireNonNullElse(stateOperations, Collections.emptyList()));
    }
  }
}
