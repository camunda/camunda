/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.filter;

import io.camunda.util.ListBuilder;
import io.camunda.util.ObjectBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record HeaderValueFilter(String name, List<UntypedOperation> valueOperations)
    implements FilterBase {

  @Override
  public String toString() {
    return "HeaderValueFilter[" + "name=" + name + ", " + "valueOperation=" + valueOperations + ']';
  }

  public static final class Builder
      implements ObjectBuilder<HeaderValueFilter>, ListBuilder<HeaderValueFilter> {

    private String name;
    private final List<UntypedOperation> valueOperations = new ArrayList<>();

    public Builder name(final String value) {
      name = value;
      return this;
    }

    public Builder valueOperation(final UntypedOperation operation) {
      valueOperations.add(operation);
      return this;
    }

    public Builder valueOperations(final List<UntypedOperation> operations) {
      valueOperations.addAll(operations);
      return this;
    }

    public <T> Builder valueTypedOperations(final List<Operation<T>> operations) {
      operations.forEach(operation -> valueOperations.add(UntypedOperation.of(operation)));
      return this;
    }

    @Override
    public HeaderValueFilter build() {
      return new HeaderValueFilter(Objects.requireNonNull(name), valueOperations);
    }

    @Override
    public List<HeaderValueFilter> buildList() {
      final List<HeaderValueFilter> headerValueFilters = new ArrayList<>();
      for (UntypedOperation untypedOperation : valueOperations) {
        final HeaderValueFilter headerValueFilter =
            new HeaderValueFilter.Builder().name(name).valueOperation(untypedOperation).build();
        headerValueFilters.add(headerValueFilter);
      }
      return headerValueFilters;
    }
  }
}
