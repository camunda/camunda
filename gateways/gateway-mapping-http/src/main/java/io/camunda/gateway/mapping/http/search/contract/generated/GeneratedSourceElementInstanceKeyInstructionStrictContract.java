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
public record GeneratedSourceElementInstanceKeyInstructionStrictContract(
    @JsonProperty("sourceType") String sourceType,
    @JsonProperty("sourceElementInstanceKey") String sourceElementInstanceKey)
    implements GeneratedSourceElementInstructionStrictContract {

  public GeneratedSourceElementInstanceKeyInstructionStrictContract {
    Objects.requireNonNull(sourceType, "No sourceType provided.");
    Objects.requireNonNull(sourceElementInstanceKey, "No sourceElementInstanceKey provided.");
    if (sourceElementInstanceKey.isBlank())
      throw new IllegalArgumentException("sourceElementInstanceKey must not be blank");
    if (sourceElementInstanceKey.length() > 25)
      throw new IllegalArgumentException(
          "The provided sourceElementInstanceKey exceeds the limit of 25 characters.");
    if (!sourceElementInstanceKey.matches("^-?[0-9]+$"))
      throw new IllegalArgumentException(
          "The provided sourceElementInstanceKey contains illegal characters. It must match the pattern '^-?[0-9]+$'.");
  }

  public static String coerceSourceElementInstanceKey(final Object value) {
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
        "sourceElementInstanceKey must be a String or Number, but was "
            + value.getClass().getName());
  }

  public static SourceTypeStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements SourceTypeStep, SourceElementInstanceKeyStep, OptionalStep {
    private String sourceType;
    private Object sourceElementInstanceKey;

    private Builder() {}

    @Override
    public SourceElementInstanceKeyStep sourceType(final String sourceType) {
      this.sourceType = sourceType;
      return this;
    }

    @Override
    public OptionalStep sourceElementInstanceKey(final Object sourceElementInstanceKey) {
      this.sourceElementInstanceKey = sourceElementInstanceKey;
      return this;
    }

    @Override
    public GeneratedSourceElementInstanceKeyInstructionStrictContract build() {
      return new GeneratedSourceElementInstanceKeyInstructionStrictContract(
          this.sourceType, coerceSourceElementInstanceKey(this.sourceElementInstanceKey));
    }
  }

  public interface SourceTypeStep {
    SourceElementInstanceKeyStep sourceType(final String sourceType);
  }

  public interface SourceElementInstanceKeyStep {
    OptionalStep sourceElementInstanceKey(final Object sourceElementInstanceKey);
  }

  public interface OptionalStep {
    GeneratedSourceElementInstanceKeyInstructionStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef SOURCE_TYPE =
        ContractPolicy.field("SourceElementInstanceKeyInstruction", "sourceType");
    public static final ContractPolicy.FieldRef SOURCE_ELEMENT_INSTANCE_KEY =
        ContractPolicy.field("SourceElementInstanceKeyInstruction", "sourceElementInstanceKey");

    private Fields() {}
  }
}
