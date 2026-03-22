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
import jakarta.annotation.Generated;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedIncidentFilterStrictContract(
    @JsonProperty("processDefinitionId")
        @Nullable GeneratedStringFilterPropertyStrictContract processDefinitionId,
    @JsonProperty("errorType")
        @Nullable GeneratedIncidentErrorTypeFilterPropertyStrictContract errorType,
    @JsonProperty("errorMessage")
        @Nullable GeneratedStringFilterPropertyStrictContract errorMessage,
    @JsonProperty("elementId") @Nullable GeneratedStringFilterPropertyStrictContract elementId,
    @JsonProperty("creationTime")
        @Nullable GeneratedDateTimeFilterPropertyStrictContract creationTime,
    @JsonProperty("state") @Nullable GeneratedIncidentStateFilterPropertyStrictContract state,
    @JsonProperty("tenantId") @Nullable GeneratedStringFilterPropertyStrictContract tenantId,
    @JsonProperty("incidentKey")
        @Nullable GeneratedBasicStringFilterPropertyStrictContract incidentKey,
    @JsonProperty("processDefinitionKey")
        @Nullable GeneratedProcessDefinitionKeyFilterPropertyStrictContract processDefinitionKey,
    @JsonProperty("processInstanceKey")
        @Nullable GeneratedProcessInstanceKeyFilterPropertyStrictContract processInstanceKey,
    @JsonProperty("elementInstanceKey")
        @Nullable GeneratedElementInstanceKeyFilterPropertyStrictContract elementInstanceKey,
    @JsonProperty("jobKey") @Nullable GeneratedJobKeyFilterPropertyStrictContract jobKey) {

  public static OptionalStep builder() {
    return new Builder();
  }

  public static final class Builder implements OptionalStep {
    private GeneratedStringFilterPropertyStrictContract processDefinitionId;
    private GeneratedIncidentErrorTypeFilterPropertyStrictContract errorType;
    private GeneratedStringFilterPropertyStrictContract errorMessage;
    private GeneratedStringFilterPropertyStrictContract elementId;
    private GeneratedDateTimeFilterPropertyStrictContract creationTime;
    private GeneratedIncidentStateFilterPropertyStrictContract state;
    private GeneratedStringFilterPropertyStrictContract tenantId;
    private GeneratedBasicStringFilterPropertyStrictContract incidentKey;
    private GeneratedProcessDefinitionKeyFilterPropertyStrictContract processDefinitionKey;
    private GeneratedProcessInstanceKeyFilterPropertyStrictContract processInstanceKey;
    private GeneratedElementInstanceKeyFilterPropertyStrictContract elementInstanceKey;
    private GeneratedJobKeyFilterPropertyStrictContract jobKey;

    private Builder() {}

    @Override
    public OptionalStep processDefinitionId(
        final @Nullable GeneratedStringFilterPropertyStrictContract processDefinitionId) {
      this.processDefinitionId = processDefinitionId;
      return this;
    }

    @Override
    public OptionalStep processDefinitionId(
        final @Nullable GeneratedStringFilterPropertyStrictContract processDefinitionId,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy) {
      this.processDefinitionId =
          policy.apply(processDefinitionId, Fields.PROCESS_DEFINITION_ID, null);
      return this;
    }

    @Override
    public OptionalStep errorType(
        final @Nullable GeneratedIncidentErrorTypeFilterPropertyStrictContract errorType) {
      this.errorType = errorType;
      return this;
    }

    @Override
    public OptionalStep errorType(
        final @Nullable GeneratedIncidentErrorTypeFilterPropertyStrictContract errorType,
        final ContractPolicy.FieldPolicy<GeneratedIncidentErrorTypeFilterPropertyStrictContract>
            policy) {
      this.errorType = policy.apply(errorType, Fields.ERROR_TYPE, null);
      return this;
    }

    @Override
    public OptionalStep errorMessage(
        final @Nullable GeneratedStringFilterPropertyStrictContract errorMessage) {
      this.errorMessage = errorMessage;
      return this;
    }

    @Override
    public OptionalStep errorMessage(
        final @Nullable GeneratedStringFilterPropertyStrictContract errorMessage,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy) {
      this.errorMessage = policy.apply(errorMessage, Fields.ERROR_MESSAGE, null);
      return this;
    }

    @Override
    public OptionalStep elementId(
        final @Nullable GeneratedStringFilterPropertyStrictContract elementId) {
      this.elementId = elementId;
      return this;
    }

    @Override
    public OptionalStep elementId(
        final @Nullable GeneratedStringFilterPropertyStrictContract elementId,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy) {
      this.elementId = policy.apply(elementId, Fields.ELEMENT_ID, null);
      return this;
    }

    @Override
    public OptionalStep creationTime(
        final @Nullable GeneratedDateTimeFilterPropertyStrictContract creationTime) {
      this.creationTime = creationTime;
      return this;
    }

    @Override
    public OptionalStep creationTime(
        final @Nullable GeneratedDateTimeFilterPropertyStrictContract creationTime,
        final ContractPolicy.FieldPolicy<GeneratedDateTimeFilterPropertyStrictContract> policy) {
      this.creationTime = policy.apply(creationTime, Fields.CREATION_TIME, null);
      return this;
    }

    @Override
    public OptionalStep state(
        final @Nullable GeneratedIncidentStateFilterPropertyStrictContract state) {
      this.state = state;
      return this;
    }

    @Override
    public OptionalStep state(
        final @Nullable GeneratedIncidentStateFilterPropertyStrictContract state,
        final ContractPolicy.FieldPolicy<GeneratedIncidentStateFilterPropertyStrictContract>
            policy) {
      this.state = policy.apply(state, Fields.STATE, null);
      return this;
    }

    @Override
    public OptionalStep tenantId(
        final @Nullable GeneratedStringFilterPropertyStrictContract tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    @Override
    public OptionalStep tenantId(
        final @Nullable GeneratedStringFilterPropertyStrictContract tenantId,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy) {
      this.tenantId = policy.apply(tenantId, Fields.TENANT_ID, null);
      return this;
    }

    @Override
    public OptionalStep incidentKey(
        final @Nullable GeneratedBasicStringFilterPropertyStrictContract incidentKey) {
      this.incidentKey = incidentKey;
      return this;
    }

    @Override
    public OptionalStep incidentKey(
        final @Nullable GeneratedBasicStringFilterPropertyStrictContract incidentKey,
        final ContractPolicy.FieldPolicy<GeneratedBasicStringFilterPropertyStrictContract> policy) {
      this.incidentKey = policy.apply(incidentKey, Fields.INCIDENT_KEY, null);
      return this;
    }

    @Override
    public OptionalStep processDefinitionKey(
        final @Nullable GeneratedProcessDefinitionKeyFilterPropertyStrictContract
            processDefinitionKey) {
      this.processDefinitionKey = processDefinitionKey;
      return this;
    }

    @Override
    public OptionalStep processDefinitionKey(
        final @Nullable GeneratedProcessDefinitionKeyFilterPropertyStrictContract
            processDefinitionKey,
        final ContractPolicy.FieldPolicy<GeneratedProcessDefinitionKeyFilterPropertyStrictContract>
            policy) {
      this.processDefinitionKey =
          policy.apply(processDefinitionKey, Fields.PROCESS_DEFINITION_KEY, null);
      return this;
    }

    @Override
    public OptionalStep processInstanceKey(
        final @Nullable GeneratedProcessInstanceKeyFilterPropertyStrictContract
            processInstanceKey) {
      this.processInstanceKey = processInstanceKey;
      return this;
    }

    @Override
    public OptionalStep processInstanceKey(
        final @Nullable GeneratedProcessInstanceKeyFilterPropertyStrictContract processInstanceKey,
        final ContractPolicy.FieldPolicy<GeneratedProcessInstanceKeyFilterPropertyStrictContract>
            policy) {
      this.processInstanceKey = policy.apply(processInstanceKey, Fields.PROCESS_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep elementInstanceKey(
        final @Nullable GeneratedElementInstanceKeyFilterPropertyStrictContract
            elementInstanceKey) {
      this.elementInstanceKey = elementInstanceKey;
      return this;
    }

    @Override
    public OptionalStep elementInstanceKey(
        final @Nullable GeneratedElementInstanceKeyFilterPropertyStrictContract elementInstanceKey,
        final ContractPolicy.FieldPolicy<GeneratedElementInstanceKeyFilterPropertyStrictContract>
            policy) {
      this.elementInstanceKey = policy.apply(elementInstanceKey, Fields.ELEMENT_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep jobKey(final @Nullable GeneratedJobKeyFilterPropertyStrictContract jobKey) {
      this.jobKey = jobKey;
      return this;
    }

    @Override
    public OptionalStep jobKey(
        final @Nullable GeneratedJobKeyFilterPropertyStrictContract jobKey,
        final ContractPolicy.FieldPolicy<GeneratedJobKeyFilterPropertyStrictContract> policy) {
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
    OptionalStep processDefinitionId(
        final @Nullable GeneratedStringFilterPropertyStrictContract processDefinitionId);

    OptionalStep processDefinitionId(
        final @Nullable GeneratedStringFilterPropertyStrictContract processDefinitionId,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy);

    OptionalStep errorType(
        final @Nullable GeneratedIncidentErrorTypeFilterPropertyStrictContract errorType);

    OptionalStep errorType(
        final @Nullable GeneratedIncidentErrorTypeFilterPropertyStrictContract errorType,
        final ContractPolicy.FieldPolicy<GeneratedIncidentErrorTypeFilterPropertyStrictContract>
            policy);

    OptionalStep errorMessage(
        final @Nullable GeneratedStringFilterPropertyStrictContract errorMessage);

    OptionalStep errorMessage(
        final @Nullable GeneratedStringFilterPropertyStrictContract errorMessage,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy);

    OptionalStep elementId(final @Nullable GeneratedStringFilterPropertyStrictContract elementId);

    OptionalStep elementId(
        final @Nullable GeneratedStringFilterPropertyStrictContract elementId,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy);

    OptionalStep creationTime(
        final @Nullable GeneratedDateTimeFilterPropertyStrictContract creationTime);

    OptionalStep creationTime(
        final @Nullable GeneratedDateTimeFilterPropertyStrictContract creationTime,
        final ContractPolicy.FieldPolicy<GeneratedDateTimeFilterPropertyStrictContract> policy);

    OptionalStep state(final @Nullable GeneratedIncidentStateFilterPropertyStrictContract state);

    OptionalStep state(
        final @Nullable GeneratedIncidentStateFilterPropertyStrictContract state,
        final ContractPolicy.FieldPolicy<GeneratedIncidentStateFilterPropertyStrictContract>
            policy);

    OptionalStep tenantId(final @Nullable GeneratedStringFilterPropertyStrictContract tenantId);

    OptionalStep tenantId(
        final @Nullable GeneratedStringFilterPropertyStrictContract tenantId,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy);

    OptionalStep incidentKey(
        final @Nullable GeneratedBasicStringFilterPropertyStrictContract incidentKey);

    OptionalStep incidentKey(
        final @Nullable GeneratedBasicStringFilterPropertyStrictContract incidentKey,
        final ContractPolicy.FieldPolicy<GeneratedBasicStringFilterPropertyStrictContract> policy);

    OptionalStep processDefinitionKey(
        final @Nullable GeneratedProcessDefinitionKeyFilterPropertyStrictContract
            processDefinitionKey);

    OptionalStep processDefinitionKey(
        final @Nullable GeneratedProcessDefinitionKeyFilterPropertyStrictContract
            processDefinitionKey,
        final ContractPolicy.FieldPolicy<GeneratedProcessDefinitionKeyFilterPropertyStrictContract>
            policy);

    OptionalStep processInstanceKey(
        final @Nullable GeneratedProcessInstanceKeyFilterPropertyStrictContract processInstanceKey);

    OptionalStep processInstanceKey(
        final @Nullable GeneratedProcessInstanceKeyFilterPropertyStrictContract processInstanceKey,
        final ContractPolicy.FieldPolicy<GeneratedProcessInstanceKeyFilterPropertyStrictContract>
            policy);

    OptionalStep elementInstanceKey(
        final @Nullable GeneratedElementInstanceKeyFilterPropertyStrictContract elementInstanceKey);

    OptionalStep elementInstanceKey(
        final @Nullable GeneratedElementInstanceKeyFilterPropertyStrictContract elementInstanceKey,
        final ContractPolicy.FieldPolicy<GeneratedElementInstanceKeyFilterPropertyStrictContract>
            policy);

    OptionalStep jobKey(final @Nullable GeneratedJobKeyFilterPropertyStrictContract jobKey);

    OptionalStep jobKey(
        final @Nullable GeneratedJobKeyFilterPropertyStrictContract jobKey,
        final ContractPolicy.FieldPolicy<GeneratedJobKeyFilterPropertyStrictContract> policy);

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
