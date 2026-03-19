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
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.gateway.mapping.http.util.KeyUtil;
import jakarta.annotation.Generated;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@JsonDeserialize(using = JsonDeserializer.None.class)
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedProcessInstanceModificationTerminateByKeyInstructionStrictContract(
    @JsonProperty("elementInstanceKey") String elementInstanceKey)
    implements GeneratedProcessInstanceModificationTerminateInstructionStrictContract {

  public GeneratedProcessInstanceModificationTerminateByKeyInstructionStrictContract {
    Objects.requireNonNull(elementInstanceKey, "No elementInstanceKey provided.");
    if (elementInstanceKey.isBlank())
      throw new IllegalArgumentException("elementInstanceKey must not be blank");
    if (elementInstanceKey.length() > 25)
      throw new IllegalArgumentException(
          "The provided elementInstanceKey exceeds the limit of 25 characters.");
    if (!elementInstanceKey.matches("^-?[0-9]+$"))
      throw new IllegalArgumentException(
          "The provided elementInstanceKey contains illegal characters. It must match the pattern '^-?[0-9]+$'.");
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
    public static final ContractPolicy.FieldRef ELEMENT_INSTANCE_KEY =
        ContractPolicy.field(
            "ProcessInstanceModificationTerminateByKeyInstruction", "elementInstanceKey");

    private Fields() {}
  }
}
