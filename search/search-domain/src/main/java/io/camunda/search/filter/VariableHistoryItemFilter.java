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

import io.camunda.search.entities.VariableHistoryItemEntity.VariableHistoryItemActorType;
import io.camunda.search.entities.VariableHistoryItemEntity.VariableHistoryItemOperationType;
import io.camunda.util.FilterUtil;
import io.camunda.util.ObjectBuilder;
import io.camunda.zeebe.protocol.record.value.VariableOperationType;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record VariableHistoryItemFilter(
    List<Operation<Long>> variableKeyOperations,
    List<Operation<Long>> processInstanceKeyOperations,
    List<Operation<String>> nameOperations,
    List<Operation<Long>> scopeKeyOperations,
    List<Operation<String>> elementIdOperations,
    List<Operation<Long>> elementInstanceKeyOperations,
    List<Operation<Long>> variableHistoryItemKeyOperations,
    List<Operation<Long>> processDefinitionKeyOperations,
    List<Operation<String>> processDefinitionIdOperations,
    List<Operation<String>> tenantIdOperations,
    List<Operation<OffsetDateTime>> timestampOperations,
    List<Operation<String>> operationTypeOperations,
    List<Operation<String>> variableOperationTypeOperations,
    List<Operation<String>> actorTypeOperations,
    List<Operation<String>> actorIdOperations,
    List<Operation<String>> agentElementIdOperations)
    implements FilterBase {

  public static VariableHistoryItemFilter of(
      final Function<VariableHistoryItemFilter.Builder, ObjectBuilder<VariableHistoryItemFilter>>
          fn) {
    return FilterBuilders.variableHistoryItem(fn);
  }

  public static final class Builder implements ObjectBuilder<VariableHistoryItemFilter> {

    private List<Operation<Long>> variableKeyOperations;
    private List<Operation<Long>> processInstanceKeyOperations;
    private List<Operation<String>> nameOperations;
    private List<Operation<Long>> scopeKeyOperations;
    private List<Operation<String>> elementIdOperations;
    private List<Operation<Long>> elementInstanceKeyOperations;
    private List<Operation<Long>> variableHistoryItemKeyOperations;
    private List<Operation<Long>> processDefinitionKeyOperations;
    private List<Operation<String>> processDefinitionIdOperations;
    private List<Operation<String>> tenantIdOperations;
    private List<Operation<OffsetDateTime>> timestampOperations;
    private List<Operation<String>> operationTypeOperations;
    private List<Operation<String>> variableOperationTypeOperations;
    private List<Operation<String>> actorTypeOperations;
    private List<Operation<String>> actorIdOperations;
    private List<Operation<String>> agentElementIdOperations;

    public Builder variableKeyOperations(final List<Operation<Long>> operations) {
      variableKeyOperations = addValuesToList(variableKeyOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder variableKeyOperations(
        final Operation<Long> operation, final Operation<Long>... operations) {
      return variableKeyOperations(collectValues(operation, operations));
    }

    public Builder variableKeys(final Long value, final Long... values) {
      return variableKeyOperations(FilterUtil.mapDefaultToOperation(value, values));
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

    public Builder scopeKeyOperations(final List<Operation<Long>> operations) {
      scopeKeyOperations = addValuesToList(scopeKeyOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder scopeKeyOperations(
        final Operation<Long> operation, final Operation<Long>... operations) {
      return scopeKeyOperations(collectValues(operation, operations));
    }

    public Builder scopeKeys(final Long value, final Long... values) {
      return scopeKeyOperations(FilterUtil.mapDefaultToOperation(value, values));
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

    public Builder variableHistoryItemKeyOperations(final List<Operation<Long>> operations) {
      variableHistoryItemKeyOperations =
          addValuesToList(variableHistoryItemKeyOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder variableHistoryItemKeyOperations(
        final Operation<Long> operation, final Operation<Long>... operations) {
      return variableHistoryItemKeyOperations(collectValues(operation, operations));
    }

    public Builder variableHistoryItemKeys(final Long value, final Long... values) {
      return variableHistoryItemKeyOperations(FilterUtil.mapDefaultToOperation(value, values));
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

    public Builder timestampOperations(final List<Operation<OffsetDateTime>> operations) {
      timestampOperations = addValuesToList(timestampOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder timestampOperations(
        final Operation<OffsetDateTime> operation, final Operation<OffsetDateTime>... operations) {
      return timestampOperations(collectValues(operation, operations));
    }

    public Builder timestamps(final OffsetDateTime value, final OffsetDateTime... values) {
      return timestampOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder operationTypeOperations(final List<Operation<String>> operations) {
      operationTypeOperations = addValuesToList(operationTypeOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder operationTypeOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return operationTypeOperations(collectValues(operation, operations));
    }

    public Builder operationTypes(
        final VariableHistoryItemOperationType value,
        final VariableHistoryItemOperationType... values) {
      final String[] rest = new String[values.length];
      for (int i = 0; i < values.length; i++) {
        rest[i] = values[i].name();
      }
      return operationTypeOperations(FilterUtil.mapDefaultToOperation(value.name(), rest));
    }

    public Builder variableOperationTypeOperations(final List<Operation<String>> operations) {
      variableOperationTypeOperations =
          addValuesToList(variableOperationTypeOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder variableOperationTypeOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return variableOperationTypeOperations(collectValues(operation, operations));
    }

    public Builder variableOperationTypes(
        final VariableOperationType value, final VariableOperationType... values) {
      final String[] rest = new String[values.length];
      for (int i = 0; i < values.length; i++) {
        rest[i] = values[i].name();
      }
      return variableOperationTypeOperations(FilterUtil.mapDefaultToOperation(value.name(), rest));
    }

    public Builder actorTypeOperations(final List<Operation<String>> operations) {
      actorTypeOperations = addValuesToList(actorTypeOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder actorTypeOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return actorTypeOperations(collectValues(operation, operations));
    }

    public Builder actorTypes(
        final VariableHistoryItemActorType value, final VariableHistoryItemActorType... values) {
      final String[] rest = new String[values.length];
      for (int i = 0; i < values.length; i++) {
        rest[i] = values[i].name();
      }
      return actorTypeOperations(FilterUtil.mapDefaultToOperation(value.name(), rest));
    }

    public Builder actorIdOperations(final List<Operation<String>> operations) {
      actorIdOperations = addValuesToList(actorIdOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder actorIdOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return actorIdOperations(collectValues(operation, operations));
    }

    public Builder actorIds(final String value, final String... values) {
      return actorIdOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder agentElementIdOperations(final List<Operation<String>> operations) {
      agentElementIdOperations = addValuesToList(agentElementIdOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder agentElementIdOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return agentElementIdOperations(collectValues(operation, operations));
    }

    public Builder agentElementIds(final String value, final String... values) {
      return agentElementIdOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    @Override
    public VariableHistoryItemFilter build() {
      return new VariableHistoryItemFilter(
          Objects.requireNonNullElse(variableKeyOperations, Collections.emptyList()),
          Objects.requireNonNullElse(processInstanceKeyOperations, Collections.emptyList()),
          Objects.requireNonNullElse(nameOperations, Collections.emptyList()),
          Objects.requireNonNullElse(scopeKeyOperations, Collections.emptyList()),
          Objects.requireNonNullElse(elementIdOperations, Collections.emptyList()),
          Objects.requireNonNullElse(elementInstanceKeyOperations, Collections.emptyList()),
          Objects.requireNonNullElse(variableHistoryItemKeyOperations, Collections.emptyList()),
          Objects.requireNonNullElse(processDefinitionKeyOperations, Collections.emptyList()),
          Objects.requireNonNullElse(processDefinitionIdOperations, Collections.emptyList()),
          Objects.requireNonNullElse(tenantIdOperations, Collections.emptyList()),
          Objects.requireNonNullElse(timestampOperations, Collections.emptyList()),
          Objects.requireNonNullElse(operationTypeOperations, Collections.emptyList()),
          Objects.requireNonNullElse(variableOperationTypeOperations, Collections.emptyList()),
          Objects.requireNonNullElse(actorTypeOperations, Collections.emptyList()),
          Objects.requireNonNullElse(actorIdOperations, Collections.emptyList()),
          Objects.requireNonNullElse(agentElementIdOperations, Collections.emptyList()));
    }
  }
}
