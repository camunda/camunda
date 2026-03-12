/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.gateway.mapping.http.util.KeyUtil;
import jakarta.annotation.Generated;
import org.springframework.lang.Nullable;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedElementInstanceFilterStrictContract(
    @Nullable String processDefinitionId,
    @Nullable Object state,
    @Nullable String type,
    @Nullable String elementId,
    @Nullable String elementName,
    @Nullable Boolean hasIncident,
    @Nullable String tenantId,
    @Nullable String elementInstanceKey,
    @Nullable String processInstanceKey,
    @Nullable String processDefinitionKey,
    @Nullable String incidentKey,
    @Nullable Object startDate,
    @Nullable Object endDate,
    @Nullable String elementInstanceScopeKey) {

  public static String coerceElementInstanceKey(final Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof String stringValue) {
      return stringValue;
    }
    if (value instanceof Number numberValue) {
      return KeyUtil.keyToString(numberValue.longValue());
    }
    throw new IllegalArgumentException(
        "elementInstanceKey must be a String or Number, but was " + value.getClass().getName());
  }

  public static String coerceProcessInstanceKey(final Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof String stringValue) {
      return stringValue;
    }
    if (value instanceof Number numberValue) {
      return KeyUtil.keyToString(numberValue.longValue());
    }
    throw new IllegalArgumentException(
        "processInstanceKey must be a String or Number, but was " + value.getClass().getName());
  }

  public static String coerceProcessDefinitionKey(final Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof String stringValue) {
      return stringValue;
    }
    if (value instanceof Number numberValue) {
      return KeyUtil.keyToString(numberValue.longValue());
    }
    throw new IllegalArgumentException(
        "processDefinitionKey must be a String or Number, but was " + value.getClass().getName());
  }

  public static String coerceIncidentKey(final Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof String stringValue) {
      return stringValue;
    }
    if (value instanceof Number numberValue) {
      return KeyUtil.keyToString(numberValue.longValue());
    }
    throw new IllegalArgumentException(
        "incidentKey must be a String or Number, but was " + value.getClass().getName());
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
    private String processDefinitionId;
    private Object state;
    private String type;
    private String elementId;
    private String elementName;
    private Boolean hasIncident;
    private String tenantId;
    private Object elementInstanceKey;
    private Object processInstanceKey;
    private Object processDefinitionKey;
    private Object incidentKey;
    private Object startDate;
    private Object endDate;
    private String elementInstanceScopeKey;

    private Builder() {}

    @Override
    public OptionalStep processDefinitionId(final String processDefinitionId) {
      this.processDefinitionId = processDefinitionId;
      return this;
    }

    @Override
    public OptionalStep processDefinitionId(
        final String processDefinitionId, final ContractPolicy.FieldPolicy<String> policy) {
      this.processDefinitionId =
          policy.apply(processDefinitionId, Fields.PROCESS_DEFINITION_ID, null);
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
    public OptionalStep elementId(final String elementId) {
      this.elementId = elementId;
      return this;
    }

    @Override
    public OptionalStep elementId(
        final String elementId, final ContractPolicy.FieldPolicy<String> policy) {
      this.elementId = policy.apply(elementId, Fields.ELEMENT_ID, null);
      return this;
    }

    @Override
    public OptionalStep elementName(final String elementName) {
      this.elementName = elementName;
      return this;
    }

    @Override
    public OptionalStep elementName(
        final String elementName, final ContractPolicy.FieldPolicy<String> policy) {
      this.elementName = policy.apply(elementName, Fields.ELEMENT_NAME, null);
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
    public OptionalStep tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    @Override
    public OptionalStep tenantId(
        final String tenantId, final ContractPolicy.FieldPolicy<String> policy) {
      this.tenantId = policy.apply(tenantId, Fields.TENANT_ID, null);
      return this;
    }

    @Override
    public OptionalStep elementInstanceKey(final String elementInstanceKey) {
      this.elementInstanceKey = elementInstanceKey;
      return this;
    }

    @Override
    public OptionalStep elementInstanceKey(final Object elementInstanceKey) {
      this.elementInstanceKey = elementInstanceKey;
      return this;
    }

    public Builder elementInstanceKey(
        final String elementInstanceKey, final ContractPolicy.FieldPolicy<String> policy) {
      this.elementInstanceKey = policy.apply(elementInstanceKey, Fields.ELEMENT_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep elementInstanceKey(
        final Object elementInstanceKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.elementInstanceKey = policy.apply(elementInstanceKey, Fields.ELEMENT_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep processInstanceKey(final String processInstanceKey) {
      this.processInstanceKey = processInstanceKey;
      return this;
    }

    @Override
    public OptionalStep processInstanceKey(final Object processInstanceKey) {
      this.processInstanceKey = processInstanceKey;
      return this;
    }

    public Builder processInstanceKey(
        final String processInstanceKey, final ContractPolicy.FieldPolicy<String> policy) {
      this.processInstanceKey = policy.apply(processInstanceKey, Fields.PROCESS_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep processInstanceKey(
        final Object processInstanceKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.processInstanceKey = policy.apply(processInstanceKey, Fields.PROCESS_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep processDefinitionKey(final String processDefinitionKey) {
      this.processDefinitionKey = processDefinitionKey;
      return this;
    }

    @Override
    public OptionalStep processDefinitionKey(final Object processDefinitionKey) {
      this.processDefinitionKey = processDefinitionKey;
      return this;
    }

    public Builder processDefinitionKey(
        final String processDefinitionKey, final ContractPolicy.FieldPolicy<String> policy) {
      this.processDefinitionKey =
          policy.apply(processDefinitionKey, Fields.PROCESS_DEFINITION_KEY, null);
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
    public OptionalStep incidentKey(final String incidentKey) {
      this.incidentKey = incidentKey;
      return this;
    }

    @Override
    public OptionalStep incidentKey(final Object incidentKey) {
      this.incidentKey = incidentKey;
      return this;
    }

    public Builder incidentKey(
        final String incidentKey, final ContractPolicy.FieldPolicy<String> policy) {
      this.incidentKey = policy.apply(incidentKey, Fields.INCIDENT_KEY, null);
      return this;
    }

    @Override
    public OptionalStep incidentKey(
        final Object incidentKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.incidentKey = policy.apply(incidentKey, Fields.INCIDENT_KEY, null);
      return this;
    }

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
    public OptionalStep elementInstanceScopeKey(final String elementInstanceScopeKey) {
      this.elementInstanceScopeKey = elementInstanceScopeKey;
      return this;
    }

    @Override
    public OptionalStep elementInstanceScopeKey(
        final String elementInstanceScopeKey, final ContractPolicy.FieldPolicy<String> policy) {
      this.elementInstanceScopeKey =
          policy.apply(elementInstanceScopeKey, Fields.ELEMENT_INSTANCE_SCOPE_KEY, null);
      return this;
    }

    @Override
    public GeneratedElementInstanceFilterStrictContract build() {
      return new GeneratedElementInstanceFilterStrictContract(
          this.processDefinitionId,
          this.state,
          this.type,
          this.elementId,
          this.elementName,
          this.hasIncident,
          this.tenantId,
          coerceElementInstanceKey(this.elementInstanceKey),
          coerceProcessInstanceKey(this.processInstanceKey),
          coerceProcessDefinitionKey(this.processDefinitionKey),
          coerceIncidentKey(this.incidentKey),
          this.startDate,
          this.endDate,
          this.elementInstanceScopeKey);
    }
  }

  public interface OptionalStep {
    OptionalStep processDefinitionId(final String processDefinitionId);

    OptionalStep processDefinitionId(
        final String processDefinitionId, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep state(final Object state);

    OptionalStep state(final Object state, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep type(final String type);

    OptionalStep type(final String type, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep elementId(final String elementId);

    OptionalStep elementId(final String elementId, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep elementName(final String elementName);

    OptionalStep elementName(
        final String elementName, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep hasIncident(final Boolean hasIncident);

    OptionalStep hasIncident(
        final Boolean hasIncident, final ContractPolicy.FieldPolicy<Boolean> policy);

    OptionalStep tenantId(final String tenantId);

    OptionalStep tenantId(final String tenantId, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep elementInstanceKey(final String elementInstanceKey);

    OptionalStep elementInstanceKey(final Object elementInstanceKey);

    OptionalStep elementInstanceKey(
        final String elementInstanceKey, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep elementInstanceKey(
        final Object elementInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep processInstanceKey(final String processInstanceKey);

    OptionalStep processInstanceKey(final Object processInstanceKey);

    OptionalStep processInstanceKey(
        final String processInstanceKey, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep processInstanceKey(
        final Object processInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep processDefinitionKey(final String processDefinitionKey);

    OptionalStep processDefinitionKey(final Object processDefinitionKey);

    OptionalStep processDefinitionKey(
        final String processDefinitionKey, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep processDefinitionKey(
        final Object processDefinitionKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep incidentKey(final String incidentKey);

    OptionalStep incidentKey(final Object incidentKey);

    OptionalStep incidentKey(
        final String incidentKey, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep incidentKey(
        final Object incidentKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep startDate(final Object startDate);

    OptionalStep startDate(final Object startDate, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep endDate(final Object endDate);

    OptionalStep endDate(final Object endDate, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep elementInstanceScopeKey(final String elementInstanceScopeKey);

    OptionalStep elementInstanceScopeKey(
        final String elementInstanceScopeKey, final ContractPolicy.FieldPolicy<String> policy);

    GeneratedElementInstanceFilterStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_ID =
        ContractPolicy.field("ElementInstanceFilter", "processDefinitionId");
    public static final ContractPolicy.FieldRef STATE =
        ContractPolicy.field("ElementInstanceFilter", "state");
    public static final ContractPolicy.FieldRef TYPE =
        ContractPolicy.field("ElementInstanceFilter", "type");
    public static final ContractPolicy.FieldRef ELEMENT_ID =
        ContractPolicy.field("ElementInstanceFilter", "elementId");
    public static final ContractPolicy.FieldRef ELEMENT_NAME =
        ContractPolicy.field("ElementInstanceFilter", "elementName");
    public static final ContractPolicy.FieldRef HAS_INCIDENT =
        ContractPolicy.field("ElementInstanceFilter", "hasIncident");
    public static final ContractPolicy.FieldRef TENANT_ID =
        ContractPolicy.field("ElementInstanceFilter", "tenantId");
    public static final ContractPolicy.FieldRef ELEMENT_INSTANCE_KEY =
        ContractPolicy.field("ElementInstanceFilter", "elementInstanceKey");
    public static final ContractPolicy.FieldRef PROCESS_INSTANCE_KEY =
        ContractPolicy.field("ElementInstanceFilter", "processInstanceKey");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_KEY =
        ContractPolicy.field("ElementInstanceFilter", "processDefinitionKey");
    public static final ContractPolicy.FieldRef INCIDENT_KEY =
        ContractPolicy.field("ElementInstanceFilter", "incidentKey");
    public static final ContractPolicy.FieldRef START_DATE =
        ContractPolicy.field("ElementInstanceFilter", "startDate");
    public static final ContractPolicy.FieldRef END_DATE =
        ContractPolicy.field("ElementInstanceFilter", "endDate");
    public static final ContractPolicy.FieldRef ELEMENT_INSTANCE_SCOPE_KEY =
        ContractPolicy.field("ElementInstanceFilter", "elementInstanceScopeKey");

    private Fields() {}
  }
}
