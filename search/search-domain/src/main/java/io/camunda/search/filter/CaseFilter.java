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

public record CaseFilter(
    List<Operation<Long>> caseKeyOperations,
    List<Operation<Long>> processDefinitionKeyOperations,
    List<Operation<String>> processDefinitionIdOperations,
    List<Operation<String>> businessIdOperations,
    List<Operation<String>> titleOperations,
    List<Operation<String>> stateOperations,
    List<Operation<String>> tenantIdOperations)
    implements FilterBase {

  public static final class Builder implements ObjectBuilder<CaseFilter> {
    List<Operation<Long>> caseKeyOperations;
    List<Operation<Long>> processDefinitionKeyOperations;
    List<Operation<String>> processDefinitionIdOperations;
    List<Operation<String>> businessIdOperations;
    List<Operation<String>> titleOperations;
    List<Operation<String>> stateOperations;
    List<Operation<String>> tenantIdOperations;

    @SafeVarargs
    public final Builder caseKeyOperations(
        final Operation<Long> operation, final Operation<Long>... operations) {
      return caseKeyOperations(collectValues(operation, operations));
    }

    public Builder caseKeyOperations(final List<Operation<Long>> operations) {
      caseKeyOperations = addValuesToList(caseKeyOperations, operations);
      return this;
    }

    public Builder caseKeys(final Long value, final Long... values) {
      return caseKeyOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    @SafeVarargs
    public final Builder processDefinitionKeyOperations(
        final Operation<Long> operation, final Operation<Long>... operations) {
      return processDefinitionKeyOperations(collectValues(operation, operations));
    }

    public Builder processDefinitionKeyOperations(final List<Operation<Long>> operations) {
      processDefinitionKeyOperations = addValuesToList(processDefinitionKeyOperations, operations);
      return this;
    }

    public Builder processDefinitionKeys(final Long value, final Long... values) {
      return processDefinitionKeyOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    @SafeVarargs
    public final Builder processDefinitionIdOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return processDefinitionIdOperations(collectValues(operation, operations));
    }

    public Builder processDefinitionIdOperations(final List<Operation<String>> operations) {
      processDefinitionIdOperations = addValuesToList(processDefinitionIdOperations, operations);
      return this;
    }

    public Builder processDefinitionIds(final String value, final String... values) {
      return processDefinitionIdOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    @SafeVarargs
    public final Builder businessIdOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return businessIdOperations(collectValues(operation, operations));
    }

    public Builder businessIdOperations(final List<Operation<String>> operations) {
      businessIdOperations = addValuesToList(businessIdOperations, operations);
      return this;
    }

    public Builder businessIds(final String value, final String... values) {
      return businessIdOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    @SafeVarargs
    public final Builder titleOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return titleOperations(collectValues(operation, operations));
    }

    public Builder titleOperations(final List<Operation<String>> operations) {
      titleOperations = addValuesToList(titleOperations, operations);
      return this;
    }

    public Builder titles(final String value, final String... values) {
      return titleOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    @SafeVarargs
    public final Builder stateOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return stateOperations(collectValues(operation, operations));
    }

    public Builder stateOperations(final List<Operation<String>> operations) {
      stateOperations = addValuesToList(stateOperations, operations);
      return this;
    }

    public Builder states(final String value, final String... values) {
      return stateOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    @SafeVarargs
    public final Builder tenantIdOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return tenantIdOperations(collectValues(operation, operations));
    }

    public Builder tenantIdOperations(final List<Operation<String>> operations) {
      tenantIdOperations = addValuesToList(tenantIdOperations, operations);
      return this;
    }

    public Builder tenantIds(final String value, final String... values) {
      return tenantIdOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    @Override
    public CaseFilter build() {
      return new CaseFilter(
          Objects.requireNonNullElse(caseKeyOperations, Collections.emptyList()),
          Objects.requireNonNullElse(processDefinitionKeyOperations, Collections.emptyList()),
          Objects.requireNonNullElse(processDefinitionIdOperations, Collections.emptyList()),
          Objects.requireNonNullElse(businessIdOperations, Collections.emptyList()),
          Objects.requireNonNullElse(titleOperations, Collections.emptyList()),
          Objects.requireNonNullElse(stateOperations, Collections.emptyList()),
          Objects.requireNonNullElse(tenantIdOperations, Collections.emptyList()));
    }
  }
}
