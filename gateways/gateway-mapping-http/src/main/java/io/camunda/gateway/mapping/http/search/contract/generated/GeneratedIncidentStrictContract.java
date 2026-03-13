/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/incidents.yaml#/components/schemas/IncidentResult
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.gateway.mapping.http.util.KeyUtil;
import jakarta.annotation.Generated;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;


@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
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
    @Nullable String jobKey
) {

  public GeneratedIncidentStrictContract {
    Objects.requireNonNull(processDefinitionId, "processDefinitionId is required and must not be null");
    Objects.requireNonNull(errorType, "errorType is required and must not be null");
    Objects.requireNonNull(errorMessage, "errorMessage is required and must not be null");
    Objects.requireNonNull(elementId, "elementId is required and must not be null");
    Objects.requireNonNull(creationTime, "creationTime is required and must not be null");
    Objects.requireNonNull(state, "state is required and must not be null");
    Objects.requireNonNull(tenantId, "tenantId is required and must not be null");
    Objects.requireNonNull(incidentKey, "incidentKey is required and must not be null");
    Objects.requireNonNull(processDefinitionKey, "processDefinitionKey is required and must not be null");
    Objects.requireNonNull(processInstanceKey, "processInstanceKey is required and must not be null");
    Objects.requireNonNull(elementInstanceKey, "elementInstanceKey is required and must not be null");
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



  public static ProcessDefinitionIdStep builder() {
    return new Builder();
  }

  public static final class Builder implements ProcessDefinitionIdStep, ErrorTypeStep, ErrorMessageStep, ElementIdStep, CreationTimeStep, StateStep, TenantIdStep, IncidentKeyStep, ProcessDefinitionKeyStep, ProcessInstanceKeyStep, ElementInstanceKeyStep, OptionalStep {
    private String processDefinitionId;
    private io.camunda.gateway.protocol.model.IncidentErrorTypeEnum errorType;
    private String errorMessage;
    private String elementId;
    private String creationTime;
    private io.camunda.gateway.protocol.model.IncidentStateEnum state;
    private String tenantId;
    private Object incidentKey;
    private Object processDefinitionKey;
    private Object processInstanceKey;
    private Object rootProcessInstanceKey;
    private Object elementInstanceKey;
    private Object jobKey;

    private Builder() {}

    @Override
    public ErrorTypeStep processDefinitionId(final String processDefinitionId) {
      this.processDefinitionId = processDefinitionId;
      return this;
    }

    @Override
    public ErrorMessageStep errorType(final io.camunda.gateway.protocol.model.IncidentErrorTypeEnum errorType) {
      this.errorType = errorType;
      return this;
    }

    @Override
    public ElementIdStep errorMessage(final String errorMessage) {
      this.errorMessage = errorMessage;
      return this;
    }

    @Override
    public CreationTimeStep elementId(final String elementId) {
      this.elementId = elementId;
      return this;
    }

    @Override
    public StateStep creationTime(final String creationTime) {
      this.creationTime = creationTime;
      return this;
    }

    @Override
    public TenantIdStep state(final io.camunda.gateway.protocol.model.IncidentStateEnum state) {
      this.state = state;
      return this;
    }

    @Override
    public IncidentKeyStep tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    @Override
    public ProcessDefinitionKeyStep incidentKey(final Object incidentKey) {
      this.incidentKey = incidentKey;
      return this;
    }

    @Override
    public ProcessInstanceKeyStep processDefinitionKey(final Object processDefinitionKey) {
      this.processDefinitionKey = processDefinitionKey;
      return this;
    }

    @Override
    public ElementInstanceKeyStep processInstanceKey(final Object processInstanceKey) {
      this.processInstanceKey = processInstanceKey;
      return this;
    }

    @Override
    public OptionalStep elementInstanceKey(final Object elementInstanceKey) {
      this.elementInstanceKey = elementInstanceKey;
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

    public Builder rootProcessInstanceKey(final @Nullable String rootProcessInstanceKey, final ContractPolicy.FieldPolicy<String> policy) {
      this.rootProcessInstanceKey = policy.apply(rootProcessInstanceKey, Fields.ROOT_PROCESS_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep rootProcessInstanceKey(final @Nullable Object rootProcessInstanceKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.rootProcessInstanceKey = policy.apply(rootProcessInstanceKey, Fields.ROOT_PROCESS_INSTANCE_KEY, null);
      return this;
    }


    @Override
    public OptionalStep jobKey(final @Nullable String jobKey) {
      this.jobKey = jobKey;
      return this;
    }

    @Override
    public OptionalStep jobKey(final @Nullable Object jobKey) {
      this.jobKey = jobKey;
      return this;
    }

    public Builder jobKey(final @Nullable String jobKey, final ContractPolicy.FieldPolicy<String> policy) {
      this.jobKey = policy.apply(jobKey, Fields.JOB_KEY, null);
      return this;
    }

    @Override
    public OptionalStep jobKey(final @Nullable Object jobKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.jobKey = policy.apply(jobKey, Fields.JOB_KEY, null);
      return this;
    }

    @Override
    public GeneratedIncidentStrictContract build() {
      return new GeneratedIncidentStrictContract(
          this.processDefinitionId,
          this.errorType,
          this.errorMessage,
          this.elementId,
          this.creationTime,
          this.state,
          this.tenantId,
          coerceIncidentKey(this.incidentKey),
          coerceProcessDefinitionKey(this.processDefinitionKey),
          coerceProcessInstanceKey(this.processInstanceKey),
          coerceRootProcessInstanceKey(this.rootProcessInstanceKey),
          coerceElementInstanceKey(this.elementInstanceKey),
          coerceJobKey(this.jobKey));
    }
  }

  public interface ProcessDefinitionIdStep {
    ErrorTypeStep processDefinitionId(final String processDefinitionId);
  }

  public interface ErrorTypeStep {
    ErrorMessageStep errorType(final io.camunda.gateway.protocol.model.IncidentErrorTypeEnum errorType);
  }

  public interface ErrorMessageStep {
    ElementIdStep errorMessage(final String errorMessage);
  }

  public interface ElementIdStep {
    CreationTimeStep elementId(final String elementId);
  }

  public interface CreationTimeStep {
    StateStep creationTime(final String creationTime);
  }

  public interface StateStep {
    TenantIdStep state(final io.camunda.gateway.protocol.model.IncidentStateEnum state);
  }

  public interface TenantIdStep {
    IncidentKeyStep tenantId(final String tenantId);
  }

  public interface IncidentKeyStep {
    ProcessDefinitionKeyStep incidentKey(final Object incidentKey);
  }

  public interface ProcessDefinitionKeyStep {
    ProcessInstanceKeyStep processDefinitionKey(final Object processDefinitionKey);
  }

  public interface ProcessInstanceKeyStep {
    ElementInstanceKeyStep processInstanceKey(final Object processInstanceKey);
  }

  public interface ElementInstanceKeyStep {
    OptionalStep elementInstanceKey(final Object elementInstanceKey);
  }

  public interface OptionalStep {
  OptionalStep rootProcessInstanceKey(final @Nullable String rootProcessInstanceKey);

  OptionalStep rootProcessInstanceKey(final @Nullable Object rootProcessInstanceKey);

  OptionalStep rootProcessInstanceKey(final @Nullable String rootProcessInstanceKey, final ContractPolicy.FieldPolicy<String> policy);

  OptionalStep rootProcessInstanceKey(final @Nullable Object rootProcessInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);


  OptionalStep jobKey(final @Nullable String jobKey);

  OptionalStep jobKey(final @Nullable Object jobKey);

  OptionalStep jobKey(final @Nullable String jobKey, final ContractPolicy.FieldPolicy<String> policy);

  OptionalStep jobKey(final @Nullable Object jobKey, final ContractPolicy.FieldPolicy<Object> policy);


    GeneratedIncidentStrictContract build();
  }


  public static final class Fields {
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_ID = ContractPolicy.field("IncidentResult", "processDefinitionId");
    public static final ContractPolicy.FieldRef ERROR_TYPE = ContractPolicy.field("IncidentResult", "errorType");
    public static final ContractPolicy.FieldRef ERROR_MESSAGE = ContractPolicy.field("IncidentResult", "errorMessage");
    public static final ContractPolicy.FieldRef ELEMENT_ID = ContractPolicy.field("IncidentResult", "elementId");
    public static final ContractPolicy.FieldRef CREATION_TIME = ContractPolicy.field("IncidentResult", "creationTime");
    public static final ContractPolicy.FieldRef STATE = ContractPolicy.field("IncidentResult", "state");
    public static final ContractPolicy.FieldRef TENANT_ID = ContractPolicy.field("IncidentResult", "tenantId");
    public static final ContractPolicy.FieldRef INCIDENT_KEY = ContractPolicy.field("IncidentResult", "incidentKey");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_KEY = ContractPolicy.field("IncidentResult", "processDefinitionKey");
    public static final ContractPolicy.FieldRef PROCESS_INSTANCE_KEY = ContractPolicy.field("IncidentResult", "processInstanceKey");
    public static final ContractPolicy.FieldRef ROOT_PROCESS_INSTANCE_KEY = ContractPolicy.field("IncidentResult", "rootProcessInstanceKey");
    public static final ContractPolicy.FieldRef ELEMENT_INSTANCE_KEY = ContractPolicy.field("IncidentResult", "elementInstanceKey");
    public static final ContractPolicy.FieldRef JOB_KEY = ContractPolicy.field("IncidentResult", "jobKey");

    private Fields() {}
  }


}
