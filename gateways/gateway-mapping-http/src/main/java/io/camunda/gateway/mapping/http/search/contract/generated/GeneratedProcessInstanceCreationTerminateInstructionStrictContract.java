/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/process-instances.yaml#/components/schemas/ProcessInstanceCreationTerminateInstruction
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import jakarta.annotation.Generated;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@JsonDeserialize(using = JsonDeserializer.None.class)
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedProcessInstanceCreationTerminateInstructionStrictContract(
    @JsonProperty("type") @Nullable String type,
    @JsonProperty("afterElementId") String afterElementId)
    implements GeneratedProcessInstanceCreationRuntimeInstructionStrictContract {

  public GeneratedProcessInstanceCreationTerminateInstructionStrictContract {
    Objects.requireNonNull(afterElementId, "No afterElementId provided.");
  }

  public static AfterElementIdStep builder() {
    return new Builder();
  }

  public static final class Builder implements AfterElementIdStep, OptionalStep {
    private String type;
    private String afterElementId;

    private Builder() {}

    @Override
    public OptionalStep afterElementId(final String afterElementId) {
      this.afterElementId = afterElementId;
      return this;
    }

    @Override
    public OptionalStep type(final @Nullable String type) {
      this.type = type;
      return this;
    }

    @Override
    public OptionalStep type(
        final @Nullable String type, final ContractPolicy.FieldPolicy<String> policy) {
      this.type = policy.apply(type, Fields.TYPE, null);
      return this;
    }

    @Override
    public GeneratedProcessInstanceCreationTerminateInstructionStrictContract build() {
      return new GeneratedProcessInstanceCreationTerminateInstructionStrictContract(
          this.type, this.afterElementId);
    }
  }

  public interface AfterElementIdStep {
    OptionalStep afterElementId(final String afterElementId);
  }

  public interface OptionalStep {
    OptionalStep type(final @Nullable String type);

    OptionalStep type(final @Nullable String type, final ContractPolicy.FieldPolicy<String> policy);

    GeneratedProcessInstanceCreationTerminateInstructionStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef TYPE =
        ContractPolicy.field("ProcessInstanceCreationTerminateInstruction", "type");
    public static final ContractPolicy.FieldRef AFTER_ELEMENT_ID =
        ContractPolicy.field("ProcessInstanceCreationTerminateInstruction", "afterElementId");

    private Fields() {}
  }
}
