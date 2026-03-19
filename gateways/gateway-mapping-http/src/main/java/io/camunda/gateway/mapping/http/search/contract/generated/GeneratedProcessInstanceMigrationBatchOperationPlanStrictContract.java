/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.gateway.mapping.http.util.KeyUtil;
import jakarta.annotation.Generated;
import java.util.ArrayList;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedProcessInstanceMigrationBatchOperationPlanStrictContract(
    @JsonProperty("targetProcessDefinitionKey") String targetProcessDefinitionKey,
    @JsonProperty("mappingInstructions")
        java.util.List<GeneratedMigrateProcessInstanceMappingInstructionStrictContract>
            mappingInstructions) {

  public GeneratedProcessInstanceMigrationBatchOperationPlanStrictContract {
    Objects.requireNonNull(targetProcessDefinitionKey, "No targetProcessDefinitionKey provided.");
    Objects.requireNonNull(mappingInstructions, "No mappingInstructions provided.");
    if (targetProcessDefinitionKey.isBlank())
      throw new IllegalArgumentException("targetProcessDefinitionKey must not be blank");
    if (targetProcessDefinitionKey.length() > 25)
      throw new IllegalArgumentException(
          "The provided targetProcessDefinitionKey exceeds the limit of 25 characters.");
    if (!targetProcessDefinitionKey.matches("^-?[0-9]+$"))
      throw new IllegalArgumentException(
          "The provided targetProcessDefinitionKey contains illegal characters. It must match the pattern '^-?[0-9]+$'.");
  }

  public static String coerceTargetProcessDefinitionKey(final Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof String stringValue) {
      return stringValue;
    }
    if (value instanceof Number numberValue) {
      return KeyUtil.keyToString(numberValue.longValue());
    }
    throw new IllegalArgumentException(
        "targetProcessDefinitionKey must be a String or Number, but was "
            + value.getClass().getName());
  }

  public static java.util.List<GeneratedMigrateProcessInstanceMappingInstructionStrictContract>
      coerceMappingInstructions(final Object value) {
    if (value == null) {
      return null;
    }
    if (!(value instanceof java.util.List<?> listValue)) {
      throw new IllegalArgumentException(
          "mappingInstructions must be a List of GeneratedMigrateProcessInstanceMappingInstructionStrictContract, but was "
              + value.getClass().getName());
    }

    final var result =
        new ArrayList<GeneratedMigrateProcessInstanceMappingInstructionStrictContract>(
            listValue.size());
    for (final var item : listValue) {
      if (item == null) {
        result.add(null);
      } else if (item
          instanceof GeneratedMigrateProcessInstanceMappingInstructionStrictContract strictItem) {
        result.add(strictItem);

      } else {
        throw new IllegalArgumentException(
            "mappingInstructions must contain only GeneratedMigrateProcessInstanceMappingInstructionStrictContract items, but got "
                + item.getClass().getName());
      }
    }
    return java.util.List.copyOf(result);
  }

  public static TargetProcessDefinitionKeyStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements TargetProcessDefinitionKeyStep, MappingInstructionsStep, OptionalStep {
    private Object targetProcessDefinitionKey;
    private Object mappingInstructions;

    private Builder() {}

    @Override
    public MappingInstructionsStep targetProcessDefinitionKey(
        final Object targetProcessDefinitionKey) {
      this.targetProcessDefinitionKey = targetProcessDefinitionKey;
      return this;
    }

    @Override
    public OptionalStep mappingInstructions(final Object mappingInstructions) {
      this.mappingInstructions = mappingInstructions;
      return this;
    }

    @Override
    public GeneratedProcessInstanceMigrationBatchOperationPlanStrictContract build() {
      return new GeneratedProcessInstanceMigrationBatchOperationPlanStrictContract(
          coerceTargetProcessDefinitionKey(this.targetProcessDefinitionKey),
          coerceMappingInstructions(this.mappingInstructions));
    }
  }

  public interface TargetProcessDefinitionKeyStep {
    MappingInstructionsStep targetProcessDefinitionKey(final Object targetProcessDefinitionKey);
  }

  public interface MappingInstructionsStep {
    OptionalStep mappingInstructions(final Object mappingInstructions);
  }

  public interface OptionalStep {
    GeneratedProcessInstanceMigrationBatchOperationPlanStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef TARGET_PROCESS_DEFINITION_KEY =
        ContractPolicy.field(
            "ProcessInstanceMigrationBatchOperationPlan", "targetProcessDefinitionKey");
    public static final ContractPolicy.FieldRef MAPPING_INSTRUCTIONS =
        ContractPolicy.field("ProcessInstanceMigrationBatchOperationPlan", "mappingInstructions");

    private Fields() {}
  }
}
