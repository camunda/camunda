/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/process-instances.yaml#/components/schemas/SourceElementIdInstruction
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import jakarta.annotation.Generated;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;


@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedSourceElementIdInstructionStrictContract(
    String sourceType,
    String sourceElementId
) {

  public GeneratedSourceElementIdInstructionStrictContract {
    Objects.requireNonNull(sourceType, "sourceType is required and must not be null");
    Objects.requireNonNull(sourceElementId, "sourceElementId is required and must not be null");
  }


  public static SourceTypeStep builder() {
    return new Builder();
  }

  public static final class Builder implements SourceTypeStep, SourceElementIdStep, OptionalStep {
    private String sourceType;
    private String sourceElementId;

    private Builder() {}

    @Override
    public SourceElementIdStep sourceType(final String sourceType) {
      this.sourceType = sourceType;
      return this;
    }

    @Override
    public OptionalStep sourceElementId(final String sourceElementId) {
      this.sourceElementId = sourceElementId;
      return this;
    }
    @Override
    public GeneratedSourceElementIdInstructionStrictContract build() {
      return new GeneratedSourceElementIdInstructionStrictContract(
          this.sourceType,
          this.sourceElementId);
    }
  }

  public interface SourceTypeStep {
    SourceElementIdStep sourceType(final String sourceType);
  }

  public interface SourceElementIdStep {
    OptionalStep sourceElementId(final String sourceElementId);
  }

  public interface OptionalStep {
    GeneratedSourceElementIdInstructionStrictContract build();
  }


  public static final class Fields {
    public static final ContractPolicy.FieldRef SOURCE_TYPE = ContractPolicy.field("SourceElementIdInstruction", "sourceType");
    public static final ContractPolicy.FieldRef SOURCE_ELEMENT_ID = ContractPolicy.field("SourceElementIdInstruction", "sourceElementId");

    private Fields() {}
  }


}
