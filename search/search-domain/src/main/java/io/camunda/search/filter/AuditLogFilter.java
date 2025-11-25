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

import io.camunda.search.entities.AuditLogEntity.AuditLogActorType;
import io.camunda.search.entities.AuditLogEntity.AuditLogResult;
import io.camunda.util.ObjectBuilder;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.Function;

public record AuditLogFilter(
    List<Operation<String>> auditLogKeyOperations,
    List<Operation<String>> processDefinitionKeyOperations,
    List<Operation<String>> processInstanceKeyOperations,
    List<Operation<String>> elementInstanceKeyOperations,
    List<Operation<String>> operationTypeOperations,
    AuditLogResult result,
    List<Operation<OffsetDateTime>> timestampOperations,
    List<Operation<String>> actorIdOperations,
    AuditLogActorType actorType,
    List<Operation<String>> entityTypeOperations,
    List<Operation<String>> tenantIdOperations,
    List<Operation<String>> categoryOperations)
    implements FilterBase {

  public static AuditLogFilter of(
      final Function<AuditLogFilter.Builder, AuditLogFilter.Builder> builderFunction) {
    return builderFunction.apply(new AuditLogFilter.Builder()).build();
  }

  public Builder toBuilder() {
    return new Builder()
        .auditLogKeyOperations(auditLogKeyOperations)
        .processDefinitionKeyOperations(processDefinitionKeyOperations)
        .processInstanceKeyOperations(processInstanceKeyOperations)
        .elementInstanceKeyOperations(elementInstanceKeyOperations)
        .operationTypeOperations(operationTypeOperations)
        .result(result)
        .timestampOperations(timestampOperations)
        .actorIdOperations(actorIdOperations)
        .actorType(actorType)
        .entityTypeOperations(entityTypeOperations)
        .tenantIdOperations(tenantIdOperations)
        .categoryOperations(categoryOperations);
  }

  public static final class Builder implements ObjectBuilder<AuditLogFilter> {
    private List<Operation<String>> auditLogKeyOperations;
    private List<Operation<String>> processDefinitionKeyOperations;
    private List<Operation<String>> processInstanceKeyOperations;
    private List<Operation<String>> elementInstanceKeyOperations;
    private List<Operation<String>> operationTypeOperations;
    private AuditLogResult result;
    private List<Operation<OffsetDateTime>> timestampOperations;
    private List<Operation<String>> actorIdOperations;
    private AuditLogActorType actorType;
    private List<Operation<String>> entityTypeOperations;
    private List<Operation<String>> tenantIdOperations;
    private List<Operation<String>> categoryOperations;

    public Builder auditLogKeyOperations(final List<Operation<String>> operations) {
      if (operations != null) {
        auditLogKeyOperations = addValuesToList(auditLogKeyOperations, operations);
      }
      return this;
    }

    public Builder auditLogKey(final String value, final String... values) {
      return auditLogKeyOperations(mapDefaultToOperation(value, values));
    }

    @SafeVarargs
    public final Builder auditLogKeyOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return auditLogKeyOperations(collectValues(operation, operations));
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

    public Builder operationTypeOperations(final List<Operation<String>> operations) {
      if (operations != null) {
        operationTypeOperations = addValuesToList(operationTypeOperations, operations);
      }
      return this;
    }

    public Builder operationType(final String value, final String... values) {
      return operationTypeOperations(mapDefaultToOperation(value, values));
    }

    @SafeVarargs
    public final Builder operationTypeOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return operationTypeOperations(collectValues(operation, operations));
    }

    public Builder result(final AuditLogResult result) {
      this.result = result;
      return this;
    }

    public Builder timestampOperations(final List<Operation<OffsetDateTime>> operations) {
      if (operations != null) {
        timestampOperations = addValuesToList(timestampOperations, operations);
      }
      return this;
    }

    public Builder timestamp(final OffsetDateTime value, final OffsetDateTime... values) {
      return timestampOperations(mapDefaultToOperation(value, values));
    }

    @SafeVarargs
    public final Builder timestampOperations(
        final Operation<OffsetDateTime> operation, final Operation<OffsetDateTime>... operations) {
      return timestampOperations(collectValues(operation, operations));
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

    public Builder actorType(final AuditLogActorType actorType) {
      this.actorType = actorType;
      return this;
    }

    public Builder entityTypeOperations(final List<Operation<String>> operations) {
      if (operations != null) {
        entityTypeOperations = addValuesToList(entityTypeOperations, operations);
      }
      return this;
    }

    public Builder entityType(final String value, final String... values) {
      return entityTypeOperations(mapDefaultToOperation(value, values));
    }

    @SafeVarargs
    public final Builder entityTypeOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return entityTypeOperations(collectValues(operation, operations));
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

    public Builder categoryOperations(final List<Operation<String>> operations) {
      if (operations != null) {
        categoryOperations = addValuesToList(categoryOperations, operations);
      }
      return this;
    }

    public Builder category(final String value, final String... values) {
      return categoryOperations(mapDefaultToOperation(value, values));
    }

    @SafeVarargs
    public final Builder categoryOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return categoryOperations(collectValues(operation, operations));
    }

    @Override
    public AuditLogFilter build() {
      return new AuditLogFilter(
          auditLogKeyOperations,
          processDefinitionKeyOperations,
          processInstanceKeyOperations,
          elementInstanceKeyOperations,
          operationTypeOperations,
          result,
          timestampOperations,
          actorIdOperations,
          actorType,
          entityTypeOperations,
          tenantIdOperations,
          categoryOperations);
    }
  }
}
