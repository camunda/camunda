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

import io.camunda.util.ObjectBuilder;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public record ProcessDefinitionProcessInstanceStatisticsFilter(
    List<Operation<String>> processDefinitionNameOperations,
    List<Operation<Long>> processDefinitionKeyOperations,
    List<Operation<Integer>> processDefinitionVersionOperations,
    List<Operation<String>> processDefinitionVersionTagOperations,
    List<Operation<String>> processDefinitionIdOperations)
    implements FilterBase {
  public static final class Builder
      implements ObjectBuilder<ProcessDefinitionProcessInstanceStatisticsFilter> {
    private List<Operation<String>> processDefinitionNameOperations;
    private List<Operation<Long>> processDefinitionKeyOperations;
    private List<Operation<Integer>> processDefinitionVersionOperations;
    private List<Operation<String>> processDefinitionVersionTagOperations;
    private List<Operation<String>> processDefinitionIdOperations;

    public Builder processDefinitionNameOperations(final List<Operation<String>> operations) {
      processDefinitionNameOperations =
          addValuesToList(processDefinitionNameOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder processDefinitionNameOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return processDefinitionNameOperations(collectValues(operation, operations));
    }

    public Builder processDefinitionKeyOperations(final List<Operation<Long>> operations) {
      processDefinitionKeyOperations = addValuesToList(processDefinitionKeyOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder processDefinitionKeyOperations(
        final Operation<Long> operation, final Operation<Long>... operations) {
      return processDefinitionKeyOperations(collectValues(operation, operations));
    }

    public Builder processDefinitionVersionOperations(final List<Operation<Integer>> operations) {
      processDefinitionVersionOperations =
          addValuesToList(processDefinitionVersionOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder processDefinitionVersionOperations(
        final Operation<Integer> operation, final Operation<Integer>... operations) {
      return processDefinitionVersionOperations(collectValues(operation, operations));
    }

    public Builder processDefinitionVersionTagOperations(final List<Operation<String>> operations) {
      processDefinitionVersionTagOperations =
          addValuesToList(processDefinitionVersionTagOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder versionTags(
        final Operation<String> operation, final Operation<String>... operations) {
      return processDefinitionVersionTagOperations(collectValues(operation, operations));
    }

    public Builder processDefinitionIdOperations(final List<Operation<String>> operations) {
      processDefinitionIdOperations = addValuesToList(processDefinitionIdOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder processDefinitionIdOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return processDefinitionIdOperations(collectValues(operation, operations));
    }

    @Override
    public ProcessDefinitionProcessInstanceStatisticsFilter build() {
      return new ProcessDefinitionProcessInstanceStatisticsFilter(
          Objects.requireNonNullElse(processDefinitionNameOperations, Collections.emptyList()),
          Objects.requireNonNullElse(processDefinitionKeyOperations, Collections.emptyList()),
          Objects.requireNonNullElse(processDefinitionVersionOperations, Collections.emptyList()),
          Objects.requireNonNullElse(
              processDefinitionVersionTagOperations, Collections.emptyList()),
          Objects.requireNonNullElse(processDefinitionIdOperations, Collections.emptyList()));
    }
  }
}
