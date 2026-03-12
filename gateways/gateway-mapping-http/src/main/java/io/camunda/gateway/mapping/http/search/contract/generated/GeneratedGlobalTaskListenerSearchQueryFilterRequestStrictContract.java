/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import jakarta.annotation.Generated;
import org.springframework.lang.Nullable;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedGlobalTaskListenerSearchQueryFilterRequestStrictContract(
    @Nullable Object id,
    @Nullable Object type,
    @Nullable Object retries,
    @Nullable java.util.List<Object> eventTypes,
    @Nullable Boolean afterNonGlobal,
    @Nullable Object priority,
    @Nullable Object source) {

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
  }

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
    public OptionalStep id(final Object id) {
      this.id = id;
      return this;
    }

    @Override
    public OptionalStep id(final Object id, final ContractPolicy.FieldPolicy<Object> policy) {
      this.id = policy.apply(id, Fields.ID, null);
      return this;
    }

    @Override
    public OptionalStep type(final Object type) {
      this.type = type;
      return this;
    }

    @Override
    public OptionalStep type(final Object type, final ContractPolicy.FieldPolicy<Object> policy) {
      this.type = policy.apply(type, Fields.TYPE, null);
      return this;
    }

    @Override
    public OptionalStep retries(final Object retries) {
      this.retries = retries;
      return this;
    }

    @Override
    public OptionalStep retries(
        final Object retries, final ContractPolicy.FieldPolicy<Object> policy) {
      this.retries = policy.apply(retries, Fields.RETRIES, null);
      return this;
    }

    @Override
    public OptionalStep eventTypes(final java.util.List<Object> eventTypes) {
      this.eventTypes = eventTypes;
      return this;
    }

    @Override
    public OptionalStep eventTypes(
        final java.util.List<Object> eventTypes,
        final ContractPolicy.FieldPolicy<java.util.List<Object>> policy) {
      this.eventTypes = policy.apply(eventTypes, Fields.EVENT_TYPES, null);
      return this;
    }

    @Override
    public OptionalStep afterNonGlobal(final Boolean afterNonGlobal) {
      this.afterNonGlobal = afterNonGlobal;
      return this;
    }

    @Override
    public OptionalStep afterNonGlobal(
        final Boolean afterNonGlobal, final ContractPolicy.FieldPolicy<Boolean> policy) {
      this.afterNonGlobal = policy.apply(afterNonGlobal, Fields.AFTER_NON_GLOBAL, null);
      return this;
    }

    @Override
    public OptionalStep priority(final Object priority) {
      this.priority = priority;
      return this;
    }

    @Override
    public OptionalStep priority(
        final Object priority, final ContractPolicy.FieldPolicy<Object> policy) {
      this.priority = policy.apply(priority, Fields.PRIORITY, null);
      return this;
    }

    @Override
    public OptionalStep source(final Object source) {
      this.source = source;
      return this;
    }

    @Override
    public OptionalStep source(
        final Object source, final ContractPolicy.FieldPolicy<Object> policy) {
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
    OptionalStep id(final Object id);

    OptionalStep id(final Object id, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep type(final Object type);

    OptionalStep type(final Object type, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep retries(final Object retries);

    OptionalStep retries(final Object retries, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep eventTypes(final java.util.List<Object> eventTypes);

    OptionalStep eventTypes(
        final java.util.List<Object> eventTypes,
        final ContractPolicy.FieldPolicy<java.util.List<Object>> policy);

    OptionalStep afterNonGlobal(final Boolean afterNonGlobal);

    OptionalStep afterNonGlobal(
        final Boolean afterNonGlobal, final ContractPolicy.FieldPolicy<Boolean> policy);

    OptionalStep priority(final Object priority);

    OptionalStep priority(final Object priority, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep source(final Object source);

    OptionalStep source(final Object source, final ContractPolicy.FieldPolicy<Object> policy);

    GeneratedGlobalTaskListenerSearchQueryFilterRequestStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef ID =
        ContractPolicy.field("GlobalTaskListenerSearchQueryFilterRequest", "id");
    public static final ContractPolicy.FieldRef TYPE =
        ContractPolicy.field("GlobalTaskListenerSearchQueryFilterRequest", "type");
    public static final ContractPolicy.FieldRef RETRIES =
        ContractPolicy.field("GlobalTaskListenerSearchQueryFilterRequest", "retries");
    public static final ContractPolicy.FieldRef EVENT_TYPES =
        ContractPolicy.field("GlobalTaskListenerSearchQueryFilterRequest", "eventTypes");
    public static final ContractPolicy.FieldRef AFTER_NON_GLOBAL =
        ContractPolicy.field("GlobalTaskListenerSearchQueryFilterRequest", "afterNonGlobal");
    public static final ContractPolicy.FieldRef PRIORITY =
        ContractPolicy.field("GlobalTaskListenerSearchQueryFilterRequest", "priority");
    public static final ContractPolicy.FieldRef SOURCE =
        ContractPolicy.field("GlobalTaskListenerSearchQueryFilterRequest", "source");

    private Fields() {}
  }
}
