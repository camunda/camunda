/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.search.filter;

import static io.camunda.util.CollectionUtil.addValuesToList;
import static io.camunda.util.CollectionUtil.collectValues;

import io.camunda.util.ObjectBuilder;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public record DecisionDefinitionFilter(
    List<String> ids,
    List<Long> keys,
    List<String> decisionIds,
    List<String> names,
    List<Integer> versions,
    List<String> decisionRequirementsIds,
    List<Long> decisionRequirementsKeys,
    List<String> decisionRequirementsNames,
    List<Integer> decisionRequirementsVersions,
    List<String> tenantIds)
    implements FilterBase {

  public static final class Builder implements ObjectBuilder<DecisionDefinitionFilter> {

    private List<String> ids;
    private List<Long> keys;
    private List<String> decisionIds;
    private List<String> names;
    private List<Integer> versions;
    private List<String> decisionRequirementsIds;
    private List<Long> decisionRequirementsKeys;
    private List<String> decisionRequirementsNames;
    private List<Integer> decisionRequirementsVersions;
    private List<String> tenantIds;

    public Builder ids(final List<String> values) {
      this.ids = addValuesToList(this.ids, values);
      return this;
    }

    public Builder ids(final String value, final String... values) {
      return ids(collectValues(value, values));
    }

    public Builder keys(final List<Long> values) {
      this.keys = addValuesToList(this.keys, values);
      return this;
    }

    public Builder keys(final Long value, final Long... values) {
      return keys(collectValues(value, values));
    }

    public Builder decisionIds(final List<String> values) {
      this.decisionIds = addValuesToList(this.decisionIds, values);
      return this;
    }

    public Builder decisionIds(final String value, final String... values) {
      return decisionIds(collectValues(value, values));
    }

    public Builder names(final List<String> values) {
      this.names = addValuesToList(this.names, values);
      return this;
    }

    public Builder names(final String value, final String... values) {
      return names(collectValues(value, values));
    }

    public Builder versions(final List<Integer> values) {
      this.versions = addValuesToList(this.versions, values);
      return this;
    }

    public Builder versions(final Integer value, final Integer... values) {
      return versions(collectValues(value, values));
    }

    public Builder decisionRequirementsIds(final List<String> values) {
      this.decisionRequirementsIds = addValuesToList(this.decisionRequirementsIds, values);
      return this;
    }

    public Builder decisionRequirementsIds(final String value, final String... values) {
      return decisionRequirementsIds(collectValues(value, values));
    }

    public Builder decisionRequirementsKeys(final List<Long> values) {
      this.decisionRequirementsKeys = addValuesToList(this.decisionRequirementsKeys, values);
      return this;
    }

    public Builder decisionRequirementsKeys(final Long value, final Long... values) {
      return decisionRequirementsKeys(collectValues(value, values));
    }

    public Builder decisionRequirementsNames(final List<String> values) {
      this.decisionRequirementsNames = addValuesToList(this.decisionRequirementsNames, values);
      return this;
    }

    public Builder decisionRequirementsNames(final String value, final String... values) {
      return decisionRequirementsNames(collectValues(value, values));
    }

    public Builder decisionRequirementsVersions(final List<Integer> values) {
      this.decisionRequirementsVersions =
          addValuesToList(this.decisionRequirementsVersions, values);
      return this;
    }

    public Builder decisionRequirementsVersions(final Integer value, final Integer... values) {
      return decisionRequirementsVersions(collectValues(value, values));
    }

    public Builder tenantIds(final List<String> values) {
      this.tenantIds = addValuesToList(this.tenantIds, values);
      return this;
    }

    public Builder tenantIds(final String value, final String... values) {
      return tenantIds(collectValues(value, values));
    }

    @Override
    public DecisionDefinitionFilter build() {
      return new DecisionDefinitionFilter(
          Objects.requireNonNullElse(ids, Collections.emptyList()),
          Objects.requireNonNullElse(keys, Collections.emptyList()),
          Objects.requireNonNullElse(decisionIds, Collections.emptyList()),
          Objects.requireNonNullElse(names, Collections.emptyList()),
          Objects.requireNonNullElse(versions, Collections.emptyList()),
          Objects.requireNonNullElse(decisionRequirementsIds, Collections.emptyList()),
          Objects.requireNonNullElse(decisionRequirementsKeys, Collections.emptyList()),
          Objects.requireNonNullElse(decisionRequirementsNames, Collections.emptyList()),
          Objects.requireNonNullElse(decisionRequirementsVersions, Collections.emptyList()),
          Objects.requireNonNullElse(tenantIds, Collections.emptyList()));
    }
  }
}
