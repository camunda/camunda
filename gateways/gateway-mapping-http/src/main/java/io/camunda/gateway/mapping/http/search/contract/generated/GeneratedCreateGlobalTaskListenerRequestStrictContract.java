/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/global-listeners.yaml#/components/schemas/CreateGlobalTaskListenerRequest
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import jakarta.annotation.Generated;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;


@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedCreateGlobalTaskListenerRequestStrictContract(
    String type,
    @Nullable Integer retries,
    @Nullable Boolean afterNonGlobal,
    @Nullable Integer priority,
    java.util.List<io.camunda.gateway.protocol.model.GlobalTaskListenerEventTypeEnum> eventTypes,
    String id
) {

  public GeneratedCreateGlobalTaskListenerRequestStrictContract {
    Objects.requireNonNull(type, "type is required and must not be null");
    Objects.requireNonNull(eventTypes, "eventTypes is required and must not be null");
    Objects.requireNonNull(id, "id is required and must not be null");
  }


  public static TypeStep builder() {
    return new Builder();
  }

  public static final class Builder implements TypeStep, EventTypesStep, IdStep, OptionalStep {
    private String type;
    private Integer retries;
    private Boolean afterNonGlobal;
    private Integer priority;
    private java.util.List<io.camunda.gateway.protocol.model.GlobalTaskListenerEventTypeEnum> eventTypes;
    private String id;

    private Builder() {}

    @Override
    public EventTypesStep type(final String type) {
      this.type = type;
      return this;
    }

    @Override
    public IdStep eventTypes(final java.util.List<io.camunda.gateway.protocol.model.GlobalTaskListenerEventTypeEnum> eventTypes) {
      this.eventTypes = eventTypes;
      return this;
    }

    @Override
    public OptionalStep id(final String id) {
      this.id = id;
      return this;
    }

    @Override
    public OptionalStep retries(final @Nullable Integer retries) {
      this.retries = retries;
      return this;
    }

    @Override
    public OptionalStep retries(final @Nullable Integer retries, final ContractPolicy.FieldPolicy<Integer> policy) {
      this.retries = policy.apply(retries, Fields.RETRIES, null);
      return this;
    }


    @Override
    public OptionalStep afterNonGlobal(final @Nullable Boolean afterNonGlobal) {
      this.afterNonGlobal = afterNonGlobal;
      return this;
    }

    @Override
    public OptionalStep afterNonGlobal(final @Nullable Boolean afterNonGlobal, final ContractPolicy.FieldPolicy<Boolean> policy) {
      this.afterNonGlobal = policy.apply(afterNonGlobal, Fields.AFTER_NON_GLOBAL, null);
      return this;
    }


    @Override
    public OptionalStep priority(final @Nullable Integer priority) {
      this.priority = priority;
      return this;
    }

    @Override
    public OptionalStep priority(final @Nullable Integer priority, final ContractPolicy.FieldPolicy<Integer> policy) {
      this.priority = policy.apply(priority, Fields.PRIORITY, null);
      return this;
    }

    @Override
    public GeneratedCreateGlobalTaskListenerRequestStrictContract build() {
      return new GeneratedCreateGlobalTaskListenerRequestStrictContract(
          this.type,
          this.retries,
          this.afterNonGlobal,
          this.priority,
          this.eventTypes,
          this.id);
    }
  }

  public interface TypeStep {
    EventTypesStep type(final String type);
  }

  public interface EventTypesStep {
    IdStep eventTypes(final java.util.List<io.camunda.gateway.protocol.model.GlobalTaskListenerEventTypeEnum> eventTypes);
  }

  public interface IdStep {
    OptionalStep id(final String id);
  }

  public interface OptionalStep {
  OptionalStep retries(final @Nullable Integer retries);

  OptionalStep retries(final @Nullable Integer retries, final ContractPolicy.FieldPolicy<Integer> policy);


  OptionalStep afterNonGlobal(final @Nullable Boolean afterNonGlobal);

  OptionalStep afterNonGlobal(final @Nullable Boolean afterNonGlobal, final ContractPolicy.FieldPolicy<Boolean> policy);


  OptionalStep priority(final @Nullable Integer priority);

  OptionalStep priority(final @Nullable Integer priority, final ContractPolicy.FieldPolicy<Integer> policy);


    GeneratedCreateGlobalTaskListenerRequestStrictContract build();
  }


  public static final class Fields {
    public static final ContractPolicy.FieldRef TYPE = ContractPolicy.field("CreateGlobalTaskListenerRequest", "type");
    public static final ContractPolicy.FieldRef RETRIES = ContractPolicy.field("CreateGlobalTaskListenerRequest", "retries");
    public static final ContractPolicy.FieldRef AFTER_NON_GLOBAL = ContractPolicy.field("CreateGlobalTaskListenerRequest", "afterNonGlobal");
    public static final ContractPolicy.FieldRef PRIORITY = ContractPolicy.field("CreateGlobalTaskListenerRequest", "priority");
    public static final ContractPolicy.FieldRef EVENT_TYPES = ContractPolicy.field("CreateGlobalTaskListenerRequest", "eventTypes");
    public static final ContractPolicy.FieldRef ID = ContractPolicy.field("CreateGlobalTaskListenerRequest", "id");

    private Fields() {}
  }


}
