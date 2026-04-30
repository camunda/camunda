/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.filter;

import static io.camunda.search.filter.util.FilterUtil.addValuesToList;
import static io.camunda.search.filter.util.FilterUtil.collectValues;

import io.camunda.util.ObjectBuilder;
import java.util.Collections;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record WaitingStateFilter(
    List<Operation<Long>> processInstanceKeyOperations,
    List<Operation<Long>> elementInstanceKeyOperations)
    implements FilterBase {

  public static final class Builder implements ObjectBuilder<WaitingStateFilter> {

    private @Nullable List<Operation<Long>> processInstanceKeyOperations;
    private @Nullable List<Operation<Long>> elementInstanceKeyOperations;

    public Builder processInstanceKeyOperations(final List<Operation<Long>> operations) {
      processInstanceKeyOperations = addValuesToList(processInstanceKeyOperations, operations);
      return this;
    }

    public Builder processInstanceKeys(final Long value, final Long... values) {
      return processInstanceKeyOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    @SafeVarargs
    public final Builder processInstanceKeyOperations(
        final Operation<Long> operation, final Operation<Long>... operations) {
      return processInstanceKeyOperations(collectValues(operation, operations));
    }

    public Builder elementInstanceKeyOperations(final List<Operation<Long>> operations) {
      elementInstanceKeyOperations = addValuesToList(elementInstanceKeyOperations, operations);
      return this;
    }

    public Builder elementInstanceKeys(final Long value, final Long... values) {
      return elementInstanceKeyOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    @SafeVarargs
    public final Builder elementInstanceKeyOperations(
        final Operation<Long> operation, final Operation<Long>... operations) {
      return elementInstanceKeyOperations(collectValues(operation, operations));
    }

    @Override
    public WaitingStateFilter build() {
      return new WaitingStateFilter(
          processInstanceKeyOperations == null
              ? Collections.emptyList()
              : processInstanceKeyOperations,
          elementInstanceKeyOperations == null
              ? Collections.emptyList()
              : elementInstanceKeyOperations);
    }
  }
}
