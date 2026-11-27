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
import java.util.function.Function;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public record CaseFilter(
    List<Operation<Long>> caseKeyOperations,
    List<Operation<Long>> processInstanceKeyOperations,
    List<Operation<String>> processDefinitionIdOperations,
    List<Operation<String>> businessIdOperations,
    List<Operation<String>> titleOperations,
    List<Operation<String>> stateOperations,
    List<Operation<String>> tenantIdOperations,
    List<CaseStageFilter> stageFilters)
    implements FilterBase {

  public static final class Builder implements ObjectBuilder<CaseFilter> {
    private @Nullable List<Operation<Long>> caseKeyOperations;
    private @Nullable List<Operation<Long>> processInstanceKeyOperations;
    private @Nullable List<Operation<String>> processDefinitionIdOperations;
    private @Nullable List<Operation<String>> businessIdOperations;
    private @Nullable List<Operation<String>> titleOperations;
    private @Nullable List<Operation<String>> stateOperations;
    private @Nullable List<Operation<String>> tenantIdOperations;
    private @Nullable List<CaseStageFilter> stageFilters;

    public Builder caseKeyOperations(final List<Operation<Long>> operations) {
      caseKeyOperations = addValuesToList(caseKeyOperations, operations);
      return this;
    }

    public Builder caseKeys(final Long value, final Long... values) {
      return caseKeyOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    @SafeVarargs
    public final Builder caseKeyOperations(
        final Operation<Long> operation, final Operation<Long>... operations) {
      return caseKeyOperations(collectValues(operation, operations));
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

    public Builder businessIdOperations(final List<Operation<String>> operations) {
      businessIdOperations = addValuesToList(businessIdOperations, operations);
      return this;
    }

    public Builder businessIds(final String value, final String... values) {
      return businessIdOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    @SafeVarargs
    public final Builder businessIdOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return businessIdOperations(collectValues(operation, operations));
    }

    public Builder titleOperations(final List<Operation<String>> operations) {
      titleOperations = addValuesToList(titleOperations, operations);
      return this;
    }

    public Builder titles(final String value, final String... values) {
      return titleOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    @SafeVarargs
    public final Builder titleOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return titleOperations(collectValues(operation, operations));
    }

    public Builder stateOperations(final List<Operation<String>> operations) {
      stateOperations = addValuesToList(stateOperations, operations);
      return this;
    }

    public Builder states(final String value, final String... values) {
      return stateOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    @SafeVarargs
    public final Builder stateOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return stateOperations(collectValues(operation, operations));
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

    public Builder stages(final List<CaseStageFilter> values) {
      stageFilters = addValuesToList(stageFilters, values);
      return this;
    }

    public Builder stages(final CaseStageFilter value, final CaseStageFilter... values) {
      return stages(collectValues(value, values));
    }

    public Builder stages(
        final Function<CaseStageFilter.Builder, ObjectBuilder<CaseStageFilter>> fn) {
      return stages(FilterBuilders.caseStage(fn));
    }

    @Override
    public CaseFilter build() {
      return new CaseFilter(
          Objects.requireNonNullElseGet(caseKeyOperations, Collections::emptyList),
          Objects.requireNonNullElseGet(processInstanceKeyOperations, Collections::emptyList),
          Objects.requireNonNullElseGet(processDefinitionIdOperations, Collections::emptyList),
          Objects.requireNonNullElseGet(businessIdOperations, Collections::emptyList),
          Objects.requireNonNullElseGet(titleOperations, Collections::emptyList),
          Objects.requireNonNullElseGet(stateOperations, Collections::emptyList),
          Objects.requireNonNullElseGet(tenantIdOperations, Collections::emptyList),
          Objects.requireNonNullElseGet(stageFilters, Collections::emptyList));
    }
  }
}
