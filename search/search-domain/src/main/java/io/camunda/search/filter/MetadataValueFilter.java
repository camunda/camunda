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

public record MetadataValueFilter(String key, List<UntypedOperation> valueOperations)
    implements FilterBase {

  @Override
  public String toString() {
    return "MetadataValueFilter["
        + "key="
        + key
        + ", "
        + "valueOperations="
        + valueOperations
        + ']';
  }

  public static final class Builder
      implements ObjectBuilder<MetadataValueFilter>, ListBuilder<MetadataValueFilter> {

    private String key;
    private final List<UntypedOperation> valueOperations = new ArrayList<>();

    public Builder key(final String value) {
      key = value;
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
    public MetadataValueFilter build() {
      return new MetadataValueFilter(Objects.requireNonNull(key), valueOperations);
    }

    @Override
    public List<MetadataValueFilter> buildList() {
      final List<MetadataValueFilter> metadataValueFilters = new ArrayList<>();
      for (final UntypedOperation untypedOperation : valueOperations) {
        final MetadataValueFilter metadataValueFilter =
            new MetadataValueFilter.Builder().key(key).valueOperation(untypedOperation).build();
        metadataValueFilters.add(metadataValueFilter);
      }
      return metadataValueFilters;
    }
  }
}
