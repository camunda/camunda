/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/clock.yaml#/components/schemas/ClockPinRequest
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
public record GeneratedClockPinRequestStrictContract(
    Long timestamp
) {

  public GeneratedClockPinRequestStrictContract {
    Objects.requireNonNull(timestamp, "timestamp is required and must not be null");
  }


  public static TimestampStep builder() {
    return new Builder();
  }

  public static final class Builder implements TimestampStep, OptionalStep {
    private Long timestamp;

    private Builder() {}

    @Override
    public OptionalStep timestamp(final Long timestamp) {
      this.timestamp = timestamp;
      return this;
    }
    @Override
    public GeneratedClockPinRequestStrictContract build() {
      return new GeneratedClockPinRequestStrictContract(
          this.timestamp);
    }
  }

  public interface TimestampStep {
    OptionalStep timestamp(final Long timestamp);
  }

  public interface OptionalStep {
    GeneratedClockPinRequestStrictContract build();
  }


  public static final class Fields {
    public static final ContractPolicy.FieldRef TIMESTAMP = ContractPolicy.field("ClockPinRequest", "timestamp");

    private Fields() {}
  }


}
