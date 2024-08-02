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
    List<Long> decisionKeys,
    List<String> dmnDecisionIds,
    List<String> dmnDecisionNames,
    List<Integer> versions,
    List<String> dmnDecisionRequirementsIds,
    List<Long> decisionRequirementsKeys,
    List<String> decisionRequirementsNames,
    List<Integer> decisionRequirementsVersions,
    List<String> tenantIds)
    implements FilterBase {

  public static final class Builder implements ObjectBuilder<DecisionDefinitionFilter> {

    private List<Long> decisionKeys;
    private List<String> dmnDecisionIds;
    private List<String> dmnDecisionNames;
    private List<Integer> versions;
    private List<String> dmnDecisionRequirementsIds;
    private List<Long> decisionRequirementsKeys;
    private List<String> decisionRequirementsNames;
    private List<Integer> decisionRequirementsVersions;
    private List<String> tenantIds;

    public Builder decisionKeys(final List<Long> values) {
      this.decisionKeys = addValuesToList(this.decisionKeys, values);
      return this;
    }

    public Builder decisionKeys(final Long value, final Long... values) {
      return decisionKeys(collectValues(value, values));
    }

    public Builder dmnDecisionIds(final List<String> values) {
      this.dmnDecisionIds = addValuesToList(this.dmnDecisionIds, values);
      return this;
    }

    public Builder dmnDecisionIds(final String value, final String... values) {
      return dmnDecisionIds(collectValues(value, values));
    }

    public Builder dmnDecisionNames(final List<String> values) {
      this.dmnDecisionNames = addValuesToList(this.dmnDecisionNames, values);
      return this;
    }

    public Builder dmnDecisionNames(final String value, final String... values) {
      return dmnDecisionNames(collectValues(value, values));
    }

    public Builder versions(final List<Integer> values) {
      this.versions = addValuesToList(this.versions, values);
      return this;
    }

    public Builder versions(final Integer value, final Integer... values) {
      return versions(collectValues(value, values));
    }

    public Builder dmnDecisionRequirementsIds(final List<String> values) {
      this.dmnDecisionRequirementsIds = addValuesToList(this.dmnDecisionRequirementsIds, values);
      return this;
    }

    public Builder dmnDecisionRequirementsIds(final String value, final String... values) {
      return dmnDecisionRequirementsIds(collectValues(value, values));
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
          Objects.requireNonNullElse(decisionKeys, Collections.emptyList()),
          Objects.requireNonNullElse(dmnDecisionIds, Collections.emptyList()),
          Objects.requireNonNullElse(dmnDecisionNames, Collections.emptyList()),
          Objects.requireNonNullElse(versions, Collections.emptyList()),
          Objects.requireNonNullElse(dmnDecisionRequirementsIds, Collections.emptyList()),
          Objects.requireNonNullElse(decisionRequirementsKeys, Collections.emptyList()),
          Objects.requireNonNullElse(decisionRequirementsNames, Collections.emptyList()),
          Objects.requireNonNullElse(decisionRequirementsVersions, Collections.emptyList()),
          Objects.requireNonNullElse(tenantIds, Collections.emptyList()));
    }
  }
}
