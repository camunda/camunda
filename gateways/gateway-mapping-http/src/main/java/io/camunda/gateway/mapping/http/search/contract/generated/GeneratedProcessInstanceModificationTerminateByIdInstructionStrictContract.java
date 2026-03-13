/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
public record GeneratedProcessInstanceModificationTerminateByIdInstructionStrictContract(
    String elementId) {

  public GeneratedProcessInstanceModificationTerminateByIdInstructionStrictContract {
    Objects.requireNonNull(elementId, "elementId is required and must not be null");
  }

  public static ElementIdStep builder() {
    return new Builder();
  }

  public static final class Builder implements ElementIdStep, OptionalStep {
    private String elementId;

    private Builder() {}

    @Override
    public OptionalStep elementId(final String elementId) {
      this.elementId = elementId;
      return this;
    }

    @Override
    public GeneratedProcessInstanceModificationTerminateByIdInstructionStrictContract build() {
      return new GeneratedProcessInstanceModificationTerminateByIdInstructionStrictContract(
          this.elementId);
    }
  }

  public interface ElementIdStep {
    OptionalStep elementId(final String elementId);
  }

  public interface OptionalStep {
    GeneratedProcessInstanceModificationTerminateByIdInstructionStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef ELEMENT_ID =
        ContractPolicy.field("ProcessInstanceModificationTerminateByIdInstruction", "elementId");

    private Fields() {}
  }
}
