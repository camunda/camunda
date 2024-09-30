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

public final record UserTaskFilter(
    List<Long> userTaskKeys,
    List<String> elementIds,
    List<String> bpmnProcessIds,
    List<String> assignees,
    List<String> states,
    List<Long> processInstanceKeys,
    List<Long> processDefinitionKeys,
    List<String> candidateUsers,
    List<String> candidateGroups,
    List<String> tenantIds,
    List<VariableValueFilter> variableFilters,
    String type)
    implements FilterBase {

  public static final class Builder implements ObjectBuilder<UserTaskFilter> {
    private List<Long> userTaskKeys;
    private List<String> elementIds;
    private List<String> bpmnProcessIds;
    private List<String> assignees;
    private List<String> states;
    private List<Long> processInstanceKeys;
    private List<Long> processDefinitionKeys;
    private List<String> candidateUsers;
    private List<String> candidateGroups;
    private List<String> tenantIds;
    private List<VariableValueFilter> variableFilters;
    private String type;

    public Builder userTaskKeys(final Long... values) {
      return userTaskKeys(collectValuesAsList(values));
    }

    public Builder userTaskKeys(final List<Long> values) {
      userTaskKeys = addValuesToList(userTaskKeys, values);
      return this;
    }

    public Builder elementIds(final String... values) {
      return elementIds(collectValuesAsList(values));
    }

    public Builder elementIds(final List<String> values) {
      elementIds = addValuesToList(elementIds, values);
      return this;
    }

    public Builder bpmnProcessIds(final String... values) {
      return bpmnProcessIds(collectValuesAsList(values));
    }

    public Builder bpmnProcessIds(final List<String> values) {
      bpmnProcessIds = addValuesToList(bpmnProcessIds, values);
      return this;
    }

    public Builder assignees(final String... values) {
      return assignees((collectValuesAsList(values)));
    }

    public Builder assignees(final List<String> values) {
      assignees = addValuesToList(assignees, values);
      return this;
    }

    public Builder states(final String... values) {
      return states(collectValuesAsList(values));
    }

    public Builder states(final List<String> values) {
      states = addValuesToList(states, values);
      return this;
    }

    public Builder processInstanceKeys(final Long... values) {
      return processInstanceKeys(collectValuesAsList(values));
    }

    public Builder processInstanceKeys(final List<Long> values) {
      processInstanceKeys = addValuesToList(processInstanceKeys, values);
      return this;
    }

    public Builder processDefinitionKeys(final Long... values) {
      return processDefinitionKeys(collectValuesAsList(values));
    }

    public Builder processDefinitionKeys(final List<Long> values) {
      processDefinitionKeys = addValuesToList(processDefinitionKeys, values);
      return this;
    }

    public Builder candidateUsers(final String... values) {
      return candidateUsers(collectValuesAsList(values));
    }

    public Builder candidateUsers(final List<String> values) {
      candidateUsers = addValuesToList(candidateUsers, values);
      return this;
    }

    public Builder candidateGroups(final String... values) {
      return candidateGroups(collectValuesAsList(values));
    }

    public Builder candidateGroups(final List<String> values) {
      candidateGroups = addValuesToList(candidateGroups, values);
      return this;
    }

    public Builder tenantIds(final String... values) {
      return tenantIds(collectValuesAsList(values));
    }

    public Builder tenantIds(final List<String> values) {
      tenantIds = addValuesToList(tenantIds, values);
      return this;
    }

    public Builder variable(final List<VariableValueFilter> values) {
      variableFilters = addValuesToList(variableFilters, values);
      return this;
    }

    public Builder type(final String value) {
      type = value;
      return this;
    }

    @Override
    public UserTaskFilter build() {
      return new UserTaskFilter(
          Objects.requireNonNullElse(userTaskKeys, Collections.emptyList()),
          Objects.requireNonNullElse(elementIds, Collections.emptyList()),
          Objects.requireNonNullElse(bpmnProcessIds, Collections.emptyList()),
          Objects.requireNonNullElse(assignees, Collections.emptyList()),
          Objects.requireNonNullElse(states, Collections.emptyList()),
          Objects.requireNonNullElse(processInstanceKeys, Collections.emptyList()),
          Objects.requireNonNullElse(processDefinitionKeys, Collections.emptyList()),
          Objects.requireNonNullElse(candidateUsers, Collections.emptyList()),
          Objects.requireNonNullElse(candidateGroups, Collections.emptyList()),
          Objects.requireNonNullElse(tenantIds, Collections.emptyList()),
          Objects.requireNonNullElse(variableFilters, Collections.emptyList()),
          type);
    }
  }
}
