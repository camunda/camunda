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

import io.camunda.service.entities.IncidentEntity.ErrorType;
import io.camunda.service.entities.IncidentEntity.IncidentState;
import io.camunda.util.ObjectBuilder;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public record IncidentFilter(
    List<Long> keys,
    List<Long> processDefinitionKeys,
    List<String> bpmnProcessIds,
    List<Long> processInstanceKeys,
    List<ErrorType> errorTypes,
    List<String> errorMessages,
    List<String> flowNodeIds,
    List<Long> flowNodeInstanceKeys,
    DateValueFilter creationTime,
    List<IncidentState> states,
    List<Long> jobKeys,
    List<String> treePaths,
    List<String> tenantIds)
    implements FilterBase {

  public static final class Builder implements ObjectBuilder<IncidentFilter> {

    private List<Long> keys;
    private List<Long> processDefinitionKeys;
    private List<String> bpmnProcessIds;
    private List<Long> processInstanceKeys;
    private List<ErrorType> errorTypes;
    private List<String> errorMessages;
    private List<String> flowNodeIds;
    private List<Long> flowNodeInstanceKeys;
    private DateValueFilter creationTimeFilter;
    private List<IncidentState> states;
    private List<Long> jobKeys;
    private List<String> treePaths;
    private List<String> tenantIds;

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

    public Builder bpmnProcessIds(final String value, final String... values) {
      return bpmnProcessIds(collectValues(value, values));
    }

    public Builder bpmnProcessIds(final List<String> values) {
      bpmnProcessIds = addValuesToList(bpmnProcessIds, values);
      return this;
    }

    public Builder processInstanceKeys(final Long value, final Long... values) {
      return processInstanceKeys(collectValues(value, values));
    }

    public Builder processInstanceKeys(final List<Long> values) {
      processInstanceKeys = addValuesToList(processInstanceKeys, values);
      return this;
    }

    public Builder errorTypes(final ErrorType value, final ErrorType... values) {
      return errorTypes(collectValues(value, values));
    }

    public Builder errorTypes(final List<ErrorType> values) {
      errorTypes = addValuesToList(errorTypes, values);
      return this;
    }

    public Builder errorMessages(final String value, final String... values) {
      return errorMessages(collectValues(value, values));
    }

    public Builder errorMessages(final List<String> values) {
      errorMessages = addValuesToList(errorMessages, values);
      return this;
    }

    public Builder creationTime(final DateValueFilter value) {
      creationTimeFilter = value;
      return this;
    }

    public Builder flowNodeIds(final String value, final String... values) {
      return flowNodeIds(collectValues(value, values));
    }

    public Builder flowNodeIds(final List<String> values) {
      flowNodeIds = addValuesToList(flowNodeIds, values);
      return this;
    }

    public Builder flowNodeInstanceKeys(final Long value, final Long... values) {
      return flowNodeInstanceKeys(collectValues(value, values));
    }

    public Builder flowNodeInstanceKeys(final List<Long> values) {
      flowNodeInstanceKeys = addValuesToList(flowNodeInstanceKeys, values);
      return this;
    }

    public Builder states(final IncidentState value, final IncidentState... values) {
      return states(collectValues(value, values));
    }

    public Builder states(final List<IncidentState> values) {
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

    public Builder treePaths(final String value, final String... values) {
      return treePaths(collectValues(value, values));
    }

    public Builder treePaths(final List<String> values) {
      treePaths = addValuesToList(treePaths, values);
      return this;
    }

    public Builder tenantIds(final String value, final String... values) {
      return tenantIds(collectValues(value, values));
    }

    public Builder tenantIds(final List<String> values) {
      tenantIds = addValuesToList(tenantIds, values);
      return this;
    }

    @Override
    public IncidentFilter build() {
      return new IncidentFilter(
          Objects.requireNonNullElse(keys, Collections.emptyList()),
          Objects.requireNonNullElse(processDefinitionKeys, Collections.emptyList()),
          Objects.requireNonNullElse(bpmnProcessIds, Collections.emptyList()),
          Objects.requireNonNullElse(processInstanceKeys, Collections.emptyList()),
          Objects.requireNonNullElse(errorTypes, Collections.emptyList()),
          Objects.requireNonNullElse(errorMessages, Collections.emptyList()),
          Objects.requireNonNullElse(flowNodeIds, Collections.emptyList()),
          Objects.requireNonNullElse(flowNodeInstanceKeys, Collections.emptyList()),
          creationTimeFilter,
          Objects.requireNonNullElse(states, Collections.emptyList()),
          Objects.requireNonNullElse(jobKeys, Collections.emptyList()),
          Objects.requireNonNullElse(treePaths, Collections.emptyList()),
          Objects.requireNonNullElse(tenantIds, Collections.emptyList()));
    }
  }
}
