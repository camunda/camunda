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
import java.util.ArrayList;
import org.springframework.lang.Nullable;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedJobResultAdHocSubProcessStrictContract(
    @Nullable java.util.List<GeneratedJobResultActivateElementStrictContract> activateElements,
    @Nullable Boolean isCompletionConditionFulfilled,
    @Nullable Boolean isCancelRemainingInstances,
    @Nullable String type) {

  public static java.util.List<GeneratedJobResultActivateElementStrictContract>
      coerceActivateElements(final Object value) {
    if (value == null) {
      return null;
    }
    if (!(value instanceof java.util.List<?> listValue)) {
      throw new IllegalArgumentException(
          "activateElements must be a List of GeneratedJobResultActivateElementStrictContract, but was "
              + value.getClass().getName());
    }

    final var result =
        new ArrayList<GeneratedJobResultActivateElementStrictContract>(listValue.size());
    for (final var item : listValue) {
      if (item == null) {
        result.add(null);
      } else if (item instanceof GeneratedJobResultActivateElementStrictContract strictItem) {
        result.add(strictItem);

      } else {
        throw new IllegalArgumentException(
            "activateElements must contain only GeneratedJobResultActivateElementStrictContract items, but got "
                + item.getClass().getName());
      }
    }
    return java.util.List.copyOf(result);
  }

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
    private Object activateElements;
    private Boolean isCompletionConditionFulfilled;
    private Boolean isCancelRemainingInstances;
    private String type;

    private Builder() {}

    @Override
    public OptionalStep activateElements(
        final java.util.List<GeneratedJobResultActivateElementStrictContract> activateElements) {
      this.activateElements = activateElements;
      return this;
    }

    @Override
    public OptionalStep activateElements(final Object activateElements) {
      this.activateElements = activateElements;
      return this;
    }

    public Builder activateElements(
        final java.util.List<GeneratedJobResultActivateElementStrictContract> activateElements,
        final ContractPolicy.FieldPolicy<
                java.util.List<GeneratedJobResultActivateElementStrictContract>>
            policy) {
      this.activateElements = policy.apply(activateElements, Fields.ACTIVATE_ELEMENTS, null);
      return this;
    }

    @Override
    public OptionalStep activateElements(
        final Object activateElements, final ContractPolicy.FieldPolicy<Object> policy) {
      this.activateElements = policy.apply(activateElements, Fields.ACTIVATE_ELEMENTS, null);
      return this;
    }

    @Override
    public OptionalStep isCompletionConditionFulfilled(
        final Boolean isCompletionConditionFulfilled) {
      this.isCompletionConditionFulfilled = isCompletionConditionFulfilled;
      return this;
    }

    @Override
    public OptionalStep isCompletionConditionFulfilled(
        final Boolean isCompletionConditionFulfilled,
        final ContractPolicy.FieldPolicy<Boolean> policy) {
      this.isCompletionConditionFulfilled =
          policy.apply(
              isCompletionConditionFulfilled, Fields.IS_COMPLETION_CONDITION_FULFILLED, null);
      return this;
    }

    @Override
    public OptionalStep isCancelRemainingInstances(final Boolean isCancelRemainingInstances) {
      this.isCancelRemainingInstances = isCancelRemainingInstances;
      return this;
    }

    @Override
    public OptionalStep isCancelRemainingInstances(
        final Boolean isCancelRemainingInstances,
        final ContractPolicy.FieldPolicy<Boolean> policy) {
      this.isCancelRemainingInstances =
          policy.apply(isCancelRemainingInstances, Fields.IS_CANCEL_REMAINING_INSTANCES, null);
      return this;
    }

    @Override
    public OptionalStep type(final String type) {
      this.type = type;
      return this;
    }

    @Override
    public OptionalStep type(final String type, final ContractPolicy.FieldPolicy<String> policy) {
      this.type = policy.apply(type, Fields.TYPE, null);
      return this;
    }

    @Override
    public GeneratedJobResultAdHocSubProcessStrictContract build() {
      return new GeneratedJobResultAdHocSubProcessStrictContract(
          coerceActivateElements(this.activateElements),
          this.isCompletionConditionFulfilled,
          this.isCancelRemainingInstances,
          this.type);
    }
  }

  public interface OptionalStep {
    OptionalStep activateElements(
        final java.util.List<GeneratedJobResultActivateElementStrictContract> activateElements);

    OptionalStep activateElements(final Object activateElements);

    OptionalStep activateElements(
        final java.util.List<GeneratedJobResultActivateElementStrictContract> activateElements,
        final ContractPolicy.FieldPolicy<
                java.util.List<GeneratedJobResultActivateElementStrictContract>>
            policy);

    OptionalStep activateElements(
        final Object activateElements, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep isCompletionConditionFulfilled(final Boolean isCompletionConditionFulfilled);

    OptionalStep isCompletionConditionFulfilled(
        final Boolean isCompletionConditionFulfilled,
        final ContractPolicy.FieldPolicy<Boolean> policy);

    OptionalStep isCancelRemainingInstances(final Boolean isCancelRemainingInstances);

    OptionalStep isCancelRemainingInstances(
        final Boolean isCancelRemainingInstances, final ContractPolicy.FieldPolicy<Boolean> policy);

    OptionalStep type(final String type);

    OptionalStep type(final String type, final ContractPolicy.FieldPolicy<String> policy);

    GeneratedJobResultAdHocSubProcessStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef ACTIVATE_ELEMENTS =
        ContractPolicy.field("JobResultAdHocSubProcess", "activateElements");
    public static final ContractPolicy.FieldRef IS_COMPLETION_CONDITION_FULFILLED =
        ContractPolicy.field("JobResultAdHocSubProcess", "isCompletionConditionFulfilled");
    public static final ContractPolicy.FieldRef IS_CANCEL_REMAINING_INSTANCES =
        ContractPolicy.field("JobResultAdHocSubProcess", "isCancelRemainingInstances");
    public static final ContractPolicy.FieldRef TYPE =
        ContractPolicy.field("JobResultAdHocSubProcess", "type");

    private Fields() {}
  }
}
