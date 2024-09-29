/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.filter;

import static io.camunda.util.CollectionUtil.addValuesToList;
import static io.camunda.util.CollectionUtil.collectValuesAsList;

import io.camunda.util.ObjectBuilder;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public record DecisionDefinitionFilter(
    List<Long> decisionDefinitionKeys,
    List<String> decisionDefinitionIds,
    List<String> decisionDefinitionNames,
    List<Integer> decisionDefinitionVersions,
    List<String> decisionRequirementsIds,
    List<Long> decisionRequirementsKeys,
    List<String> tenantIds)
    implements FilterBase {

  public static final class Builder implements ObjectBuilder<DecisionDefinitionFilter> {

    private List<Long> decisionDefinitionKeys;
    private List<String> decisionDefinitionIds;
    private List<String> decisionDefinitionNames;
    private List<Integer> decisionDefinitionVersions;
    private List<String> decisionRequirementsIds;
    private List<Long> decisionRequirementsKeys;
    private List<String> tenantIds;

    public Builder decisionDefinitionKeys(final List<Long> values) {
      decisionDefinitionKeys = addValuesToList(decisionDefinitionKeys, values);
      return this;
    }

    public Builder decisionDefinitionKeys(final Long... values) {
      return decisionDefinitionKeys(collectValuesAsList(values));
    }

    public Builder decisionDefinitionIds(final List<String> values) {
      decisionDefinitionIds = addValuesToList(decisionDefinitionIds, values);
      return this;
    }

    public Builder decisionDefinitionIds(final String... values) {
      return decisionDefinitionIds(collectValuesAsList(values));
    }

    public Builder decisionDefinitionNames(final List<String> values) {
      decisionDefinitionNames = addValuesToList(decisionDefinitionNames, values);
      return this;
    }

    public Builder decisionDefinitionNames(final String... values) {
      return decisionDefinitionNames(collectValuesAsList(values));
    }

    public Builder decisionDefinitionVersions(final List<Integer> values) {
      decisionDefinitionVersions = addValuesToList(decisionDefinitionVersions, values);
      return this;
    }

    public Builder decisionDefinitionVersions(final Integer... values) {
      return decisionDefinitionVersions(collectValuesAsList(values));
    }

    public Builder decisionRequirementsIds(final List<String> values) {
      decisionRequirementsIds = addValuesToList(decisionRequirementsIds, values);
      return this;
    }

    public Builder decisionRequirementsIds(final String... values) {
      return decisionRequirementsIds(collectValuesAsList(values));
    }

    public Builder decisionRequirementsKeys(final List<Long> values) {
      decisionRequirementsKeys = addValuesToList(decisionRequirementsKeys, values);
      return this;
    }

    public Builder decisionRequirementsKeys(final Long... values) {
      return decisionRequirementsKeys(collectValuesAsList(values));
    }

    public Builder tenantIds(final List<String> values) {
      tenantIds = addValuesToList(tenantIds, values);
      return this;
    }

    public Builder tenantIds(final String... values) {
      return tenantIds(collectValuesAsList(values));
    }

    @Override
    public DecisionDefinitionFilter build() {
      return new DecisionDefinitionFilter(
          Objects.requireNonNullElse(decisionDefinitionKeys, Collections.emptyList()),
          Objects.requireNonNullElse(decisionDefinitionIds, Collections.emptyList()),
          Objects.requireNonNullElse(decisionDefinitionNames, Collections.emptyList()),
          Objects.requireNonNullElse(decisionDefinitionVersions, Collections.emptyList()),
          Objects.requireNonNullElse(decisionRequirementsIds, Collections.emptyList()),
          Objects.requireNonNullElse(decisionRequirementsKeys, Collections.emptyList()),
          Objects.requireNonNullElse(tenantIds, Collections.emptyList()));
    }
  }
}
