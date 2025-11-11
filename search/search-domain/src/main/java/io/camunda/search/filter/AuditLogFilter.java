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
import static io.camunda.util.FilterUtil.mapDefaultToOperation;

import io.camunda.util.ObjectBuilder;
import java.util.List;
import java.util.function.Function;

public record AuditLogFilter(
    List<Operation<String>> operationKeyOperations,
    List<Operation<String>> entityKeyOperations,
    List<Operation<Short>> entityTypeOperations,
    List<Operation<Short>> operationTypeOperations,
    List<Operation<String>> operationStateOperations,
    List<Operation<String>> actorIdOperations,
    List<Operation<String>> actorTypeOperations,
    List<Operation<String>> tenantIdOperations,
    List<Operation<String>> batchOperationKeyOperations,
    List<Operation<String>> processDefinitionKeyOperations,
    List<Operation<String>> processInstanceKeyOperations,
    List<Operation<String>> elementInstanceKeyOperations,
    List<Operation<Long>> timestampOperations,
    String userTaskKey,
    String decisionRequirementsKey,
    String decisionKey)
    implements FilterBase {

  public static AuditLogFilter of(
      final Function<AuditLogFilter.Builder, AuditLogFilter.Builder> builderFunction) {
    return builderFunction.apply(new AuditLogFilter.Builder()).build();
  }

  public Builder toBuilder() {
    return new Builder()
        .operationKeyOperations(operationKeyOperations)
        .entityKeyOperations(entityKeyOperations)
        .entityTypeOperations(entityTypeOperations)
        .operationTypeOperations(operationTypeOperations)
        .operationStateOperations(operationStateOperations)
        .actorIdOperations(actorIdOperations)
        .actorTypeOperations(actorTypeOperations)
        .tenantIdOperations(tenantIdOperations)
        .batchOperationKeyOperations(batchOperationKeyOperations)
        .processDefinitionKeyOperations(processDefinitionKeyOperations)
        .processInstanceKeyOperations(processInstanceKeyOperations)
        .elementInstanceKeyOperations(elementInstanceKeyOperations)
        .timestampOperations(timestampOperations)
        .userTaskKey(userTaskKey)
        .decisionRequirementsKey(decisionRequirementsKey)
        .decisionKey(decisionKey);
  }

  public static final class Builder implements ObjectBuilder<AuditLogFilter> {
    private List<Operation<String>> operationKeyOperations;
    private List<Operation<String>> entityKeyOperations;
    private List<Operation<Short>> entityTypeOperations;
    private List<Operation<Short>> operationTypeOperations;
    private List<Operation<String>> operationStateOperations;
    private List<Operation<String>> actorIdOperations;
    private List<Operation<String>> actorTypeOperations;
    private List<Operation<String>> tenantIdOperations;
    private List<Operation<String>> batchOperationKeyOperations;
    private List<Operation<String>> processDefinitionKeyOperations;
    private List<Operation<String>> processInstanceKeyOperations;
    private List<Operation<String>> elementInstanceKeyOperations;
    private List<Operation<Long>> timestampOperations;
    private String userTaskKey;
    private String decisionRequirementsKey;
    private String decisionKey;

    public Builder operationKeyOperations(final List<Operation<String>> operations) {
      if (operations != null) {
        operationKeyOperations = addValuesToList(operationKeyOperations, operations);
      }
      return this;
    }

    public Builder operationKey(final String value, final String... values) {
      return operationKeyOperations(mapDefaultToOperation(value, values));
    }

    @SafeVarargs
    public final Builder operationKeyOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return operationKeyOperations(collectValues(operation, operations));
    }

    public Builder entityKeyOperations(final List<Operation<String>> operations) {
      if (operations != null) {
        entityKeyOperations = addValuesToList(entityKeyOperations, operations);
      }
      return this;
    }

    public Builder entityKey(final String value, final String... values) {
      return entityKeyOperations(mapDefaultToOperation(value, values));
    }

    @SafeVarargs
    public final Builder entityKeyOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return entityKeyOperations(collectValues(operation, operations));
    }

    public Builder entityTypeOperations(final List<Operation<Short>> operations) {
      if (operations != null) {
        entityTypeOperations = addValuesToList(entityTypeOperations, operations);
      }
      return this;
    }

    public Builder entityType(final Short value, final Short... values) {
      return entityTypeOperations(mapDefaultToOperation(value));
    }

    @SafeVarargs
    public final Builder entityTypeOperations(
        final Operation<Short> operation, final Operation<Short>... operations) {
      return entityTypeOperations(collectValues(operation, operations));
    }

    public Builder operationTypeOperations(final List<Operation<Short>> operations) {
      if (operations != null) {
        operationTypeOperations = addValuesToList(operationTypeOperations, operations);
      }
      return this;
    }

    public Builder operationType(final Short value, final Short... values) {
      return operationTypeOperations(mapDefaultToOperation(value));
    }

    @SafeVarargs
    public final Builder operationTypeOperations(
        final Operation<Short> operation, final Operation<Short>... operations) {
      return operationTypeOperations(collectValues(operation, operations));
    }

    public Builder operationStateOperations(final List<Operation<String>> operations) {
      if (operations != null) {
        operationStateOperations = addValuesToList(operationStateOperations, operations);
      }
      return this;
    }

    public Builder operationState(final String value, final String... values) {
      return operationStateOperations(mapDefaultToOperation(value, values));
    }

    @SafeVarargs
    public final Builder operationStateOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return operationStateOperations(collectValues(operation, operations));
    }

    public Builder actorIdOperations(final List<Operation<String>> operations) {
      if (operations != null) {
        actorIdOperations = addValuesToList(actorIdOperations, operations);
      }
      return this;
    }

    public Builder actorId(final String value, final String... values) {
      return actorIdOperations(mapDefaultToOperation(value, values));
    }

    @SafeVarargs
    public final Builder actorIdOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return actorIdOperations(collectValues(operation, operations));
    }

    public Builder actorTypeOperations(final List<Operation<String>> operations) {
      if (operations != null) {
        actorTypeOperations = addValuesToList(actorTypeOperations, operations);
      }
      return this;
    }

    public Builder actorType(final String value, final String... values) {
      return actorTypeOperations(mapDefaultToOperation(value, values));
    }

    @SafeVarargs
    public final Builder actorTypeOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return actorTypeOperations(collectValues(operation, operations));
    }

    public Builder tenantIdOperations(final List<Operation<String>> operations) {
      if (operations != null) {
        tenantIdOperations = addValuesToList(tenantIdOperations, operations);
      }
      return this;
    }

    public Builder tenantId(final String value, final String... values) {
      return tenantIdOperations(mapDefaultToOperation(value, values));
    }

    @SafeVarargs
    public final Builder tenantIdOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return tenantIdOperations(collectValues(operation, operations));
    }

    public Builder batchOperationKeyOperations(final List<Operation<String>> operations) {
      if (operations != null) {
        batchOperationKeyOperations = addValuesToList(batchOperationKeyOperations, operations);
      }
      return this;
    }

    public Builder batchOperationKey(final String value, final String... values) {
      return batchOperationKeyOperations(mapDefaultToOperation(value, values));
    }

    @SafeVarargs
    public final Builder batchOperationKeyOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return batchOperationKeyOperations(collectValues(operation, operations));
    }

    public Builder processDefinitionKeyOperations(final List<Operation<String>> operations) {
      if (operations != null) {
        processDefinitionKeyOperations =
            addValuesToList(processDefinitionKeyOperations, operations);
      }
      return this;
    }

    public Builder processDefinitionKey(final String value, final String... values) {
      return processDefinitionKeyOperations(mapDefaultToOperation(value, values));
    }

    @SafeVarargs
    public final Builder processDefinitionKeyOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return processDefinitionKeyOperations(collectValues(operation, operations));
    }

    public Builder processInstanceKeyOperations(final List<Operation<String>> operations) {
      if (operations != null) {
        processInstanceKeyOperations = addValuesToList(processInstanceKeyOperations, operations);
      }
      return this;
    }

    public Builder processInstanceKey(final String value, final String... values) {
      return processInstanceKeyOperations(mapDefaultToOperation(value, values));
    }

    @SafeVarargs
    public final Builder processInstanceKeyOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return processInstanceKeyOperations(collectValues(operation, operations));
    }

    public Builder elementInstanceKeyOperations(final List<Operation<String>> operations) {
      if (operations != null) {
        elementInstanceKeyOperations = addValuesToList(elementInstanceKeyOperations, operations);
      }
      return this;
    }

    public Builder elementInstanceKey(final String value, final String... values) {
      return elementInstanceKeyOperations(mapDefaultToOperation(value, values));
    }

    @SafeVarargs
    public final Builder elementInstanceKeyOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return elementInstanceKeyOperations(collectValues(operation, operations));
    }

    public Builder userTaskKey(final String value) {
      userTaskKey = value;
      return this;
    }

    public Builder decisionRequirementsKey(final String value) {
      decisionRequirementsKey = value;
      return this;
    }

    public Builder decisionKey(final String value) {
      decisionKey = value;
      return this;
    }

    public Builder timestampOperations(final List<Operation<Long>> operations) {
      if (operations != null) {
        timestampOperations = addValuesToList(timestampOperations, operations);
      }
      return this;
    }

    public Builder timestamp(final Long value, final Long... values) {
      return timestampOperations(mapDefaultToOperation(value));
    }

    @SafeVarargs
    public final Builder timestampOperations(
        final Operation<Long> operation, final Operation<Long>... operations) {
      return timestampOperations(collectValues(operation, operations));
    }

    @Override
    public AuditLogFilter build() {
      return new AuditLogFilter(
          operationKeyOperations,
          entityKeyOperations,
          entityTypeOperations,
          operationTypeOperations,
          operationStateOperations,
          actorIdOperations,
          actorTypeOperations,
          tenantIdOperations,
          batchOperationKeyOperations,
          processDefinitionKeyOperations,
          processInstanceKeyOperations,
          elementInstanceKeyOperations,
          timestampOperations,
          userTaskKey,
          decisionRequirementsKey,
          decisionKey);
    }
  }
}
