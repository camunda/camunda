/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import jakarta.annotation.Generated;
import java.util.Objects;
import org.springframework.lang.Nullable;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedProcessInstanceMigrationBatchOperationRequestStrictContract(
    GeneratedProcessInstanceFilterFieldsStrictContract filter,
    GeneratedProcessInstanceMigrationBatchOperationPlanStrictContract migrationPlan,
    @Nullable Long operationReference) {

  public GeneratedProcessInstanceMigrationBatchOperationRequestStrictContract {
    Objects.requireNonNull(filter, "filter is required and must not be null");
    Objects.requireNonNull(migrationPlan, "migrationPlan is required and must not be null");
  }

  public static GeneratedProcessInstanceFilterFieldsStrictContract coerceFilter(
      final Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof GeneratedProcessInstanceFilterFieldsStrictContract strictValue) {
      return strictValue;
    }

    throw new IllegalArgumentException(
        "filter must be a GeneratedProcessInstanceFilterFieldsStrictContract, but was "
            + value.getClass().getName());
  }

  public static GeneratedProcessInstanceMigrationBatchOperationPlanStrictContract
      coerceMigrationPlan(final Object value) {
    if (value == null) {
      return null;
    }
    if (value
        instanceof GeneratedProcessInstanceMigrationBatchOperationPlanStrictContract strictValue) {
      return strictValue;
    }

    throw new IllegalArgumentException(
        "migrationPlan must be a GeneratedProcessInstanceMigrationBatchOperationPlanStrictContract, but was "
            + value.getClass().getName());
  }

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
  }

  public static FilterStep builder() {
    return new Builder();
  }

  public static final class Builder implements FilterStep, MigrationPlanStep, OptionalStep {
    private Object filter;
    private ContractPolicy.FieldPolicy<Object> filterPolicy;
    private Object migrationPlan;
    private ContractPolicy.FieldPolicy<Object> migrationPlanPolicy;
    private Long operationReference;

    private Builder() {}

    @Override
    public MigrationPlanStep filter(
        final Object filter, final ContractPolicy.FieldPolicy<Object> policy) {
      this.filter = filter;
      this.filterPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep migrationPlan(
        final Object migrationPlan, final ContractPolicy.FieldPolicy<Object> policy) {
      this.migrationPlan = migrationPlan;
      this.migrationPlanPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep operationReference(final Long operationReference) {
      this.operationReference = operationReference;
      return this;
    }

    @Override
    public OptionalStep operationReference(
        final Long operationReference, final ContractPolicy.FieldPolicy<Long> policy) {
      this.operationReference = policy.apply(operationReference, Fields.OPERATION_REFERENCE, null);
      return this;
    }

    @Override
    public GeneratedProcessInstanceMigrationBatchOperationRequestStrictContract build() {
      return new GeneratedProcessInstanceMigrationBatchOperationRequestStrictContract(
          coerceFilter(applyRequiredPolicy(this.filter, this.filterPolicy, Fields.FILTER)),
          coerceMigrationPlan(
              applyRequiredPolicy(
                  this.migrationPlan, this.migrationPlanPolicy, Fields.MIGRATION_PLAN)),
          this.operationReference);
    }
  }

  public interface FilterStep {
    MigrationPlanStep filter(final Object filter, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface MigrationPlanStep {
    OptionalStep migrationPlan(
        final Object migrationPlan, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface OptionalStep {
    OptionalStep operationReference(final Long operationReference);

    OptionalStep operationReference(
        final Long operationReference, final ContractPolicy.FieldPolicy<Long> policy);

    GeneratedProcessInstanceMigrationBatchOperationRequestStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef FILTER =
        ContractPolicy.field("ProcessInstanceMigrationBatchOperationRequest", "filter");
    public static final ContractPolicy.FieldRef MIGRATION_PLAN =
        ContractPolicy.field("ProcessInstanceMigrationBatchOperationRequest", "migrationPlan");
    public static final ContractPolicy.FieldRef OPERATION_REFERENCE =
        ContractPolicy.field("ProcessInstanceMigrationBatchOperationRequest", "operationReference");

    private Fields() {}
  }
}
