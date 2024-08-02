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
    List<Long> keys,
    List<String> decisionIds,
    List<String> names,
    List<Integer> versions,
    List<String> decisionRequirementsIds,
    List<Long> decisionRequirementsKeys,
    List<String> tenantIds)
    implements FilterBase {

  public static final class Builder implements ObjectBuilder<DecisionDefinitionFilter> {

    private List<Long> decisionKeys;
    private List<String> decisionIds;
    private List<String> names;
    private List<Integer> versions;
    private List<String> decisionRequirementsIds;
    private List<Long> decisionRequirementsKeys;
    private List<String> tenantIds;

    public Builder decisionKeys(final List<Long> values) {
      this.decisionKeys = addValuesToList(this.decisionKeys, values);
      return this;
    }

    public Builder decisionKeys(final Long value, final Long... values) {
      return decisionKeys(collectValues(value, values));
    }

    public Builder dmnDecisionIds(final List<String> values) {
      this.decisionIds = addValuesToList(this.decisionIds, values);
      return this;
    }

    public Builder dmnDecisionIds(final String value, final String... values) {
      return dmnDecisionIds(collectValues(value, values));
    }

    public Builder dmnDecisionNames(final List<String> values) {
      this.names = addValuesToList(this.names, values);
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
      this.decisionRequirementsIds = addValuesToList(this.decisionRequirementsIds, values);
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
          Objects.requireNonNullElse(decisionIds, Collections.emptyList()),
          Objects.requireNonNullElse(names, Collections.emptyList()),
          Objects.requireNonNullElse(versions, Collections.emptyList()),
          Objects.requireNonNullElse(decisionRequirementsIds, Collections.emptyList()),
          Objects.requireNonNullElse(decisionRequirementsKeys, Collections.emptyList()),
          Objects.requireNonNullElse(tenantIds, Collections.emptyList()));
    }
  }
}
