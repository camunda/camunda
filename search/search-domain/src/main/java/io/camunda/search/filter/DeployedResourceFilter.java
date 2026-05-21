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

public record DeployedResourceFilter(
    List<Operation<Long>> resourceKeyOperations,
    List<Operation<String>> resourceNameOperations,
    List<Operation<String>> resourceIdOperations,
    List<Operation<String>> resourceTypeOperations,
    List<Operation<Integer>> versionOperations,
    List<Operation<String>> versionTagOperations,
    List<Operation<Long>> deploymentKeyOperations,
    List<String> tenantIds)
    implements FilterBase {

  public static final class Builder implements ObjectBuilder<DeployedResourceFilter> {

    private List<Operation<Long>> resourceKeyOperations;
    private List<Operation<String>> resourceNameOperations;
    private List<Operation<String>> resourceIdOperations;
    private List<Operation<String>> resourceTypeOperations;
    private List<Operation<Integer>> versionOperations;
    private List<Operation<String>> versionTagOperations;
    private List<Operation<Long>> deploymentKeyOperations;
    private List<String> tenantIds;

    public Builder resourceKeyOperations(final List<Operation<Long>> operations) {
      resourceKeyOperations = addValuesToList(resourceKeyOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder resourceKeyOperations(
        final Operation<Long> operation, final Operation<Long>... operations) {
      return resourceKeyOperations(collectValues(operation, operations));
    }

    public Builder resourceNameOperations(final List<Operation<String>> operations) {
      resourceNameOperations = addValuesToList(resourceNameOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder resourceNameOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return resourceNameOperations(collectValues(operation, operations));
    }

    public Builder resourceNames(final String value, final String... values) {
      return resourceNameOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder resourceIdOperations(final List<Operation<String>> operations) {
      resourceIdOperations = addValuesToList(resourceIdOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder resourceIdOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return resourceIdOperations(collectValues(operation, operations));
    }

    public Builder resourceIds(final String value, final String... values) {
      return resourceIdOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder resourceTypeOperations(final List<Operation<String>> operations) {
      resourceTypeOperations = addValuesToList(resourceTypeOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder resourceTypeOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return resourceTypeOperations(collectValues(operation, operations));
    }

    public Builder resourceTypes(final String value, final String... values) {
      return resourceTypeOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder versionOperations(final List<Operation<Integer>> operations) {
      versionOperations = addValuesToList(versionOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder versionOperations(
        final Operation<Integer> operation, final Operation<Integer>... operations) {
      return versionOperations(collectValues(operation, operations));
    }

    public Builder versions(final Integer value, final Integer... values) {
      return versionOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder versionTagOperations(final List<Operation<String>> operations) {
      versionTagOperations = addValuesToList(versionTagOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder versionTagOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return versionTagOperations(collectValues(operation, operations));
    }

    public Builder versionTags(final String value, final String... values) {
      return versionTagOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder deploymentKeyOperations(final List<Operation<Long>> operations) {
      deploymentKeyOperations = addValuesToList(deploymentKeyOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder deploymentKeyOperations(
        final Operation<Long> operation, final Operation<Long>... operations) {
      return deploymentKeyOperations(collectValues(operation, operations));
    }

    public Builder tenantIds(final String value, final String... values) {
      return tenantIds(collectValues(value, values));
    }

    public Builder tenantIds(final List<String> values) {
      tenantIds = addValuesToList(tenantIds, values);
      return this;
    }

    @Override
    public DeployedResourceFilter build() {
      return new DeployedResourceFilter(
          Objects.requireNonNullElse(resourceKeyOperations, Collections.emptyList()),
          Objects.requireNonNullElse(resourceNameOperations, Collections.emptyList()),
          Objects.requireNonNullElse(resourceIdOperations, Collections.emptyList()),
          Objects.requireNonNullElse(resourceTypeOperations, Collections.emptyList()),
          Objects.requireNonNullElse(versionOperations, Collections.emptyList()),
          Objects.requireNonNullElse(versionTagOperations, Collections.emptyList()),
          Objects.requireNonNullElse(deploymentKeyOperations, Collections.emptyList()),
          Objects.requireNonNullElse(tenantIds, Collections.emptyList()));
    }
  }
}
