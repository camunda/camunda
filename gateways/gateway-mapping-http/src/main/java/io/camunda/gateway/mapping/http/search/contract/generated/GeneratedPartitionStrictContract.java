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
import org.jspecify.annotations.NullMarked;

@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedPartitionStrictContract(Integer partitionId, String role, String health) {

  public GeneratedPartitionStrictContract {
    Objects.requireNonNull(partitionId, "partitionId is required and must not be null");
    Objects.requireNonNull(role, "role is required and must not be null");
    Objects.requireNonNull(health, "health is required and must not be null");
  }

  public static PartitionIdStep builder() {
    return new Builder();
  }

  public static final class Builder implements PartitionIdStep, RoleStep, HealthStep, OptionalStep {
    private Integer partitionId;
    private String role;
    private String health;

    private Builder() {}

    @Override
    public RoleStep partitionId(final Integer partitionId) {
      this.partitionId = partitionId;
      return this;
    }

    @Override
    public HealthStep role(final String role) {
      this.role = role;
      return this;
    }

    @Override
    public OptionalStep health(final String health) {
      this.health = health;
      return this;
    }

    @Override
    public GeneratedPartitionStrictContract build() {
      return new GeneratedPartitionStrictContract(this.partitionId, this.role, this.health);
    }
  }

  public interface PartitionIdStep {
    RoleStep partitionId(final Integer partitionId);
  }

  public interface RoleStep {
    HealthStep role(final String role);
  }

  public interface HealthStep {
    OptionalStep health(final String health);
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
