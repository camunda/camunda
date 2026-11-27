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
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public record CaseStageFilter(
    List<Operation<String>> elementIdOperations, List<Operation<String>> statusOperations)
    implements FilterBase {

  public static final class Builder implements ObjectBuilder<CaseStageFilter> {
    private @Nullable List<Operation<String>> elementIdOperations;
    private @Nullable List<Operation<String>> statusOperations;

    public Builder elementIdOperations(final List<Operation<String>> operations) {
      elementIdOperations = addValuesToList(elementIdOperations, operations);
      return this;
    }

    public Builder elementIds(final String value, final String... values) {
      return elementIdOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    @SafeVarargs
    public final Builder elementIdOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return elementIdOperations(collectValues(operation, operations));
    }

    public Builder statusOperations(final List<Operation<String>> operations) {
      statusOperations = addValuesToList(statusOperations, operations);
      return this;
    }

    public Builder statuses(final String value, final String... values) {
      return statusOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    @SafeVarargs
    public final Builder statusOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return statusOperations(collectValues(operation, operations));
    }

    @Override
    public CaseStageFilter build() {
      return new CaseStageFilter(
          Objects.requireNonNullElseGet(elementIdOperations, Collections::emptyList),
          Objects.requireNonNullElseGet(statusOperations, Collections::emptyList));
    }
  }
}
