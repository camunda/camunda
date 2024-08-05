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

public record DecisionRequirementsFilter(
    List<Long> decisionRequirementsKeys,
    List<String> dmnDecisionRequirementsNames,
    List<Integer> versions,
    List<String> decisionRequirementsIds,
    List<String> tenantIds)
    implements FilterBase {

  public static final class Builder implements ObjectBuilder<DecisionRequirementsFilter> {

    private List<Long> decisionRequirementsKeys;
    private List<String> dmnDecisionRequirementsNames;
    private List<Integer> versions;
    private List<String> dmnDecisionRequirementsIds;
    private List<String> tenantIds;

    public Builder decisionRequirementsKeys(final List<Long> values) {
      decisionRequirementsKeys = addValuesToList(decisionRequirementsKeys, values);
      return this;
    }

    public Builder decisionRequirementsKeys(final Long... values) {
      return decisionRequirementsKeys(collectValuesAsList(values));
    }

    public Builder dmnDecisionRequirementsNames(final List<String> values) {
      dmnDecisionRequirementsNames = addValuesToList(dmnDecisionRequirementsNames, values);
      return this;
    }

    public Builder dmnDecisionRequirementsNames(final String... values) {
      return dmnDecisionRequirementsNames(collectValuesAsList(values));
    }

    public Builder versions(final List<Integer> values) {
      versions = addValuesToList(versions, values);
      return this;
    }

    public Builder versions(final Integer... values) {
      return versions(collectValuesAsList(values));
    }

    public Builder dmnDecisionRequirementsIds(final List<String> values) {
      dmnDecisionRequirementsIds = addValuesToList(dmnDecisionRequirementsIds, values);
      return this;
    }

    public Builder dmnDecisionRequirementsIds(final String... values) {
      return dmnDecisionRequirementsIds(collectValuesAsList(values));
    }

    public Builder tenantIds(final List<String> values) {
      tenantIds = addValuesToList(tenantIds, values);
      return this;
    }

    public Builder tenantIds(final String... values) {
      return tenantIds(collectValuesAsList(values));
    }

    @Override
    public DecisionRequirementsFilter build() {
      return new DecisionRequirementsFilter(
          Objects.requireNonNullElse(decisionRequirementsKeys, Collections.emptyList()),
          Objects.requireNonNullElse(dmnDecisionRequirementsNames, Collections.emptyList()),
          Objects.requireNonNullElse(versions, Collections.emptyList()),
          Objects.requireNonNullElse(dmnDecisionRequirementsIds, Collections.emptyList()),
          Objects.requireNonNullElse(tenantIds, Collections.emptyList()));
    }
  }
}
