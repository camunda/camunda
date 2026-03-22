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
import java.util.ArrayList;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedGlobalTaskListenerSearchQueryFilterRequestStrictContract(
    @JsonProperty("id") @Nullable GeneratedStringFilterPropertyStrictContract id,
    @JsonProperty("type") @Nullable GeneratedStringFilterPropertyStrictContract type,
    @JsonProperty("retries") @Nullable GeneratedIntegerFilterPropertyStrictContract retries,
    @JsonProperty("eventTypes")
        java.util.@Nullable List<GeneratedGlobalTaskListenerEventTypeFilterPropertyStrictContract>
            eventTypes,
    @JsonProperty("afterNonGlobal") @Nullable Boolean afterNonGlobal,
    @JsonProperty("priority") @Nullable GeneratedIntegerFilterPropertyStrictContract priority,
    @JsonProperty("source")
        @Nullable GeneratedGlobalListenerSourceFilterPropertyStrictContract source) {

  public static java.util.List<GeneratedGlobalTaskListenerEventTypeFilterPropertyStrictContract>
      coerceEventTypes(final Object value) {
    if (value == null) {
      return null;
    }
    if (!(value instanceof java.util.List<?> listValue)) {
      throw new IllegalArgumentException(
          "eventTypes must be a List of GeneratedGlobalTaskListenerEventTypeFilterPropertyStrictContract, but was "
              + value.getClass().getName());
    }

    final var result =
        new ArrayList<GeneratedGlobalTaskListenerEventTypeFilterPropertyStrictContract>(
            listValue.size());
    for (final var item : listValue) {
      if (item == null) {
        result.add(null);
      } else if (item
          instanceof GeneratedGlobalTaskListenerEventTypeFilterPropertyStrictContract strictItem) {
        result.add(strictItem);

      } else {
        throw new IllegalArgumentException(
            "eventTypes must contain only GeneratedGlobalTaskListenerEventTypeFilterPropertyStrictContract items, but got "
                + item.getClass().getName());
      }
    }
    return java.util.List.copyOf(result);
  }

  public static OptionalStep builder() {
    return new Builder();
  }

  public static final class Builder implements OptionalStep {
    private GeneratedStringFilterPropertyStrictContract id;
    private GeneratedStringFilterPropertyStrictContract type;
    private GeneratedIntegerFilterPropertyStrictContract retries;
    private Object eventTypes;
    private Boolean afterNonGlobal;
    private GeneratedIntegerFilterPropertyStrictContract priority;
    private GeneratedGlobalListenerSourceFilterPropertyStrictContract source;

    private Builder() {}

    @Override
    public OptionalStep id(final @Nullable GeneratedStringFilterPropertyStrictContract id) {
      this.id = id;
      return this;
    }

    @Override
    public OptionalStep id(
        final @Nullable GeneratedStringFilterPropertyStrictContract id,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy) {
      this.id = policy.apply(id, Fields.ID, null);
      return this;
    }

    @Override
    public OptionalStep type(final @Nullable GeneratedStringFilterPropertyStrictContract type) {
      this.type = type;
      return this;
    }

    @Override
    public OptionalStep type(
        final @Nullable GeneratedStringFilterPropertyStrictContract type,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy) {
      this.type = policy.apply(type, Fields.TYPE, null);
      return this;
    }

    @Override
    public OptionalStep retries(
        final @Nullable GeneratedIntegerFilterPropertyStrictContract retries) {
      this.retries = retries;
      return this;
    }

    @Override
    public OptionalStep retries(
        final @Nullable GeneratedIntegerFilterPropertyStrictContract retries,
        final ContractPolicy.FieldPolicy<GeneratedIntegerFilterPropertyStrictContract> policy) {
      this.retries = policy.apply(retries, Fields.RETRIES, null);
      return this;
    }

    @Override
    public OptionalStep eventTypes(
        final java.util.@Nullable List<
                GeneratedGlobalTaskListenerEventTypeFilterPropertyStrictContract>
            eventTypes) {
      this.eventTypes = eventTypes;
      return this;
    }

    @Override
    public OptionalStep eventTypes(final @Nullable Object eventTypes) {
      this.eventTypes = eventTypes;
      return this;
    }

    public Builder eventTypes(
        final java.util.@Nullable List<
                GeneratedGlobalTaskListenerEventTypeFilterPropertyStrictContract>
            eventTypes,
        final ContractPolicy.FieldPolicy<
                java.util.List<GeneratedGlobalTaskListenerEventTypeFilterPropertyStrictContract>>
            policy) {
      this.eventTypes = policy.apply(eventTypes, Fields.EVENT_TYPES, null);
      return this;
    }

    @Override
    public OptionalStep eventTypes(
        final @Nullable Object eventTypes, final ContractPolicy.FieldPolicy<Object> policy) {
      this.eventTypes = policy.apply(eventTypes, Fields.EVENT_TYPES, null);
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
    public OptionalStep priority(
        final @Nullable GeneratedIntegerFilterPropertyStrictContract priority) {
      this.priority = priority;
      return this;
    }

    @Override
    public OptionalStep priority(
        final @Nullable GeneratedIntegerFilterPropertyStrictContract priority,
        final ContractPolicy.FieldPolicy<GeneratedIntegerFilterPropertyStrictContract> policy) {
      this.priority = policy.apply(priority, Fields.PRIORITY, null);
      return this;
    }

    @Override
    public OptionalStep source(
        final @Nullable GeneratedGlobalListenerSourceFilterPropertyStrictContract source) {
      this.source = source;
      return this;
    }

    @Override
    public OptionalStep source(
        final @Nullable GeneratedGlobalListenerSourceFilterPropertyStrictContract source,
        final ContractPolicy.FieldPolicy<GeneratedGlobalListenerSourceFilterPropertyStrictContract>
            policy) {
      this.source = policy.apply(source, Fields.SOURCE, null);
      return this;
    }

    @Override
    public GeneratedGlobalTaskListenerSearchQueryFilterRequestStrictContract build() {
      return new GeneratedGlobalTaskListenerSearchQueryFilterRequestStrictContract(
          this.id,
          this.type,
          this.retries,
          coerceEventTypes(this.eventTypes),
          this.afterNonGlobal,
          this.priority,
          this.source);
    }
  }

  public interface OptionalStep {
    OptionalStep id(final @Nullable GeneratedStringFilterPropertyStrictContract id);

    OptionalStep id(
        final @Nullable GeneratedStringFilterPropertyStrictContract id,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy);

    OptionalStep type(final @Nullable GeneratedStringFilterPropertyStrictContract type);

    OptionalStep type(
        final @Nullable GeneratedStringFilterPropertyStrictContract type,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy);

    OptionalStep retries(final @Nullable GeneratedIntegerFilterPropertyStrictContract retries);

    OptionalStep retries(
        final @Nullable GeneratedIntegerFilterPropertyStrictContract retries,
        final ContractPolicy.FieldPolicy<GeneratedIntegerFilterPropertyStrictContract> policy);

    OptionalStep eventTypes(
        final java.util.@Nullable List<
                GeneratedGlobalTaskListenerEventTypeFilterPropertyStrictContract>
            eventTypes);

    OptionalStep eventTypes(final @Nullable Object eventTypes);

    OptionalStep eventTypes(
        final java.util.@Nullable List<
                GeneratedGlobalTaskListenerEventTypeFilterPropertyStrictContract>
            eventTypes,
        final ContractPolicy.FieldPolicy<
                java.util.List<GeneratedGlobalTaskListenerEventTypeFilterPropertyStrictContract>>
            policy);

    OptionalStep eventTypes(
        final @Nullable Object eventTypes, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep afterNonGlobal(final @Nullable Boolean afterNonGlobal);

    OptionalStep afterNonGlobal(
        final @Nullable Boolean afterNonGlobal, final ContractPolicy.FieldPolicy<Boolean> policy);

    OptionalStep priority(final @Nullable GeneratedIntegerFilterPropertyStrictContract priority);

    OptionalStep priority(
        final @Nullable GeneratedIntegerFilterPropertyStrictContract priority,
        final ContractPolicy.FieldPolicy<GeneratedIntegerFilterPropertyStrictContract> policy);

    OptionalStep source(
        final @Nullable GeneratedGlobalListenerSourceFilterPropertyStrictContract source);

    OptionalStep source(
        final @Nullable GeneratedGlobalListenerSourceFilterPropertyStrictContract source,
        final ContractPolicy.FieldPolicy<GeneratedGlobalListenerSourceFilterPropertyStrictContract>
            policy);

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
