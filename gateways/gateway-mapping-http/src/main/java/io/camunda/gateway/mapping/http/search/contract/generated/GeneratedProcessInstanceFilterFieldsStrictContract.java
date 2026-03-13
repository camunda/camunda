/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/process-instances.yaml#/components/schemas/ProcessInstanceFilterFields
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.gateway.mapping.http.util.KeyUtil;
import jakarta.annotation.Generated;
import java.util.ArrayList;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;


@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedProcessInstanceFilterFieldsStrictContract(
    @Nullable Object startDate,
    @Nullable Object endDate,
    @Nullable Object state,
    @Nullable Boolean hasIncident,
    @Nullable Object tenantId,
    java.util.@Nullable List<GeneratedVariableValueFilterPropertyStrictContract> variables,
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
    java.util.@Nullable Set<String> tags,
    @Nullable Object businessId,
    @Nullable Object processDefinitionId,
    @Nullable Object processDefinitionName,
    @Nullable Object processDefinitionVersion,
    @Nullable Object processDefinitionVersionTag,
    @Nullable Object processDefinitionKey
) {

  public static java.util.List<GeneratedVariableValueFilterPropertyStrictContract> coerceVariables(final Object value) {
    if (value == null) {
      return null;
    }
    if (!(value instanceof java.util.List<?> listValue)) {
      throw new IllegalArgumentException(
          "variables must be a List of GeneratedVariableValueFilterPropertyStrictContract, but was " + value.getClass().getName());
    }

    final var result = new ArrayList<GeneratedVariableValueFilterPropertyStrictContract>(listValue.size());
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
    private Object processDefinitionId;
    private Object processDefinitionName;
    private Object processDefinitionVersion;
    private Object processDefinitionVersionTag;
    private Object processDefinitionKey;

    private Builder() {}

    @Override
    public OptionalStep startDate(final @Nullable Object startDate) {
      this.startDate = startDate;
      return this;
    }

    @Override
    public OptionalStep startDate(final @Nullable Object startDate, final ContractPolicy.FieldPolicy<Object> policy) {
      this.startDate = policy.apply(startDate, Fields.START_DATE, null);
      return this;
    }


    @Override
    public OptionalStep endDate(final @Nullable Object endDate) {
      this.endDate = endDate;
      return this;
    }

    @Override
    public OptionalStep endDate(final @Nullable Object endDate, final ContractPolicy.FieldPolicy<Object> policy) {
      this.endDate = policy.apply(endDate, Fields.END_DATE, null);
      return this;
    }


    @Override
    public OptionalStep state(final @Nullable Object state) {
      this.state = state;
      return this;
    }

    @Override
    public OptionalStep state(final @Nullable Object state, final ContractPolicy.FieldPolicy<Object> policy) {
      this.state = policy.apply(state, Fields.STATE, null);
      return this;
    }


    @Override
    public OptionalStep hasIncident(final @Nullable Boolean hasIncident) {
      this.hasIncident = hasIncident;
      return this;
    }

    @Override
    public OptionalStep hasIncident(final @Nullable Boolean hasIncident, final ContractPolicy.FieldPolicy<Boolean> policy) {
      this.hasIncident = policy.apply(hasIncident, Fields.HAS_INCIDENT, null);
      return this;
    }


    @Override
    public OptionalStep tenantId(final @Nullable Object tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    @Override
    public OptionalStep tenantId(final @Nullable Object tenantId, final ContractPolicy.FieldPolicy<Object> policy) {
      this.tenantId = policy.apply(tenantId, Fields.TENANT_ID, null);
      return this;
    }


    @Override
    public OptionalStep variables(final java.util.@Nullable List<GeneratedVariableValueFilterPropertyStrictContract> variables) {
      this.variables = variables;
      return this;
    }

    @Override
    public OptionalStep variables(final @Nullable Object variables) {
      this.variables = variables;
      return this;
    }

    public Builder variables(final java.util.@Nullable List<GeneratedVariableValueFilterPropertyStrictContract> variables, final ContractPolicy.FieldPolicy<java.util.List<GeneratedVariableValueFilterPropertyStrictContract>> policy) {
      this.variables = policy.apply(variables, Fields.VARIABLES, null);
      return this;
    }

    @Override
    public OptionalStep variables(final @Nullable Object variables, final ContractPolicy.FieldPolicy<Object> policy) {
      this.variables = policy.apply(variables, Fields.VARIABLES, null);
      return this;
    }


    @Override
    public OptionalStep processInstanceKey(final @Nullable Object processInstanceKey) {
      this.processInstanceKey = processInstanceKey;
      return this;
    }

    @Override
    public OptionalStep processInstanceKey(final @Nullable Object processInstanceKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.processInstanceKey = policy.apply(processInstanceKey, Fields.PROCESS_INSTANCE_KEY, null);
      return this;
    }


    @Override
    public OptionalStep parentProcessInstanceKey(final @Nullable Object parentProcessInstanceKey) {
      this.parentProcessInstanceKey = parentProcessInstanceKey;
      return this;
    }

    @Override
    public OptionalStep parentProcessInstanceKey(final @Nullable Object parentProcessInstanceKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.parentProcessInstanceKey = policy.apply(parentProcessInstanceKey, Fields.PARENT_PROCESS_INSTANCE_KEY, null);
      return this;
    }


    @Override
    public OptionalStep parentElementInstanceKey(final @Nullable Object parentElementInstanceKey) {
      this.parentElementInstanceKey = parentElementInstanceKey;
      return this;
    }

    @Override
    public OptionalStep parentElementInstanceKey(final @Nullable Object parentElementInstanceKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.parentElementInstanceKey = policy.apply(parentElementInstanceKey, Fields.PARENT_ELEMENT_INSTANCE_KEY, null);
      return this;
    }


    @Override
    public OptionalStep batchOperationId(final @Nullable Object batchOperationId) {
      this.batchOperationId = batchOperationId;
      return this;
    }

    @Override
    public OptionalStep batchOperationId(final @Nullable Object batchOperationId, final ContractPolicy.FieldPolicy<Object> policy) {
      this.batchOperationId = policy.apply(batchOperationId, Fields.BATCH_OPERATION_ID, null);
      return this;
    }


    @Override
    public OptionalStep errorMessage(final @Nullable Object errorMessage) {
      this.errorMessage = errorMessage;
      return this;
    }

    @Override
    public OptionalStep errorMessage(final @Nullable Object errorMessage, final ContractPolicy.FieldPolicy<Object> policy) {
      this.errorMessage = policy.apply(errorMessage, Fields.ERROR_MESSAGE, null);
      return this;
    }


    @Override
    public OptionalStep hasRetriesLeft(final @Nullable Boolean hasRetriesLeft) {
      this.hasRetriesLeft = hasRetriesLeft;
      return this;
    }

    @Override
    public OptionalStep hasRetriesLeft(final @Nullable Boolean hasRetriesLeft, final ContractPolicy.FieldPolicy<Boolean> policy) {
      this.hasRetriesLeft = policy.apply(hasRetriesLeft, Fields.HAS_RETRIES_LEFT, null);
      return this;
    }


    @Override
    public OptionalStep elementInstanceState(final @Nullable Object elementInstanceState) {
      this.elementInstanceState = elementInstanceState;
      return this;
    }

    @Override
    public OptionalStep elementInstanceState(final @Nullable Object elementInstanceState, final ContractPolicy.FieldPolicy<Object> policy) {
      this.elementInstanceState = policy.apply(elementInstanceState, Fields.ELEMENT_INSTANCE_STATE, null);
      return this;
    }


    @Override
    public OptionalStep elementId(final @Nullable Object elementId) {
      this.elementId = elementId;
      return this;
    }

    @Override
    public OptionalStep elementId(final @Nullable Object elementId, final ContractPolicy.FieldPolicy<Object> policy) {
      this.elementId = policy.apply(elementId, Fields.ELEMENT_ID, null);
      return this;
    }


    @Override
    public OptionalStep hasElementInstanceIncident(final @Nullable Boolean hasElementInstanceIncident) {
      this.hasElementInstanceIncident = hasElementInstanceIncident;
      return this;
    }

    @Override
    public OptionalStep hasElementInstanceIncident(final @Nullable Boolean hasElementInstanceIncident, final ContractPolicy.FieldPolicy<Boolean> policy) {
      this.hasElementInstanceIncident = policy.apply(hasElementInstanceIncident, Fields.HAS_ELEMENT_INSTANCE_INCIDENT, null);
      return this;
    }


    @Override
    public OptionalStep incidentErrorHashCode(final @Nullable Object incidentErrorHashCode) {
      this.incidentErrorHashCode = incidentErrorHashCode;
      return this;
    }

    @Override
    public OptionalStep incidentErrorHashCode(final @Nullable Object incidentErrorHashCode, final ContractPolicy.FieldPolicy<Object> policy) {
      this.incidentErrorHashCode = policy.apply(incidentErrorHashCode, Fields.INCIDENT_ERROR_HASH_CODE, null);
      return this;
    }


    @Override
    public OptionalStep tags(final java.util.@Nullable Set<String> tags) {
      this.tags = tags;
      return this;
    }

    @Override
    public OptionalStep tags(final java.util.@Nullable Set<String> tags, final ContractPolicy.FieldPolicy<java.util.Set<String>> policy) {
      this.tags = policy.apply(tags, Fields.TAGS, null);
      return this;
    }


    @Override
    public OptionalStep businessId(final @Nullable Object businessId) {
      this.businessId = businessId;
      return this;
    }

    @Override
    public OptionalStep businessId(final @Nullable Object businessId, final ContractPolicy.FieldPolicy<Object> policy) {
      this.businessId = policy.apply(businessId, Fields.BUSINESS_ID, null);
      return this;
    }


    @Override
    public OptionalStep processDefinitionId(final @Nullable Object processDefinitionId) {
      this.processDefinitionId = processDefinitionId;
      return this;
    }

    @Override
    public OptionalStep processDefinitionId(final @Nullable Object processDefinitionId, final ContractPolicy.FieldPolicy<Object> policy) {
      this.processDefinitionId = policy.apply(processDefinitionId, Fields.PROCESS_DEFINITION_ID, null);
      return this;
    }


    @Override
    public OptionalStep processDefinitionName(final @Nullable Object processDefinitionName) {
      this.processDefinitionName = processDefinitionName;
      return this;
    }

    @Override
    public OptionalStep processDefinitionName(final @Nullable Object processDefinitionName, final ContractPolicy.FieldPolicy<Object> policy) {
      this.processDefinitionName = policy.apply(processDefinitionName, Fields.PROCESS_DEFINITION_NAME, null);
      return this;
    }


    @Override
    public OptionalStep processDefinitionVersion(final @Nullable Object processDefinitionVersion) {
      this.processDefinitionVersion = processDefinitionVersion;
      return this;
    }

    @Override
    public OptionalStep processDefinitionVersion(final @Nullable Object processDefinitionVersion, final ContractPolicy.FieldPolicy<Object> policy) {
      this.processDefinitionVersion = policy.apply(processDefinitionVersion, Fields.PROCESS_DEFINITION_VERSION, null);
      return this;
    }


    @Override
    public OptionalStep processDefinitionVersionTag(final @Nullable Object processDefinitionVersionTag) {
      this.processDefinitionVersionTag = processDefinitionVersionTag;
      return this;
    }

    @Override
    public OptionalStep processDefinitionVersionTag(final @Nullable Object processDefinitionVersionTag, final ContractPolicy.FieldPolicy<Object> policy) {
      this.processDefinitionVersionTag = policy.apply(processDefinitionVersionTag, Fields.PROCESS_DEFINITION_VERSION_TAG, null);
      return this;
    }


    @Override
    public OptionalStep processDefinitionKey(final @Nullable Object processDefinitionKey) {
      this.processDefinitionKey = processDefinitionKey;
      return this;
    }

    @Override
    public OptionalStep processDefinitionKey(final @Nullable Object processDefinitionKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.processDefinitionKey = policy.apply(processDefinitionKey, Fields.PROCESS_DEFINITION_KEY, null);
      return this;
    }

    @Override
    public GeneratedProcessInstanceFilterFieldsStrictContract build() {
      return new GeneratedProcessInstanceFilterFieldsStrictContract(
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
          this.businessId,
          this.processDefinitionId,
          this.processDefinitionName,
          this.processDefinitionVersion,
          this.processDefinitionVersionTag,
          this.processDefinitionKey);
    }
  }

  public interface OptionalStep {
  OptionalStep startDate(final @Nullable Object startDate);

  OptionalStep startDate(final @Nullable Object startDate, final ContractPolicy.FieldPolicy<Object> policy);


  OptionalStep endDate(final @Nullable Object endDate);

  OptionalStep endDate(final @Nullable Object endDate, final ContractPolicy.FieldPolicy<Object> policy);


  OptionalStep state(final @Nullable Object state);

  OptionalStep state(final @Nullable Object state, final ContractPolicy.FieldPolicy<Object> policy);


  OptionalStep hasIncident(final @Nullable Boolean hasIncident);

  OptionalStep hasIncident(final @Nullable Boolean hasIncident, final ContractPolicy.FieldPolicy<Boolean> policy);


  OptionalStep tenantId(final @Nullable Object tenantId);

  OptionalStep tenantId(final @Nullable Object tenantId, final ContractPolicy.FieldPolicy<Object> policy);


  OptionalStep variables(final java.util.@Nullable List<GeneratedVariableValueFilterPropertyStrictContract> variables);

  OptionalStep variables(final @Nullable Object variables);

  OptionalStep variables(final java.util.@Nullable List<GeneratedVariableValueFilterPropertyStrictContract> variables, final ContractPolicy.FieldPolicy<java.util.List<GeneratedVariableValueFilterPropertyStrictContract>> policy);

  OptionalStep variables(final @Nullable Object variables, final ContractPolicy.FieldPolicy<Object> policy);


  OptionalStep processInstanceKey(final @Nullable Object processInstanceKey);

  OptionalStep processInstanceKey(final @Nullable Object processInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);


  OptionalStep parentProcessInstanceKey(final @Nullable Object parentProcessInstanceKey);

  OptionalStep parentProcessInstanceKey(final @Nullable Object parentProcessInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);


  OptionalStep parentElementInstanceKey(final @Nullable Object parentElementInstanceKey);

  OptionalStep parentElementInstanceKey(final @Nullable Object parentElementInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);


  OptionalStep batchOperationId(final @Nullable Object batchOperationId);

  OptionalStep batchOperationId(final @Nullable Object batchOperationId, final ContractPolicy.FieldPolicy<Object> policy);


  OptionalStep errorMessage(final @Nullable Object errorMessage);

  OptionalStep errorMessage(final @Nullable Object errorMessage, final ContractPolicy.FieldPolicy<Object> policy);


  OptionalStep hasRetriesLeft(final @Nullable Boolean hasRetriesLeft);

  OptionalStep hasRetriesLeft(final @Nullable Boolean hasRetriesLeft, final ContractPolicy.FieldPolicy<Boolean> policy);


  OptionalStep elementInstanceState(final @Nullable Object elementInstanceState);

  OptionalStep elementInstanceState(final @Nullable Object elementInstanceState, final ContractPolicy.FieldPolicy<Object> policy);


  OptionalStep elementId(final @Nullable Object elementId);

  OptionalStep elementId(final @Nullable Object elementId, final ContractPolicy.FieldPolicy<Object> policy);


  OptionalStep hasElementInstanceIncident(final @Nullable Boolean hasElementInstanceIncident);

  OptionalStep hasElementInstanceIncident(final @Nullable Boolean hasElementInstanceIncident, final ContractPolicy.FieldPolicy<Boolean> policy);


  OptionalStep incidentErrorHashCode(final @Nullable Object incidentErrorHashCode);

  OptionalStep incidentErrorHashCode(final @Nullable Object incidentErrorHashCode, final ContractPolicy.FieldPolicy<Object> policy);


  OptionalStep tags(final java.util.@Nullable Set<String> tags);

  OptionalStep tags(final java.util.@Nullable Set<String> tags, final ContractPolicy.FieldPolicy<java.util.Set<String>> policy);


  OptionalStep businessId(final @Nullable Object businessId);

  OptionalStep businessId(final @Nullable Object businessId, final ContractPolicy.FieldPolicy<Object> policy);


  OptionalStep processDefinitionId(final @Nullable Object processDefinitionId);

  OptionalStep processDefinitionId(final @Nullable Object processDefinitionId, final ContractPolicy.FieldPolicy<Object> policy);


  OptionalStep processDefinitionName(final @Nullable Object processDefinitionName);

  OptionalStep processDefinitionName(final @Nullable Object processDefinitionName, final ContractPolicy.FieldPolicy<Object> policy);


  OptionalStep processDefinitionVersion(final @Nullable Object processDefinitionVersion);

  OptionalStep processDefinitionVersion(final @Nullable Object processDefinitionVersion, final ContractPolicy.FieldPolicy<Object> policy);


  OptionalStep processDefinitionVersionTag(final @Nullable Object processDefinitionVersionTag);

  OptionalStep processDefinitionVersionTag(final @Nullable Object processDefinitionVersionTag, final ContractPolicy.FieldPolicy<Object> policy);


  OptionalStep processDefinitionKey(final @Nullable Object processDefinitionKey);

  OptionalStep processDefinitionKey(final @Nullable Object processDefinitionKey, final ContractPolicy.FieldPolicy<Object> policy);


    GeneratedProcessInstanceFilterFieldsStrictContract build();
  }


  public static final class Fields {
    public static final ContractPolicy.FieldRef START_DATE = ContractPolicy.field("ProcessInstanceFilterFields", "startDate");
    public static final ContractPolicy.FieldRef END_DATE = ContractPolicy.field("ProcessInstanceFilterFields", "endDate");
    public static final ContractPolicy.FieldRef STATE = ContractPolicy.field("ProcessInstanceFilterFields", "state");
    public static final ContractPolicy.FieldRef HAS_INCIDENT = ContractPolicy.field("ProcessInstanceFilterFields", "hasIncident");
    public static final ContractPolicy.FieldRef TENANT_ID = ContractPolicy.field("ProcessInstanceFilterFields", "tenantId");
    public static final ContractPolicy.FieldRef VARIABLES = ContractPolicy.field("ProcessInstanceFilterFields", "variables");
    public static final ContractPolicy.FieldRef PROCESS_INSTANCE_KEY = ContractPolicy.field("ProcessInstanceFilterFields", "processInstanceKey");
    public static final ContractPolicy.FieldRef PARENT_PROCESS_INSTANCE_KEY = ContractPolicy.field("ProcessInstanceFilterFields", "parentProcessInstanceKey");
    public static final ContractPolicy.FieldRef PARENT_ELEMENT_INSTANCE_KEY = ContractPolicy.field("ProcessInstanceFilterFields", "parentElementInstanceKey");
    public static final ContractPolicy.FieldRef BATCH_OPERATION_ID = ContractPolicy.field("ProcessInstanceFilterFields", "batchOperationId");
    public static final ContractPolicy.FieldRef ERROR_MESSAGE = ContractPolicy.field("ProcessInstanceFilterFields", "errorMessage");
    public static final ContractPolicy.FieldRef HAS_RETRIES_LEFT = ContractPolicy.field("ProcessInstanceFilterFields", "hasRetriesLeft");
    public static final ContractPolicy.FieldRef ELEMENT_INSTANCE_STATE = ContractPolicy.field("ProcessInstanceFilterFields", "elementInstanceState");
    public static final ContractPolicy.FieldRef ELEMENT_ID = ContractPolicy.field("ProcessInstanceFilterFields", "elementId");
    public static final ContractPolicy.FieldRef HAS_ELEMENT_INSTANCE_INCIDENT = ContractPolicy.field("ProcessInstanceFilterFields", "hasElementInstanceIncident");
    public static final ContractPolicy.FieldRef INCIDENT_ERROR_HASH_CODE = ContractPolicy.field("ProcessInstanceFilterFields", "incidentErrorHashCode");
    public static final ContractPolicy.FieldRef TAGS = ContractPolicy.field("ProcessInstanceFilterFields", "tags");
    public static final ContractPolicy.FieldRef BUSINESS_ID = ContractPolicy.field("ProcessInstanceFilterFields", "businessId");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_ID = ContractPolicy.field("ProcessInstanceFilterFields", "processDefinitionId");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_NAME = ContractPolicy.field("ProcessInstanceFilterFields", "processDefinitionName");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_VERSION = ContractPolicy.field("ProcessInstanceFilterFields", "processDefinitionVersion");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_VERSION_TAG = ContractPolicy.field("ProcessInstanceFilterFields", "processDefinitionVersionTag");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_KEY = ContractPolicy.field("ProcessInstanceFilterFields", "processDefinitionKey");

    private Fields() {}
  }


}
