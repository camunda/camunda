/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/global-listeners.yaml#/components/schemas/GlobalTaskListenerSearchQueryFilterRequest
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
public record GeneratedGlobalTaskListenerSearchQueryFilterRequestStrictContract(
    @Nullable Object id,
    @Nullable Object type,
    @Nullable Object retries,
    java.util.@Nullable List<Object> eventTypes,
    @Nullable Boolean afterNonGlobal,
    @Nullable Object priority,
    @Nullable Object source
) {


  public static OptionalStep builder() {
    return new Builder();
  }

  public static final class Builder implements OptionalStep {
    private Object id;
    private Object type;
    private Object retries;
    private java.util.List<Object> eventTypes;
    private Boolean afterNonGlobal;
    private Object priority;
    private Object source;

    private Builder() {}

    @Override
    public OptionalStep id(final @Nullable Object id) {
      this.id = id;
      return this;
    }

    @Override
    public OptionalStep id(final @Nullable Object id, final ContractPolicy.FieldPolicy<Object> policy) {
      this.id = policy.apply(id, Fields.ID, null);
      return this;
    }


    @Override
    public OptionalStep type(final @Nullable Object type) {
      this.type = type;
      return this;
    }

    @Override
    public OptionalStep type(final @Nullable Object type, final ContractPolicy.FieldPolicy<Object> policy) {
      this.type = policy.apply(type, Fields.TYPE, null);
      return this;
    }


    @Override
    public OptionalStep retries(final @Nullable Object retries) {
      this.retries = retries;
      return this;
    }

    @Override
    public OptionalStep retries(final @Nullable Object retries, final ContractPolicy.FieldPolicy<Object> policy) {
      this.retries = policy.apply(retries, Fields.RETRIES, null);
      return this;
    }


    @Override
    public OptionalStep eventTypes(final java.util.@Nullable List<Object> eventTypes) {
      this.eventTypes = eventTypes;
      return this;
    }

    @Override
    public OptionalStep eventTypes(final java.util.@Nullable List<Object> eventTypes, final ContractPolicy.FieldPolicy<java.util.List<Object>> policy) {
      this.eventTypes = policy.apply(eventTypes, Fields.EVENT_TYPES, null);
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
    public OptionalStep priority(final @Nullable Object priority) {
      this.priority = priority;
      return this;
    }

    @Override
    public OptionalStep priority(final @Nullable Object priority, final ContractPolicy.FieldPolicy<Object> policy) {
      this.priority = policy.apply(priority, Fields.PRIORITY, null);
      return this;
    }


    @Override
    public OptionalStep source(final @Nullable Object source) {
      this.source = source;
      return this;
    }

    @Override
    public OptionalStep source(final @Nullable Object source, final ContractPolicy.FieldPolicy<Object> policy) {
      this.source = policy.apply(source, Fields.SOURCE, null);
      return this;
    }

    @Override
    public GeneratedGlobalTaskListenerSearchQueryFilterRequestStrictContract build() {
      return new GeneratedGlobalTaskListenerSearchQueryFilterRequestStrictContract(
          this.id,
          this.type,
          this.retries,
          this.eventTypes,
          this.afterNonGlobal,
          this.priority,
          this.source);
    }
  }

  public interface OptionalStep {
  OptionalStep id(final @Nullable Object id);

  OptionalStep id(final @Nullable Object id, final ContractPolicy.FieldPolicy<Object> policy);


  OptionalStep type(final @Nullable Object type);

  OptionalStep type(final @Nullable Object type, final ContractPolicy.FieldPolicy<Object> policy);


  OptionalStep retries(final @Nullable Object retries);

  OptionalStep retries(final @Nullable Object retries, final ContractPolicy.FieldPolicy<Object> policy);


  OptionalStep eventTypes(final java.util.@Nullable List<Object> eventTypes);

  OptionalStep eventTypes(final java.util.@Nullable List<Object> eventTypes, final ContractPolicy.FieldPolicy<java.util.List<Object>> policy);


  OptionalStep afterNonGlobal(final @Nullable Boolean afterNonGlobal);

  OptionalStep afterNonGlobal(final @Nullable Boolean afterNonGlobal, final ContractPolicy.FieldPolicy<Boolean> policy);


  OptionalStep priority(final @Nullable Object priority);

  OptionalStep priority(final @Nullable Object priority, final ContractPolicy.FieldPolicy<Object> policy);


  OptionalStep source(final @Nullable Object source);

  OptionalStep source(final @Nullable Object source, final ContractPolicy.FieldPolicy<Object> policy);


    GeneratedGlobalTaskListenerSearchQueryFilterRequestStrictContract build();
  }


  public static final class Fields {
    public static final ContractPolicy.FieldRef ID = ContractPolicy.field("GlobalTaskListenerSearchQueryFilterRequest", "id");
    public static final ContractPolicy.FieldRef TYPE = ContractPolicy.field("GlobalTaskListenerSearchQueryFilterRequest", "type");
    public static final ContractPolicy.FieldRef RETRIES = ContractPolicy.field("GlobalTaskListenerSearchQueryFilterRequest", "retries");
    public static final ContractPolicy.FieldRef EVENT_TYPES = ContractPolicy.field("GlobalTaskListenerSearchQueryFilterRequest", "eventTypes");
    public static final ContractPolicy.FieldRef AFTER_NON_GLOBAL = ContractPolicy.field("GlobalTaskListenerSearchQueryFilterRequest", "afterNonGlobal");
    public static final ContractPolicy.FieldRef PRIORITY = ContractPolicy.field("GlobalTaskListenerSearchQueryFilterRequest", "priority");
    public static final ContractPolicy.FieldRef SOURCE = ContractPolicy.field("GlobalTaskListenerSearchQueryFilterRequest", "source");

    private Fields() {}
  }


}
