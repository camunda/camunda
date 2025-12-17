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

public record BatchOperationFilter(
    List<Operation<String>> batchOperationKeyOperations,
    List<Operation<String>> operationTypeOperations,
    List<Operation<String>> stateOperations,
    List<Operation<String>> actorTypeOperations,
    List<Operation<String>> actorIdOperations)
    implements FilterBase {

  public static final class Builder implements ObjectBuilder<BatchOperationFilter> {

    private List<Operation<String>> batchOperationKeyOperations;
    private List<Operation<String>> operationTypeOperations;
    private List<Operation<String>> stateOperations;
    private List<Operation<String>> actorTypeOperations;
    private List<Operation<String>> actorIdOperations;

    public Builder batchOperationKeyOperations(final List<Operation<String>> operations) {
      batchOperationKeyOperations = addValuesToList(batchOperationKeyOperations, operations);
      return this;
    }

    public Builder batchOperationKeys(final String value, final String... values) {
      return batchOperationKeyOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder replaceBatchOperationKeyOperations(final List<Operation<String>> operations) {
      batchOperationKeyOperations = new ArrayList<>(operations);
      return this;
    }

    @SafeVarargs
    public final Builder batchOperationKeyOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return batchOperationKeyOperations(collectValues(operation, operations));
    }

    public Builder operationTypeOperations(final List<Operation<String>> operations) {
      operationTypeOperations = addValuesToList(operationTypeOperations, operations);
      return this;
    }

    public Builder operationTypes(final String value, final String... values) {
      return operationTypeOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder replaceOperationTypeOperations(final List<Operation<String>> operations) {
      operationTypeOperations = new ArrayList<>(operations);
      return this;
    }

    @SafeVarargs
    public final Builder operationTypeOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return operationTypeOperations(collectValues(operation, operations));
    }

    public Builder stateOperations(final List<Operation<String>> operations) {
      stateOperations = addValuesToList(stateOperations, operations);
      return this;
    }

    public Builder states(final String value, final String... values) {
      return stateOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder replaceStateOperations(final List<Operation<String>> operations) {
      stateOperations = new ArrayList<>(operations);
      return this;
    }

    @SafeVarargs
    public final Builder stateOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return stateOperations(collectValues(operation, operations));
    }

    public Builder actorTypeOperations(final List<Operation<String>> operations) {
      actorTypeOperations = addValuesToList(actorTypeOperations, operations);
      return this;
    }

    public Builder actorTypes(final String value, final String... values) {
      return actorTypeOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder replaceActorTypeOperations(final List<Operation<String>> operations) {
      actorTypeOperations = new ArrayList<>(operations);
      return this;
    }

    @SafeVarargs
    public final Builder actorTypeOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return actorTypeOperations(collectValues(operation, operations));
    }

    public Builder actorIdOperations(final List<Operation<String>> operations) {
      actorIdOperations = addValuesToList(actorIdOperations, operations);
      return this;
    }

    public Builder actorIds(final String value, final String... values) {
      return actorIdOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder replaceActorIdOperations(final List<Operation<String>> operations) {
      actorIdOperations = new ArrayList<>(operations);
      return this;
    }

    @SafeVarargs
    public final Builder actorIdOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return actorIdOperations(collectValues(operation, operations));
    }

    @Override
    public BatchOperationFilter build() {
      return new BatchOperationFilter(
          Objects.requireNonNullElse(batchOperationKeyOperations, Collections.emptyList()),
          Objects.requireNonNullElse(operationTypeOperations, Collections.emptyList()),
          Objects.requireNonNullElse(stateOperations, Collections.emptyList()),
          Objects.requireNonNullElse(actorTypeOperations, Collections.emptyList()),
          Objects.requireNonNullElse(actorIdOperations, Collections.emptyList()));
    }
  }
}
