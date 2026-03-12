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
public record GeneratedBaseProcessInstanceFilterFieldsStrictContract(
    @Nullable Object startDate,
    @Nullable Object endDate,
    @Nullable Object state,
    @Nullable Boolean hasIncident,
    @Nullable Object tenantId,
    @Nullable java.util.List<GeneratedVariableValueFilterPropertyStrictContract> variables,
    @Nullable Object processInstanceKey,
    @Nullable Object parentProcessInstanceKey,
    @Nullable Object parentElementInstanceKey,
    @Nullable Object batchOperationId,
    @Nullable Object errorMessage,
    @Nullable Boolean hasRetriesLeft,
    @Nullable Object elementInstanceState,
    @Nullable Object elementId,
    @Nullable Boolean hasElementInstanceIncident,
    @Nullable Object incidentErrorHashCode,
    @Nullable java.util.Set<String> tags,
    @Nullable Object businessId) {

  public static java.util.List<GeneratedVariableValueFilterPropertyStrictContract> coerceVariables(
      final Object value) {
    if (value == null) {
      return null;
    }
    if (!(value instanceof java.util.List<?> listValue)) {
      throw new IllegalArgumentException(
          "variables must be a List of GeneratedVariableValueFilterPropertyStrictContract, but was "
              + value.getClass().getName());
    }

    final var result =
        new ArrayList<GeneratedVariableValueFilterPropertyStrictContract>(listValue.size());
    for (final var item : listValue) {
      if (item == null) {
        result.add(null);
      } else if (item instanceof GeneratedVariableValueFilterPropertyStrictContract strictItem) {
        result.add(strictItem);

      } else {
        throw new IllegalArgumentException(
            "variables must contain only GeneratedVariableValueFilterPropertyStrictContract items, but got "
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
    private Object startDate;
    private Object endDate;
    private Object state;
    private Boolean hasIncident;
    private Object tenantId;
    private Object variables;
    private Object processInstanceKey;
    private Object parentProcessInstanceKey;
    private Object parentElementInstanceKey;
    private Object batchOperationId;
    private Object errorMessage;
    private Boolean hasRetriesLeft;
    private Object elementInstanceState;
    private Object elementId;
    private Boolean hasElementInstanceIncident;
    private Object incidentErrorHashCode;
    private java.util.Set<String> tags;
    private Object businessId;

    private Builder() {}

    @Override
    public OptionalStep startDate(final Object startDate) {
      this.startDate = startDate;
      return this;
    }

    @Override
    public OptionalStep startDate(
        final Object startDate, final ContractPolicy.FieldPolicy<Object> policy) {
      this.startDate = policy.apply(startDate, Fields.START_DATE, null);
      return this;
    }

    @Override
    public OptionalStep endDate(final Object endDate) {
      this.endDate = endDate;
      return this;
    }

    @Override
    public OptionalStep endDate(
        final Object endDate, final ContractPolicy.FieldPolicy<Object> policy) {
      this.endDate = policy.apply(endDate, Fields.END_DATE, null);
      return this;
    }

    @Override
    public OptionalStep state(final Object state) {
      this.state = state;
      return this;
    }

    @Override
    public OptionalStep state(final Object state, final ContractPolicy.FieldPolicy<Object> policy) {
      this.state = policy.apply(state, Fields.STATE, null);
      return this;
    }

    @Override
    public OptionalStep hasIncident(final Boolean hasIncident) {
      this.hasIncident = hasIncident;
      return this;
    }

    @Override
    public OptionalStep hasIncident(
        final Boolean hasIncident, final ContractPolicy.FieldPolicy<Boolean> policy) {
      this.hasIncident = policy.apply(hasIncident, Fields.HAS_INCIDENT, null);
      return this;
    }

    @Override
    public OptionalStep tenantId(final Object tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    @Override
    public OptionalStep tenantId(
        final Object tenantId, final ContractPolicy.FieldPolicy<Object> policy) {
      this.tenantId = policy.apply(tenantId, Fields.TENANT_ID, null);
      return this;
    }

    @Override
    public OptionalStep variables(
        final java.util.List<GeneratedVariableValueFilterPropertyStrictContract> variables) {
      this.variables = variables;
      return this;
    }

    @Override
    public OptionalStep variables(final Object variables) {
      this.variables = variables;
      return this;
    }

    public Builder variables(
        final java.util.List<GeneratedVariableValueFilterPropertyStrictContract> variables,
        final ContractPolicy.FieldPolicy<
                java.util.List<GeneratedVariableValueFilterPropertyStrictContract>>
            policy) {
      this.variables = policy.apply(variables, Fields.VARIABLES, null);
      return this;
    }

    @Override
    public OptionalStep variables(
        final Object variables, final ContractPolicy.FieldPolicy<Object> policy) {
      this.variables = policy.apply(variables, Fields.VARIABLES, null);
      return this;
    }

    @Override
    public OptionalStep processInstanceKey(final Object processInstanceKey) {
      this.processInstanceKey = processInstanceKey;
      return this;
    }

    @Override
    public OptionalStep processInstanceKey(
        final Object processInstanceKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.processInstanceKey = policy.apply(processInstanceKey, Fields.PROCESS_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep parentProcessInstanceKey(final Object parentProcessInstanceKey) {
      this.parentProcessInstanceKey = parentProcessInstanceKey;
      return this;
    }

    @Override
    public OptionalStep parentProcessInstanceKey(
        final Object parentProcessInstanceKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.parentProcessInstanceKey =
          policy.apply(parentProcessInstanceKey, Fields.PARENT_PROCESS_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep parentElementInstanceKey(final Object parentElementInstanceKey) {
      this.parentElementInstanceKey = parentElementInstanceKey;
      return this;
    }

    @Override
    public OptionalStep parentElementInstanceKey(
        final Object parentElementInstanceKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.parentElementInstanceKey =
          policy.apply(parentElementInstanceKey, Fields.PARENT_ELEMENT_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep batchOperationId(final Object batchOperationId) {
      this.batchOperationId = batchOperationId;
      return this;
    }

    @Override
    public OptionalStep batchOperationId(
        final Object batchOperationId, final ContractPolicy.FieldPolicy<Object> policy) {
      this.batchOperationId = policy.apply(batchOperationId, Fields.BATCH_OPERATION_ID, null);
      return this;
    }

    @Override
    public OptionalStep errorMessage(final Object errorMessage) {
      this.errorMessage = errorMessage;
      return this;
    }

    @Override
    public OptionalStep errorMessage(
        final Object errorMessage, final ContractPolicy.FieldPolicy<Object> policy) {
      this.errorMessage = policy.apply(errorMessage, Fields.ERROR_MESSAGE, null);
      return this;
    }

    @Override
    public OptionalStep hasRetriesLeft(final Boolean hasRetriesLeft) {
      this.hasRetriesLeft = hasRetriesLeft;
      return this;
    }

    @Override
    public OptionalStep hasRetriesLeft(
        final Boolean hasRetriesLeft, final ContractPolicy.FieldPolicy<Boolean> policy) {
      this.hasRetriesLeft = policy.apply(hasRetriesLeft, Fields.HAS_RETRIES_LEFT, null);
      return this;
    }

    @Override
    public OptionalStep elementInstanceState(final Object elementInstanceState) {
      this.elementInstanceState = elementInstanceState;
      return this;
    }

    @Override
    public OptionalStep elementInstanceState(
        final Object elementInstanceState, final ContractPolicy.FieldPolicy<Object> policy) {
      this.elementInstanceState =
          policy.apply(elementInstanceState, Fields.ELEMENT_INSTANCE_STATE, null);
      return this;
    }

    @Override
    public OptionalStep elementId(final Object elementId) {
      this.elementId = elementId;
      return this;
    }

    @Override
    public OptionalStep elementId(
        final Object elementId, final ContractPolicy.FieldPolicy<Object> policy) {
      this.elementId = policy.apply(elementId, Fields.ELEMENT_ID, null);
      return this;
    }

    @Override
    public OptionalStep hasElementInstanceIncident(final Boolean hasElementInstanceIncident) {
      this.hasElementInstanceIncident = hasElementInstanceIncident;
      return this;
    }

    @Override
    public OptionalStep hasElementInstanceIncident(
        final Boolean hasElementInstanceIncident,
        final ContractPolicy.FieldPolicy<Boolean> policy) {
      this.hasElementInstanceIncident =
          policy.apply(hasElementInstanceIncident, Fields.HAS_ELEMENT_INSTANCE_INCIDENT, null);
      return this;
    }

    @Override
    public OptionalStep incidentErrorHashCode(final Object incidentErrorHashCode) {
      this.incidentErrorHashCode = incidentErrorHashCode;
      return this;
    }

    @Override
    public OptionalStep incidentErrorHashCode(
        final Object incidentErrorHashCode, final ContractPolicy.FieldPolicy<Object> policy) {
      this.incidentErrorHashCode =
          policy.apply(incidentErrorHashCode, Fields.INCIDENT_ERROR_HASH_CODE, null);
      return this;
    }

    @Override
    public OptionalStep tags(final java.util.Set<String> tags) {
      this.tags = tags;
      return this;
    }

    @Override
    public OptionalStep tags(
        final java.util.Set<String> tags,
        final ContractPolicy.FieldPolicy<java.util.Set<String>> policy) {
      this.tags = policy.apply(tags, Fields.TAGS, null);
      return this;
    }

    @Override
    public OptionalStep businessId(final Object businessId) {
      this.businessId = businessId;
      return this;
    }

    @Override
    public OptionalStep businessId(
        final Object businessId, final ContractPolicy.FieldPolicy<Object> policy) {
      this.businessId = policy.apply(businessId, Fields.BUSINESS_ID, null);
      return this;
    }

    @Override
    public GeneratedBaseProcessInstanceFilterFieldsStrictContract build() {
      return new GeneratedBaseProcessInstanceFilterFieldsStrictContract(
          this.startDate,
          this.endDate,
          this.state,
          this.hasIncident,
          this.tenantId,
          coerceVariables(this.variables),
          this.processInstanceKey,
          this.parentProcessInstanceKey,
          this.parentElementInstanceKey,
          this.batchOperationId,
          this.errorMessage,
          this.hasRetriesLeft,
          this.elementInstanceState,
          this.elementId,
          this.hasElementInstanceIncident,
          this.incidentErrorHashCode,
          this.tags,
          this.businessId);
    }
  }

  public interface OptionalStep {
    OptionalStep startDate(final Object startDate);

    OptionalStep startDate(final Object startDate, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep endDate(final Object endDate);

    OptionalStep endDate(final Object endDate, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep state(final Object state);

    OptionalStep state(final Object state, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep hasIncident(final Boolean hasIncident);

    OptionalStep hasIncident(
        final Boolean hasIncident, final ContractPolicy.FieldPolicy<Boolean> policy);

    OptionalStep tenantId(final Object tenantId);

    OptionalStep tenantId(final Object tenantId, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep variables(
        final java.util.List<GeneratedVariableValueFilterPropertyStrictContract> variables);

    OptionalStep variables(final Object variables);

    OptionalStep variables(
        final java.util.List<GeneratedVariableValueFilterPropertyStrictContract> variables,
        final ContractPolicy.FieldPolicy<
                java.util.List<GeneratedVariableValueFilterPropertyStrictContract>>
            policy);

    OptionalStep variables(final Object variables, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep processInstanceKey(final Object processInstanceKey);

    OptionalStep processInstanceKey(
        final Object processInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep parentProcessInstanceKey(final Object parentProcessInstanceKey);

    OptionalStep parentProcessInstanceKey(
        final Object parentProcessInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep parentElementInstanceKey(final Object parentElementInstanceKey);

    OptionalStep parentElementInstanceKey(
        final Object parentElementInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep batchOperationId(final Object batchOperationId);

    OptionalStep batchOperationId(
        final Object batchOperationId, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep errorMessage(final Object errorMessage);

    OptionalStep errorMessage(
        final Object errorMessage, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep hasRetriesLeft(final Boolean hasRetriesLeft);

    OptionalStep hasRetriesLeft(
        final Boolean hasRetriesLeft, final ContractPolicy.FieldPolicy<Boolean> policy);

    OptionalStep elementInstanceState(final Object elementInstanceState);

    OptionalStep elementInstanceState(
        final Object elementInstanceState, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep elementId(final Object elementId);

    OptionalStep elementId(final Object elementId, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep hasElementInstanceIncident(final Boolean hasElementInstanceIncident);

    OptionalStep hasElementInstanceIncident(
        final Boolean hasElementInstanceIncident, final ContractPolicy.FieldPolicy<Boolean> policy);

    OptionalStep incidentErrorHashCode(final Object incidentErrorHashCode);

    OptionalStep incidentErrorHashCode(
        final Object incidentErrorHashCode, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep tags(final java.util.Set<String> tags);

    OptionalStep tags(
        final java.util.Set<String> tags,
        final ContractPolicy.FieldPolicy<java.util.Set<String>> policy);

    OptionalStep businessId(final Object businessId);

    OptionalStep businessId(
        final Object businessId, final ContractPolicy.FieldPolicy<Object> policy);

    GeneratedBaseProcessInstanceFilterFieldsStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef START_DATE =
        ContractPolicy.field("BaseProcessInstanceFilterFields", "startDate");
    public static final ContractPolicy.FieldRef END_DATE =
        ContractPolicy.field("BaseProcessInstanceFilterFields", "endDate");
    public static final ContractPolicy.FieldRef STATE =
        ContractPolicy.field("BaseProcessInstanceFilterFields", "state");
    public static final ContractPolicy.FieldRef HAS_INCIDENT =
        ContractPolicy.field("BaseProcessInstanceFilterFields", "hasIncident");
    public static final ContractPolicy.FieldRef TENANT_ID =
        ContractPolicy.field("BaseProcessInstanceFilterFields", "tenantId");
    public static final ContractPolicy.FieldRef VARIABLES =
        ContractPolicy.field("BaseProcessInstanceFilterFields", "variables");
    public static final ContractPolicy.FieldRef PROCESS_INSTANCE_KEY =
        ContractPolicy.field("BaseProcessInstanceFilterFields", "processInstanceKey");
    public static final ContractPolicy.FieldRef PARENT_PROCESS_INSTANCE_KEY =
        ContractPolicy.field("BaseProcessInstanceFilterFields", "parentProcessInstanceKey");
    public static final ContractPolicy.FieldRef PARENT_ELEMENT_INSTANCE_KEY =
        ContractPolicy.field("BaseProcessInstanceFilterFields", "parentElementInstanceKey");
    public static final ContractPolicy.FieldRef BATCH_OPERATION_ID =
        ContractPolicy.field("BaseProcessInstanceFilterFields", "batchOperationId");
    public static final ContractPolicy.FieldRef ERROR_MESSAGE =
        ContractPolicy.field("BaseProcessInstanceFilterFields", "errorMessage");
    public static final ContractPolicy.FieldRef HAS_RETRIES_LEFT =
        ContractPolicy.field("BaseProcessInstanceFilterFields", "hasRetriesLeft");
    public static final ContractPolicy.FieldRef ELEMENT_INSTANCE_STATE =
        ContractPolicy.field("BaseProcessInstanceFilterFields", "elementInstanceState");
    public static final ContractPolicy.FieldRef ELEMENT_ID =
        ContractPolicy.field("BaseProcessInstanceFilterFields", "elementId");
    public static final ContractPolicy.FieldRef HAS_ELEMENT_INSTANCE_INCIDENT =
        ContractPolicy.field("BaseProcessInstanceFilterFields", "hasElementInstanceIncident");
    public static final ContractPolicy.FieldRef INCIDENT_ERROR_HASH_CODE =
        ContractPolicy.field("BaseProcessInstanceFilterFields", "incidentErrorHashCode");
    public static final ContractPolicy.FieldRef TAGS =
        ContractPolicy.field("BaseProcessInstanceFilterFields", "tags");
    public static final ContractPolicy.FieldRef BUSINESS_ID =
        ContractPolicy.field("BaseProcessInstanceFilterFields", "businessId");

    private Fields() {}
  }
}
