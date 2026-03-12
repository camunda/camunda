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
import java.util.Objects;
import org.springframework.lang.Nullable;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedElementInstanceStrictContract(
    String processDefinitionId,
    String startDate,
    @Nullable String endDate,
    String elementId,
    String elementName,
    String type,
    io.camunda.gateway.protocol.model.ElementInstanceStateEnum state,
    Boolean hasIncident,
    String tenantId,
    String elementInstanceKey,
    String processInstanceKey,
    @Nullable String rootProcessInstanceKey,
    String processDefinitionKey,
    @Nullable String incidentKey) {

  public GeneratedElementInstanceStrictContract {
    Objects.requireNonNull(
        processDefinitionId, "processDefinitionId is required and must not be null");
    Objects.requireNonNull(startDate, "startDate is required and must not be null");
    Objects.requireNonNull(elementId, "elementId is required and must not be null");
    Objects.requireNonNull(elementName, "elementName is required and must not be null");
    Objects.requireNonNull(type, "type is required and must not be null");
    Objects.requireNonNull(state, "state is required and must not be null");
    Objects.requireNonNull(hasIncident, "hasIncident is required and must not be null");
    Objects.requireNonNull(tenantId, "tenantId is required and must not be null");
    Objects.requireNonNull(
        elementInstanceKey, "elementInstanceKey is required and must not be null");
    Objects.requireNonNull(
        processInstanceKey, "processInstanceKey is required and must not be null");
    Objects.requireNonNull(
        processDefinitionKey, "processDefinitionKey is required and must not be null");
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

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
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
    private ContractPolicy.FieldPolicy<String> processDefinitionIdPolicy;
    private String startDate;
    private ContractPolicy.FieldPolicy<String> startDatePolicy;
    private String endDate;
    private String elementId;
    private ContractPolicy.FieldPolicy<String> elementIdPolicy;
    private String elementName;
    private ContractPolicy.FieldPolicy<String> elementNamePolicy;
    private String type;
    private ContractPolicy.FieldPolicy<String> typePolicy;
    private io.camunda.gateway.protocol.model.ElementInstanceStateEnum state;
    private ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.ElementInstanceStateEnum>
        statePolicy;
    private Boolean hasIncident;
    private ContractPolicy.FieldPolicy<Boolean> hasIncidentPolicy;
    private String tenantId;
    private ContractPolicy.FieldPolicy<String> tenantIdPolicy;
    private Object elementInstanceKey;
    private ContractPolicy.FieldPolicy<Object> elementInstanceKeyPolicy;
    private Object processInstanceKey;
    private ContractPolicy.FieldPolicy<Object> processInstanceKeyPolicy;
    private Object rootProcessInstanceKey;
    private Object processDefinitionKey;
    private ContractPolicy.FieldPolicy<Object> processDefinitionKeyPolicy;
    private Object incidentKey;

    private Builder() {}

    @Override
    public StartDateStep processDefinitionId(
        final String processDefinitionId, final ContractPolicy.FieldPolicy<String> policy) {
      this.processDefinitionId = processDefinitionId;
      this.processDefinitionIdPolicy = policy;
      return this;
    }

    @Override
    public ElementIdStep startDate(
        final String startDate, final ContractPolicy.FieldPolicy<String> policy) {
      this.startDate = startDate;
      this.startDatePolicy = policy;
      return this;
    }

    @Override
    public ElementNameStep elementId(
        final String elementId, final ContractPolicy.FieldPolicy<String> policy) {
      this.elementId = elementId;
      this.elementIdPolicy = policy;
      return this;
    }

    @Override
    public TypeStep elementName(
        final String elementName, final ContractPolicy.FieldPolicy<String> policy) {
      this.elementName = elementName;
      this.elementNamePolicy = policy;
      return this;
    }

    @Override
    public StateStep type(final String type, final ContractPolicy.FieldPolicy<String> policy) {
      this.type = type;
      this.typePolicy = policy;
      return this;
    }

    @Override
    public HasIncidentStep state(
        final io.camunda.gateway.protocol.model.ElementInstanceStateEnum state,
        final ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.ElementInstanceStateEnum>
            policy) {
      this.state = state;
      this.statePolicy = policy;
      return this;
    }

    @Override
    public TenantIdStep hasIncident(
        final Boolean hasIncident, final ContractPolicy.FieldPolicy<Boolean> policy) {
      this.hasIncident = hasIncident;
      this.hasIncidentPolicy = policy;
      return this;
    }

    @Override
    public ElementInstanceKeyStep tenantId(
        final String tenantId, final ContractPolicy.FieldPolicy<String> policy) {
      this.tenantId = tenantId;
      this.tenantIdPolicy = policy;
      return this;
    }

    @Override
    public ProcessInstanceKeyStep elementInstanceKey(
        final Object elementInstanceKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.elementInstanceKey = elementInstanceKey;
      this.elementInstanceKeyPolicy = policy;
      return this;
    }

    @Override
    public ProcessDefinitionKeyStep processInstanceKey(
        final Object processInstanceKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.processInstanceKey = processInstanceKey;
      this.processInstanceKeyPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep processDefinitionKey(
        final Object processDefinitionKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.processDefinitionKey = processDefinitionKey;
      this.processDefinitionKeyPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep endDate(final String endDate) {
      this.endDate = endDate;
      return this;
    }

    @Override
    public OptionalStep endDate(
        final String endDate, final ContractPolicy.FieldPolicy<String> policy) {
      this.endDate = policy.apply(endDate, Fields.END_DATE, null);
      return this;
    }

    @Override
    public OptionalStep rootProcessInstanceKey(final String rootProcessInstanceKey) {
      this.rootProcessInstanceKey = rootProcessInstanceKey;
      return this;
    }

    @Override
    public OptionalStep rootProcessInstanceKey(final Object rootProcessInstanceKey) {
      this.rootProcessInstanceKey = rootProcessInstanceKey;
      return this;
    }

    public Builder rootProcessInstanceKey(
        final String rootProcessInstanceKey, final ContractPolicy.FieldPolicy<String> policy) {
      this.rootProcessInstanceKey =
          policy.apply(rootProcessInstanceKey, Fields.ROOT_PROCESS_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep rootProcessInstanceKey(
        final Object rootProcessInstanceKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.rootProcessInstanceKey =
          policy.apply(rootProcessInstanceKey, Fields.ROOT_PROCESS_INSTANCE_KEY, null);
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
    public GeneratedElementInstanceStrictContract build() {
      return new GeneratedElementInstanceStrictContract(
          applyRequiredPolicy(
              this.processDefinitionId,
              this.processDefinitionIdPolicy,
              Fields.PROCESS_DEFINITION_ID),
          applyRequiredPolicy(this.startDate, this.startDatePolicy, Fields.START_DATE),
          this.endDate,
          applyRequiredPolicy(this.elementId, this.elementIdPolicy, Fields.ELEMENT_ID),
          applyRequiredPolicy(this.elementName, this.elementNamePolicy, Fields.ELEMENT_NAME),
          applyRequiredPolicy(this.type, this.typePolicy, Fields.TYPE),
          applyRequiredPolicy(this.state, this.statePolicy, Fields.STATE),
          applyRequiredPolicy(this.hasIncident, this.hasIncidentPolicy, Fields.HAS_INCIDENT),
          applyRequiredPolicy(this.tenantId, this.tenantIdPolicy, Fields.TENANT_ID),
          coerceElementInstanceKey(
              applyRequiredPolicy(
                  this.elementInstanceKey,
                  this.elementInstanceKeyPolicy,
                  Fields.ELEMENT_INSTANCE_KEY)),
          coerceProcessInstanceKey(
              applyRequiredPolicy(
                  this.processInstanceKey,
                  this.processInstanceKeyPolicy,
                  Fields.PROCESS_INSTANCE_KEY)),
          coerceRootProcessInstanceKey(this.rootProcessInstanceKey),
          coerceProcessDefinitionKey(
              applyRequiredPolicy(
                  this.processDefinitionKey,
                  this.processDefinitionKeyPolicy,
                  Fields.PROCESS_DEFINITION_KEY)),
          coerceIncidentKey(this.incidentKey));
    }
  }

  public interface ProcessDefinitionIdStep {
    StartDateStep processDefinitionId(
        final String processDefinitionId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface StartDateStep {
    ElementIdStep startDate(
        final String startDate, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface ElementIdStep {
    ElementNameStep elementId(
        final String elementId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface ElementNameStep {
    TypeStep elementName(final String elementName, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface TypeStep {
    StateStep type(final String type, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface StateStep {
    HasIncidentStep state(
        final io.camunda.gateway.protocol.model.ElementInstanceStateEnum state,
        final ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.ElementInstanceStateEnum>
            policy);
  }

  public interface HasIncidentStep {
    TenantIdStep hasIncident(
        final Boolean hasIncident, final ContractPolicy.FieldPolicy<Boolean> policy);
  }

  public interface TenantIdStep {
    ElementInstanceKeyStep tenantId(
        final String tenantId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface ElementInstanceKeyStep {
    ProcessInstanceKeyStep elementInstanceKey(
        final Object elementInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface ProcessInstanceKeyStep {
    ProcessDefinitionKeyStep processInstanceKey(
        final Object processInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface ProcessDefinitionKeyStep {
    OptionalStep processDefinitionKey(
        final Object processDefinitionKey, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface OptionalStep {
    OptionalStep endDate(final String endDate);

    OptionalStep endDate(final String endDate, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep rootProcessInstanceKey(final String rootProcessInstanceKey);

    OptionalStep rootProcessInstanceKey(final Object rootProcessInstanceKey);

    OptionalStep rootProcessInstanceKey(
        final String rootProcessInstanceKey, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep rootProcessInstanceKey(
        final Object rootProcessInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep incidentKey(final String incidentKey);

    OptionalStep incidentKey(final Object incidentKey);

    OptionalStep incidentKey(
        final String incidentKey, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep incidentKey(
        final Object incidentKey, final ContractPolicy.FieldPolicy<Object> policy);

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
