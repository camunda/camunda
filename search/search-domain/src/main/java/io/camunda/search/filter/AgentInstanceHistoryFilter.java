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

import io.camunda.search.entities.AgentInstanceHistoryEntity.AgentInstanceHistoryCommitStatus;
import io.camunda.search.entities.AgentInstanceHistoryEntity.AgentInstanceHistoryRole;
import io.camunda.util.FilterUtil;
import io.camunda.util.ObjectBuilder;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record AgentInstanceHistoryFilter(
    List<Operation<Long>> agentInstanceKeyOperations,
    List<Operation<Long>> historyItemKeyOperations,
    List<Operation<String>> roleOperations,
    List<Operation<Long>> elementInstanceKeyOperations,
    List<Operation<Long>> jobKeyOperations,
    List<Operation<Integer>> iterationOperations,
    List<Operation<String>> commitStatusOperations,
    List<Operation<OffsetDateTime>> producedAtOperations)
    implements FilterBase {

  public static AgentInstanceHistoryFilter of(
      final Function<AgentInstanceHistoryFilter.Builder, ObjectBuilder<AgentInstanceHistoryFilter>>
          fn) {
    return FilterBuilders.agentInstanceHistory(fn);
  }

  public static final class Builder implements ObjectBuilder<AgentInstanceHistoryFilter> {

    private List<Operation<Long>> agentInstanceKeyOperations;
    private List<Operation<Long>> historyItemKeyOperations;
    private List<Operation<String>> roleOperations;
    private List<Operation<Long>> elementInstanceKeyOperations;
    private List<Operation<Long>> jobKeyOperations;
    private List<Operation<Integer>> iterationOperations;
    private List<Operation<String>> commitStatusOperations;
    private List<Operation<OffsetDateTime>> producedAtOperations;

    public Builder agentInstanceKeyOperations(final List<Operation<Long>> operations) {
      agentInstanceKeyOperations = addValuesToList(agentInstanceKeyOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder agentInstanceKeyOperations(
        final Operation<Long> operation, final Operation<Long>... operations) {
      return agentInstanceKeyOperations(collectValues(operation, operations));
    }

    public Builder agentInstanceKeys(final Long value, final Long... values) {
      return agentInstanceKeyOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder historyItemKeyOperations(final List<Operation<Long>> operations) {
      historyItemKeyOperations = addValuesToList(historyItemKeyOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder historyItemKeyOperations(
        final Operation<Long> operation, final Operation<Long>... operations) {
      return historyItemKeyOperations(collectValues(operation, operations));
    }

    public Builder historyItemKeys(final Long value, final Long... values) {
      return historyItemKeyOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder roleOperations(final List<Operation<String>> operations) {
      roleOperations = addValuesToList(roleOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder roleOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return roleOperations(collectValues(operation, operations));
    }

    public Builder roles(
        final AgentInstanceHistoryRole value, final AgentInstanceHistoryRole... values) {
      final String[] rest = new String[values.length];
      for (int i = 0; i < values.length; i++) {
        rest[i] = values[i].name();
      }
      return roleOperations(FilterUtil.mapDefaultToOperation(value.name(), rest));
    }

    public Builder elementInstanceKeyOperations(final List<Operation<Long>> operations) {
      elementInstanceKeyOperations = addValuesToList(elementInstanceKeyOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder elementInstanceKeyOperations(
        final Operation<Long> operation, final Operation<Long>... operations) {
      return elementInstanceKeyOperations(collectValues(operation, operations));
    }

    public Builder elementInstanceKeys(final Long value, final Long... values) {
      return elementInstanceKeyOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder jobKeyOperations(final List<Operation<Long>> operations) {
      jobKeyOperations = addValuesToList(jobKeyOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder jobKeyOperations(
        final Operation<Long> operation, final Operation<Long>... operations) {
      return jobKeyOperations(collectValues(operation, operations));
    }

    public Builder jobKeys(final Long value, final Long... values) {
      return jobKeyOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder iterationOperations(final List<Operation<Integer>> operations) {
      iterationOperations = addValuesToList(iterationOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder iterationOperations(
        final Operation<Integer> operation, final Operation<Integer>... operations) {
      return iterationOperations(collectValues(operation, operations));
    }

    public Builder iterations(final Integer value, final Integer... values) {
      return iterationOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder commitStatusOperations(final List<Operation<String>> operations) {
      commitStatusOperations = addValuesToList(commitStatusOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder commitStatusOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return commitStatusOperations(collectValues(operation, operations));
    }

    public Builder commitStatuses(
        final AgentInstanceHistoryCommitStatus value,
        final AgentInstanceHistoryCommitStatus... values) {
      final String[] rest = new String[values.length];
      for (int i = 0; i < values.length; i++) {
        rest[i] = values[i].name();
      }
      return commitStatusOperations(FilterUtil.mapDefaultToOperation(value.name(), rest));
    }

    public Builder producedAtOperations(final List<Operation<OffsetDateTime>> operations) {
      producedAtOperations = addValuesToList(producedAtOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder producedAtOperations(
        final Operation<OffsetDateTime> operation, final Operation<OffsetDateTime>... operations) {
      return producedAtOperations(collectValues(operation, operations));
    }

    @Override
    public AgentInstanceHistoryFilter build() {
      return new AgentInstanceHistoryFilter(
          Objects.requireNonNullElse(agentInstanceKeyOperations, Collections.emptyList()),
          Objects.requireNonNullElse(historyItemKeyOperations, Collections.emptyList()),
          Objects.requireNonNullElse(roleOperations, Collections.emptyList()),
          Objects.requireNonNullElse(elementInstanceKeyOperations, Collections.emptyList()),
          Objects.requireNonNullElse(jobKeyOperations, Collections.emptyList()),
          Objects.requireNonNullElse(iterationOperations, Collections.emptyList()),
          Objects.requireNonNullElse(commitStatusOperations, Collections.emptyList()),
          Objects.requireNonNullElse(producedAtOperations, Collections.emptyList()));
    }
  }
}
