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

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedPartitionStrictContract(Integer partitionId, String role, String health) {

  public GeneratedPartitionStrictContract {
    Objects.requireNonNull(partitionId, "partitionId is required and must not be null");
    Objects.requireNonNull(role, "role is required and must not be null");
    Objects.requireNonNull(health, "health is required and must not be null");
  }

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
  }

  public static PartitionIdStep builder() {
    return new Builder();
  }

  public static final class Builder implements PartitionIdStep, RoleStep, HealthStep, OptionalStep {
    private Integer partitionId;
    private ContractPolicy.FieldPolicy<Integer> partitionIdPolicy;
    private String role;
    private ContractPolicy.FieldPolicy<String> rolePolicy;
    private String health;
    private ContractPolicy.FieldPolicy<String> healthPolicy;

    private Builder() {}

    @Override
    public RoleStep partitionId(
        final Integer partitionId, final ContractPolicy.FieldPolicy<Integer> policy) {
      this.partitionId = partitionId;
      this.partitionIdPolicy = policy;
      return this;
    }

    @Override
    public HealthStep role(final String role, final ContractPolicy.FieldPolicy<String> policy) {
      this.role = role;
      this.rolePolicy = policy;
      return this;
    }

    @Override
    public OptionalStep health(
        final String health, final ContractPolicy.FieldPolicy<String> policy) {
      this.health = health;
      this.healthPolicy = policy;
      return this;
    }

    @Override
    public GeneratedPartitionStrictContract build() {
      return new GeneratedPartitionStrictContract(
          applyRequiredPolicy(this.partitionId, this.partitionIdPolicy, Fields.PARTITION_ID),
          applyRequiredPolicy(this.role, this.rolePolicy, Fields.ROLE),
          applyRequiredPolicy(this.health, this.healthPolicy, Fields.HEALTH));
    }
  }

  public interface PartitionIdStep {
    RoleStep partitionId(
        final Integer partitionId, final ContractPolicy.FieldPolicy<Integer> policy);
  }

  public interface RoleStep {
    HealthStep role(final String role, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface HealthStep {
    OptionalStep health(final String health, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface OptionalStep {
    GeneratedPartitionStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef PARTITION_ID =
        ContractPolicy.field("Partition", "partitionId");
    public static final ContractPolicy.FieldRef ROLE = ContractPolicy.field("Partition", "role");
    public static final ContractPolicy.FieldRef HEALTH =
        ContractPolicy.field("Partition", "health");

    private Fields() {}
  }
}
