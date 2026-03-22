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
import io.camunda.gateway.mapping.http.util.KeyUtil;
import jakarta.annotation.Generated;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedElementInstanceFilterStrictContract(
    @JsonProperty("processDefinitionId") @Nullable String processDefinitionId,
    @JsonProperty("state")
        @Nullable GeneratedElementInstanceStateFilterPropertyStrictContract state,
    @JsonProperty("type") @Nullable String type,
    @JsonProperty("elementId") @Nullable String elementId,
    @JsonProperty("elementName") @Nullable String elementName,
    @JsonProperty("hasIncident") @Nullable Boolean hasIncident,
    @JsonProperty("tenantId") @Nullable String tenantId,
    @JsonProperty("elementInstanceKey") @Nullable String elementInstanceKey,
    @JsonProperty("processInstanceKey") @Nullable String processInstanceKey,
    @JsonProperty("processDefinitionKey") @Nullable String processDefinitionKey,
    @JsonProperty("incidentKey") @Nullable String incidentKey,
    @JsonProperty("startDate") @Nullable GeneratedDateTimeFilterPropertyStrictContract startDate,
    @JsonProperty("endDate") @Nullable GeneratedDateTimeFilterPropertyStrictContract endDate,
    @JsonProperty("elementInstanceScopeKey") @Nullable String elementInstanceScopeKey) {

  public GeneratedElementInstanceFilterStrictContract {
    if (processDefinitionId != null)
      if (processDefinitionId.isBlank())
        throw new IllegalArgumentException("processDefinitionId must not be blank");
    if (processDefinitionId != null)
      if (!processDefinitionId.matches("^[a-zA-Z_][a-zA-Z0-9_\\-\\.]*$"))
        throw new IllegalArgumentException(
            "The provided processDefinitionId contains illegal characters. It must match the pattern '^[a-zA-Z_][a-zA-Z0-9_\\-\\.]*$'.");
    if (tenantId != null)
      if (tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
    if (tenantId != null)
      if (tenantId.length() > 256)
        throw new IllegalArgumentException(
            "The provided tenantId exceeds the limit of 256 characters.");
    if (tenantId != null)
      if (!tenantId.matches("^(<default>|[A-Za-z0-9_@.+-]+)$"))
        throw new IllegalArgumentException(
            "The provided tenantId contains illegal characters. It must match the pattern '^(<default>|[A-Za-z0-9_@.+-]+)$'.");
    if (elementInstanceKey != null)
      if (elementInstanceKey.isBlank())
        throw new IllegalArgumentException("elementInstanceKey must not be blank");
    if (elementInstanceKey != null)
      if (elementInstanceKey.length() > 25)
        throw new IllegalArgumentException(
            "The provided elementInstanceKey exceeds the limit of 25 characters.");
    if (elementInstanceKey != null)
      if (!elementInstanceKey.matches("^-?[0-9]+$"))
        throw new IllegalArgumentException(
            "The provided elementInstanceKey contains illegal characters. It must match the pattern '^-?[0-9]+$'.");
    if (processInstanceKey != null)
      if (processInstanceKey.isBlank())
        throw new IllegalArgumentException("processInstanceKey must not be blank");
    if (processInstanceKey != null)
      if (processInstanceKey.length() > 25)
        throw new IllegalArgumentException(
            "The provided processInstanceKey exceeds the limit of 25 characters.");
    if (processInstanceKey != null)
      if (!processInstanceKey.matches("^-?[0-9]+$"))
        throw new IllegalArgumentException(
            "The provided processInstanceKey contains illegal characters. It must match the pattern '^-?[0-9]+$'.");
    if (processDefinitionKey != null)
      if (processDefinitionKey.isBlank())
        throw new IllegalArgumentException("processDefinitionKey must not be blank");
    if (processDefinitionKey != null)
      if (processDefinitionKey.length() > 25)
        throw new IllegalArgumentException(
            "The provided processDefinitionKey exceeds the limit of 25 characters.");
    if (processDefinitionKey != null)
      if (!processDefinitionKey.matches("^-?[0-9]+$"))
        throw new IllegalArgumentException(
            "The provided processDefinitionKey contains illegal characters. It must match the pattern '^-?[0-9]+$'.");
    if (incidentKey != null)
      if (incidentKey.isBlank())
        throw new IllegalArgumentException("incidentKey must not be blank");
    if (incidentKey != null)
      if (incidentKey.length() > 25)
        throw new IllegalArgumentException(
            "The provided incidentKey exceeds the limit of 25 characters.");
    if (incidentKey != null)
      if (!incidentKey.matches("^-?[0-9]+$"))
        throw new IllegalArgumentException(
            "The provided incidentKey contains illegal characters. It must match the pattern '^-?[0-9]+$'.");
  }

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

  public static OptionalStep builder() {
    return new Builder();
  }

  public static final class Builder implements OptionalStep {
    private String processDefinitionId;
    private GeneratedElementInstanceStateFilterPropertyStrictContract state;
    private String type;
    private String elementId;
    private String elementName;
    private Boolean hasIncident;
    private String tenantId;
    private Object elementInstanceKey;
    private Object processInstanceKey;
    private Object processDefinitionKey;
    private Object incidentKey;
    private GeneratedDateTimeFilterPropertyStrictContract startDate;
    private GeneratedDateTimeFilterPropertyStrictContract endDate;
    private String elementInstanceScopeKey;

    private Builder() {}

    @Override
    public OptionalStep processDefinitionId(final @Nullable String processDefinitionId) {
      this.processDefinitionId = processDefinitionId;
      return this;
    }

    @Override
    public OptionalStep processDefinitionId(
        final @Nullable String processDefinitionId,
        final ContractPolicy.FieldPolicy<String> policy) {
      this.processDefinitionId =
          policy.apply(processDefinitionId, Fields.PROCESS_DEFINITION_ID, null);
      return this;
    }

    @Override
    public OptionalStep state(
        final @Nullable GeneratedElementInstanceStateFilterPropertyStrictContract state) {
      this.state = state;
      return this;
    }

    @Override
    public OptionalStep state(
        final @Nullable GeneratedElementInstanceStateFilterPropertyStrictContract state,
        final ContractPolicy.FieldPolicy<GeneratedElementInstanceStateFilterPropertyStrictContract>
            policy) {
      this.state = policy.apply(state, Fields.STATE, null);
      return this;
    }

    @Override
    public OptionalStep type(final @Nullable String type) {
      this.type = type;
      return this;
    }

    @Override
    public OptionalStep type(
        final @Nullable String type, final ContractPolicy.FieldPolicy<String> policy) {
      this.type = policy.apply(type, Fields.TYPE, null);
      return this;
    }

    @Override
    public OptionalStep elementId(final @Nullable String elementId) {
      this.elementId = elementId;
      return this;
    }

    @Override
    public OptionalStep elementId(
        final @Nullable String elementId, final ContractPolicy.FieldPolicy<String> policy) {
      this.elementId = policy.apply(elementId, Fields.ELEMENT_ID, null);
      return this;
    }

    @Override
    public OptionalStep elementName(final @Nullable String elementName) {
      this.elementName = elementName;
      return this;
    }

    @Override
    public OptionalStep elementName(
        final @Nullable String elementName, final ContractPolicy.FieldPolicy<String> policy) {
      this.elementName = policy.apply(elementName, Fields.ELEMENT_NAME, null);
      return this;
    }

    @Override
    public OptionalStep hasIncident(final @Nullable Boolean hasIncident) {
      this.hasIncident = hasIncident;
      return this;
    }

    @Override
    public OptionalStep hasIncident(
        final @Nullable Boolean hasIncident, final ContractPolicy.FieldPolicy<Boolean> policy) {
      this.hasIncident = policy.apply(hasIncident, Fields.HAS_INCIDENT, null);
      return this;
    }

    @Override
    public OptionalStep tenantId(final @Nullable String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    @Override
    public OptionalStep tenantId(
        final @Nullable String tenantId, final ContractPolicy.FieldPolicy<String> policy) {
      this.tenantId = policy.apply(tenantId, Fields.TENANT_ID, null);
      return this;
    }

    @Override
    public OptionalStep elementInstanceKey(final @Nullable String elementInstanceKey) {
      this.elementInstanceKey = elementInstanceKey;
      return this;
    }

    @Override
    public OptionalStep elementInstanceKey(final @Nullable Object elementInstanceKey) {
      this.elementInstanceKey = elementInstanceKey;
      return this;
    }

    public Builder elementInstanceKey(
        final @Nullable String elementInstanceKey,
        final ContractPolicy.FieldPolicy<String> policy) {
      this.elementInstanceKey = policy.apply(elementInstanceKey, Fields.ELEMENT_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep elementInstanceKey(
        final @Nullable Object elementInstanceKey,
        final ContractPolicy.FieldPolicy<Object> policy) {
      this.elementInstanceKey = policy.apply(elementInstanceKey, Fields.ELEMENT_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep processInstanceKey(final @Nullable String processInstanceKey) {
      this.processInstanceKey = processInstanceKey;
      return this;
    }

    @Override
    public OptionalStep processInstanceKey(final @Nullable Object processInstanceKey) {
      this.processInstanceKey = processInstanceKey;
      return this;
    }

    public Builder processInstanceKey(
        final @Nullable String processInstanceKey,
        final ContractPolicy.FieldPolicy<String> policy) {
      this.processInstanceKey = policy.apply(processInstanceKey, Fields.PROCESS_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep processInstanceKey(
        final @Nullable Object processInstanceKey,
        final ContractPolicy.FieldPolicy<Object> policy) {
      this.processInstanceKey = policy.apply(processInstanceKey, Fields.PROCESS_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep processDefinitionKey(final @Nullable String processDefinitionKey) {
      this.processDefinitionKey = processDefinitionKey;
      return this;
    }

    @Override
    public OptionalStep processDefinitionKey(final @Nullable Object processDefinitionKey) {
      this.processDefinitionKey = processDefinitionKey;
      return this;
    }

    public Builder processDefinitionKey(
        final @Nullable String processDefinitionKey,
        final ContractPolicy.FieldPolicy<String> policy) {
      this.processDefinitionKey =
          policy.apply(processDefinitionKey, Fields.PROCESS_DEFINITION_KEY, null);
      return this;
    }

    @Override
    public OptionalStep processDefinitionKey(
        final @Nullable Object processDefinitionKey,
        final ContractPolicy.FieldPolicy<Object> policy) {
      this.processDefinitionKey =
          policy.apply(processDefinitionKey, Fields.PROCESS_DEFINITION_KEY, null);
      return this;
    }

    @Override
    public OptionalStep incidentKey(final @Nullable String incidentKey) {
      this.incidentKey = incidentKey;
      return this;
    }

    @Override
    public OptionalStep incidentKey(final @Nullable Object incidentKey) {
      this.incidentKey = incidentKey;
      return this;
    }

    public Builder incidentKey(
        final @Nullable String incidentKey, final ContractPolicy.FieldPolicy<String> policy) {
      this.incidentKey = policy.apply(incidentKey, Fields.INCIDENT_KEY, null);
      return this;
    }

    @Override
    public OptionalStep incidentKey(
        final @Nullable Object incidentKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.incidentKey = policy.apply(incidentKey, Fields.INCIDENT_KEY, null);
      return this;
    }

    @Override
    public OptionalStep startDate(
        final @Nullable GeneratedDateTimeFilterPropertyStrictContract startDate) {
      this.startDate = startDate;
      return this;
    }

    @Override
    public OptionalStep startDate(
        final @Nullable GeneratedDateTimeFilterPropertyStrictContract startDate,
        final ContractPolicy.FieldPolicy<GeneratedDateTimeFilterPropertyStrictContract> policy) {
      this.startDate = policy.apply(startDate, Fields.START_DATE, null);
      return this;
    }

    @Override
    public OptionalStep endDate(
        final @Nullable GeneratedDateTimeFilterPropertyStrictContract endDate) {
      this.endDate = endDate;
      return this;
    }

    @Override
    public OptionalStep endDate(
        final @Nullable GeneratedDateTimeFilterPropertyStrictContract endDate,
        final ContractPolicy.FieldPolicy<GeneratedDateTimeFilterPropertyStrictContract> policy) {
      this.endDate = policy.apply(endDate, Fields.END_DATE, null);
      return this;
    }

    @Override
    public OptionalStep elementInstanceScopeKey(final @Nullable String elementInstanceScopeKey) {
      this.elementInstanceScopeKey = elementInstanceScopeKey;
      return this;
    }

    @Override
    public OptionalStep elementInstanceScopeKey(
        final @Nullable String elementInstanceScopeKey,
        final ContractPolicy.FieldPolicy<String> policy) {
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
    OptionalStep processDefinitionId(final @Nullable String processDefinitionId);

    OptionalStep processDefinitionId(
        final @Nullable String processDefinitionId,
        final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep state(
        final @Nullable GeneratedElementInstanceStateFilterPropertyStrictContract state);

    OptionalStep state(
        final @Nullable GeneratedElementInstanceStateFilterPropertyStrictContract state,
        final ContractPolicy.FieldPolicy<GeneratedElementInstanceStateFilterPropertyStrictContract>
            policy);

    OptionalStep type(final @Nullable String type);

    OptionalStep type(final @Nullable String type, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep elementId(final @Nullable String elementId);

    OptionalStep elementId(
        final @Nullable String elementId, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep elementName(final @Nullable String elementName);

    OptionalStep elementName(
        final @Nullable String elementName, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep hasIncident(final @Nullable Boolean hasIncident);

    OptionalStep hasIncident(
        final @Nullable Boolean hasIncident, final ContractPolicy.FieldPolicy<Boolean> policy);

    OptionalStep tenantId(final @Nullable String tenantId);

    OptionalStep tenantId(
        final @Nullable String tenantId, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep elementInstanceKey(final @Nullable String elementInstanceKey);

    OptionalStep elementInstanceKey(final @Nullable Object elementInstanceKey);

    OptionalStep elementInstanceKey(
        final @Nullable String elementInstanceKey, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep elementInstanceKey(
        final @Nullable Object elementInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep processInstanceKey(final @Nullable String processInstanceKey);

    OptionalStep processInstanceKey(final @Nullable Object processInstanceKey);

    OptionalStep processInstanceKey(
        final @Nullable String processInstanceKey, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep processInstanceKey(
        final @Nullable Object processInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep processDefinitionKey(final @Nullable String processDefinitionKey);

    OptionalStep processDefinitionKey(final @Nullable Object processDefinitionKey);

    OptionalStep processDefinitionKey(
        final @Nullable String processDefinitionKey,
        final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep processDefinitionKey(
        final @Nullable Object processDefinitionKey,
        final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep incidentKey(final @Nullable String incidentKey);

    OptionalStep incidentKey(final @Nullable Object incidentKey);

    OptionalStep incidentKey(
        final @Nullable String incidentKey, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep incidentKey(
        final @Nullable Object incidentKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep startDate(final @Nullable GeneratedDateTimeFilterPropertyStrictContract startDate);

    OptionalStep startDate(
        final @Nullable GeneratedDateTimeFilterPropertyStrictContract startDate,
        final ContractPolicy.FieldPolicy<GeneratedDateTimeFilterPropertyStrictContract> policy);

    OptionalStep endDate(final @Nullable GeneratedDateTimeFilterPropertyStrictContract endDate);

    OptionalStep endDate(
        final @Nullable GeneratedDateTimeFilterPropertyStrictContract endDate,
        final ContractPolicy.FieldPolicy<GeneratedDateTimeFilterPropertyStrictContract> policy);

    OptionalStep elementInstanceScopeKey(final @Nullable String elementInstanceScopeKey);

    OptionalStep elementInstanceScopeKey(
        final @Nullable String elementInstanceScopeKey,
        final ContractPolicy.FieldPolicy<String> policy);

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
