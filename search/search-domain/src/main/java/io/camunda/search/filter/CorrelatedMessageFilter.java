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

public record CorrelatedMessageFilter(
    List<Operation<Long>> messageKeyOperations,
    List<Operation<Long>> subscriptionKeyOperations,
    List<Operation<String>> messageNameOperations,
    List<Operation<String>> correlationKeyOperations,
    List<Operation<Long>> processInstanceKeyOperations,
    List<Operation<Long>> flowNodeInstanceKeyOperations,
    List<Operation<String>> startEventIdOperations,
    List<Operation<String>> bpmnProcessIdOperations,
    List<Operation<String>> variablesOperations,
    List<Operation<String>> tenantIdOperations,
    List<Operation<OffsetDateTime>> dateTimeOperations)
    implements FilterBase {

  public static final class Builder implements ObjectBuilder<CorrelatedMessageFilter> {

    private List<Operation<Long>> messageKeyOperations;
    private List<Operation<Long>> subscriptionKeyOperations;
    private List<Operation<String>> messageNameOperations;
    private List<Operation<String>> correlationKeyOperations;
    private List<Operation<Long>> processInstanceKeyOperations;
    private List<Operation<Long>> flowNodeInstanceKeyOperations;
    private List<Operation<String>> startEventIdOperations;
    private List<Operation<String>> bpmnProcessIdOperations;
    private List<Operation<String>> variablesOperations;
    private List<Operation<String>> tenantIdOperations;
    private List<Operation<OffsetDateTime>> dateTimeOperations;

    public Builder messageKeys(final Long value, final Long... values) {
      return messageKeyOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder messageKeyOperations(final List<Operation<Long>> operations) {
      messageKeyOperations = addValuesToList(messageKeyOperations, operations);
      return this;
    }

    public Builder subscriptionKeys(final Long value, final Long... values) {
      return subscriptionKeyOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder subscriptionKeyOperations(final List<Operation<Long>> operations) {
      subscriptionKeyOperations = addValuesToList(subscriptionKeyOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder messageKeyOperations(
        final Operation<Long> operation, final Operation<Long>... operations) {
      return messageKeyOperations(collectValues(operation, operations));
    }

    @SafeVarargs
    public final Builder subscriptionKeyOperations(
        final Operation<Long> operation, final Operation<Long>... operations) {
      return subscriptionKeyOperations(collectValues(operation, operations));
    }

    public Builder messageNames(final String value, final String... values) {
      return messageNameOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder messageNameOperations(final List<Operation<String>> operations) {
      messageNameOperations = addValuesToList(messageNameOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder messageNameOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return messageNameOperations(collectValues(operation, operations));
    }

    public Builder correlationKeys(final String value, final String... values) {
      return correlationKeyOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder correlationKeyOperations(final List<Operation<String>> operations) {
      correlationKeyOperations = addValuesToList(correlationKeyOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder correlationKeyOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return correlationKeyOperations(collectValues(operation, operations));
    }

    public Builder processInstanceKeys(final Long value, final Long... values) {
      return processInstanceKeyOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder processInstanceKeyOperations(final List<Operation<Long>> operations) {
      processInstanceKeyOperations = addValuesToList(processInstanceKeyOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder processInstanceKeyOperations(
        final Operation<Long> operation, final Operation<Long>... operations) {
      return processInstanceKeyOperations(collectValues(operation, operations));
    }

    public Builder flowNodeInstanceKeys(final Long value, final Long... values) {
      return flowNodeInstanceKeyOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder flowNodeInstanceKeyOperations(final List<Operation<Long>> operations) {
      flowNodeInstanceKeyOperations = addValuesToList(flowNodeInstanceKeyOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder flowNodeInstanceKeyOperations(
        final Operation<Long> operation, final Operation<Long>... operations) {
      return flowNodeInstanceKeyOperations(collectValues(operation, operations));
    }

    public Builder startEventIds(final String value, final String... values) {
      return startEventIdOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder startEventIdOperations(final List<Operation<String>> operations) {
      startEventIdOperations = addValuesToList(startEventIdOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder startEventIdOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return startEventIdOperations(collectValues(operation, operations));
    }

    public Builder bpmnProcessIds(final String value, final String... values) {
      return bpmnProcessIdOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder bpmnProcessIdOperations(final List<Operation<String>> operations) {
      bpmnProcessIdOperations = addValuesToList(bpmnProcessIdOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder bpmnProcessIdOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return bpmnProcessIdOperations(collectValues(operation, operations));
    }

    public Builder variables(final String value, final String... values) {
      return variablesOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder variablesOperations(final List<Operation<String>> operations) {
      variablesOperations = addValuesToList(variablesOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder variablesOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return variablesOperations(collectValues(operation, operations));
    }

    public Builder tenantIds(final String value, final String... values) {
      return tenantIdOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder tenantIdOperations(final List<Operation<String>> operations) {
      tenantIdOperations = addValuesToList(tenantIdOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder tenantIdOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return tenantIdOperations(collectValues(operation, operations));
    }

    public Builder dateTimes(final OffsetDateTime value, final OffsetDateTime... values) {
      return dateTimeOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder dateTimeOperations(final List<Operation<OffsetDateTime>> operations) {
      dateTimeOperations = addValuesToList(dateTimeOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder dateTimeOperations(
        final Operation<OffsetDateTime> operation, final Operation<OffsetDateTime>... operations) {
      return dateTimeOperations(collectValues(operation, operations));
    }

    @Override
    public CorrelatedMessageFilter build() {
      return new CorrelatedMessageFilter(
          Objects.requireNonNullElse(messageKeyOperations, Collections.emptyList()),
          Objects.requireNonNullElse(subscriptionKeyOperations, Collections.emptyList()),
          Objects.requireNonNullElse(messageNameOperations, Collections.emptyList()),
          Objects.requireNonNullElse(correlationKeyOperations, Collections.emptyList()),
          Objects.requireNonNullElse(processInstanceKeyOperations, Collections.emptyList()),
          Objects.requireNonNullElse(flowNodeInstanceKeyOperations, Collections.emptyList()),
          Objects.requireNonNullElse(startEventIdOperations, Collections.emptyList()),
          Objects.requireNonNullElse(bpmnProcessIdOperations, Collections.emptyList()),
          Objects.requireNonNullElse(variablesOperations, Collections.emptyList()),
          Objects.requireNonNullElse(tenantIdOperations, Collections.emptyList()),
          Objects.requireNonNullElse(dateTimeOperations, Collections.emptyList()));
    }
  }
}
