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
public record GeneratedIncidentStrictContract(
    String processDefinitionId,
    io.camunda.gateway.protocol.model.IncidentErrorTypeEnum errorType,
    String errorMessage,
    String elementId,
    String creationTime,
    io.camunda.gateway.protocol.model.IncidentStateEnum state,
    String tenantId,
    String incidentKey,
    String processDefinitionKey,
    String processInstanceKey,
    @Nullable String rootProcessInstanceKey,
    String elementInstanceKey,
    @Nullable String jobKey) {

  public GeneratedIncidentStrictContract {
    Objects.requireNonNull(
        processDefinitionId, "processDefinitionId is required and must not be null");
    Objects.requireNonNull(errorType, "errorType is required and must not be null");
    Objects.requireNonNull(errorMessage, "errorMessage is required and must not be null");
    Objects.requireNonNull(elementId, "elementId is required and must not be null");
    Objects.requireNonNull(creationTime, "creationTime is required and must not be null");
    Objects.requireNonNull(state, "state is required and must not be null");
    Objects.requireNonNull(tenantId, "tenantId is required and must not be null");
    Objects.requireNonNull(incidentKey, "incidentKey is required and must not be null");
    Objects.requireNonNull(
        processDefinitionKey, "processDefinitionKey is required and must not be null");
    Objects.requireNonNull(
        processInstanceKey, "processInstanceKey is required and must not be null");
    Objects.requireNonNull(
        elementInstanceKey, "elementInstanceKey is required and must not be null");
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

  public static String coerceJobKey(final Object value) {
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
        "jobKey must be a String or Number, but was " + value.getClass().getName());
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
          ErrorTypeStep,
          ErrorMessageStep,
          ElementIdStep,
          CreationTimeStep,
          StateStep,
          TenantIdStep,
          IncidentKeyStep,
          ProcessDefinitionKeyStep,
          ProcessInstanceKeyStep,
          ElementInstanceKeyStep,
          OptionalStep {
    private String processDefinitionId;
    private ContractPolicy.FieldPolicy<String> processDefinitionIdPolicy;
    private io.camunda.gateway.protocol.model.IncidentErrorTypeEnum errorType;
    private ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.IncidentErrorTypeEnum>
        errorTypePolicy;
    private String errorMessage;
    private ContractPolicy.FieldPolicy<String> errorMessagePolicy;
    private String elementId;
    private ContractPolicy.FieldPolicy<String> elementIdPolicy;
    private String creationTime;
    private ContractPolicy.FieldPolicy<String> creationTimePolicy;
    private io.camunda.gateway.protocol.model.IncidentStateEnum state;
    private ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.IncidentStateEnum>
        statePolicy;
    private String tenantId;
    private ContractPolicy.FieldPolicy<String> tenantIdPolicy;
    private Object incidentKey;
    private ContractPolicy.FieldPolicy<Object> incidentKeyPolicy;
    private Object processDefinitionKey;
    private ContractPolicy.FieldPolicy<Object> processDefinitionKeyPolicy;
    private Object processInstanceKey;
    private ContractPolicy.FieldPolicy<Object> processInstanceKeyPolicy;
    private Object rootProcessInstanceKey;
    private Object elementInstanceKey;
    private ContractPolicy.FieldPolicy<Object> elementInstanceKeyPolicy;
    private Object jobKey;

    private Builder() {}

    @Override
    public ErrorTypeStep processDefinitionId(
        final String processDefinitionId, final ContractPolicy.FieldPolicy<String> policy) {
      this.processDefinitionId = processDefinitionId;
      this.processDefinitionIdPolicy = policy;
      return this;
    }

    @Override
    public ErrorMessageStep errorType(
        final io.camunda.gateway.protocol.model.IncidentErrorTypeEnum errorType,
        final ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.IncidentErrorTypeEnum>
            policy) {
      this.errorType = errorType;
      this.errorTypePolicy = policy;
      return this;
    }

    @Override
    public ElementIdStep errorMessage(
        final String errorMessage, final ContractPolicy.FieldPolicy<String> policy) {
      this.errorMessage = errorMessage;
      this.errorMessagePolicy = policy;
      return this;
    }

    @Override
    public CreationTimeStep elementId(
        final String elementId, final ContractPolicy.FieldPolicy<String> policy) {
      this.elementId = elementId;
      this.elementIdPolicy = policy;
      return this;
    }

    @Override
    public StateStep creationTime(
        final String creationTime, final ContractPolicy.FieldPolicy<String> policy) {
      this.creationTime = creationTime;
      this.creationTimePolicy = policy;
      return this;
    }

    @Override
    public TenantIdStep state(
        final io.camunda.gateway.protocol.model.IncidentStateEnum state,
        final ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.IncidentStateEnum>
            policy) {
      this.state = state;
      this.statePolicy = policy;
      return this;
    }

    @Override
    public IncidentKeyStep tenantId(
        final String tenantId, final ContractPolicy.FieldPolicy<String> policy) {
      this.tenantId = tenantId;
      this.tenantIdPolicy = policy;
      return this;
    }

    @Override
    public ProcessDefinitionKeyStep incidentKey(
        final Object incidentKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.incidentKey = incidentKey;
      this.incidentKeyPolicy = policy;
      return this;
    }

    @Override
    public ProcessInstanceKeyStep processDefinitionKey(
        final Object processDefinitionKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.processDefinitionKey = processDefinitionKey;
      this.processDefinitionKeyPolicy = policy;
      return this;
    }

    @Override
    public ElementInstanceKeyStep processInstanceKey(
        final Object processInstanceKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.processInstanceKey = processInstanceKey;
      this.processInstanceKeyPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep elementInstanceKey(
        final Object elementInstanceKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.elementInstanceKey = elementInstanceKey;
      this.elementInstanceKeyPolicy = policy;
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
    public OptionalStep jobKey(final String jobKey) {
      this.jobKey = jobKey;
      return this;
    }

    @Override
    public OptionalStep jobKey(final Object jobKey) {
      this.jobKey = jobKey;
      return this;
    }

    public Builder jobKey(final String jobKey, final ContractPolicy.FieldPolicy<String> policy) {
      this.jobKey = policy.apply(jobKey, Fields.JOB_KEY, null);
      return this;
    }

    @Override
    public OptionalStep jobKey(
        final Object jobKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.jobKey = policy.apply(jobKey, Fields.JOB_KEY, null);
      return this;
    }

    @Override
    public GeneratedIncidentStrictContract build() {
      return new GeneratedIncidentStrictContract(
          applyRequiredPolicy(
              this.processDefinitionId,
              this.processDefinitionIdPolicy,
              Fields.PROCESS_DEFINITION_ID),
          applyRequiredPolicy(this.errorType, this.errorTypePolicy, Fields.ERROR_TYPE),
          applyRequiredPolicy(this.errorMessage, this.errorMessagePolicy, Fields.ERROR_MESSAGE),
          applyRequiredPolicy(this.elementId, this.elementIdPolicy, Fields.ELEMENT_ID),
          applyRequiredPolicy(this.creationTime, this.creationTimePolicy, Fields.CREATION_TIME),
          applyRequiredPolicy(this.state, this.statePolicy, Fields.STATE),
          applyRequiredPolicy(this.tenantId, this.tenantIdPolicy, Fields.TENANT_ID),
          coerceIncidentKey(
              applyRequiredPolicy(this.incidentKey, this.incidentKeyPolicy, Fields.INCIDENT_KEY)),
          coerceProcessDefinitionKey(
              applyRequiredPolicy(
                  this.processDefinitionKey,
                  this.processDefinitionKeyPolicy,
                  Fields.PROCESS_DEFINITION_KEY)),
          coerceProcessInstanceKey(
              applyRequiredPolicy(
                  this.processInstanceKey,
                  this.processInstanceKeyPolicy,
                  Fields.PROCESS_INSTANCE_KEY)),
          coerceRootProcessInstanceKey(this.rootProcessInstanceKey),
          coerceElementInstanceKey(
              applyRequiredPolicy(
                  this.elementInstanceKey,
                  this.elementInstanceKeyPolicy,
                  Fields.ELEMENT_INSTANCE_KEY)),
          coerceJobKey(this.jobKey));
    }
  }

  public interface ProcessDefinitionIdStep {
    ErrorTypeStep processDefinitionId(
        final String processDefinitionId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface ErrorTypeStep {
    ErrorMessageStep errorType(
        final io.camunda.gateway.protocol.model.IncidentErrorTypeEnum errorType,
        final ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.IncidentErrorTypeEnum>
            policy);
  }

  public interface ErrorMessageStep {
    ElementIdStep errorMessage(
        final String errorMessage, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface ElementIdStep {
    CreationTimeStep elementId(
        final String elementId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface CreationTimeStep {
    StateStep creationTime(
        final String creationTime, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface StateStep {
    TenantIdStep state(
        final io.camunda.gateway.protocol.model.IncidentStateEnum state,
        final ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.IncidentStateEnum>
            policy);
  }

  public interface TenantIdStep {
    IncidentKeyStep tenantId(
        final String tenantId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface IncidentKeyStep {
    ProcessDefinitionKeyStep incidentKey(
        final Object incidentKey, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface ProcessDefinitionKeyStep {
    ProcessInstanceKeyStep processDefinitionKey(
        final Object processDefinitionKey, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface ProcessInstanceKeyStep {
    ElementInstanceKeyStep processInstanceKey(
        final Object processInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface ElementInstanceKeyStep {
    OptionalStep elementInstanceKey(
        final Object elementInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface OptionalStep {
    OptionalStep rootProcessInstanceKey(final String rootProcessInstanceKey);

    OptionalStep rootProcessInstanceKey(final Object rootProcessInstanceKey);

    OptionalStep rootProcessInstanceKey(
        final String rootProcessInstanceKey, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep rootProcessInstanceKey(
        final Object rootProcessInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep jobKey(final String jobKey);

    OptionalStep jobKey(final Object jobKey);

    OptionalStep jobKey(final String jobKey, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep jobKey(final Object jobKey, final ContractPolicy.FieldPolicy<Object> policy);

    GeneratedIncidentStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_ID =
        ContractPolicy.field("IncidentResult", "processDefinitionId");
    public static final ContractPolicy.FieldRef ERROR_TYPE =
        ContractPolicy.field("IncidentResult", "errorType");
    public static final ContractPolicy.FieldRef ERROR_MESSAGE =
        ContractPolicy.field("IncidentResult", "errorMessage");
    public static final ContractPolicy.FieldRef ELEMENT_ID =
        ContractPolicy.field("IncidentResult", "elementId");
    public static final ContractPolicy.FieldRef CREATION_TIME =
        ContractPolicy.field("IncidentResult", "creationTime");
    public static final ContractPolicy.FieldRef STATE =
        ContractPolicy.field("IncidentResult", "state");
    public static final ContractPolicy.FieldRef TENANT_ID =
        ContractPolicy.field("IncidentResult", "tenantId");
    public static final ContractPolicy.FieldRef INCIDENT_KEY =
        ContractPolicy.field("IncidentResult", "incidentKey");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_KEY =
        ContractPolicy.field("IncidentResult", "processDefinitionKey");
    public static final ContractPolicy.FieldRef PROCESS_INSTANCE_KEY =
        ContractPolicy.field("IncidentResult", "processInstanceKey");
    public static final ContractPolicy.FieldRef ROOT_PROCESS_INSTANCE_KEY =
        ContractPolicy.field("IncidentResult", "rootProcessInstanceKey");
    public static final ContractPolicy.FieldRef ELEMENT_INSTANCE_KEY =
        ContractPolicy.field("IncidentResult", "elementInstanceKey");
    public static final ContractPolicy.FieldRef JOB_KEY =
        ContractPolicy.field("IncidentResult", "jobKey");

    private Fields() {}
  }
}
