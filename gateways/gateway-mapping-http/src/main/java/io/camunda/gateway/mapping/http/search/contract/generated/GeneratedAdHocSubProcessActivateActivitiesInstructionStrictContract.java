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
import java.util.Objects;
import org.springframework.lang.Nullable;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedAdHocSubProcessActivateActivitiesInstructionStrictContract(
    java.util.List<GeneratedAdHocSubProcessActivateActivityReferenceStrictContract> elements,
    @Nullable Boolean cancelRemainingInstances) {

  public GeneratedAdHocSubProcessActivateActivitiesInstructionStrictContract {
    Objects.requireNonNull(elements, "elements is required and must not be null");
  }

  public static java.util.List<GeneratedAdHocSubProcessActivateActivityReferenceStrictContract>
      coerceElements(final Object value) {
    if (value == null) {
      return null;
    }
    if (!(value instanceof java.util.List<?> listValue)) {
      throw new IllegalArgumentException(
          "elements must be a List of GeneratedAdHocSubProcessActivateActivityReferenceStrictContract, but was "
              + value.getClass().getName());
    }

    final var result =
        new ArrayList<GeneratedAdHocSubProcessActivateActivityReferenceStrictContract>(
            listValue.size());
    for (final var item : listValue) {
      if (item == null) {
        result.add(null);
      } else if (item
          instanceof GeneratedAdHocSubProcessActivateActivityReferenceStrictContract strictItem) {
        result.add(strictItem);

      } else {
        throw new IllegalArgumentException(
            "elements must contain only GeneratedAdHocSubProcessActivateActivityReferenceStrictContract items, but got "
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

  public static ElementsStep builder() {
    return new Builder();
  }

  public static final class Builder implements ElementsStep, OptionalStep {
    private Object elements;
    private ContractPolicy.FieldPolicy<Object> elementsPolicy;
    private Boolean cancelRemainingInstances;

    private Builder() {}

    @Override
    public OptionalStep elements(
        final Object elements, final ContractPolicy.FieldPolicy<Object> policy) {
      this.elements = elements;
      this.elementsPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep cancelRemainingInstances(final Boolean cancelRemainingInstances) {
      this.cancelRemainingInstances = cancelRemainingInstances;
      return this;
    }

    @Override
    public OptionalStep cancelRemainingInstances(
        final Boolean cancelRemainingInstances, final ContractPolicy.FieldPolicy<Boolean> policy) {
      this.cancelRemainingInstances =
          policy.apply(cancelRemainingInstances, Fields.CANCEL_REMAINING_INSTANCES, null);
      return this;
    }

    @Override
    public GeneratedAdHocSubProcessActivateActivitiesInstructionStrictContract build() {
      return new GeneratedAdHocSubProcessActivateActivitiesInstructionStrictContract(
          coerceElements(applyRequiredPolicy(this.elements, this.elementsPolicy, Fields.ELEMENTS)),
          this.cancelRemainingInstances);
    }
  }

  public interface ElementsStep {
    OptionalStep elements(final Object elements, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface OptionalStep {
    OptionalStep cancelRemainingInstances(final Boolean cancelRemainingInstances);

    OptionalStep cancelRemainingInstances(
        final Boolean cancelRemainingInstances, final ContractPolicy.FieldPolicy<Boolean> policy);

    GeneratedAdHocSubProcessActivateActivitiesInstructionStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef ELEMENTS =
        ContractPolicy.field("AdHocSubProcessActivateActivitiesInstruction", "elements");
    public static final ContractPolicy.FieldRef CANCEL_REMAINING_INSTANCES =
        ContractPolicy.field(
            "AdHocSubProcessActivateActivitiesInstruction", "cancelRemainingInstances");

    private Fields() {}
  }
}
