/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/process-instances.yaml#/components/schemas/ProcessInstanceModificationTerminateByKeyInstruction
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.gateway.mapping.http.util.KeyUtil;
import jakarta.annotation.Generated;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;


@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedProcessInstanceModificationTerminateByKeyInstructionStrictContract(
    String elementInstanceKey
) {

  public GeneratedProcessInstanceModificationTerminateByKeyInstructionStrictContract {
    Objects.requireNonNull(elementInstanceKey, "elementInstanceKey is required and must not be null");
  }

  public static String coerceElementInstanceKey(final Object value) {
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
        "elementInstanceKey must be a String or Number, but was " + value.getClass().getName());
  }



  public static ElementInstanceKeyStep builder() {
    return new Builder();
  }

  public static final class Builder implements ElementInstanceKeyStep, OptionalStep {
    private Object elementInstanceKey;

    private Builder() {}

    @Override
    public OptionalStep elementInstanceKey(final Object elementInstanceKey) {
      this.elementInstanceKey = elementInstanceKey;
      return this;
    }
    @Override
    public GeneratedProcessInstanceModificationTerminateByKeyInstructionStrictContract build() {
      return new GeneratedProcessInstanceModificationTerminateByKeyInstructionStrictContract(
          coerceElementInstanceKey(this.elementInstanceKey));
    }
  }

  public interface ElementInstanceKeyStep {
    OptionalStep elementInstanceKey(final Object elementInstanceKey);
  }

  public interface OptionalStep {
    GeneratedProcessInstanceModificationTerminateByKeyInstructionStrictContract build();
  }


  public static final class Fields {
    public static final ContractPolicy.FieldRef ELEMENT_INSTANCE_KEY = ContractPolicy.field("ProcessInstanceModificationTerminateByKeyInstruction", "elementInstanceKey");

    private Fields() {}
  }


}
