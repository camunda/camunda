/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/process-instances.yaml#/components/schemas/ProcessInstanceFilter
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import jakarta.annotation.Generated;
import java.util.ArrayList;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedProcessInstanceFilterStrictContract(
    @JsonProperty("startDate") @Nullable GeneratedDateTimeFilterPropertyStrictContract startDate,
    @JsonProperty("endDate") @Nullable GeneratedDateTimeFilterPropertyStrictContract endDate,
    @JsonProperty("state")
        @Nullable GeneratedProcessInstanceStateFilterPropertyStrictContract state,
    @JsonProperty("hasIncident") @Nullable Boolean hasIncident,
    @JsonProperty("tenantId") @Nullable GeneratedStringFilterPropertyStrictContract tenantId,
    @JsonProperty("variables")
        java.util.@Nullable List<GeneratedVariableValueFilterPropertyStrictContract> variables,
    @JsonProperty("processInstanceKey")
        @Nullable GeneratedProcessInstanceKeyFilterPropertyStrictContract processInstanceKey,
    @JsonProperty("parentProcessInstanceKey")
        @Nullable GeneratedProcessInstanceKeyFilterPropertyStrictContract parentProcessInstanceKey,
    @JsonProperty("parentElementInstanceKey")
        @Nullable GeneratedElementInstanceKeyFilterPropertyStrictContract parentElementInstanceKey,
    @JsonProperty("batchOperationId")
        @Nullable GeneratedStringFilterPropertyStrictContract batchOperationId,
    @JsonProperty("errorMessage")
        @Nullable GeneratedStringFilterPropertyStrictContract errorMessage,
    @JsonProperty("hasRetriesLeft") @Nullable Boolean hasRetriesLeft,
    @JsonProperty("elementInstanceState")
        @Nullable GeneratedElementInstanceStateFilterPropertyStrictContract elementInstanceState,
    @JsonProperty("elementId") @Nullable GeneratedStringFilterPropertyStrictContract elementId,
    @JsonProperty("hasElementInstanceIncident") @Nullable Boolean hasElementInstanceIncident,
    @JsonProperty("incidentErrorHashCode")
        @Nullable GeneratedIntegerFilterPropertyStrictContract incidentErrorHashCode,
    @JsonProperty("tags") java.util.@Nullable Set<String> tags,
    @JsonProperty("businessId") @Nullable GeneratedStringFilterPropertyStrictContract businessId,
    @JsonProperty("processDefinitionId")
        @Nullable GeneratedStringFilterPropertyStrictContract processDefinitionId,
    @JsonProperty("processDefinitionName")
        @Nullable GeneratedStringFilterPropertyStrictContract processDefinitionName,
    @JsonProperty("processDefinitionVersion")
        @Nullable GeneratedIntegerFilterPropertyStrictContract processDefinitionVersion,
    @JsonProperty("processDefinitionVersionTag")
        @Nullable GeneratedStringFilterPropertyStrictContract processDefinitionVersionTag,
    @JsonProperty("processDefinitionKey")
        @Nullable GeneratedProcessDefinitionKeyFilterPropertyStrictContract processDefinitionKey,
    @JsonProperty("$or")
        java.util.@Nullable List<GeneratedProcessInstanceFilterFieldsStrictContract> $or) {

  public GeneratedProcessInstanceFilterStrictContract {
    if (tags != null)
      if (tags.size() > 10) throw new IllegalArgumentException("tags must have at most 10 items");
  }

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

  public static java.util.List<GeneratedProcessInstanceFilterFieldsStrictContract> coerce$or(
      final Object value) {
    if (value == null) {
      return null;
    }
    if (!(value instanceof java.util.List<?> listValue)) {
      throw new IllegalArgumentException(
          "$or must be a List of GeneratedProcessInstanceFilterFieldsStrictContract, but was "
              + value.getClass().getName());
    }

    final var result =
        new ArrayList<GeneratedProcessInstanceFilterFieldsStrictContract>(listValue.size());
    for (final var item : listValue) {
      if (item == null) {
        result.add(null);
      } else if (item instanceof GeneratedProcessInstanceFilterFieldsStrictContract strictItem) {
        result.add(strictItem);

      } else {
        throw new IllegalArgumentException(
            "$or must contain only GeneratedProcessInstanceFilterFieldsStrictContract items, but got "
                + item.getClass().getName());
      }
    }
    return java.util.List.copyOf(result);
  }

  public static OptionalStep builder() {
    return new Builder();
  }

  public static final class Builder implements OptionalStep {
    private GeneratedDateTimeFilterPropertyStrictContract startDate;
    private GeneratedDateTimeFilterPropertyStrictContract endDate;
    private GeneratedProcessInstanceStateFilterPropertyStrictContract state;
    private Boolean hasIncident;
    private GeneratedStringFilterPropertyStrictContract tenantId;
    private Object variables;
    private GeneratedProcessInstanceKeyFilterPropertyStrictContract processInstanceKey;
    private GeneratedProcessInstanceKeyFilterPropertyStrictContract parentProcessInstanceKey;
    private GeneratedElementInstanceKeyFilterPropertyStrictContract parentElementInstanceKey;
    private GeneratedStringFilterPropertyStrictContract batchOperationId;
    private GeneratedStringFilterPropertyStrictContract errorMessage;
    private Boolean hasRetriesLeft;
    private GeneratedElementInstanceStateFilterPropertyStrictContract elementInstanceState;
    private GeneratedStringFilterPropertyStrictContract elementId;
    private Boolean hasElementInstanceIncident;
    private GeneratedIntegerFilterPropertyStrictContract incidentErrorHashCode;
    private java.util.Set<String> tags;
    private GeneratedStringFilterPropertyStrictContract businessId;
    private GeneratedStringFilterPropertyStrictContract processDefinitionId;
    private GeneratedStringFilterPropertyStrictContract processDefinitionName;
    private GeneratedIntegerFilterPropertyStrictContract processDefinitionVersion;
    private GeneratedStringFilterPropertyStrictContract processDefinitionVersionTag;
    private GeneratedProcessDefinitionKeyFilterPropertyStrictContract processDefinitionKey;
    private Object $or;

    private Builder() {}

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
    public OptionalStep state(
        final @Nullable GeneratedProcessInstanceStateFilterPropertyStrictContract state) {
      this.state = state;
      return this;
    }

    @Override
    public OptionalStep state(
        final @Nullable GeneratedProcessInstanceStateFilterPropertyStrictContract state,
        final ContractPolicy.FieldPolicy<GeneratedProcessInstanceStateFilterPropertyStrictContract>
            policy) {
      this.state = policy.apply(state, Fields.STATE, null);
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
    public OptionalStep variables(
        final java.util.@Nullable List<GeneratedVariableValueFilterPropertyStrictContract>
            variables) {
      this.variables = variables;
      return this;
    }

    @Override
    public OptionalStep variables(final @Nullable Object variables) {
      this.variables = variables;
      return this;
    }

    public Builder variables(
        final java.util.@Nullable List<GeneratedVariableValueFilterPropertyStrictContract>
            variables,
        final ContractPolicy.FieldPolicy<
                java.util.List<GeneratedVariableValueFilterPropertyStrictContract>>
            policy) {
      this.variables = policy.apply(variables, Fields.VARIABLES, null);
      return this;
    }

    @Override
    public OptionalStep variables(
        final @Nullable Object variables, final ContractPolicy.FieldPolicy<Object> policy) {
      this.variables = policy.apply(variables, Fields.VARIABLES, null);
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
    public OptionalStep parentProcessInstanceKey(
        final @Nullable GeneratedProcessInstanceKeyFilterPropertyStrictContract
            parentProcessInstanceKey) {
      this.parentProcessInstanceKey = parentProcessInstanceKey;
      return this;
    }

    @Override
    public OptionalStep parentProcessInstanceKey(
        final @Nullable GeneratedProcessInstanceKeyFilterPropertyStrictContract
            parentProcessInstanceKey,
        final ContractPolicy.FieldPolicy<GeneratedProcessInstanceKeyFilterPropertyStrictContract>
            policy) {
      this.parentProcessInstanceKey =
          policy.apply(parentProcessInstanceKey, Fields.PARENT_PROCESS_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep parentElementInstanceKey(
        final @Nullable GeneratedElementInstanceKeyFilterPropertyStrictContract
            parentElementInstanceKey) {
      this.parentElementInstanceKey = parentElementInstanceKey;
      return this;
    }

    @Override
    public OptionalStep parentElementInstanceKey(
        final @Nullable GeneratedElementInstanceKeyFilterPropertyStrictContract
            parentElementInstanceKey,
        final ContractPolicy.FieldPolicy<GeneratedElementInstanceKeyFilterPropertyStrictContract>
            policy) {
      this.parentElementInstanceKey =
          policy.apply(parentElementInstanceKey, Fields.PARENT_ELEMENT_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep batchOperationId(
        final @Nullable GeneratedStringFilterPropertyStrictContract batchOperationId) {
      this.batchOperationId = batchOperationId;
      return this;
    }

    @Override
    public OptionalStep batchOperationId(
        final @Nullable GeneratedStringFilterPropertyStrictContract batchOperationId,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy) {
      this.batchOperationId = policy.apply(batchOperationId, Fields.BATCH_OPERATION_ID, null);
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
    public OptionalStep hasRetriesLeft(final @Nullable Boolean hasRetriesLeft) {
      this.hasRetriesLeft = hasRetriesLeft;
      return this;
    }

    @Override
    public OptionalStep hasRetriesLeft(
        final @Nullable Boolean hasRetriesLeft, final ContractPolicy.FieldPolicy<Boolean> policy) {
      this.hasRetriesLeft = policy.apply(hasRetriesLeft, Fields.HAS_RETRIES_LEFT, null);
      return this;
    }

    @Override
    public OptionalStep elementInstanceState(
        final @Nullable GeneratedElementInstanceStateFilterPropertyStrictContract
            elementInstanceState) {
      this.elementInstanceState = elementInstanceState;
      return this;
    }

    @Override
    public OptionalStep elementInstanceState(
        final @Nullable GeneratedElementInstanceStateFilterPropertyStrictContract
            elementInstanceState,
        final ContractPolicy.FieldPolicy<GeneratedElementInstanceStateFilterPropertyStrictContract>
            policy) {
      this.elementInstanceState =
          policy.apply(elementInstanceState, Fields.ELEMENT_INSTANCE_STATE, null);
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
    public OptionalStep hasElementInstanceIncident(
        final @Nullable Boolean hasElementInstanceIncident) {
      this.hasElementInstanceIncident = hasElementInstanceIncident;
      return this;
    }

    @Override
    public OptionalStep hasElementInstanceIncident(
        final @Nullable Boolean hasElementInstanceIncident,
        final ContractPolicy.FieldPolicy<Boolean> policy) {
      this.hasElementInstanceIncident =
          policy.apply(hasElementInstanceIncident, Fields.HAS_ELEMENT_INSTANCE_INCIDENT, null);
      return this;
    }

    @Override
    public OptionalStep incidentErrorHashCode(
        final @Nullable GeneratedIntegerFilterPropertyStrictContract incidentErrorHashCode) {
      this.incidentErrorHashCode = incidentErrorHashCode;
      return this;
    }

    @Override
    public OptionalStep incidentErrorHashCode(
        final @Nullable GeneratedIntegerFilterPropertyStrictContract incidentErrorHashCode,
        final ContractPolicy.FieldPolicy<GeneratedIntegerFilterPropertyStrictContract> policy) {
      this.incidentErrorHashCode =
          policy.apply(incidentErrorHashCode, Fields.INCIDENT_ERROR_HASH_CODE, null);
      return this;
    }

    @Override
    public OptionalStep tags(final java.util.@Nullable Set<String> tags) {
      this.tags = tags;
      return this;
    }

    @Override
    public OptionalStep tags(
        final java.util.@Nullable Set<String> tags,
        final ContractPolicy.FieldPolicy<java.util.Set<String>> policy) {
      this.tags = policy.apply(tags, Fields.TAGS, null);
      return this;
    }

    @Override
    public OptionalStep businessId(
        final @Nullable GeneratedStringFilterPropertyStrictContract businessId) {
      this.businessId = businessId;
      return this;
    }

    @Override
    public OptionalStep businessId(
        final @Nullable GeneratedStringFilterPropertyStrictContract businessId,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy) {
      this.businessId = policy.apply(businessId, Fields.BUSINESS_ID, null);
      return this;
    }

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
    public OptionalStep processDefinitionName(
        final @Nullable GeneratedStringFilterPropertyStrictContract processDefinitionName) {
      this.processDefinitionName = processDefinitionName;
      return this;
    }

    @Override
    public OptionalStep processDefinitionName(
        final @Nullable GeneratedStringFilterPropertyStrictContract processDefinitionName,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy) {
      this.processDefinitionName =
          policy.apply(processDefinitionName, Fields.PROCESS_DEFINITION_NAME, null);
      return this;
    }

    @Override
    public OptionalStep processDefinitionVersion(
        final @Nullable GeneratedIntegerFilterPropertyStrictContract processDefinitionVersion) {
      this.processDefinitionVersion = processDefinitionVersion;
      return this;
    }

    @Override
    public OptionalStep processDefinitionVersion(
        final @Nullable GeneratedIntegerFilterPropertyStrictContract processDefinitionVersion,
        final ContractPolicy.FieldPolicy<GeneratedIntegerFilterPropertyStrictContract> policy) {
      this.processDefinitionVersion =
          policy.apply(processDefinitionVersion, Fields.PROCESS_DEFINITION_VERSION, null);
      return this;
    }

    @Override
    public OptionalStep processDefinitionVersionTag(
        final @Nullable GeneratedStringFilterPropertyStrictContract processDefinitionVersionTag) {
      this.processDefinitionVersionTag = processDefinitionVersionTag;
      return this;
    }

    @Override
    public OptionalStep processDefinitionVersionTag(
        final @Nullable GeneratedStringFilterPropertyStrictContract processDefinitionVersionTag,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy) {
      this.processDefinitionVersionTag =
          policy.apply(processDefinitionVersionTag, Fields.PROCESS_DEFINITION_VERSION_TAG, null);
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
    public OptionalStep $or(
        final java.util.@Nullable List<GeneratedProcessInstanceFilterFieldsStrictContract> $or) {
      this.$or = $or;
      return this;
    }

    @Override
    public OptionalStep $or(final @Nullable Object $or) {
      this.$or = $or;
      return this;
    }

    public Builder $or(
        final java.util.@Nullable List<GeneratedProcessInstanceFilterFieldsStrictContract> $or,
        final ContractPolicy.FieldPolicy<
                java.util.List<GeneratedProcessInstanceFilterFieldsStrictContract>>
            policy) {
      this.$or = policy.apply($or, Fields.$OR, null);
      return this;
    }

    @Override
    public OptionalStep $or(
        final @Nullable Object $or, final ContractPolicy.FieldPolicy<Object> policy) {
      this.$or = policy.apply($or, Fields.$OR, null);
      return this;
    }

    @Override
    public GeneratedProcessInstanceFilterStrictContract build() {
      return new GeneratedProcessInstanceFilterStrictContract(
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
          this.processDefinitionKey,
          coerce$or(this.$or));
    }
  }

  public interface OptionalStep {
    OptionalStep startDate(final @Nullable GeneratedDateTimeFilterPropertyStrictContract startDate);

    OptionalStep startDate(
        final @Nullable GeneratedDateTimeFilterPropertyStrictContract startDate,
        final ContractPolicy.FieldPolicy<GeneratedDateTimeFilterPropertyStrictContract> policy);

    OptionalStep endDate(final @Nullable GeneratedDateTimeFilterPropertyStrictContract endDate);

    OptionalStep endDate(
        final @Nullable GeneratedDateTimeFilterPropertyStrictContract endDate,
        final ContractPolicy.FieldPolicy<GeneratedDateTimeFilterPropertyStrictContract> policy);

    OptionalStep state(
        final @Nullable GeneratedProcessInstanceStateFilterPropertyStrictContract state);

    OptionalStep state(
        final @Nullable GeneratedProcessInstanceStateFilterPropertyStrictContract state,
        final ContractPolicy.FieldPolicy<GeneratedProcessInstanceStateFilterPropertyStrictContract>
            policy);

    OptionalStep hasIncident(final @Nullable Boolean hasIncident);

    OptionalStep hasIncident(
        final @Nullable Boolean hasIncident, final ContractPolicy.FieldPolicy<Boolean> policy);

    OptionalStep tenantId(final @Nullable GeneratedStringFilterPropertyStrictContract tenantId);

    OptionalStep tenantId(
        final @Nullable GeneratedStringFilterPropertyStrictContract tenantId,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy);

    OptionalStep variables(
        final java.util.@Nullable List<GeneratedVariableValueFilterPropertyStrictContract>
            variables);

    OptionalStep variables(final @Nullable Object variables);

    OptionalStep variables(
        final java.util.@Nullable List<GeneratedVariableValueFilterPropertyStrictContract>
            variables,
        final ContractPolicy.FieldPolicy<
                java.util.List<GeneratedVariableValueFilterPropertyStrictContract>>
            policy);

    OptionalStep variables(
        final @Nullable Object variables, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep processInstanceKey(
        final @Nullable GeneratedProcessInstanceKeyFilterPropertyStrictContract processInstanceKey);

    OptionalStep processInstanceKey(
        final @Nullable GeneratedProcessInstanceKeyFilterPropertyStrictContract processInstanceKey,
        final ContractPolicy.FieldPolicy<GeneratedProcessInstanceKeyFilterPropertyStrictContract>
            policy);

    OptionalStep parentProcessInstanceKey(
        final @Nullable GeneratedProcessInstanceKeyFilterPropertyStrictContract
            parentProcessInstanceKey);

    OptionalStep parentProcessInstanceKey(
        final @Nullable GeneratedProcessInstanceKeyFilterPropertyStrictContract
            parentProcessInstanceKey,
        final ContractPolicy.FieldPolicy<GeneratedProcessInstanceKeyFilterPropertyStrictContract>
            policy);

    OptionalStep parentElementInstanceKey(
        final @Nullable GeneratedElementInstanceKeyFilterPropertyStrictContract
            parentElementInstanceKey);

    OptionalStep parentElementInstanceKey(
        final @Nullable GeneratedElementInstanceKeyFilterPropertyStrictContract
            parentElementInstanceKey,
        final ContractPolicy.FieldPolicy<GeneratedElementInstanceKeyFilterPropertyStrictContract>
            policy);

    OptionalStep batchOperationId(
        final @Nullable GeneratedStringFilterPropertyStrictContract batchOperationId);

    OptionalStep batchOperationId(
        final @Nullable GeneratedStringFilterPropertyStrictContract batchOperationId,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy);

    OptionalStep errorMessage(
        final @Nullable GeneratedStringFilterPropertyStrictContract errorMessage);

    OptionalStep errorMessage(
        final @Nullable GeneratedStringFilterPropertyStrictContract errorMessage,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy);

    OptionalStep hasRetriesLeft(final @Nullable Boolean hasRetriesLeft);

    OptionalStep hasRetriesLeft(
        final @Nullable Boolean hasRetriesLeft, final ContractPolicy.FieldPolicy<Boolean> policy);

    OptionalStep elementInstanceState(
        final @Nullable GeneratedElementInstanceStateFilterPropertyStrictContract
            elementInstanceState);

    OptionalStep elementInstanceState(
        final @Nullable GeneratedElementInstanceStateFilterPropertyStrictContract
            elementInstanceState,
        final ContractPolicy.FieldPolicy<GeneratedElementInstanceStateFilterPropertyStrictContract>
            policy);

    OptionalStep elementId(final @Nullable GeneratedStringFilterPropertyStrictContract elementId);

    OptionalStep elementId(
        final @Nullable GeneratedStringFilterPropertyStrictContract elementId,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy);

    OptionalStep hasElementInstanceIncident(final @Nullable Boolean hasElementInstanceIncident);

    OptionalStep hasElementInstanceIncident(
        final @Nullable Boolean hasElementInstanceIncident,
        final ContractPolicy.FieldPolicy<Boolean> policy);

    OptionalStep incidentErrorHashCode(
        final @Nullable GeneratedIntegerFilterPropertyStrictContract incidentErrorHashCode);

    OptionalStep incidentErrorHashCode(
        final @Nullable GeneratedIntegerFilterPropertyStrictContract incidentErrorHashCode,
        final ContractPolicy.FieldPolicy<GeneratedIntegerFilterPropertyStrictContract> policy);

    OptionalStep tags(final java.util.@Nullable Set<String> tags);

    OptionalStep tags(
        final java.util.@Nullable Set<String> tags,
        final ContractPolicy.FieldPolicy<java.util.Set<String>> policy);

    OptionalStep businessId(final @Nullable GeneratedStringFilterPropertyStrictContract businessId);

    OptionalStep businessId(
        final @Nullable GeneratedStringFilterPropertyStrictContract businessId,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy);

    OptionalStep processDefinitionId(
        final @Nullable GeneratedStringFilterPropertyStrictContract processDefinitionId);

    OptionalStep processDefinitionId(
        final @Nullable GeneratedStringFilterPropertyStrictContract processDefinitionId,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy);

    OptionalStep processDefinitionName(
        final @Nullable GeneratedStringFilterPropertyStrictContract processDefinitionName);

    OptionalStep processDefinitionName(
        final @Nullable GeneratedStringFilterPropertyStrictContract processDefinitionName,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy);

    OptionalStep processDefinitionVersion(
        final @Nullable GeneratedIntegerFilterPropertyStrictContract processDefinitionVersion);

    OptionalStep processDefinitionVersion(
        final @Nullable GeneratedIntegerFilterPropertyStrictContract processDefinitionVersion,
        final ContractPolicy.FieldPolicy<GeneratedIntegerFilterPropertyStrictContract> policy);

    OptionalStep processDefinitionVersionTag(
        final @Nullable GeneratedStringFilterPropertyStrictContract processDefinitionVersionTag);

    OptionalStep processDefinitionVersionTag(
        final @Nullable GeneratedStringFilterPropertyStrictContract processDefinitionVersionTag,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy);

    OptionalStep processDefinitionKey(
        final @Nullable GeneratedProcessDefinitionKeyFilterPropertyStrictContract
            processDefinitionKey);

    OptionalStep processDefinitionKey(
        final @Nullable GeneratedProcessDefinitionKeyFilterPropertyStrictContract
            processDefinitionKey,
        final ContractPolicy.FieldPolicy<GeneratedProcessDefinitionKeyFilterPropertyStrictContract>
            policy);

    OptionalStep $or(
        final java.util.@Nullable List<GeneratedProcessInstanceFilterFieldsStrictContract> $or);

    OptionalStep $or(final @Nullable Object $or);

    OptionalStep $or(
        final java.util.@Nullable List<GeneratedProcessInstanceFilterFieldsStrictContract> $or,
        final ContractPolicy.FieldPolicy<
                java.util.List<GeneratedProcessInstanceFilterFieldsStrictContract>>
            policy);

    OptionalStep $or(final @Nullable Object $or, final ContractPolicy.FieldPolicy<Object> policy);

    GeneratedProcessInstanceFilterStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef START_DATE =
        ContractPolicy.field("ProcessInstanceFilter", "startDate");
    public static final ContractPolicy.FieldRef END_DATE =
        ContractPolicy.field("ProcessInstanceFilter", "endDate");
    public static final ContractPolicy.FieldRef STATE =
        ContractPolicy.field("ProcessInstanceFilter", "state");
    public static final ContractPolicy.FieldRef HAS_INCIDENT =
        ContractPolicy.field("ProcessInstanceFilter", "hasIncident");
    public static final ContractPolicy.FieldRef TENANT_ID =
        ContractPolicy.field("ProcessInstanceFilter", "tenantId");
    public static final ContractPolicy.FieldRef VARIABLES =
        ContractPolicy.field("ProcessInstanceFilter", "variables");
    public static final ContractPolicy.FieldRef PROCESS_INSTANCE_KEY =
        ContractPolicy.field("ProcessInstanceFilter", "processInstanceKey");
    public static final ContractPolicy.FieldRef PARENT_PROCESS_INSTANCE_KEY =
        ContractPolicy.field("ProcessInstanceFilter", "parentProcessInstanceKey");
    public static final ContractPolicy.FieldRef PARENT_ELEMENT_INSTANCE_KEY =
        ContractPolicy.field("ProcessInstanceFilter", "parentElementInstanceKey");
    public static final ContractPolicy.FieldRef BATCH_OPERATION_ID =
        ContractPolicy.field("ProcessInstanceFilter", "batchOperationId");
    public static final ContractPolicy.FieldRef ERROR_MESSAGE =
        ContractPolicy.field("ProcessInstanceFilter", "errorMessage");
    public static final ContractPolicy.FieldRef HAS_RETRIES_LEFT =
        ContractPolicy.field("ProcessInstanceFilter", "hasRetriesLeft");
    public static final ContractPolicy.FieldRef ELEMENT_INSTANCE_STATE =
        ContractPolicy.field("ProcessInstanceFilter", "elementInstanceState");
    public static final ContractPolicy.FieldRef ELEMENT_ID =
        ContractPolicy.field("ProcessInstanceFilter", "elementId");
    public static final ContractPolicy.FieldRef HAS_ELEMENT_INSTANCE_INCIDENT =
        ContractPolicy.field("ProcessInstanceFilter", "hasElementInstanceIncident");
    public static final ContractPolicy.FieldRef INCIDENT_ERROR_HASH_CODE =
        ContractPolicy.field("ProcessInstanceFilter", "incidentErrorHashCode");
    public static final ContractPolicy.FieldRef TAGS =
        ContractPolicy.field("ProcessInstanceFilter", "tags");
    public static final ContractPolicy.FieldRef BUSINESS_ID =
        ContractPolicy.field("ProcessInstanceFilter", "businessId");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_ID =
        ContractPolicy.field("ProcessInstanceFilter", "processDefinitionId");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_NAME =
        ContractPolicy.field("ProcessInstanceFilter", "processDefinitionName");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_VERSION =
        ContractPolicy.field("ProcessInstanceFilter", "processDefinitionVersion");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_VERSION_TAG =
        ContractPolicy.field("ProcessInstanceFilter", "processDefinitionVersionTag");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_KEY =
        ContractPolicy.field("ProcessInstanceFilter", "processDefinitionKey");
    public static final ContractPolicy.FieldRef $OR =
        ContractPolicy.field("ProcessInstanceFilter", "$or");

    private Fields() {}
  }
}
