/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/element-instances.yaml#/components/schemas/ElementInstanceResult
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.gateway.mapping.http.util.KeyUtil;
import jakarta.annotation.Generated;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedElementInstanceStrictContract(
    @JsonProperty("processDefinitionId") String processDefinitionId,
    @JsonProperty("startDate") String startDate,
    @JsonProperty("endDate") @Nullable String endDate,
    @JsonProperty("elementId") String elementId,
    @JsonProperty("elementName") String elementName,
    @JsonProperty("type") String type,
    @JsonProperty("state")
        io.camunda.gateway.mapping.http.search.contract.generated.GeneratedElementInstanceStateEnum
            state,
    @JsonProperty("hasIncident") Boolean hasIncident,
    @JsonProperty("tenantId") String tenantId,
    @JsonProperty("elementInstanceKey") String elementInstanceKey,
    @JsonProperty("processInstanceKey") String processInstanceKey,
    @JsonProperty("rootProcessInstanceKey") @Nullable String rootProcessInstanceKey,
    @JsonProperty("processDefinitionKey") String processDefinitionKey,
    @JsonProperty("incidentKey") @Nullable String incidentKey) {

  public GeneratedElementInstanceStrictContract {
    Objects.requireNonNull(processDefinitionId, "No processDefinitionId provided.");
    Objects.requireNonNull(startDate, "No startDate provided.");
    Objects.requireNonNull(elementId, "No elementId provided.");
    Objects.requireNonNull(elementName, "No elementName provided.");
    Objects.requireNonNull(type, "No type provided.");
    Objects.requireNonNull(state, "No state provided.");
    Objects.requireNonNull(hasIncident, "No hasIncident provided.");
    Objects.requireNonNull(tenantId, "No tenantId provided.");
    Objects.requireNonNull(elementInstanceKey, "No elementInstanceKey provided.");
    Objects.requireNonNull(processInstanceKey, "No processInstanceKey provided.");
    Objects.requireNonNull(processDefinitionKey, "No processDefinitionKey provided.");
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

  public static String coerceRootProcessInstanceKey(final Object value) {
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
        "rootProcessInstanceKey must be a String or Number, but was " + value.getClass().getName());
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

  public static ProcessDefinitionIdStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements ProcessDefinitionIdStep,
          StartDateStep,
          ElementIdStep,
          ElementNameStep,
          TypeStep,
          StateStep,
          HasIncidentStep,
          TenantIdStep,
          ElementInstanceKeyStep,
          ProcessInstanceKeyStep,
          ProcessDefinitionKeyStep,
          OptionalStep {
    private String processDefinitionId;
    private String startDate;
    private String endDate;
    private String elementId;
    private String elementName;
    private String type;
    private io.camunda.gateway.mapping.http.search.contract.generated
            .GeneratedElementInstanceStateEnum
        state;
    private Boolean hasIncident;
    private String tenantId;
    private Object elementInstanceKey;
    private Object processInstanceKey;
    private Object rootProcessInstanceKey;
    private Object processDefinitionKey;
    private Object incidentKey;

    private Builder() {}

    @Override
    public StartDateStep processDefinitionId(final String processDefinitionId) {
      this.processDefinitionId = processDefinitionId;
      return this;
    }

    @Override
    public ElementIdStep startDate(final String startDate) {
      this.startDate = startDate;
      return this;
    }

    @Override
    public ElementNameStep elementId(final String elementId) {
      this.elementId = elementId;
      return this;
    }

    @Override
    public TypeStep elementName(final String elementName) {
      this.elementName = elementName;
      return this;
    }

    @Override
    public StateStep type(final String type) {
      this.type = type;
      return this;
    }

    @Override
    public HasIncidentStep state(
        final io.camunda.gateway.mapping.http.search.contract.generated
                .GeneratedElementInstanceStateEnum
            state) {
      this.state = state;
      return this;
    }

    @Override
    public TenantIdStep hasIncident(final Boolean hasIncident) {
      this.hasIncident = hasIncident;
      return this;
    }

    @Override
    public ElementInstanceKeyStep tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    @Override
    public ProcessInstanceKeyStep elementInstanceKey(final Object elementInstanceKey) {
      this.elementInstanceKey = elementInstanceKey;
      return this;
    }

    @Override
    public ProcessDefinitionKeyStep processInstanceKey(final Object processInstanceKey) {
      this.processInstanceKey = processInstanceKey;
      return this;
    }

    @Override
    public OptionalStep processDefinitionKey(final Object processDefinitionKey) {
      this.processDefinitionKey = processDefinitionKey;
      return this;
    }

    @Override
    public OptionalStep endDate(final @Nullable String endDate) {
      this.endDate = endDate;
      return this;
    }

    @Override
    public OptionalStep endDate(
        final @Nullable String endDate, final ContractPolicy.FieldPolicy<String> policy) {
      this.endDate = policy.apply(endDate, Fields.END_DATE, null);
      return this;
    }

    @Override
    public OptionalStep rootProcessInstanceKey(final @Nullable String rootProcessInstanceKey) {
      this.rootProcessInstanceKey = rootProcessInstanceKey;
      return this;
    }

    @Override
    public OptionalStep rootProcessInstanceKey(final @Nullable Object rootProcessInstanceKey) {
      this.rootProcessInstanceKey = rootProcessInstanceKey;
      return this;
    }

    public Builder rootProcessInstanceKey(
        final @Nullable String rootProcessInstanceKey,
        final ContractPolicy.FieldPolicy<String> policy) {
      this.rootProcessInstanceKey =
          policy.apply(rootProcessInstanceKey, Fields.ROOT_PROCESS_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep rootProcessInstanceKey(
        final @Nullable Object rootProcessInstanceKey,
        final ContractPolicy.FieldPolicy<Object> policy) {
      this.rootProcessInstanceKey =
          policy.apply(rootProcessInstanceKey, Fields.ROOT_PROCESS_INSTANCE_KEY, null);
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
    public GeneratedElementInstanceStrictContract build() {
      return new GeneratedElementInstanceStrictContract(
          this.processDefinitionId,
          this.startDate,
          this.endDate,
          this.elementId,
          this.elementName,
          this.type,
          this.state,
          this.hasIncident,
          this.tenantId,
          coerceElementInstanceKey(this.elementInstanceKey),
          coerceProcessInstanceKey(this.processInstanceKey),
          coerceRootProcessInstanceKey(this.rootProcessInstanceKey),
          coerceProcessDefinitionKey(this.processDefinitionKey),
          coerceIncidentKey(this.incidentKey));
    }
  }

  public interface ProcessDefinitionIdStep {
    StartDateStep processDefinitionId(final String processDefinitionId);
  }

  public interface StartDateStep {
    ElementIdStep startDate(final String startDate);
  }

  public interface ElementIdStep {
    ElementNameStep elementId(final String elementId);
  }

  public interface ElementNameStep {
    TypeStep elementName(final String elementName);
  }

  public interface TypeStep {
    StateStep type(final String type);
  }

  public interface StateStep {
    HasIncidentStep state(
        final io.camunda.gateway.mapping.http.search.contract.generated
                .GeneratedElementInstanceStateEnum
            state);
  }

  public interface HasIncidentStep {
    TenantIdStep hasIncident(final Boolean hasIncident);
  }

  public interface TenantIdStep {
    ElementInstanceKeyStep tenantId(final String tenantId);
  }

  public interface ElementInstanceKeyStep {
    ProcessInstanceKeyStep elementInstanceKey(final Object elementInstanceKey);
  }

  public interface ProcessInstanceKeyStep {
    ProcessDefinitionKeyStep processInstanceKey(final Object processInstanceKey);
  }

  public interface ProcessDefinitionKeyStep {
    OptionalStep processDefinitionKey(final Object processDefinitionKey);
  }

  public interface OptionalStep {
    OptionalStep endDate(final @Nullable String endDate);

    OptionalStep endDate(
        final @Nullable String endDate, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep rootProcessInstanceKey(final @Nullable String rootProcessInstanceKey);

    OptionalStep rootProcessInstanceKey(final @Nullable Object rootProcessInstanceKey);

    OptionalStep rootProcessInstanceKey(
        final @Nullable String rootProcessInstanceKey,
        final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep rootProcessInstanceKey(
        final @Nullable Object rootProcessInstanceKey,
        final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep incidentKey(final @Nullable String incidentKey);

    OptionalStep incidentKey(final @Nullable Object incidentKey);

    OptionalStep incidentKey(
        final @Nullable String incidentKey, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep incidentKey(
        final @Nullable Object incidentKey, final ContractPolicy.FieldPolicy<Object> policy);

    GeneratedElementInstanceStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_ID =
        ContractPolicy.field("ElementInstanceResult", "processDefinitionId");
    public static final ContractPolicy.FieldRef START_DATE =
        ContractPolicy.field("ElementInstanceResult", "startDate");
    public static final ContractPolicy.FieldRef END_DATE =
        ContractPolicy.field("ElementInstanceResult", "endDate");
    public static final ContractPolicy.FieldRef ELEMENT_ID =
        ContractPolicy.field("ElementInstanceResult", "elementId");
    public static final ContractPolicy.FieldRef ELEMENT_NAME =
        ContractPolicy.field("ElementInstanceResult", "elementName");
    public static final ContractPolicy.FieldRef TYPE =
        ContractPolicy.field("ElementInstanceResult", "type");
    public static final ContractPolicy.FieldRef STATE =
        ContractPolicy.field("ElementInstanceResult", "state");
    public static final ContractPolicy.FieldRef HAS_INCIDENT =
        ContractPolicy.field("ElementInstanceResult", "hasIncident");
    public static final ContractPolicy.FieldRef TENANT_ID =
        ContractPolicy.field("ElementInstanceResult", "tenantId");
    public static final ContractPolicy.FieldRef ELEMENT_INSTANCE_KEY =
        ContractPolicy.field("ElementInstanceResult", "elementInstanceKey");
    public static final ContractPolicy.FieldRef PROCESS_INSTANCE_KEY =
        ContractPolicy.field("ElementInstanceResult", "processInstanceKey");
    public static final ContractPolicy.FieldRef ROOT_PROCESS_INSTANCE_KEY =
        ContractPolicy.field("ElementInstanceResult", "rootProcessInstanceKey");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_KEY =
        ContractPolicy.field("ElementInstanceResult", "processDefinitionKey");
    public static final ContractPolicy.FieldRef INCIDENT_KEY =
        ContractPolicy.field("ElementInstanceResult", "incidentKey");

    private Fields() {}
  }
}
