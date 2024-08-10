/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.search.filter;

import static io.camunda.util.CollectionUtil.addValuesToList;
import static io.camunda.util.CollectionUtil.collectValuesAsList;

import io.camunda.util.ObjectBuilder;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public record DecisionDefinitionFilter(
    List<Long> decisionKeys,
    List<String> dmnDecisionIds,
    List<String> dmnDecisionNames,
    List<Integer> versions,
    List<String> dmnDecisionRequirementsIds,
    List<Long> decisionRequirementsKeys,
    List<String> tenantIds)
    implements FilterBase {

  public static final class Builder implements ObjectBuilder<DecisionDefinitionFilter> {

    private List<Long> decisionKeys;
    private List<String> dmnDecisionIds;
    private List<String> dmnDecisionNames;
    private List<Integer> versions;
    private List<String> dmnDecisionRequirementsIds;
    private List<Long> decisionRequirementsKeys;
    private List<String> tenantIds;

    public Builder decisionKeys(final List<Long> values) {
      this.decisionKeys = addValuesToList(this.decisionKeys, values);
      return this;
    }

    public Builder decisionKeys(final Long... values) {
      return decisionKeys(collectValuesAsList(values));
    }

    public Builder dmnDecisionIds(final List<String> values) {
      this.dmnDecisionIds = addValuesToList(this.dmnDecisionIds, values);
      return this;
    }

    public Builder dmnDecisionIds(final String... values) {
      return dmnDecisionIds(collectValuesAsList(values));
    }

    public Builder dmnDecisionNames(final List<String> values) {
      this.dmnDecisionNames = addValuesToList(this.dmnDecisionNames, values);
      return this;
    }

    public Builder dmnDecisionNames(final String... values) {
      return dmnDecisionNames(collectValuesAsList(values));
    }

    public Builder versions(final List<Integer> values) {
      this.versions = addValuesToList(this.versions, values);
      return this;
    }

    public Builder versions(final Integer... values) {
      return versions(collectValuesAsList(values));
    }

    public Builder dmnDecisionRequirementsIds(final List<String> values) {
      this.dmnDecisionRequirementsIds = addValuesToList(this.dmnDecisionRequirementsIds, values);
      return this;
    }

    public Builder dmnDecisionRequirementsIds(final String... values) {
      return dmnDecisionRequirementsIds(collectValuesAsList(values));
    }

    public Builder decisionRequirementsKeys(final List<Long> values) {
      this.decisionRequirementsKeys = addValuesToList(this.decisionRequirementsKeys, values);
      return this;
    }

    public Builder decisionRequirementsKeys(final Long... values) {
      return decisionRequirementsKeys(collectValuesAsList(values));
    }

    public Builder tenantIds(final List<String> values) {
      this.tenantIds = addValuesToList(this.tenantIds, values);
      return this;
    }

    public Builder tenantIds(final String... values) {
      return tenantIds(collectValuesAsList(values));
    }

    @Override
    public DecisionDefinitionFilter build() {
      return new DecisionDefinitionFilter(
          Objects.requireNonNullElse(decisionKeys, Collections.emptyList()),
          Objects.requireNonNullElse(dmnDecisionIds, Collections.emptyList()),
          Objects.requireNonNullElse(dmnDecisionNames, Collections.emptyList()),
          Objects.requireNonNullElse(versions, Collections.emptyList()),
          Objects.requireNonNullElse(dmnDecisionRequirementsIds, Collections.emptyList()),
          Objects.requireNonNullElse(decisionRequirementsKeys, Collections.emptyList()),
          Objects.requireNonNullElse(tenantIds, Collections.emptyList()));
    }
  }
}
