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
public record GeneratedJobFilterStrictContract(
    @JsonProperty("deadline") @Nullable GeneratedDateTimeFilterPropertyStrictContract deadline,
    @JsonProperty("deniedReason")
        @Nullable GeneratedStringFilterPropertyStrictContract deniedReason,
    @JsonProperty("elementId") @Nullable GeneratedStringFilterPropertyStrictContract elementId,
    @JsonProperty("elementInstanceKey")
        @Nullable GeneratedElementInstanceKeyFilterPropertyStrictContract elementInstanceKey,
    @JsonProperty("endTime") @Nullable GeneratedDateTimeFilterPropertyStrictContract endTime,
    @JsonProperty("errorCode") @Nullable GeneratedStringFilterPropertyStrictContract errorCode,
    @JsonProperty("errorMessage")
        @Nullable GeneratedStringFilterPropertyStrictContract errorMessage,
    @JsonProperty("hasFailedWithRetriesLeft") @Nullable Boolean hasFailedWithRetriesLeft,
    @JsonProperty("isDenied") @Nullable Boolean isDenied,
    @JsonProperty("jobKey") @Nullable GeneratedJobKeyFilterPropertyStrictContract jobKey,
    @JsonProperty("kind") @Nullable GeneratedJobKindFilterPropertyStrictContract kind,
    @JsonProperty("listenerEventType")
        @Nullable GeneratedJobListenerEventTypeFilterPropertyStrictContract listenerEventType,
    @JsonProperty("processDefinitionId")
        @Nullable GeneratedStringFilterPropertyStrictContract processDefinitionId,
    @JsonProperty("processDefinitionKey")
        @Nullable GeneratedProcessDefinitionKeyFilterPropertyStrictContract processDefinitionKey,
    @JsonProperty("processInstanceKey")
        @Nullable GeneratedProcessInstanceKeyFilterPropertyStrictContract processInstanceKey,
    @JsonProperty("retries") @Nullable GeneratedIntegerFilterPropertyStrictContract retries,
    @JsonProperty("state") @Nullable GeneratedJobStateFilterPropertyStrictContract state,
    @JsonProperty("tenantId") @Nullable GeneratedStringFilterPropertyStrictContract tenantId,
    @JsonProperty("type") @Nullable GeneratedStringFilterPropertyStrictContract type,
    @JsonProperty("worker") @Nullable GeneratedStringFilterPropertyStrictContract worker,
    @JsonProperty("creationTime")
        @Nullable GeneratedDateTimeFilterPropertyStrictContract creationTime,
    @JsonProperty("lastUpdateTime")
        @Nullable GeneratedDateTimeFilterPropertyStrictContract lastUpdateTime) {

  public static OptionalStep builder() {
    return new Builder();
  }

  public static final class Builder implements OptionalStep {
    private GeneratedDateTimeFilterPropertyStrictContract deadline;
    private GeneratedStringFilterPropertyStrictContract deniedReason;
    private GeneratedStringFilterPropertyStrictContract elementId;
    private GeneratedElementInstanceKeyFilterPropertyStrictContract elementInstanceKey;
    private GeneratedDateTimeFilterPropertyStrictContract endTime;
    private GeneratedStringFilterPropertyStrictContract errorCode;
    private GeneratedStringFilterPropertyStrictContract errorMessage;
    private Boolean hasFailedWithRetriesLeft;
    private Boolean isDenied;
    private GeneratedJobKeyFilterPropertyStrictContract jobKey;
    private GeneratedJobKindFilterPropertyStrictContract kind;
    private GeneratedJobListenerEventTypeFilterPropertyStrictContract listenerEventType;
    private GeneratedStringFilterPropertyStrictContract processDefinitionId;
    private GeneratedProcessDefinitionKeyFilterPropertyStrictContract processDefinitionKey;
    private GeneratedProcessInstanceKeyFilterPropertyStrictContract processInstanceKey;
    private GeneratedIntegerFilterPropertyStrictContract retries;
    private GeneratedJobStateFilterPropertyStrictContract state;
    private GeneratedStringFilterPropertyStrictContract tenantId;
    private GeneratedStringFilterPropertyStrictContract type;
    private GeneratedStringFilterPropertyStrictContract worker;
    private GeneratedDateTimeFilterPropertyStrictContract creationTime;
    private GeneratedDateTimeFilterPropertyStrictContract lastUpdateTime;

    private Builder() {}

    @Override
    public OptionalStep deadline(
        final @Nullable GeneratedDateTimeFilterPropertyStrictContract deadline) {
      this.deadline = deadline;
      return this;
    }

    @Override
    public OptionalStep deadline(
        final @Nullable GeneratedDateTimeFilterPropertyStrictContract deadline,
        final ContractPolicy.FieldPolicy<GeneratedDateTimeFilterPropertyStrictContract> policy) {
      this.deadline = policy.apply(deadline, Fields.DEADLINE, null);
      return this;
    }

    @Override
    public OptionalStep deniedReason(
        final @Nullable GeneratedStringFilterPropertyStrictContract deniedReason) {
      this.deniedReason = deniedReason;
      return this;
    }

    @Override
    public OptionalStep deniedReason(
        final @Nullable GeneratedStringFilterPropertyStrictContract deniedReason,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy) {
      this.deniedReason = policy.apply(deniedReason, Fields.DENIED_REASON, null);
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
    public OptionalStep endTime(
        final @Nullable GeneratedDateTimeFilterPropertyStrictContract endTime) {
      this.endTime = endTime;
      return this;
    }

    @Override
    public OptionalStep endTime(
        final @Nullable GeneratedDateTimeFilterPropertyStrictContract endTime,
        final ContractPolicy.FieldPolicy<GeneratedDateTimeFilterPropertyStrictContract> policy) {
      this.endTime = policy.apply(endTime, Fields.END_TIME, null);
      return this;
    }

    @Override
    public OptionalStep errorCode(
        final @Nullable GeneratedStringFilterPropertyStrictContract errorCode) {
      this.errorCode = errorCode;
      return this;
    }

    @Override
    public OptionalStep errorCode(
        final @Nullable GeneratedStringFilterPropertyStrictContract errorCode,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy) {
      this.errorCode = policy.apply(errorCode, Fields.ERROR_CODE, null);
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
    public OptionalStep hasFailedWithRetriesLeft(final @Nullable Boolean hasFailedWithRetriesLeft) {
      this.hasFailedWithRetriesLeft = hasFailedWithRetriesLeft;
      return this;
    }

    @Override
    public OptionalStep hasFailedWithRetriesLeft(
        final @Nullable Boolean hasFailedWithRetriesLeft,
        final ContractPolicy.FieldPolicy<Boolean> policy) {
      this.hasFailedWithRetriesLeft =
          policy.apply(hasFailedWithRetriesLeft, Fields.HAS_FAILED_WITH_RETRIES_LEFT, null);
      return this;
    }

    @Override
    public OptionalStep isDenied(final @Nullable Boolean isDenied) {
      this.isDenied = isDenied;
      return this;
    }

    @Override
    public OptionalStep isDenied(
        final @Nullable Boolean isDenied, final ContractPolicy.FieldPolicy<Boolean> policy) {
      this.isDenied = policy.apply(isDenied, Fields.IS_DENIED, null);
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
    public OptionalStep kind(final @Nullable GeneratedJobKindFilterPropertyStrictContract kind) {
      this.kind = kind;
      return this;
    }

    @Override
    public OptionalStep kind(
        final @Nullable GeneratedJobKindFilterPropertyStrictContract kind,
        final ContractPolicy.FieldPolicy<GeneratedJobKindFilterPropertyStrictContract> policy) {
      this.kind = policy.apply(kind, Fields.KIND, null);
      return this;
    }

    @Override
    public OptionalStep listenerEventType(
        final @Nullable GeneratedJobListenerEventTypeFilterPropertyStrictContract
            listenerEventType) {
      this.listenerEventType = listenerEventType;
      return this;
    }

    @Override
    public OptionalStep listenerEventType(
        final @Nullable GeneratedJobListenerEventTypeFilterPropertyStrictContract listenerEventType,
        final ContractPolicy.FieldPolicy<GeneratedJobListenerEventTypeFilterPropertyStrictContract>
            policy) {
      this.listenerEventType = policy.apply(listenerEventType, Fields.LISTENER_EVENT_TYPE, null);
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
    public OptionalStep retries(
        final @Nullable GeneratedIntegerFilterPropertyStrictContract retries) {
      this.retries = retries;
      return this;
    }

    @Override
    public OptionalStep retries(
        final @Nullable GeneratedIntegerFilterPropertyStrictContract retries,
        final ContractPolicy.FieldPolicy<GeneratedIntegerFilterPropertyStrictContract> policy) {
      this.retries = policy.apply(retries, Fields.RETRIES, null);
      return this;
    }

    @Override
    public OptionalStep state(final @Nullable GeneratedJobStateFilterPropertyStrictContract state) {
      this.state = state;
      return this;
    }

    @Override
    public OptionalStep state(
        final @Nullable GeneratedJobStateFilterPropertyStrictContract state,
        final ContractPolicy.FieldPolicy<GeneratedJobStateFilterPropertyStrictContract> policy) {
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
    public OptionalStep type(final @Nullable GeneratedStringFilterPropertyStrictContract type) {
      this.type = type;
      return this;
    }

    @Override
    public OptionalStep type(
        final @Nullable GeneratedStringFilterPropertyStrictContract type,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy) {
      this.type = policy.apply(type, Fields.TYPE, null);
      return this;
    }

    @Override
    public OptionalStep worker(final @Nullable GeneratedStringFilterPropertyStrictContract worker) {
      this.worker = worker;
      return this;
    }

    @Override
    public OptionalStep worker(
        final @Nullable GeneratedStringFilterPropertyStrictContract worker,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy) {
      this.worker = policy.apply(worker, Fields.WORKER, null);
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
    public OptionalStep lastUpdateTime(
        final @Nullable GeneratedDateTimeFilterPropertyStrictContract lastUpdateTime) {
      this.lastUpdateTime = lastUpdateTime;
      return this;
    }

    @Override
    public OptionalStep lastUpdateTime(
        final @Nullable GeneratedDateTimeFilterPropertyStrictContract lastUpdateTime,
        final ContractPolicy.FieldPolicy<GeneratedDateTimeFilterPropertyStrictContract> policy) {
      this.lastUpdateTime = policy.apply(lastUpdateTime, Fields.LAST_UPDATE_TIME, null);
      return this;
    }

    @Override
    public GeneratedJobFilterStrictContract build() {
      return new GeneratedJobFilterStrictContract(
          this.deadline,
          this.deniedReason,
          this.elementId,
          this.elementInstanceKey,
          this.endTime,
          this.errorCode,
          this.errorMessage,
          this.hasFailedWithRetriesLeft,
          this.isDenied,
          this.jobKey,
          this.kind,
          this.listenerEventType,
          this.processDefinitionId,
          this.processDefinitionKey,
          this.processInstanceKey,
          this.retries,
          this.state,
          this.tenantId,
          this.type,
          this.worker,
          this.creationTime,
          this.lastUpdateTime);
    }
  }

  public interface OptionalStep {
    OptionalStep deadline(final @Nullable GeneratedDateTimeFilterPropertyStrictContract deadline);

    OptionalStep deadline(
        final @Nullable GeneratedDateTimeFilterPropertyStrictContract deadline,
        final ContractPolicy.FieldPolicy<GeneratedDateTimeFilterPropertyStrictContract> policy);

    OptionalStep deniedReason(
        final @Nullable GeneratedStringFilterPropertyStrictContract deniedReason);

    OptionalStep deniedReason(
        final @Nullable GeneratedStringFilterPropertyStrictContract deniedReason,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy);

    OptionalStep elementId(final @Nullable GeneratedStringFilterPropertyStrictContract elementId);

    OptionalStep elementId(
        final @Nullable GeneratedStringFilterPropertyStrictContract elementId,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy);

    OptionalStep elementInstanceKey(
        final @Nullable GeneratedElementInstanceKeyFilterPropertyStrictContract elementInstanceKey);

    OptionalStep elementInstanceKey(
        final @Nullable GeneratedElementInstanceKeyFilterPropertyStrictContract elementInstanceKey,
        final ContractPolicy.FieldPolicy<GeneratedElementInstanceKeyFilterPropertyStrictContract>
            policy);

    OptionalStep endTime(final @Nullable GeneratedDateTimeFilterPropertyStrictContract endTime);

    OptionalStep endTime(
        final @Nullable GeneratedDateTimeFilterPropertyStrictContract endTime,
        final ContractPolicy.FieldPolicy<GeneratedDateTimeFilterPropertyStrictContract> policy);

    OptionalStep errorCode(final @Nullable GeneratedStringFilterPropertyStrictContract errorCode);

    OptionalStep errorCode(
        final @Nullable GeneratedStringFilterPropertyStrictContract errorCode,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy);

    OptionalStep errorMessage(
        final @Nullable GeneratedStringFilterPropertyStrictContract errorMessage);

    OptionalStep errorMessage(
        final @Nullable GeneratedStringFilterPropertyStrictContract errorMessage,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy);

    OptionalStep hasFailedWithRetriesLeft(final @Nullable Boolean hasFailedWithRetriesLeft);

    OptionalStep hasFailedWithRetriesLeft(
        final @Nullable Boolean hasFailedWithRetriesLeft,
        final ContractPolicy.FieldPolicy<Boolean> policy);

    OptionalStep isDenied(final @Nullable Boolean isDenied);

    OptionalStep isDenied(
        final @Nullable Boolean isDenied, final ContractPolicy.FieldPolicy<Boolean> policy);

    OptionalStep jobKey(final @Nullable GeneratedJobKeyFilterPropertyStrictContract jobKey);

    OptionalStep jobKey(
        final @Nullable GeneratedJobKeyFilterPropertyStrictContract jobKey,
        final ContractPolicy.FieldPolicy<GeneratedJobKeyFilterPropertyStrictContract> policy);

    OptionalStep kind(final @Nullable GeneratedJobKindFilterPropertyStrictContract kind);

    OptionalStep kind(
        final @Nullable GeneratedJobKindFilterPropertyStrictContract kind,
        final ContractPolicy.FieldPolicy<GeneratedJobKindFilterPropertyStrictContract> policy);

    OptionalStep listenerEventType(
        final @Nullable GeneratedJobListenerEventTypeFilterPropertyStrictContract
            listenerEventType);

    OptionalStep listenerEventType(
        final @Nullable GeneratedJobListenerEventTypeFilterPropertyStrictContract listenerEventType,
        final ContractPolicy.FieldPolicy<GeneratedJobListenerEventTypeFilterPropertyStrictContract>
            policy);

    OptionalStep processDefinitionId(
        final @Nullable GeneratedStringFilterPropertyStrictContract processDefinitionId);

    OptionalStep processDefinitionId(
        final @Nullable GeneratedStringFilterPropertyStrictContract processDefinitionId,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy);

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

    OptionalStep retries(final @Nullable GeneratedIntegerFilterPropertyStrictContract retries);

    OptionalStep retries(
        final @Nullable GeneratedIntegerFilterPropertyStrictContract retries,
        final ContractPolicy.FieldPolicy<GeneratedIntegerFilterPropertyStrictContract> policy);

    OptionalStep state(final @Nullable GeneratedJobStateFilterPropertyStrictContract state);

    OptionalStep state(
        final @Nullable GeneratedJobStateFilterPropertyStrictContract state,
        final ContractPolicy.FieldPolicy<GeneratedJobStateFilterPropertyStrictContract> policy);

    OptionalStep tenantId(final @Nullable GeneratedStringFilterPropertyStrictContract tenantId);

    OptionalStep tenantId(
        final @Nullable GeneratedStringFilterPropertyStrictContract tenantId,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy);

    OptionalStep type(final @Nullable GeneratedStringFilterPropertyStrictContract type);

    OptionalStep type(
        final @Nullable GeneratedStringFilterPropertyStrictContract type,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy);

    OptionalStep worker(final @Nullable GeneratedStringFilterPropertyStrictContract worker);

    OptionalStep worker(
        final @Nullable GeneratedStringFilterPropertyStrictContract worker,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy);

    OptionalStep creationTime(
        final @Nullable GeneratedDateTimeFilterPropertyStrictContract creationTime);

    OptionalStep creationTime(
        final @Nullable GeneratedDateTimeFilterPropertyStrictContract creationTime,
        final ContractPolicy.FieldPolicy<GeneratedDateTimeFilterPropertyStrictContract> policy);

    OptionalStep lastUpdateTime(
        final @Nullable GeneratedDateTimeFilterPropertyStrictContract lastUpdateTime);

    OptionalStep lastUpdateTime(
        final @Nullable GeneratedDateTimeFilterPropertyStrictContract lastUpdateTime,
        final ContractPolicy.FieldPolicy<GeneratedDateTimeFilterPropertyStrictContract> policy);

    GeneratedJobFilterStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef DEADLINE =
        ContractPolicy.field("JobFilter", "deadline");
    public static final ContractPolicy.FieldRef DENIED_REASON =
        ContractPolicy.field("JobFilter", "deniedReason");
    public static final ContractPolicy.FieldRef ELEMENT_ID =
        ContractPolicy.field("JobFilter", "elementId");
    public static final ContractPolicy.FieldRef ELEMENT_INSTANCE_KEY =
        ContractPolicy.field("JobFilter", "elementInstanceKey");
    public static final ContractPolicy.FieldRef END_TIME =
        ContractPolicy.field("JobFilter", "endTime");
    public static final ContractPolicy.FieldRef ERROR_CODE =
        ContractPolicy.field("JobFilter", "errorCode");
    public static final ContractPolicy.FieldRef ERROR_MESSAGE =
        ContractPolicy.field("JobFilter", "errorMessage");
    public static final ContractPolicy.FieldRef HAS_FAILED_WITH_RETRIES_LEFT =
        ContractPolicy.field("JobFilter", "hasFailedWithRetriesLeft");
    public static final ContractPolicy.FieldRef IS_DENIED =
        ContractPolicy.field("JobFilter", "isDenied");
    public static final ContractPolicy.FieldRef JOB_KEY =
        ContractPolicy.field("JobFilter", "jobKey");
    public static final ContractPolicy.FieldRef KIND = ContractPolicy.field("JobFilter", "kind");
    public static final ContractPolicy.FieldRef LISTENER_EVENT_TYPE =
        ContractPolicy.field("JobFilter", "listenerEventType");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_ID =
        ContractPolicy.field("JobFilter", "processDefinitionId");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_KEY =
        ContractPolicy.field("JobFilter", "processDefinitionKey");
    public static final ContractPolicy.FieldRef PROCESS_INSTANCE_KEY =
        ContractPolicy.field("JobFilter", "processInstanceKey");
    public static final ContractPolicy.FieldRef RETRIES =
        ContractPolicy.field("JobFilter", "retries");
    public static final ContractPolicy.FieldRef STATE = ContractPolicy.field("JobFilter", "state");
    public static final ContractPolicy.FieldRef TENANT_ID =
        ContractPolicy.field("JobFilter", "tenantId");
    public static final ContractPolicy.FieldRef TYPE = ContractPolicy.field("JobFilter", "type");
    public static final ContractPolicy.FieldRef WORKER =
        ContractPolicy.field("JobFilter", "worker");
    public static final ContractPolicy.FieldRef CREATION_TIME =
        ContractPolicy.field("JobFilter", "creationTime");
    public static final ContractPolicy.FieldRef LAST_UPDATE_TIME =
        ContractPolicy.field("JobFilter", "lastUpdateTime");

    private Fields() {}
  }
}
