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
public record GeneratedProcessInstanceModificationMoveBatchOperationInstructionStrictContract(
    String sourceElementId, String targetElementId) {

  public GeneratedProcessInstanceModificationMoveBatchOperationInstructionStrictContract {
    Objects.requireNonNull(sourceElementId, "sourceElementId is required and must not be null");
    Objects.requireNonNull(targetElementId, "targetElementId is required and must not be null");
  }

  public static SourceElementIdStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements SourceElementIdStep, TargetElementIdStep, OptionalStep {
    private String sourceElementId;
    private String targetElementId;

    private Builder() {}

    @Override
    public TargetElementIdStep sourceElementId(final String sourceElementId) {
      this.sourceElementId = sourceElementId;
      return this;
    }

    @Override
    public OptionalStep targetElementId(final String targetElementId) {
      this.targetElementId = targetElementId;
      return this;
    }

    @Override
    public GeneratedProcessInstanceModificationMoveBatchOperationInstructionStrictContract build() {
      return new GeneratedProcessInstanceModificationMoveBatchOperationInstructionStrictContract(
          this.sourceElementId, this.targetElementId);
    }
  }

  public interface SourceElementIdStep {
    TargetElementIdStep sourceElementId(final String sourceElementId);
  }

  public interface TargetElementIdStep {
    OptionalStep targetElementId(final String targetElementId);
  }

  public interface OptionalStep {
    GeneratedProcessInstanceModificationMoveBatchOperationInstructionStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef SOURCE_ELEMENT_ID =
        ContractPolicy.field(
            "ProcessInstanceModificationMoveBatchOperationInstruction", "sourceElementId");
    public static final ContractPolicy.FieldRef TARGET_ELEMENT_ID =
        ContractPolicy.field(
            "ProcessInstanceModificationMoveBatchOperationInstruction", "targetElementId");

    private Fields() {}
  }
}
