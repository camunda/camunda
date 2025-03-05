/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.filter;

import io.camunda.search.entities.IncidentEntity.ErrorType;
import io.camunda.search.entities.IncidentEntity.IncidentState;
import static io.camunda.util.CollectionUtil.addValuesToList;
import static io.camunda.util.CollectionUtil.collectValues;
import io.camunda.util.ObjectBuilder;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public record IncidentFilter(
    List<Long> incidentKeys,
    List<Long> processDefinitionKeys,
    List<String> processDefinitionIds,
    List<Long> processInstanceKeys,
    List<ErrorType> errorTypes,
    List<String> errorMessages,
    List<String> flowNodeIds,
    List<Long> flowNodeInstanceKeys,
    DateValueFilter creationTime,
    List<IncidentState> states,
    List<Long> jobKeys,
    List<String> tenantIds,
    List<Integer> partitionIds)
    implements FilterBase {

  public static final class Builder implements ObjectBuilder<IncidentFilter> {

    private List<Long> incidentKeys;
    private List<Long> processDefinitionKeys;
    private List<String> processDefinitionIds;
    private List<Long> processInstanceKeys;
    private List<ErrorType> errorTypes;
    private List<String> errorMessages;
    private List<String> flowNodeIds;
    private List<Long> flowNodeInstanceKeys;
    private DateValueFilter creationTimeFilter;
    private List<IncidentState> states;
    private List<Long> jobKeys;
    private List<String> tenantIds;
    private List<Integer> partitionIds;

    public Builder incidentKeys(final Long value, final Long... values) {
      return incidentKeys(collectValues(value, values));
    }

    public Builder incidentKeys(final List<Long> values) {
      incidentKeys = addValuesToList(incidentKeys, values);
      return this;
    }

    public Builder processDefinitionKeys(final Long value, final Long... values) {
      return processDefinitionKeys(collectValues(value, values));
    }

    public Builder processDefinitionKeys(final List<Long> values) {
      processDefinitionKeys = addValuesToList(processDefinitionKeys, values);
      return this;
    }

    public Builder processDefinitionIds(final String value, final String... values) {
      return processDefinitionIds(collectValues(value, values));
    }

    public Builder processDefinitionIds(final List<String> values) {
      processDefinitionIds = addValuesToList(processDefinitionIds, values);
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

    public Builder tenantIds(final String value, final String... values) {
      return tenantIds(collectValues(value, values));
    }

    public Builder tenantIds(final List<String> values) {
      tenantIds = addValuesToList(tenantIds, values);
      return this;
    }

    public Builder partitionIds(final Integer value, final Integer... values) {
      return partitionIds(collectValues(value, values));
    }

    public Builder partitionIds(final List<Integer> values) {
      partitionIds = addValuesToList(partitionIds, values);
      return this;
    }

    @Override
    public IncidentFilter build() {
      return new IncidentFilter(
          Objects.requireNonNullElse(incidentKeys, Collections.emptyList()),
          Objects.requireNonNullElse(processDefinitionKeys, Collections.emptyList()),
          Objects.requireNonNullElse(processDefinitionIds, Collections.emptyList()),
          Objects.requireNonNullElse(processInstanceKeys, Collections.emptyList()),
          Objects.requireNonNullElse(errorTypes, Collections.emptyList()),
          Objects.requireNonNullElse(errorMessages, Collections.emptyList()),
          Objects.requireNonNullElse(flowNodeIds, Collections.emptyList()),
          Objects.requireNonNullElse(flowNodeInstanceKeys, Collections.emptyList()),
          creationTimeFilter,
          Objects.requireNonNullElse(states, Collections.emptyList()),
          Objects.requireNonNullElse(jobKeys, Collections.emptyList()),
          Objects.requireNonNullElse(tenantIds, Collections.emptyList()),
          Objects.requireNonNullElse(partitionIds, Collections.emptyList()));
    }
  }
}
