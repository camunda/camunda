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
import jakarta.annotation.Generated;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedMigrateProcessInstanceMappingInstructionStrictContract(
    @JsonProperty("sourceElementId") String sourceElementId,
    @JsonProperty("targetElementId") String targetElementId) {

  public GeneratedMigrateProcessInstanceMappingInstructionStrictContract {
    Objects.requireNonNull(sourceElementId, "No sourceElementId provided.");
    Objects.requireNonNull(targetElementId, "No targetElementId provided.");
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
    public GeneratedMigrateProcessInstanceMappingInstructionStrictContract build() {
      return new GeneratedMigrateProcessInstanceMappingInstructionStrictContract(
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
    GeneratedMigrateProcessInstanceMappingInstructionStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef SOURCE_ELEMENT_ID =
        ContractPolicy.field("MigrateProcessInstanceMappingInstruction", "sourceElementId");
    public static final ContractPolicy.FieldRef TARGET_ELEMENT_ID =
        ContractPolicy.field("MigrateProcessInstanceMappingInstruction", "targetElementId");

    private Fields() {}
  }
}
