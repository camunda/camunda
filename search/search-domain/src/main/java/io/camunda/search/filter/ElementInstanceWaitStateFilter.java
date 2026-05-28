/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.filter;

import static io.camunda.util.CollectionUtil.addValuesToList;

import io.camunda.util.FilterUtil;
import io.camunda.util.ObjectBuilder;
import java.util.List;
import java.util.Objects;

public record ElementInstanceWaitStateFilter(
    List<Operation<Long>> elementInstanceKeyOperations,
    List<Operation<Long>> processInstanceKeyOperations,
    List<Operation<Long>> rootProcessInstanceKeyOperations,
    List<Operation<String>> elementIdOperations)
    implements FilterBase {

  public static final class Builder implements ObjectBuilder<ElementInstanceWaitStateFilter> {
    private List<Operation<Long>> elementInstanceKeyOperations;
    private List<Operation<Long>> processInstanceKeyOperations;
    private List<Operation<Long>> rootProcessInstanceKeyOperations;
    private List<Operation<String>> elementIdOperations;

    public Builder elementInstanceKeyOperations(final List<Operation<Long>> operations) {
      elementInstanceKeyOperations = addValuesToList(elementInstanceKeyOperations, operations);
      return this;
    }

    public Builder elementInstanceKeys(final Long value, final Long... values) {
      return elementInstanceKeyOperations(List.of(FilterUtil.mapDefaultToOperation(value, values)));
    }

    public Builder processInstanceKeyOperations(final List<Operation<Long>> operations) {
      processInstanceKeyOperations = addValuesToList(processInstanceKeyOperations, operations);
      return this;
    }

    public Builder processInstanceKeys(final Long value, final Long... values) {
      return processInstanceKeyOperations(List.of(FilterUtil.mapDefaultToOperation(value, values)));
    }

    public Builder rootProcessInstanceKeyOperations(final List<Operation<Long>> operations) {
      rootProcessInstanceKeyOperations =
          addValuesToList(rootProcessInstanceKeyOperations, operations);
      return this;
    }

    public Builder rootProcessInstanceKeys(final Long value, final Long... values) {
      return rootProcessInstanceKeyOperations(
          List.of(FilterUtil.mapDefaultToOperation(value, values)));
    }

    public Builder elementIdOperations(final List<Operation<String>> operations) {
      elementIdOperations = addValuesToList(elementIdOperations, operations);
      return this;
    }

    public Builder elementIds(final String value, final String... values) {
      return elementIdOperations(List.of(FilterUtil.mapDefaultToOperation(value, values)));
    }

    @Override
    public ElementInstanceWaitStateFilter build() {
      return new ElementInstanceWaitStateFilter(
          Objects.requireNonNullElse(elementInstanceKeyOperations, List.of()),
          Objects.requireNonNullElse(processInstanceKeyOperations, List.of()),
          Objects.requireNonNullElse(rootProcessInstanceKeyOperations, List.of()),
          Objects.requireNonNullElse(elementIdOperations, List.of()));
    }
  }
}
