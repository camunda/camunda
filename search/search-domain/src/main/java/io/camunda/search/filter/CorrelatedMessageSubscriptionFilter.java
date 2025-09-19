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
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public record CorrelatedMessageSubscriptionFilter(
    List<Operation<String>> correlationKeyOperations,
    List<Operation<OffsetDateTime>> correlationTimeOperations,
    List<Operation<String>> flowNodeIdOperations,
    List<Operation<Long>> flowNodeInstanceKeyOperations,
    List<Operation<Long>> messageKeyOperations,
    List<Operation<String>> messageNameOperations,
    List<Operation<Integer>> partitionIdOperations,
    List<Operation<String>> processDefinitionIdOperations,
    List<Operation<Long>> processDefinitionKeyOperations,
    List<Operation<Long>> processInstanceKeyOperations,
    List<Operation<Long>> subscriptionKeyOperations,
    List<Operation<String>> tenantIdOperations)
    implements FilterBase {

  public static final CorrelatedMessageSubscriptionFilter EMPTY =
      new CorrelatedMessageSubscriptionFilter.Builder().build();

  public static final class Builder implements ObjectBuilder<CorrelatedMessageSubscriptionFilter> {
    private List<Operation<String>> correlationKeyOperations;
    private List<Operation<OffsetDateTime>> correlationTimeOperations;
    private List<Operation<String>> flowNodeIdOperations;
    private List<Operation<Long>> flowNodeInstanceKeyOperations;
    private List<Operation<Long>> messageKeyOperations;
    private List<Operation<String>> messageNameOperations;
    private List<Operation<Integer>> partitionIdOperations;
    private List<Operation<String>> processDefinitionIdOperations;
    private List<Operation<Long>> processDefinitionKeyOperations;
    private List<Operation<Long>> processInstanceKeyOperations;
    private List<Operation<Long>> subscriptionKeyOperations;
    private List<Operation<String>> tenantIdOperations;

    public Builder correlationKeyOperations(final List<Operation<String>> operations) {
      correlationKeyOperations = addValuesToList(correlationKeyOperations, operations);
      return this;
    }

    public Builder correlationKeys(final String value, final String... values) {
      return correlationKeyOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    @SafeVarargs
    public final Builder correlationKeyOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return correlationKeyOperations(collectValues(operation, operations));
    }

    public Builder correlationTimeOperations(final List<Operation<OffsetDateTime>> operations) {
      correlationTimeOperations = addValuesToList(correlationTimeOperations, operations);
      return this;
    }

    public Builder correlationTimes(final OffsetDateTime value, final OffsetDateTime... values) {
      return correlationTimeOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    @SafeVarargs
    public final Builder correlationTimeOperations(
        final Operation<OffsetDateTime> operation, final Operation<OffsetDateTime>... operations) {
      return correlationTimeOperations(collectValues(operation, operations));
    }

    public Builder flowNodeIdOperations(final List<Operation<String>> operations) {
      flowNodeIdOperations = addValuesToList(flowNodeIdOperations, operations);
      return this;
    }

    public Builder flowNodeIds(final String value, final String... values) {
      return flowNodeIdOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    @SafeVarargs
    public final Builder flowNodeIdOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return flowNodeIdOperations(collectValues(operation, operations));
    }

    public Builder flowNodeInstanceKeyOperations(final List<Operation<Long>> operations) {
      flowNodeInstanceKeyOperations = addValuesToList(flowNodeInstanceKeyOperations, operations);
      return this;
    }

    public Builder flowNodeInstanceKeys(final Long value, final Long... values) {
      return flowNodeInstanceKeyOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    @SafeVarargs
    public final Builder flowNodeInstanceKeyOperations(
        final Operation<Long> operation, final Operation<Long>... operations) {
      return flowNodeInstanceKeyOperations(collectValues(operation, operations));
    }

    public Builder messageKeyOperations(final List<Operation<Long>> operations) {
      messageKeyOperations = addValuesToList(messageKeyOperations, operations);
      return this;
    }

    public Builder messageKeys(final Long value, final Long... values) {
      return messageKeyOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    @SafeVarargs
    public final Builder messageKeyOperations(
        final Operation<Long> operation, final Operation<Long>... operations) {
      return messageKeyOperations(collectValues(operation, operations));
    }

    public Builder messageNameOperations(final List<Operation<String>> operations) {
      messageNameOperations = addValuesToList(messageNameOperations, operations);
      return this;
    }

    public Builder messageNames(final String value, final String... values) {
      return messageNameOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    @SafeVarargs
    public final Builder messageNameOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return messageNameOperations(collectValues(operation, operations));
    }

    public Builder partitionIdOperations(final List<Operation<Integer>> operations) {
      partitionIdOperations = addValuesToList(partitionIdOperations, operations);
      return this;
    }

    public Builder partitionIds(final Integer value, final Integer... values) {
      return partitionIdOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    @SafeVarargs
    public final Builder partitionIdOperations(
        final Operation<Integer> operation, final Operation<Integer>... operations) {
      return partitionIdOperations(collectValues(operation, operations));
    }

    public Builder processDefinitionIdOperations(final List<Operation<String>> operations) {
      processDefinitionIdOperations = addValuesToList(processDefinitionIdOperations, operations);
      return this;
    }

    public Builder processDefinitionIds(final String value, final String... values) {
      return processDefinitionIdOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    @SafeVarargs
    public final Builder processDefinitionIdOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return processDefinitionIdOperations(collectValues(operation, operations));
    }

    public Builder processDefinitionKeyOperations(final List<Operation<Long>> operations) {
      processDefinitionKeyOperations = addValuesToList(processDefinitionKeyOperations, operations);
      return this;
    }

    public Builder processDefinitionKeys(final Long value, final Long... values) {
      return processDefinitionKeyOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    @SafeVarargs
    public final Builder processDefinitionKeyOperations(
        final Operation<Long> operation, final Operation<Long>... operations) {
      return processDefinitionKeyOperations(collectValues(operation, operations));
    }

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

    public Builder subscriptionKeyOperations(final List<Operation<Long>> operations) {
      subscriptionKeyOperations = addValuesToList(subscriptionKeyOperations, operations);
      return this;
    }

    public Builder subscriptionKeys(final Long value, final Long... values) {
      return subscriptionKeyOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    @SafeVarargs
    public final Builder subscriptionKeyOperations(
        final Operation<Long> operation, final Operation<Long>... operations) {
      return subscriptionKeyOperations(collectValues(operation, operations));
    }

    public Builder tenantIdOperations(final List<Operation<String>> operations) {
      tenantIdOperations = addValuesToList(tenantIdOperations, operations);
      return this;
    }

    public Builder tenantIds(final String value, final String... values) {
      return tenantIdOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    @SafeVarargs
    public final Builder tenantIdOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return tenantIdOperations(collectValues(operation, operations));
    }

    @Override
    public CorrelatedMessageSubscriptionFilter build() {
      return new CorrelatedMessageSubscriptionFilter(
          Objects.requireNonNullElse(correlationKeyOperations, Collections.emptyList()),
          Objects.requireNonNullElse(correlationTimeOperations, Collections.emptyList()),
          Objects.requireNonNullElse(flowNodeIdOperations, Collections.emptyList()),
          Objects.requireNonNullElse(flowNodeInstanceKeyOperations, Collections.emptyList()),
          Objects.requireNonNullElse(messageKeyOperations, Collections.emptyList()),
          Objects.requireNonNullElse(messageNameOperations, Collections.emptyList()),
          Objects.requireNonNullElse(partitionIdOperations, Collections.emptyList()),
          Objects.requireNonNullElse(processDefinitionIdOperations, Collections.emptyList()),
          Objects.requireNonNullElse(processDefinitionKeyOperations, Collections.emptyList()),
          Objects.requireNonNullElse(processInstanceKeyOperations, Collections.emptyList()),
          Objects.requireNonNullElse(subscriptionKeyOperations, Collections.emptyList()),
          Objects.requireNonNullElse(tenantIdOperations, Collections.emptyList()));
    }
  }
}
