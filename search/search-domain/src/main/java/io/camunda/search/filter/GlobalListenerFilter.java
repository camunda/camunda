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
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public record GlobalListenerFilter(
    List<Operation<String>> listenerIdOperations,
    List<Operation<String>> typeOperations,
    List<Operation<Integer>> retriesOperations,
    List<Operation<String>> eventTypeOperations,
    Boolean afterNonGlobal,
    List<Operation<Integer>> priorityOperations,
    List<Operation<String>> sourceOperations,
    List<Operation<String>> listenerTypeOperations)
    implements FilterBase {

  public static final class Builder implements ObjectBuilder<GlobalListenerFilter> {
    List<Operation<String>> listenerIdOperations;
    List<Operation<String>> typeOperations;
    List<Operation<Integer>> retriesOperations;
    List<Operation<String>> eventTypeOperations;
    Boolean afterNonGlobal;
    List<Operation<Integer>> priorityOperations;
    List<Operation<String>> sourceOperations;
    List<Operation<String>> listenerTypeOperations;

    @SafeVarargs
    public final Builder listenerIdOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return listenerIdOperations(collectValues(operation, operations));
    }

    public Builder listenerIdOperations(final List<Operation<String>> operations) {
      listenerIdOperations = addValuesToList(listenerIdOperations, operations);
      return this;
    }

    public Builder listenerIds(final String value, final String... values) {
      return listenerIdOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    @SafeVarargs
    public final Builder typeOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return typeOperations(collectValues(operation, operations));
    }

    public Builder typeOperations(final List<Operation<String>> operations) {
      typeOperations = addValuesToList(typeOperations, operations);
      return this;
    }

    public Builder types(final String value, final String... values) {
      return typeOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    @SafeVarargs
    public final Builder retriesOperations(
        final Operation<Integer> operation, final Operation<Integer>... operations) {
      return retriesOperations(collectValues(operation, operations));
    }

    public Builder retriesOperations(final List<Operation<Integer>> operations) {
      retriesOperations = addValuesToList(retriesOperations, operations);
      return this;
    }

    public Builder retries(final Integer value, final Integer... values) {
      return retriesOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    @SafeVarargs
    public final Builder eventTypeOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return eventTypeOperations(collectValues(operation, operations));
    }

    public Builder eventTypeOperations(final List<Operation<String>> operations) {
      eventTypeOperations = addValuesToList(eventTypeOperations, operations);
      return this;
    }

    public Builder eventTypes(final String value, final String... values) {
      return eventTypeOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder afterNonGlobal(final Boolean value) {
      afterNonGlobal = value;
      return this;
    }

    @SafeVarargs
    public final Builder priorityOperations(
        final Operation<Integer> operation, final Operation<Integer>... operations) {
      return priorityOperations(collectValues(operation, operations));
    }

    public Builder priorityOperations(final List<Operation<Integer>> operations) {
      priorityOperations = addValuesToList(priorityOperations, operations);
      return this;
    }

    public Builder priorities(final Integer value, final Integer... values) {
      return priorityOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    @SafeVarargs
    public final Builder sourceOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return sourceOperations(collectValues(operation, operations));
    }

    public Builder sourceOperations(final List<Operation<String>> operations) {
      sourceOperations = addValuesToList(sourceOperations, operations);
      return this;
    }

    public Builder sources(final String value, final String... values) {
      return sourceOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    @SafeVarargs
    public final Builder listenerTypeOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return listenerTypeOperations(collectValues(operation, operations));
    }

    public Builder listenerTypeOperations(final List<Operation<String>> operations) {
      listenerTypeOperations = addValuesToList(listenerTypeOperations, operations);
      return this;
    }

    public Builder listenerTypes(final String value, final String... values) {
      return listenerTypeOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    @Override
    public GlobalListenerFilter build() {
      return new GlobalListenerFilter(
          Objects.requireNonNullElse(listenerIdOperations, Collections.emptyList()),
          Objects.requireNonNullElse(typeOperations, Collections.emptyList()),
          Objects.requireNonNullElse(retriesOperations, Collections.emptyList()),
          Objects.requireNonNullElse(eventTypeOperations, Collections.emptyList()),
          afterNonGlobal,
          Objects.requireNonNullElse(priorityOperations, Collections.emptyList()),
          Objects.requireNonNullElse(sourceOperations, Collections.emptyList()),
          Objects.requireNonNullElse(listenerTypeOperations, Collections.emptyList()));
    }
  }
}
