/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.filter;

import static io.camunda.util.CollectionUtil.addValuesToList;
import static io.camunda.util.CollectionUtil.collectValues;

import io.camunda.util.FilterUtil;
import io.camunda.util.ObjectBuilder;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record AgentDefinitionFilter(
    List<Operation<Long>> agentDefinitionKeyOperations,
    List<Operation<String>> agentTypeOperations,
    List<Operation<String>> nameOperations,
    List<Operation<String>> elementIdOperations,
    List<Operation<String>> processDefinitionIdOperations,
    List<Operation<Long>> processDefinitionKeyOperations,
    List<Operation<Integer>> processDefinitionVersionOperations,
    List<Operation<String>> processDefinitionVersionTagOperations,
    List<Operation<String>> tenantIdOperations)
    implements FilterBase {

  public static AgentDefinitionFilter of(
      final Function<AgentDefinitionFilter.Builder, ObjectBuilder<AgentDefinitionFilter>> fn) {
    return FilterBuilders.agentDefinition(fn);
  }

  public static final class Builder implements ObjectBuilder<AgentDefinitionFilter> {

    private List<Operation<Long>> agentDefinitionKeyOperations;
    private List<Operation<String>> agentTypeOperations;
    private List<Operation<String>> nameOperations;
    private List<Operation<String>> elementIdOperations;
    private List<Operation<String>> processDefinitionIdOperations;
    private List<Operation<Long>> processDefinitionKeyOperations;
    private List<Operation<Integer>> processDefinitionVersionOperations;
    private List<Operation<String>> processDefinitionVersionTagOperations;
    private List<Operation<String>> tenantIdOperations;

    public Builder agentDefinitionKeyOperations(final List<Operation<Long>> operations) {
      agentDefinitionKeyOperations = addValuesToList(agentDefinitionKeyOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder agentDefinitionKeyOperations(
        final Operation<Long> operation, final Operation<Long>... operations) {
      return agentDefinitionKeyOperations(collectValues(operation, operations));
    }

    public Builder agentDefinitionKeys(final Long value, final Long... values) {
      return agentDefinitionKeyOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder agentTypeOperations(final List<Operation<String>> operations) {
      agentTypeOperations = addValuesToList(agentTypeOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder agentTypeOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return agentTypeOperations(collectValues(operation, operations));
    }

    public Builder agentTypes(final String value, final String... values) {
      return agentTypeOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder nameOperations(final List<Operation<String>> operations) {
      nameOperations = addValuesToList(nameOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder nameOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return nameOperations(collectValues(operation, operations));
    }

    public Builder names(final String value, final String... values) {
      return nameOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder elementIdOperations(final List<Operation<String>> operations) {
      elementIdOperations = addValuesToList(elementIdOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder elementIdOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return elementIdOperations(collectValues(operation, operations));
    }

    public Builder elementIds(final String value, final String... values) {
      return elementIdOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder processDefinitionIdOperations(final List<Operation<String>> operations) {
      processDefinitionIdOperations = addValuesToList(processDefinitionIdOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder processDefinitionIdOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return processDefinitionIdOperations(collectValues(operation, operations));
    }

    public Builder processDefinitionIds(final String value, final String... values) {
      return processDefinitionIdOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder processDefinitionKeyOperations(final List<Operation<Long>> operations) {
      processDefinitionKeyOperations = addValuesToList(processDefinitionKeyOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder processDefinitionKeyOperations(
        final Operation<Long> operation, final Operation<Long>... operations) {
      return processDefinitionKeyOperations(collectValues(operation, operations));
    }

    public Builder processDefinitionKeys(final Long value, final Long... values) {
      return processDefinitionKeyOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder processDefinitionVersionOperations(final List<Operation<Integer>> operations) {
      processDefinitionVersionOperations =
          addValuesToList(processDefinitionVersionOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder processDefinitionVersionOperations(
        final Operation<Integer> operation, final Operation<Integer>... operations) {
      return processDefinitionVersionOperations(collectValues(operation, operations));
    }

    public Builder processDefinitionVersions(final Integer value, final Integer... values) {
      return processDefinitionVersionOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder processDefinitionVersionTagOperations(final List<Operation<String>> operations) {
      processDefinitionVersionTagOperations =
          addValuesToList(processDefinitionVersionTagOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder processDefinitionVersionTagOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return processDefinitionVersionTagOperations(collectValues(operation, operations));
    }

    public Builder processDefinitionVersionTags(final String value, final String... values) {
      return processDefinitionVersionTagOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder tenantIdOperations(final List<Operation<String>> operations) {
      tenantIdOperations = addValuesToList(tenantIdOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder tenantIdOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return tenantIdOperations(collectValues(operation, operations));
    }

    public Builder tenantIds(final String value, final String... values) {
      return tenantIdOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    @Override
    public AgentDefinitionFilter build() {
      return new AgentDefinitionFilter(
          Objects.requireNonNullElse(agentDefinitionKeyOperations, Collections.emptyList()),
          Objects.requireNonNullElse(agentTypeOperations, Collections.emptyList()),
          Objects.requireNonNullElse(nameOperations, Collections.emptyList()),
          Objects.requireNonNullElse(elementIdOperations, Collections.emptyList()),
          Objects.requireNonNullElse(processDefinitionIdOperations, Collections.emptyList()),
          Objects.requireNonNullElse(processDefinitionKeyOperations, Collections.emptyList()),
          Objects.requireNonNullElse(processDefinitionVersionOperations, Collections.emptyList()),
          Objects.requireNonNullElse(
              processDefinitionVersionTagOperations, Collections.emptyList()),
          Objects.requireNonNullElse(tenantIdOperations, Collections.emptyList()));
    }
  }
}
