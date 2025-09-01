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

    public Builder messageNames(final String value, final String... values) {
      return messageNameOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder messageNameOperations(final List<Operation<String>> operations) {
      messageNameOperations = addValuesToList(messageNameOperations, operations);
      return this;
    }

    public Builder correlationKeys(final String value, final String... values) {
      return correlationKeyOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder correlationKeyOperations(final List<Operation<String>> operations) {
      correlationKeyOperations = addValuesToList(correlationKeyOperations, operations);
      return this;
    }

    public Builder processInstanceKeys(final Long value, final Long... values) {
      return processInstanceKeyOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder processInstanceKeyOperations(final List<Operation<Long>> operations) {
      processInstanceKeyOperations = addValuesToList(processInstanceKeyOperations, operations);
      return this;
    }

    public Builder flowNodeInstanceKeys(final Long value, final Long... values) {
      return flowNodeInstanceKeyOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder flowNodeInstanceKeyOperations(final List<Operation<Long>> operations) {
      flowNodeInstanceKeyOperations = addValuesToList(flowNodeInstanceKeyOperations, operations);
      return this;
    }

    public Builder startEventIds(final String value, final String... values) {
      return startEventIdOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder startEventIdOperations(final List<Operation<String>> operations) {
      startEventIdOperations = addValuesToList(startEventIdOperations, operations);
      return this;
    }

    public Builder bpmnProcessIds(final String value, final String... values) {
      return bpmnProcessIdOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder bpmnProcessIdOperations(final List<Operation<String>> operations) {
      bpmnProcessIdOperations = addValuesToList(bpmnProcessIdOperations, operations);
      return this;
    }

    public Builder variables(final String value, final String... values) {
      return variablesOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder variablesOperations(final List<Operation<String>> operations) {
      variablesOperations = addValuesToList(variablesOperations, operations);
      return this;
    }

    public Builder tenantIds(final String value, final String... values) {
      return tenantIdOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder tenantIdOperations(final List<Operation<String>> operations) {
      tenantIdOperations = addValuesToList(tenantIdOperations, operations);
      return this;
    }

    public Builder dateTimes(final OffsetDateTime value, final OffsetDateTime... values) {
      return dateTimeOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder dateTimeOperations(final List<Operation<OffsetDateTime>> operations) {
      dateTimeOperations = addValuesToList(dateTimeOperations, operations);
      return this;
    }

    @Override
    public CorrelatedMessageFilter build() {
      return new CorrelatedMessageFilter(
          Objects.requireNonNullElse(messageKeyOperations, Collections.emptyList()),
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

  @Override
  public List<List<Operation<?>>> getFilterOperations() {
    return List.of(
        collectValues(messageKeyOperations),
        collectValues(messageNameOperations),
        collectValues(correlationKeyOperations),
        collectValues(processInstanceKeyOperations),
        collectValues(flowNodeInstanceKeyOperations),
        collectValues(startEventIdOperations),
        collectValues(bpmnProcessIdOperations),
        collectValues(variablesOperations),
        collectValues(tenantIdOperations),
        collectValues(dateTimeOperations));
  }
}