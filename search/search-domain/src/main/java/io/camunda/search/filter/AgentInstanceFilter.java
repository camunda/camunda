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
import static io.camunda.util.CollectionUtil.collectValuesAsList;

import io.camunda.util.FilterUtil;
import io.camunda.util.ObjectBuilder;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record AgentInstanceFilter(
    List<Long> agentInstanceKeys,
    List<Long> elementInstanceKeys,
    List<Long> processInstanceKeys,
    List<Long> processDefinitionKeys,
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

    private List<Long> agentInstanceKeys;
    private List<Long> elementInstanceKeys;
    private List<Long> processInstanceKeys;
    private List<Long> processDefinitionKeys;
    private List<Operation<String>> elementIdOperations;
    private List<Operation<String>> statusOperations;
    private List<Operation<String>> tenantIdOperations;
    private List<Operation<OffsetDateTime>> creationDateOperations;
    private List<Operation<OffsetDateTime>> lastUpdatedDateOperations;
    private List<Operation<OffsetDateTime>> completionDateOperations;

    public Builder agentInstanceKeys(final List<Long> values) {
      agentInstanceKeys = addValuesToList(agentInstanceKeys, values);
      return this;
    }

    public Builder agentInstanceKeys(final Long... values) {
      return agentInstanceKeys(collectValuesAsList(values));
    }

    public Builder elementInstanceKeys(final List<Long> values) {
      elementInstanceKeys = addValuesToList(elementInstanceKeys, values);
      return this;
    }

    public Builder elementInstanceKeys(final Long... values) {
      return elementInstanceKeys(collectValuesAsList(values));
    }

    public Builder processInstanceKeys(final List<Long> values) {
      processInstanceKeys = addValuesToList(processInstanceKeys, values);
      return this;
    }

    public Builder processInstanceKeys(final Long... values) {
      return processInstanceKeys(collectValuesAsList(values));
    }

    public Builder processDefinitionKeys(final List<Long> values) {
      processDefinitionKeys = addValuesToList(processDefinitionKeys, values);
      return this;
    }

    public Builder processDefinitionKeys(final Long... values) {
      return processDefinitionKeys(collectValuesAsList(values));
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
          Objects.requireNonNullElse(agentInstanceKeys, Collections.emptyList()),
          Objects.requireNonNullElse(elementInstanceKeys, Collections.emptyList()),
          Objects.requireNonNullElse(processInstanceKeys, Collections.emptyList()),
          Objects.requireNonNullElse(processDefinitionKeys, Collections.emptyList()),
          Objects.requireNonNullElse(elementIdOperations, Collections.emptyList()),
          Objects.requireNonNullElse(statusOperations, Collections.emptyList()),
          Objects.requireNonNullElse(tenantIdOperations, Collections.emptyList()),
          Objects.requireNonNullElse(creationDateOperations, Collections.emptyList()),
          Objects.requireNonNullElse(lastUpdatedDateOperations, Collections.emptyList()),
          Objects.requireNonNullElse(completionDateOperations, Collections.emptyList()));
    }
  }
}
