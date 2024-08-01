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

public record DecisionRequirementFilter(
    List<String> ids,
    List<Long> keys,
    List<String> names,
    List<Integer> versions,
    List<String> decisionRequirementsIds,
    List<String> tenantIds)
    implements FilterBase {

  public static final class Builder implements ObjectBuilder<DecisionRequirementFilter> {

    private List<String> ids;
    private List<Long> keys;
    private List<String> names;
    private List<Integer> versions;
    private List<String> decisionRequirementsIds;
    private List<String> tenantIds;

    public Builder ids(final List<String> values) {
      ids = addValuesToList(ids, values);
      return this;
    }

    public Builder ids(final String value, final String... values) {
      return ids(collectValues(value, values));
    }

    public Builder keys(final List<Long> values) {
      keys = addValuesToList(keys, values);
      return this;
    }

    public Builder keys(final Long value, final Long... values) {
      return keys(collectValues(value, values));
    }

    public Builder names(final List<String> values) {
      names = addValuesToList(names, values);
      return this;
    }

    public Builder names(final String value, final String... values) {
      return names(collectValues(value, values));
    }

    public Builder versions(final List<Integer> values) {
      versions = addValuesToList(versions, values);
      return this;
    }

    public Builder versions(final Integer value, final Integer... values) {
      return versions(collectValues(value, values));
    }

    public Builder decisionRequirementsIds(final List<String> values) {
      decisionRequirementsIds = addValuesToList(decisionRequirementsIds, values);
      return this;
    }

    public Builder decisionRequirementsIds(final String value, final String... values) {
      return decisionRequirementsIds(collectValues(value, values));
    }

    public Builder tenantIds(final List<String> values) {
      tenantIds = addValuesToList(tenantIds, values);
      return this;
    }

    public Builder tenantIds(final String value, final String... values) {
      return tenantIds(collectValues(value, values));
    }

    @Override
    public DecisionRequirementFilter build() {
      return new DecisionRequirementFilter(
          Objects.requireNonNullElse(ids, Collections.emptyList()),
          Objects.requireNonNullElse(keys, Collections.emptyList()),
          Objects.requireNonNullElse(names, Collections.emptyList()),
          Objects.requireNonNullElse(versions, Collections.emptyList()),
          Objects.requireNonNullElse(decisionRequirementsIds, Collections.emptyList()),
          Objects.requireNonNullElse(tenantIds, Collections.emptyList()));
    }
  }
}
