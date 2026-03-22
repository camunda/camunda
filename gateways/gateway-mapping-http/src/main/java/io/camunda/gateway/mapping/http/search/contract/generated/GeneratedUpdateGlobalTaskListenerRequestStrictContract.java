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
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedUpdateGlobalTaskListenerRequestStrictContract(
    @JsonProperty("type") String type,
    @JsonProperty("retries") @Nullable Integer retries,
    @JsonProperty("afterNonGlobal") @Nullable Boolean afterNonGlobal,
    @JsonProperty("priority") @Nullable Integer priority,
    @JsonProperty("eventTypes")
        java.util.List<
                io.camunda.gateway.mapping.http.search.contract.generated
                    .GeneratedGlobalTaskListenerEventTypeEnum>
            eventTypes) {

  public GeneratedUpdateGlobalTaskListenerRequestStrictContract {
    Objects.requireNonNull(type, "No type provided.");
    Objects.requireNonNull(eventTypes, "No eventTypes provided.");
  }

  public static TypeStep builder() {
    return new Builder();
  }

  public static final class Builder implements TypeStep, EventTypesStep, OptionalStep {
    private String type;
    private Integer retries;
    private Boolean afterNonGlobal;
    private Integer priority;
    private java.util.List<
            io.camunda.gateway.mapping.http.search.contract.generated
                .GeneratedGlobalTaskListenerEventTypeEnum>
        eventTypes;

    private Builder() {}

    @Override
    public EventTypesStep type(final String type) {
      this.type = type;
      return this;
    }

    @Override
    public OptionalStep eventTypes(
        final java.util.List<
                io.camunda.gateway.mapping.http.search.contract.generated
                    .GeneratedGlobalTaskListenerEventTypeEnum>
            eventTypes) {
      this.eventTypes = eventTypes;
      return this;
    }

    @Override
    public OptionalStep retries(final @Nullable Integer retries) {
      this.retries = retries;
      return this;
    }

    @Override
    public OptionalStep retries(
        final @Nullable Integer retries, final ContractPolicy.FieldPolicy<Integer> policy) {
      this.retries = policy.apply(retries, Fields.RETRIES, null);
      return this;
    }

    @Override
    public OptionalStep afterNonGlobal(final @Nullable Boolean afterNonGlobal) {
      this.afterNonGlobal = afterNonGlobal;
      return this;
    }

    @Override
    public OptionalStep afterNonGlobal(
        final @Nullable Boolean afterNonGlobal, final ContractPolicy.FieldPolicy<Boolean> policy) {
      this.afterNonGlobal = policy.apply(afterNonGlobal, Fields.AFTER_NON_GLOBAL, null);
      return this;
    }

    @Override
    public OptionalStep priority(final @Nullable Integer priority) {
      this.priority = priority;
      return this;
    }

    @Override
    public OptionalStep priority(
        final @Nullable Integer priority, final ContractPolicy.FieldPolicy<Integer> policy) {
      this.priority = policy.apply(priority, Fields.PRIORITY, null);
      return this;
    }

    @Override
    public GeneratedUpdateGlobalTaskListenerRequestStrictContract build() {
      return new GeneratedUpdateGlobalTaskListenerRequestStrictContract(
          this.type, this.retries, this.afterNonGlobal, this.priority, this.eventTypes);
    }
  }

  public interface TypeStep {
    EventTypesStep type(final String type);
  }

  public interface EventTypesStep {
    OptionalStep eventTypes(
        final java.util.List<
                io.camunda.gateway.mapping.http.search.contract.generated
                    .GeneratedGlobalTaskListenerEventTypeEnum>
            eventTypes);
  }

  public interface OptionalStep {
    OptionalStep retries(final @Nullable Integer retries);

    OptionalStep retries(
        final @Nullable Integer retries, final ContractPolicy.FieldPolicy<Integer> policy);

    OptionalStep afterNonGlobal(final @Nullable Boolean afterNonGlobal);

    OptionalStep afterNonGlobal(
        final @Nullable Boolean afterNonGlobal, final ContractPolicy.FieldPolicy<Boolean> policy);

    OptionalStep priority(final @Nullable Integer priority);

    OptionalStep priority(
        final @Nullable Integer priority, final ContractPolicy.FieldPolicy<Integer> policy);

    GeneratedUpdateGlobalTaskListenerRequestStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef TYPE =
        ContractPolicy.field("UpdateGlobalTaskListenerRequest", "type");
    public static final ContractPolicy.FieldRef RETRIES =
        ContractPolicy.field("UpdateGlobalTaskListenerRequest", "retries");
    public static final ContractPolicy.FieldRef AFTER_NON_GLOBAL =
        ContractPolicy.field("UpdateGlobalTaskListenerRequest", "afterNonGlobal");
    public static final ContractPolicy.FieldRef PRIORITY =
        ContractPolicy.field("UpdateGlobalTaskListenerRequest", "priority");
    public static final ContractPolicy.FieldRef EVENT_TYPES =
        ContractPolicy.field("UpdateGlobalTaskListenerRequest", "eventTypes");

    private Fields() {}
  }
}
