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
public record GeneratedIncidentFilterStrictContract(
    @Nullable Object processDefinitionId,
    @Nullable Object errorType,
    @Nullable Object errorMessage,
    @Nullable Object elementId,
    @Nullable Object creationTime,
    @Nullable Object state,
    @Nullable Object tenantId,
    @Nullable Object incidentKey,
    @Nullable Object processDefinitionKey,
    @Nullable Object processInstanceKey,
    @Nullable Object elementInstanceKey,
    @Nullable Object jobKey) {

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
    private Object processDefinitionId;
    private Object errorType;
    private Object errorMessage;
    private Object elementId;
    private Object creationTime;
    private Object state;
    private Object tenantId;
    private Object incidentKey;
    private Object processDefinitionKey;
    private Object processInstanceKey;
    private Object elementInstanceKey;
    private Object jobKey;

    private Builder() {}

    @Override
    public OptionalStep processDefinitionId(final Object processDefinitionId) {
      this.processDefinitionId = processDefinitionId;
      return this;
    }

    @Override
    public OptionalStep processDefinitionId(
        final Object processDefinitionId, final ContractPolicy.FieldPolicy<Object> policy) {
      this.processDefinitionId =
          policy.apply(processDefinitionId, Fields.PROCESS_DEFINITION_ID, null);
      return this;
    }

    @Override
    public OptionalStep errorType(final Object errorType) {
      this.errorType = errorType;
      return this;
    }

    @Override
    public OptionalStep errorType(
        final Object errorType, final ContractPolicy.FieldPolicy<Object> policy) {
      this.errorType = policy.apply(errorType, Fields.ERROR_TYPE, null);
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
    public OptionalStep creationTime(final Object creationTime) {
      this.creationTime = creationTime;
      return this;
    }

    @Override
    public OptionalStep creationTime(
        final Object creationTime, final ContractPolicy.FieldPolicy<Object> policy) {
      this.creationTime = policy.apply(creationTime, Fields.CREATION_TIME, null);
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
    public OptionalStep incidentKey(final Object incidentKey) {
      this.incidentKey = incidentKey;
      return this;
    }

    @Override
    public OptionalStep incidentKey(
        final Object incidentKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.incidentKey = policy.apply(incidentKey, Fields.INCIDENT_KEY, null);
      return this;
    }

    @Override
    public OptionalStep processDefinitionKey(final Object processDefinitionKey) {
      this.processDefinitionKey = processDefinitionKey;
      return this;
    }

    @Override
    public OptionalStep processDefinitionKey(
        final Object processDefinitionKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.processDefinitionKey =
          policy.apply(processDefinitionKey, Fields.PROCESS_DEFINITION_KEY, null);
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
    public OptionalStep elementInstanceKey(final Object elementInstanceKey) {
      this.elementInstanceKey = elementInstanceKey;
      return this;
    }

    @Override
    public OptionalStep elementInstanceKey(
        final Object elementInstanceKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.elementInstanceKey = policy.apply(elementInstanceKey, Fields.ELEMENT_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep jobKey(final Object jobKey) {
      this.jobKey = jobKey;
      return this;
    }

    @Override
    public OptionalStep jobKey(
        final Object jobKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.jobKey = policy.apply(jobKey, Fields.JOB_KEY, null);
      return this;
    }

    @Override
    public GeneratedIncidentFilterStrictContract build() {
      return new GeneratedIncidentFilterStrictContract(
          this.processDefinitionId,
          this.errorType,
          this.errorMessage,
          this.elementId,
          this.creationTime,
          this.state,
          this.tenantId,
          this.incidentKey,
          this.processDefinitionKey,
          this.processInstanceKey,
          this.elementInstanceKey,
          this.jobKey);
    }
  }

  public interface OptionalStep {
    OptionalStep processDefinitionId(final Object processDefinitionId);

    OptionalStep processDefinitionId(
        final Object processDefinitionId, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep errorType(final Object errorType);

    OptionalStep errorType(final Object errorType, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep errorMessage(final Object errorMessage);

    OptionalStep errorMessage(
        final Object errorMessage, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep elementId(final Object elementId);

    OptionalStep elementId(final Object elementId, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep creationTime(final Object creationTime);

    OptionalStep creationTime(
        final Object creationTime, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep state(final Object state);

    OptionalStep state(final Object state, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep tenantId(final Object tenantId);

    OptionalStep tenantId(final Object tenantId, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep incidentKey(final Object incidentKey);

    OptionalStep incidentKey(
        final Object incidentKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep processDefinitionKey(final Object processDefinitionKey);

    OptionalStep processDefinitionKey(
        final Object processDefinitionKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep processInstanceKey(final Object processInstanceKey);

    OptionalStep processInstanceKey(
        final Object processInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep elementInstanceKey(final Object elementInstanceKey);

    OptionalStep elementInstanceKey(
        final Object elementInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep jobKey(final Object jobKey);

    OptionalStep jobKey(final Object jobKey, final ContractPolicy.FieldPolicy<Object> policy);

    GeneratedIncidentFilterStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_ID =
        ContractPolicy.field("IncidentFilter", "processDefinitionId");
    public static final ContractPolicy.FieldRef ERROR_TYPE =
        ContractPolicy.field("IncidentFilter", "errorType");
    public static final ContractPolicy.FieldRef ERROR_MESSAGE =
        ContractPolicy.field("IncidentFilter", "errorMessage");
    public static final ContractPolicy.FieldRef ELEMENT_ID =
        ContractPolicy.field("IncidentFilter", "elementId");
    public static final ContractPolicy.FieldRef CREATION_TIME =
        ContractPolicy.field("IncidentFilter", "creationTime");
    public static final ContractPolicy.FieldRef STATE =
        ContractPolicy.field("IncidentFilter", "state");
    public static final ContractPolicy.FieldRef TENANT_ID =
        ContractPolicy.field("IncidentFilter", "tenantId");
    public static final ContractPolicy.FieldRef INCIDENT_KEY =
        ContractPolicy.field("IncidentFilter", "incidentKey");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_KEY =
        ContractPolicy.field("IncidentFilter", "processDefinitionKey");
    public static final ContractPolicy.FieldRef PROCESS_INSTANCE_KEY =
        ContractPolicy.field("IncidentFilter", "processInstanceKey");
    public static final ContractPolicy.FieldRef ELEMENT_INSTANCE_KEY =
        ContractPolicy.field("IncidentFilter", "elementInstanceKey");
    public static final ContractPolicy.FieldRef JOB_KEY =
        ContractPolicy.field("IncidentFilter", "jobKey");

    private Fields() {}
  }
}
