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

public record JobFilter(
    List<Operation<OffsetDateTime>> deadlineOperations,
    List<Operation<String>> deniedReasonOperations,
    List<Operation<Long>> elementInstanceKeyOperations,
    List<Operation<String>> elementIdOperations,
    List<Operation<OffsetDateTime>> endTimeOperations,
    List<Operation<String>> errorCodeOperations,
    List<Operation<String>> errorMessageOperations,
    Boolean hasFailedWithRetriesLeft,
    Boolean isDenied,
    List<Operation<Long>> jobKeyOperations,
    List<Operation<String>> kindOperations,
    List<Operation<String>> listenerEventTypeOperations,
    List<Operation<Long>> processDefinitionKeyOperations,
    List<Operation<String>> processDefinitionIdOperations,
    List<Operation<Long>> processInstanceKeyOperations,
    List<Operation<Integer>> retriesOperations,
    List<Operation<String>> stateOperations,
    List<Operation<String>> tenantIdOperations,
    List<Operation<String>> typeOperations,
    List<Operation<String>> workerOperations)
    implements FilterBase {

  public static final class Builder implements ObjectBuilder<JobFilter> {
    private List<Operation<OffsetDateTime>> deadlineOperations;
    private List<Operation<String>> deniedReasonOperations;
    private List<Operation<OffsetDateTime>> endTimeOperations;
    private List<Operation<String>> errorCodeOperations;
    private List<Operation<String>> errorMessageOperations;
    private Boolean hasFailedWithRetriesLeft;
    private Boolean isDenied;
    private List<Operation<Integer>> retriesOperations;
    private List<Operation<Long>> jobKeyOperations;
    private List<Operation<String>> kindOperations;
    private List<Operation<String>> listenerEventTypeOperations;
    private List<Operation<String>> processDefinitionIdOperations;
    private List<Operation<String>> tenantIdOperations;
    private List<Operation<String>> typeOperations;
    private List<Operation<String>> workerOperations;
    private List<Operation<String>> stateOperations;
    private List<Operation<Long>> processDefinitionKeyOperations;
    private List<Operation<Long>> processInstanceKeyOperations;
    private List<Operation<String>> elementIdOperation;
    private List<Operation<Long>> elementInstanceKeyOperations;

    public Builder deadlineOperations(final List<Operation<OffsetDateTime>> operations) {
      deadlineOperations = addValuesToList(deadlineOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder deadlineOperations(
        final Operation<OffsetDateTime> operation, final Operation<OffsetDateTime>... operations) {
      return deadlineOperations(collectValues(operation, operations));
    }

    public Builder deadlines(final OffsetDateTime value, final OffsetDateTime... values) {
      return deadlineOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder deniedReasonOperations(final List<Operation<String>> operations) {
      deniedReasonOperations = addValuesToList(deniedReasonOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder deniedReasonOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return deniedReasonOperations(collectValues(operation, operations));
    }

    public Builder deniedReasons(final String value, final String... values) {
      return deniedReasonOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder endTimeOperations(final List<Operation<OffsetDateTime>> operations) {
      endTimeOperations = addValuesToList(endTimeOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder endTimeOperations(
        final Operation<OffsetDateTime> operation, final Operation<OffsetDateTime>... operations) {
      return endTimeOperations(collectValues(operation, operations));
    }

    public Builder endTimes(final OffsetDateTime value, final OffsetDateTime... values) {
      return endTimeOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder errorCodeOperations(final List<Operation<String>> operations) {
      errorCodeOperations = addValuesToList(errorCodeOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder errorCodeOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return errorCodeOperations(collectValues(operation, operations));
    }

    public Builder errorCodes(final String value, final String... values) {
      return errorCodeOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder errorMessageOperations(final List<Operation<String>> operations) {
      errorMessageOperations = addValuesToList(errorMessageOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder errorMessageOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return errorMessageOperations(collectValues(operation, operations));
    }

    public Builder errorMessages(final String value, final String... values) {
      return errorMessageOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder hasFailedWithRetriesLeft(final Boolean hasFailedWithRetriesLeft) {
      this.hasFailedWithRetriesLeft = hasFailedWithRetriesLeft;
      return this;
    }

    public Builder isDenied(final Boolean isDenied) {
      this.isDenied = isDenied;
      return this;
    }

    public Builder retriesOperations(final List<Operation<Integer>> operations) {
      retriesOperations = addValuesToList(retriesOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder retriesOperations(
        final Operation<Integer> operation, final Operation<Integer>... operations) {
      return retriesOperations(collectValues(operation, operations));
    }

    public Builder retries(final Integer value, final Integer... values) {
      return retriesOperations(FilterUtil.mapDefaultToOperation(value, values));
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

    public Builder kindOperations(final List<Operation<String>> operations) {
      kindOperations = addValuesToList(kindOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder kindOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return kindOperations(collectValues(operation, operations));
    }

    public Builder kinds(final String value, final String... values) {
      return kindOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder listenerEventTypeOperations(final List<Operation<String>> operations) {
      listenerEventTypeOperations = addValuesToList(listenerEventTypeOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder listenerEventTypeOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return listenerEventTypeOperations(collectValues(operation, operations));
    }

    public Builder listenerEventTypes(final String value, final String... values) {
      return listenerEventTypeOperations(FilterUtil.mapDefaultToOperation(value, values));
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

    public Builder processDefinitionIds(final String value, final String... values) {
      return processDefinitionIdOperations(FilterUtil.mapDefaultToOperation(value, values));
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

    public Builder tenantIds(final String value, final String... values) {
      return tenantIdOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder stateOperations(final List<Operation<String>> operations) {
      stateOperations = addValuesToList(stateOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder stateOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return stateOperations(collectValues(operation, operations));
    }

    public Builder states(final String value, final String... values) {
      return stateOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder typeOperations(final List<Operation<String>> operations) {
      typeOperations = addValuesToList(typeOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder typeOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return typeOperations(collectValues(operation, operations));
    }

    public Builder types(final String value, final String... values) {
      return typeOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder workerOperations(final List<Operation<String>> operations) {
      workerOperations = addValuesToList(workerOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder workerOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return workerOperations(collectValues(operation, operations));
    }

    public Builder workers(final String value, final String... values) {
      return workerOperations(FilterUtil.mapDefaultToOperation(value, values));
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

    public Builder processInstanceKeys(final Long value, final Long... values) {
      return processInstanceKeyOperations(FilterUtil.mapDefaultToOperation(value, values));
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

    public Builder processDefinitionKeys(final Long value, final Long... values) {
      return processDefinitionKeyOperations(FilterUtil.mapDefaultToOperation(value, values));
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

    public Builder elementIdOperations(final List<Operation<String>> operations) {
      elementIdOperation = addValuesToList(elementIdOperation, operations);
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

    @Override
    public JobFilter build() {
      return new JobFilter(
          Objects.requireNonNullElse(deadlineOperations, Collections.emptyList()),
          Objects.requireNonNullElse(deniedReasonOperations, Collections.emptyList()),
          Objects.requireNonNullElse(elementInstanceKeyOperations, Collections.emptyList()),
          Objects.requireNonNullElse(elementIdOperation, Collections.emptyList()),
          Objects.requireNonNullElse(endTimeOperations, Collections.emptyList()),
          Objects.requireNonNullElse(errorCodeOperations, Collections.emptyList()),
          Objects.requireNonNullElse(errorMessageOperations, Collections.emptyList()),
          hasFailedWithRetriesLeft,
          isDenied,
          Objects.requireNonNullElse(jobKeyOperations, Collections.emptyList()),
          Objects.requireNonNullElse(kindOperations, Collections.emptyList()),
          Objects.requireNonNullElse(listenerEventTypeOperations, Collections.emptyList()),
          Objects.requireNonNullElse(processDefinitionKeyOperations, Collections.emptyList()),
          Objects.requireNonNullElse(processDefinitionIdOperations, Collections.emptyList()),
          Objects.requireNonNullElse(processInstanceKeyOperations, Collections.emptyList()),
          Objects.requireNonNullElse(retriesOperations, Collections.emptyList()),
          Objects.requireNonNullElse(stateOperations, Collections.emptyList()),
          Objects.requireNonNullElse(tenantIdOperations, Collections.emptyList()),
          Objects.requireNonNullElse(typeOperations, Collections.emptyList()),
          Objects.requireNonNullElse(workerOperations, Collections.emptyList()));
    }
  }
}
