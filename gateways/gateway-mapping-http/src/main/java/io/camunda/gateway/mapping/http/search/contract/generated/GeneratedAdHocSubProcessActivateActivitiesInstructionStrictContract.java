/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/element-instances.yaml#/components/schemas/AdHocSubProcessActivateActivitiesInstruction
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import jakarta.annotation.Generated;
import java.util.ArrayList;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedAdHocSubProcessActivateActivitiesInstructionStrictContract(
    @JsonProperty("elements")
        java.util.List<GeneratedAdHocSubProcessActivateActivityReferenceStrictContract> elements,
    @JsonProperty("cancelRemainingInstances") @Nullable Boolean cancelRemainingInstances) {

  public GeneratedAdHocSubProcessActivateActivitiesInstructionStrictContract {
    Objects.requireNonNull(elements, "No elements provided.");
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

  public static ElementsStep builder() {
    return new Builder();
  }

  public static final class Builder implements ElementsStep, OptionalStep {
    private Object elements;
    private Boolean cancelRemainingInstances;

    private Builder() {}

    @Override
    public OptionalStep elements(final Object elements) {
      this.elements = elements;
      return this;
    }

    @Override
    public OptionalStep cancelRemainingInstances(final @Nullable Boolean cancelRemainingInstances) {
      this.cancelRemainingInstances = cancelRemainingInstances;
      return this;
    }

    @Override
    public OptionalStep cancelRemainingInstances(
        final @Nullable Boolean cancelRemainingInstances,
        final ContractPolicy.FieldPolicy<Boolean> policy) {
      this.cancelRemainingInstances =
          policy.apply(cancelRemainingInstances, Fields.CANCEL_REMAINING_INSTANCES, null);
      return this;
    }

    @Override
    public GeneratedAdHocSubProcessActivateActivitiesInstructionStrictContract build() {
      return new GeneratedAdHocSubProcessActivateActivitiesInstructionStrictContract(
          coerceElements(this.elements), this.cancelRemainingInstances);
    }
  }

  public interface ElementsStep {
    OptionalStep elements(final Object elements);
  }

  public interface OptionalStep {
    OptionalStep cancelRemainingInstances(final @Nullable Boolean cancelRemainingInstances);

    OptionalStep cancelRemainingInstances(
        final @Nullable Boolean cancelRemainingInstances,
        final ContractPolicy.FieldPolicy<Boolean> policy);

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
