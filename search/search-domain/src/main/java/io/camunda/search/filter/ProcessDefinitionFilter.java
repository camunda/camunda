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

public record ProcessDefinitionFilter(
    Boolean isLatestVersion,
    List<Long> processDefinitionKeys,
    List<Operation<String>> namesOperations,
    List<Operation<String>> processDefinitionIdsOperations,
    List<String> resourceNames,
    List<Integer> versions,
    List<String> versionTags,
    List<String> tenantIds)
    implements FilterBase {

  public static final class Builder implements ObjectBuilder<ProcessDefinitionFilter> {

    private Boolean isLatestVersion;
    private List<String> tenantIds;
    private List<Long> processDefinitionKeys;
    private List<Operation<String>> namesOperations;
    private List<Operation<String>> processDefinitionIdsOperations;
    private List<String> resourceNames;
    private List<Integer> versions;
    private List<String> versionTags;

    public Builder processDefinitionKeys(final List<Long> values) {
      processDefinitionKeys = addValuesToList(processDefinitionKeys, values);
      return this;
    }

    public Builder processDefinitionKeys(final Long value, final Long... values) {
      return processDefinitionKeys(collectValues(value, values));
    }

    public Builder namesOperations(final List<Operation<String>> operations) {
      namesOperations = addValuesToList(namesOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder namesOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return namesOperations(collectValues(operation, operations));
    }

    public Builder names(final String value, final String... values) {
      return namesOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    @SafeVarargs
    public final Builder processDefinitionIdsOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return processDefinitionIdsOperations(collectValues(operation, operations));
    }

    public Builder processDefinitionIdsOperations(final List<Operation<String>> operations) {
      processDefinitionIdsOperations = addValuesToList(processDefinitionIdsOperations, operations);
      return this;
    }

    public Builder processDefinitionIds(final String value, final String... values) {
      processDefinitionIdsOperations(FilterUtil.mapDefaultToOperation(value, values));
      return this;
    }

    public Builder resourceNames(final List<String> values) {
      resourceNames = addValuesToList(resourceNames, values);
      return this;
    }

    public Builder resourceNames(final String value, final String... values) {
      return resourceNames(collectValues(value, values));
    }

    public Builder versions(final List<Integer> values) {
      versions = addValuesToList(versions, values);
      return this;
    }

    public Builder versions(final Integer value, final Integer... values) {
      return versions(collectValues(value, values));
    }

    public Builder versionTags(final List<String> values) {
      versionTags = addValuesToList(versionTags, values);
      return this;
    }

    public Builder versionTags(final String value, final String... values) {
      return versionTags(collectValues(value, values));
    }

    public Builder tenantIds(final String value, final String... values) {
      return tenantIds(collectValues(value, values));
    }

    public Builder tenantIds(final List<String> values) {
      tenantIds = addValuesToList(tenantIds, values);
      return this;
    }

    public Builder isLatestVersion(final Boolean latest) {
      isLatestVersion = latest;
      return this;
    }

    @Override
    public ProcessDefinitionFilter build() {
      return new ProcessDefinitionFilter(
          Objects.requireNonNullElse(isLatestVersion, false),
          Objects.requireNonNullElse(processDefinitionKeys, Collections.emptyList()),
          Objects.requireNonNullElse(namesOperations, Collections.emptyList()),
          Objects.requireNonNullElse(processDefinitionIdsOperations, Collections.emptyList()),
          Objects.requireNonNullElse(resourceNames, Collections.emptyList()),
          Objects.requireNonNullElse(versions, Collections.emptyList()),
          Objects.requireNonNullElse(versionTags, Collections.emptyList()),
          Objects.requireNonNullElse(tenantIds, Collections.emptyList()));
    }
  }
}
