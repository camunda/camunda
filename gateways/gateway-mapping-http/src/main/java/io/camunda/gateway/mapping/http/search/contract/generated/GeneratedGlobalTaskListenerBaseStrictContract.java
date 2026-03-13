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
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedGlobalTaskListenerBaseStrictContract(
    java.util.@Nullable List<io.camunda.gateway.protocol.model.GlobalTaskListenerEventTypeEnum>
        eventTypes) {

  public static OptionalStep builder() {
    return new Builder();
  }

  public static final class Builder implements OptionalStep {
    private java.util.List<io.camunda.gateway.protocol.model.GlobalTaskListenerEventTypeEnum>
        eventTypes;

    private Builder() {}

    @Override
    public OptionalStep eventTypes(
        final java.util.@Nullable List<
                io.camunda.gateway.protocol.model.GlobalTaskListenerEventTypeEnum>
            eventTypes) {
      this.eventTypes = eventTypes;
      return this;
    }

    @Override
    public OptionalStep eventTypes(
        final java.util.@Nullable List<
                io.camunda.gateway.protocol.model.GlobalTaskListenerEventTypeEnum>
            eventTypes,
        final ContractPolicy.FieldPolicy<
                java.util.List<io.camunda.gateway.protocol.model.GlobalTaskListenerEventTypeEnum>>
            policy) {
      this.eventTypes = policy.apply(eventTypes, Fields.EVENT_TYPES, null);
      return this;
    }

    @Override
    public GeneratedGlobalTaskListenerBaseStrictContract build() {
      return new GeneratedGlobalTaskListenerBaseStrictContract(this.eventTypes);
    }
  }

  public interface OptionalStep {
    OptionalStep eventTypes(
        final java.util.@Nullable List<
                io.camunda.gateway.protocol.model.GlobalTaskListenerEventTypeEnum>
            eventTypes);

    OptionalStep eventTypes(
        final java.util.@Nullable List<
                io.camunda.gateway.protocol.model.GlobalTaskListenerEventTypeEnum>
            eventTypes,
        final ContractPolicy.FieldPolicy<
                java.util.List<io.camunda.gateway.protocol.model.GlobalTaskListenerEventTypeEnum>>
            policy);

    GeneratedGlobalTaskListenerBaseStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef EVENT_TYPES =
        ContractPolicy.field("GlobalTaskListenerBase", "eventTypes");

    private Fields() {}
  }
}
