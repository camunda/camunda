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

public record IncidentFilter(
    List<Long> keys,
    List<Long> processDefinitionKeys,
    List<Long> processInstanceKeys,
    List<String> types,
    List<String> flowNodeIds,
    List<String> flowNodeInstanceIds,
    List<String> states,
    List<Long> jobKeys,
    List<String> tenantIds,
    boolean hasActiveOperation)
    implements FilterBase {

  public static final class Builder implements ObjectBuilder<IncidentFilter> {

    private List<Long> keys;
    private List<Long> processDefinitionKeys;
    private List<Long> processInstanceKeys;
    private List<String> types;
    private List<String> flowNodeIds;
    private List<String> flowNodeInstanceIds;
    private DateValueFilter creationTimeFilter;
    private List<String> states;
    private List<Long> jobKeys;
    private List<String> tenantIds;
    private boolean hasActiveOperation = false;

    public Builder keys(final Long value, final Long... values) {
      return keys(collectValues(value, values));
    }

    public Builder keys(final List<Long> values) {
      keys = addValuesToList(keys, values);
      return this;
    }

    public Builder processDefinitionKeys(final Long value, final Long... values) {
      return processDefinitionKeys(collectValues(value, values));
    }

    public Builder processDefinitionKeys(final List<Long> values) {
      processDefinitionKeys = addValuesToList(processDefinitionKeys, values);
      return this;
    }

    public Builder processInstanceKeys(final Long value, final Long... values) {
      return processInstanceKeys(collectValues(value, values));
    }

    public Builder processInstanceKeys(final List<Long> values) {
      processInstanceKeys = addValuesToList(processInstanceKeys, values);
      return this;
    }

    public Builder types(final String value, final String... values) {
      return types(collectValues(value, values));
    }

    public Builder types(final List<String> values) {
      types = addValuesToList(types, values);
      return this;
    }

    public Builder flowNodeIds(final String value, final String... values) {
      return flowNodeIds(collectValues(value, values));
    }

    public Builder flowNodeIds(final List<String> values) {
      flowNodeIds = addValuesToList(flowNodeIds, values);
      return this;
    }

    public Builder flowNodeInstanceIds(final String value, final String... values) {
      return flowNodeInstanceIds(collectValues(value, values));
    }

    public Builder flowNodeInstanceIds(final List<String> values) {
      flowNodeInstanceIds = addValuesToList(flowNodeInstanceIds, values);
      return this;
    }

    public Builder states(final String value, final String... values) {
      return states(collectValues(value, values));
    }

    public Builder states(final List<String> values) {
      states = addValuesToList(states, values);
      return this;
    }

    public Builder jobKeys(final Long value, final Long... values) {
      return jobKeys(collectValues(value, values));
    }

    public Builder jobKeys(final List<Long> values) {
      jobKeys = addValuesToList(jobKeys, values);
      return this;
    }

    public Builder tenantIds(final String value, final String... values) {
      return tenantIds(collectValues(value, values));
    }

    public Builder tenantIds(final List<String> values) {
      tenantIds = addValuesToList(tenantIds, values);
      return this;
    }

    public Builder hasActiveOperation(final boolean value) {
      hasActiveOperation = value;
      return this;
    }

    @Override
    public IncidentFilter build() {
      return new IncidentFilter(
          Objects.requireNonNullElse(keys, Collections.emptyList()),
          Objects.requireNonNullElse(processDefinitionKeys, Collections.emptyList()),
          Objects.requireNonNullElse(processInstanceKeys, Collections.emptyList()),
          Objects.requireNonNullElse(types, Collections.emptyList()),
          Objects.requireNonNullElse(flowNodeIds, Collections.emptyList()),
          Objects.requireNonNullElse(flowNodeInstanceIds, Collections.emptyList()),
          Objects.requireNonNullElse(states, Collections.emptyList()),
          Objects.requireNonNullElse(jobKeys, Collections.emptyList()),
          Objects.requireNonNullElse(tenantIds, Collections.emptyList()),
          hasActiveOperation);
    }
  }
}
