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

public record MessageSubscriptionFilter(
    List<Operation<Long>> messageSubscriptionKeyOperations,
    List<Operation<String>> processDefinitionIdOperations,
    List<Operation<Long>> processDefinitionKeyOperations,
    List<Operation<Long>> processInstanceKeyOperations,
    List<Operation<String>> flowNodeIdOperations,
    List<Operation<Long>> flowNodeInstanceKeyOperations,
    List<Operation<String>> messageSubscriptionStateOperations,
    List<Operation<OffsetDateTime>> dateTimeOperations,
    List<Operation<String>> messageNameOperations,
    List<Operation<String>> correlationKeyOperations,
    List<Operation<String>> tenantIdOperations)
    implements FilterBase {

  public static final class Builder implements ObjectBuilder<MessageSubscriptionFilter> {

    private List<Operation<Long>> messageSubscriptionKeyOperations;
    private List<Operation<String>> processDefinitionIdOperations;
    private List<Operation<Long>> processDefinitionKeyOperations;
    private List<Operation<Long>> processInstanceKeyOperations;
    private List<Operation<String>> flowNodeIdOperations;
    private List<Operation<Long>> flowNodeInstanceKeyOperations;
    private List<Operation<String>> messageSubscriptionStateOperations;
    private List<Operation<OffsetDateTime>> dateTimeOperations;
    private List<Operation<String>> messageNameOperations;
    private List<Operation<String>> correlationKeyOperations;
    private List<Operation<String>> tenantIdOperations;

    public Builder messageSubscriptionKeys(final Long value, final Long... values) {
      return messageSubscriptionKeyOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    @SafeVarargs
    public final Builder messageSubscriptionKeyOperations(
        final Operation<Long> operation, final Operation<Long>... operations) {
      return messageSubscriptionKeyOperations(collectValues(operation, operations));
    }

    public Builder messageSubscriptionKeyOperations(final List<Operation<Long>> operations) {
      messageSubscriptionKeyOperations = operations;
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

    public Builder processDefinitionIdOperations(final List<Operation<String>> operations) {
      processDefinitionIdOperations = operations;
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

    public Builder processDefinitionKeyOperations(final List<Operation<Long>> operations) {
      processDefinitionKeyOperations = operations;
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

    public Builder processInstanceKeyOperations(final List<Operation<Long>> operations) {
      processInstanceKeyOperations = addValuesToList(processInstanceKeyOperations, operations);
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

    public Builder flowNodeIdOperations(final List<Operation<String>> operations) {
      flowNodeIdOperations = addValuesToList(flowNodeIdOperations, operations);
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

    public Builder flowNodeInstanceKeyOperations(final List<Operation<Long>> operations) {
      flowNodeInstanceKeyOperations = addValuesToList(flowNodeInstanceKeyOperations, operations);
      return this;
    }

    public Builder messageSubscriptionStates(final String value, final String... values) {
      return messageSubscriptionStateOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    @SafeVarargs
    public final Builder messageSubscriptionStateOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return messageSubscriptionStateOperations(collectValues(operation, operations));
    }

    public Builder messageSubscriptionStateOperations(final List<Operation<String>> operations) {
      messageSubscriptionStateOperations =
          addValuesToList(messageSubscriptionStateOperations, operations);
      return this;
    }

    public Builder dateTimes(final OffsetDateTime value, final OffsetDateTime... values) {
      return dateTimeOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    @SafeVarargs
    public final Builder dateTimeOperations(
        final Operation<OffsetDateTime> operation, final Operation<OffsetDateTime>... operations) {
      return dateTimeOperations(collectValues(operation, operations));
    }

    public Builder dateTimeOperations(final List<Operation<OffsetDateTime>> operations) {
      dateTimeOperations = addValuesToList(dateTimeOperations, operations);
      return this;
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

    @Override
    public MessageSubscriptionFilter build() {
      return new MessageSubscriptionFilter(
          Objects.requireNonNullElse(messageSubscriptionKeyOperations, Collections.emptyList()),
          Objects.requireNonNullElse(processDefinitionIdOperations, Collections.emptyList()),
          Objects.requireNonNullElse(processDefinitionKeyOperations, Collections.emptyList()),
          Objects.requireNonNullElse(processInstanceKeyOperations, Collections.emptyList()),
          Objects.requireNonNullElse(flowNodeIdOperations, Collections.emptyList()),
          Objects.requireNonNullElse(flowNodeInstanceKeyOperations, Collections.emptyList()),
          Objects.requireNonNullElse(messageSubscriptionStateOperations, Collections.emptyList()),
          Objects.requireNonNullElse(dateTimeOperations, Collections.emptyList()),
          Objects.requireNonNullElse(messageNameOperations, Collections.emptyList()),
          Objects.requireNonNullElse(correlationKeyOperations, Collections.emptyList()),
          Objects.requireNonNullElse(tenantIdOperations, Collections.emptyList()));
    }
  }
}
