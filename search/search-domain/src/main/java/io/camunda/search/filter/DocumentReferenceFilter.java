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

public record DocumentReferenceFilter(
    List<Operation<Long>> processInstanceKeyOperations,
    List<Operation<Long>> variableKeyOperations,
    List<String> tenantIds,
    List<Operation<String>> documentIdOperations)
    implements FilterBase {

  public static final class Builder implements ObjectBuilder<DocumentReferenceFilter> {
    private List<Operation<Long>> processInstanceKeyOperations;
    private List<Operation<Long>> variableKeyOperations;
    private List<String> tenantIds;
    private List<Operation<String>> documentIdOperations;

    public Builder processInstanceKeyOperations(final List<Operation<Long>> operations) {
      processInstanceKeyOperations = addValuesToList(processInstanceKeyOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder processInstanceKeyOperations(
        final Operation<Long> operation, final Operation<Long>... operations) {
      return processInstanceKeyOperations(collectValues(operation, operations));
    }

    public Builder processInstanceKey(final long value) {
      return processInstanceKeyOperations(FilterUtil.mapDefaultToOperation(value));
    }

    public Builder variableKeyOperations(final List<Operation<Long>> operations) {
      variableKeyOperations = addValuesToList(variableKeyOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder variableKeyOperations(
        final Operation<Long> operation, final Operation<Long>... operations) {
      return variableKeyOperations(collectValues(operation, operations));
    }

    public Builder variableKey(final long value) {
      return variableKeyOperations(FilterUtil.mapDefaultToOperation(value));
    }

    public Builder tenantIds(final String value, final String... values) {
      return tenantIds(collectValues(value, values));
    }

    public Builder tenantIds(final List<String> values) {
      tenantIds = addValuesToList(tenantIds, values);
      return this;
    }

    public Builder documentIdOperations(final List<Operation<String>> operations) {
      documentIdOperations = addValuesToList(documentIdOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder documentIdOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return documentIdOperations(collectValues(operation, operations));
    }

    public Builder documentIds(final String value, final String... values) {
      return documentIdOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    @Override
    public DocumentReferenceFilter build() {
      return new DocumentReferenceFilter(
          Objects.requireNonNullElseGet(processInstanceKeyOperations, Collections::emptyList),
          Objects.requireNonNullElseGet(variableKeyOperations, Collections::emptyList),
          Objects.requireNonNullElseGet(tenantIds, Collections::emptyList),
          Objects.requireNonNullElseGet(documentIdOperations, Collections::emptyList));
    }
  }
}
