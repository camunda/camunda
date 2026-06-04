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
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record AgentInstanceFilter(
    List<Operation<Long>> agentInstanceKeyOperations,
    List<Operation<Long>> elementInstanceKeyOperations,
    List<Operation<Long>> processInstanceKeyOperations,
    List<Operation<Long>> rootProcessInstanceKeyOperations,
    List<Operation<Long>> processDefinitionKeyOperations,
    List<Operation<String>> processDefinitionIdOperations,
    List<Operation<Integer>> processDefinitionVersionOperations,
    List<Operation<String>> versionTagOperations,
    List<Operation<String>> elementIdOperations,
    List<Operation<String>> statusOperations,
    List<Operation<String>> tenantIdOperations,
    List<Operation<OffsetDateTime>> creationDateOperations,
    List<Operation<OffsetDateTime>> lastUpdatedDateOperations,
    List<Operation<OffsetDateTime>> completionDateOperations)
    implements FilterBase {

  public static AgentInstanceFilter of(
      final Function<AgentInstanceFilter.Builder, ObjectBuilder<AgentInstanceFilter>> fn) {
    return FilterBuilders.agentInstance(fn);
  }

  public static final class Builder implements ObjectBuilder<AgentInstanceFilter> {

    private List<Operation<Long>> agentInstanceKeyOperations;
    private List<Operation<Long>> elementInstanceKeyOperations;
    private List<Operation<Long>> processInstanceKeyOperations;
    private List<Operation<Long>> rootProcessInstanceKeyOperations;
    private List<Operation<Long>> processDefinitionKeyOperations;
    private List<Operation<String>> processDefinitionIdOperations;
    private List<Operation<Integer>> processDefinitionVersionOperations;
    private List<Operation<String>> versionTagOperations;
    private List<Operation<String>> elementIdOperations;
    private List<Operation<String>> statusOperations;
    private List<Operation<String>> tenantIdOperations;
    private List<Operation<OffsetDateTime>> creationDateOperations;
    private List<Operation<OffsetDateTime>> lastUpdatedDateOperations;
    private List<Operation<OffsetDateTime>> completionDateOperations;

    public Builder agentInstanceKeyOperations(final List<Operation<Long>> operations) {
      agentInstanceKeyOperations = addValuesToList(agentInstanceKeyOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder agentInstanceKeyOperations(
        final Operation<Long> operation, final Operation<Long>... operations) {
      return agentInstanceKeyOperations(collectValues(operation, operations));
    }

    public Builder agentInstanceKeys(final Long value, final Long... values) {
      return agentInstanceKeyOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder elementInstanceKeyOperations(final List<Operation<Long>> operations) {
      elementInstanceKeyOperations = addValuesToList(elementInstanceKeyOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder elementInstanceKeyOperations(
        final Operation<Long> operation, final Operation<Long>... operations) {
      return elementInstanceKeyOperations(collectValues(operation, operations));
    }

    public Builder elementInstanceKeys(final Long value, final Long... values) {
      return elementInstanceKeyOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder processInstanceKeyOperations(final List<Operation<Long>> operations) {
      processInstanceKeyOperations = addValuesToList(processInstanceKeyOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder processInstanceKeyOperations(
        final Operation<Long> operation, final Operation<Long>... operations) {
      return processInstanceKeyOperations(collectValues(operation, operations));
    }

    public Builder processInstanceKeys(final Long value, final Long... values) {
      return processInstanceKeyOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder rootProcessInstanceKeyOperations(final List<Operation<Long>> operations) {
      rootProcessInstanceKeyOperations =
          addValuesToList(rootProcessInstanceKeyOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder rootProcessInstanceKeyOperations(
        final Operation<Long> operation, final Operation<Long>... operations) {
      return rootProcessInstanceKeyOperations(collectValues(operation, operations));
    }

    public Builder rootProcessInstanceKeys(final Long value, final Long... values) {
      return rootProcessInstanceKeyOperations(FilterUtil.mapDefaultToOperation(value, values));
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

    public Builder versionTagOperations(final List<Operation<String>> operations) {
      versionTagOperations = addValuesToList(versionTagOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder versionTagOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return versionTagOperations(collectValues(operation, operations));
    }

    public Builder versionTags(final String value, final String... values) {
      return versionTagOperations(FilterUtil.mapDefaultToOperation(value, values));
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

    public Builder statusOperations(final List<Operation<String>> operations) {
      statusOperations = addValuesToList(statusOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder statusOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return statusOperations(collectValues(operation, operations));
    }

    public Builder statuses(final String value, final String... values) {
      return statusOperations(FilterUtil.mapDefaultToOperation(value, values));
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

    public Builder creationDateOperations(final List<Operation<OffsetDateTime>> operations) {
      creationDateOperations = addValuesToList(creationDateOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder creationDateOperations(
        final Operation<OffsetDateTime> operation, final Operation<OffsetDateTime>... operations) {
      return creationDateOperations(collectValues(operation, operations));
    }

    public Builder lastUpdatedDateOperations(final List<Operation<OffsetDateTime>> operations) {
      lastUpdatedDateOperations = addValuesToList(lastUpdatedDateOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder lastUpdatedDateOperations(
        final Operation<OffsetDateTime> operation, final Operation<OffsetDateTime>... operations) {
      return lastUpdatedDateOperations(collectValues(operation, operations));
    }

    public Builder completionDateOperations(final List<Operation<OffsetDateTime>> operations) {
      completionDateOperations = addValuesToList(completionDateOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder completionDateOperations(
        final Operation<OffsetDateTime> operation, final Operation<OffsetDateTime>... operations) {
      return completionDateOperations(collectValues(operation, operations));
    }

    @Override
    public AgentInstanceFilter build() {
      return new AgentInstanceFilter(
          Objects.requireNonNullElse(agentInstanceKeyOperations, Collections.emptyList()),
          Objects.requireNonNullElse(elementInstanceKeyOperations, Collections.emptyList()),
          Objects.requireNonNullElse(processInstanceKeyOperations, Collections.emptyList()),
          Objects.requireNonNullElse(rootProcessInstanceKeyOperations, Collections.emptyList()),
          Objects.requireNonNullElse(processDefinitionKeyOperations, Collections.emptyList()),
          Objects.requireNonNullElse(processDefinitionIdOperations, Collections.emptyList()),
          Objects.requireNonNullElse(processDefinitionVersionOperations, Collections.emptyList()),
          Objects.requireNonNullElse(versionTagOperations, Collections.emptyList()),
          Objects.requireNonNullElse(elementIdOperations, Collections.emptyList()),
          Objects.requireNonNullElse(statusOperations, Collections.emptyList()),
          Objects.requireNonNullElse(tenantIdOperations, Collections.emptyList()),
          Objects.requireNonNullElse(creationDateOperations, Collections.emptyList()),
          Objects.requireNonNullElse(lastUpdatedDateOperations, Collections.emptyList()),
          Objects.requireNonNullElse(completionDateOperations, Collections.emptyList()));
    }
  }
}
