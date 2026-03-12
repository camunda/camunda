/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import jakarta.annotation.Generated;
import org.springframework.lang.Nullable;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedJobFilterStrictContract(
    @Nullable Object deadline,
    @Nullable Object deniedReason,
    @Nullable Object elementId,
    @Nullable Object elementInstanceKey,
    @Nullable Object endTime,
    @Nullable Object errorCode,
    @Nullable Object errorMessage,
    @Nullable Boolean hasFailedWithRetriesLeft,
    @Nullable Boolean isDenied,
    @Nullable Object jobKey,
    @Nullable Object kind,
    @Nullable Object listenerEventType,
    @Nullable Object processDefinitionId,
    @Nullable Object processDefinitionKey,
    @Nullable Object processInstanceKey,
    @Nullable Object retries,
    @Nullable Object state,
    @Nullable Object tenantId,
    @Nullable Object type,
    @Nullable Object worker,
    @Nullable Object creationTime,
    @Nullable Object lastUpdateTime) {

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
    private Object deadline;
    private Object deniedReason;
    private Object elementId;
    private Object elementInstanceKey;
    private Object endTime;
    private Object errorCode;
    private Object errorMessage;
    private Boolean hasFailedWithRetriesLeft;
    private Boolean isDenied;
    private Object jobKey;
    private Object kind;
    private Object listenerEventType;
    private Object processDefinitionId;
    private Object processDefinitionKey;
    private Object processInstanceKey;
    private Object retries;
    private Object state;
    private Object tenantId;
    private Object type;
    private Object worker;
    private Object creationTime;
    private Object lastUpdateTime;

    private Builder() {}

    @Override
    public OptionalStep deadline(final Object deadline) {
      this.deadline = deadline;
      return this;
    }

    @Override
    public OptionalStep deadline(
        final Object deadline, final ContractPolicy.FieldPolicy<Object> policy) {
      this.deadline = policy.apply(deadline, Fields.DEADLINE, null);
      return this;
    }

    @Override
    public OptionalStep deniedReason(final Object deniedReason) {
      this.deniedReason = deniedReason;
      return this;
    }

    @Override
    public OptionalStep deniedReason(
        final Object deniedReason, final ContractPolicy.FieldPolicy<Object> policy) {
      this.deniedReason = policy.apply(deniedReason, Fields.DENIED_REASON, null);
      return this;
    }

    @Override
    public OptionalStep elementId(final Object elementId) {
      this.elementId = elementId;
      return this;
    }

    @Override
    public OptionalStep elementId(
        final Object elementId, final ContractPolicy.FieldPolicy<Object> policy) {
      this.elementId = policy.apply(elementId, Fields.ELEMENT_ID, null);
      return this;
    }

    @Override
    public OptionalStep elementInstanceKey(final Object elementInstanceKey) {
      this.elementInstanceKey = elementInstanceKey;
      return this;
    }

    @Override
    public OptionalStep elementInstanceKey(
        final Object elementInstanceKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.elementInstanceKey = policy.apply(elementInstanceKey, Fields.ELEMENT_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep endTime(final Object endTime) {
      this.endTime = endTime;
      return this;
    }

    @Override
    public OptionalStep endTime(
        final Object endTime, final ContractPolicy.FieldPolicy<Object> policy) {
      this.endTime = policy.apply(endTime, Fields.END_TIME, null);
      return this;
    }

    @Override
    public OptionalStep errorCode(final Object errorCode) {
      this.errorCode = errorCode;
      return this;
    }

    @Override
    public OptionalStep errorCode(
        final Object errorCode, final ContractPolicy.FieldPolicy<Object> policy) {
      this.errorCode = policy.apply(errorCode, Fields.ERROR_CODE, null);
      return this;
    }

    @Override
    public OptionalStep errorMessage(final Object errorMessage) {
      this.errorMessage = errorMessage;
      return this;
    }

    @Override
    public OptionalStep errorMessage(
        final Object errorMessage, final ContractPolicy.FieldPolicy<Object> policy) {
      this.errorMessage = policy.apply(errorMessage, Fields.ERROR_MESSAGE, null);
      return this;
    }

    @Override
    public OptionalStep hasFailedWithRetriesLeft(final Boolean hasFailedWithRetriesLeft) {
      this.hasFailedWithRetriesLeft = hasFailedWithRetriesLeft;
      return this;
    }

    @Override
    public OptionalStep hasFailedWithRetriesLeft(
        final Boolean hasFailedWithRetriesLeft, final ContractPolicy.FieldPolicy<Boolean> policy) {
      this.hasFailedWithRetriesLeft =
          policy.apply(hasFailedWithRetriesLeft, Fields.HAS_FAILED_WITH_RETRIES_LEFT, null);
      return this;
    }

    @Override
    public OptionalStep isDenied(final Boolean isDenied) {
      this.isDenied = isDenied;
      return this;
    }

    @Override
    public OptionalStep isDenied(
        final Boolean isDenied, final ContractPolicy.FieldPolicy<Boolean> policy) {
      this.isDenied = policy.apply(isDenied, Fields.IS_DENIED, null);
      return this;
    }

    @Override
    public OptionalStep jobKey(final Object jobKey) {
      this.jobKey = jobKey;
      return this;
    }

    @Override
    public OptionalStep jobKey(
        final Object jobKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.jobKey = policy.apply(jobKey, Fields.JOB_KEY, null);
      return this;
    }

    @Override
    public OptionalStep kind(final Object kind) {
      this.kind = kind;
      return this;
    }

    @Override
    public OptionalStep kind(final Object kind, final ContractPolicy.FieldPolicy<Object> policy) {
      this.kind = policy.apply(kind, Fields.KIND, null);
      return this;
    }

    @Override
    public OptionalStep listenerEventType(final Object listenerEventType) {
      this.listenerEventType = listenerEventType;
      return this;
    }

    @Override
    public OptionalStep listenerEventType(
        final Object listenerEventType, final ContractPolicy.FieldPolicy<Object> policy) {
      this.listenerEventType = policy.apply(listenerEventType, Fields.LISTENER_EVENT_TYPE, null);
      return this;
    }

    @Override
    public OptionalStep processDefinitionId(final Object processDefinitionId) {
      this.processDefinitionId = processDefinitionId;
      return this;
    }

    @Override
    public OptionalStep processDefinitionId(
        final Object processDefinitionId, final ContractPolicy.FieldPolicy<Object> policy) {
      this.processDefinitionId =
          policy.apply(processDefinitionId, Fields.PROCESS_DEFINITION_ID, null);
      return this;
    }

    @Override
    public OptionalStep processDefinitionKey(final Object processDefinitionKey) {
      this.processDefinitionKey = processDefinitionKey;
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
    public OptionalStep processInstanceKey(final Object processInstanceKey) {
      this.processInstanceKey = processInstanceKey;
      return this;
    }

    @Override
    public OptionalStep processInstanceKey(
        final Object processInstanceKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.processInstanceKey = policy.apply(processInstanceKey, Fields.PROCESS_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep retries(final Object retries) {
      this.retries = retries;
      return this;
    }

    @Override
    public OptionalStep retries(
        final Object retries, final ContractPolicy.FieldPolicy<Object> policy) {
      this.retries = policy.apply(retries, Fields.RETRIES, null);
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
    public OptionalStep tenantId(final Object tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    @Override
    public OptionalStep tenantId(
        final Object tenantId, final ContractPolicy.FieldPolicy<Object> policy) {
      this.tenantId = policy.apply(tenantId, Fields.TENANT_ID, null);
      return this;
    }

    @Override
    public OptionalStep type(final Object type) {
      this.type = type;
      return this;
    }

    @Override
    public OptionalStep type(final Object type, final ContractPolicy.FieldPolicy<Object> policy) {
      this.type = policy.apply(type, Fields.TYPE, null);
      return this;
    }

    @Override
    public OptionalStep worker(final Object worker) {
      this.worker = worker;
      return this;
    }

    @Override
    public OptionalStep worker(
        final Object worker, final ContractPolicy.FieldPolicy<Object> policy) {
      this.worker = policy.apply(worker, Fields.WORKER, null);
      return this;
    }

    @Override
    public OptionalStep creationTime(final Object creationTime) {
      this.creationTime = creationTime;
      return this;
    }

    @Override
    public OptionalStep creationTime(
        final Object creationTime, final ContractPolicy.FieldPolicy<Object> policy) {
      this.creationTime = policy.apply(creationTime, Fields.CREATION_TIME, null);
      return this;
    }

    @Override
    public OptionalStep lastUpdateTime(final Object lastUpdateTime) {
      this.lastUpdateTime = lastUpdateTime;
      return this;
    }

    @Override
    public OptionalStep lastUpdateTime(
        final Object lastUpdateTime, final ContractPolicy.FieldPolicy<Object> policy) {
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
    OptionalStep deadline(final Object deadline);

    OptionalStep deadline(final Object deadline, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep deniedReason(final Object deniedReason);

    OptionalStep deniedReason(
        final Object deniedReason, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep elementId(final Object elementId);

    OptionalStep elementId(final Object elementId, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep elementInstanceKey(final Object elementInstanceKey);

    OptionalStep elementInstanceKey(
        final Object elementInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep endTime(final Object endTime);

    OptionalStep endTime(final Object endTime, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep errorCode(final Object errorCode);

    OptionalStep errorCode(final Object errorCode, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep errorMessage(final Object errorMessage);

    OptionalStep errorMessage(
        final Object errorMessage, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep hasFailedWithRetriesLeft(final Boolean hasFailedWithRetriesLeft);

    OptionalStep hasFailedWithRetriesLeft(
        final Boolean hasFailedWithRetriesLeft, final ContractPolicy.FieldPolicy<Boolean> policy);

    OptionalStep isDenied(final Boolean isDenied);

    OptionalStep isDenied(final Boolean isDenied, final ContractPolicy.FieldPolicy<Boolean> policy);

    OptionalStep jobKey(final Object jobKey);

    OptionalStep jobKey(final Object jobKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep kind(final Object kind);

    OptionalStep kind(final Object kind, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep listenerEventType(final Object listenerEventType);

    OptionalStep listenerEventType(
        final Object listenerEventType, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep processDefinitionId(final Object processDefinitionId);

    OptionalStep processDefinitionId(
        final Object processDefinitionId, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep processDefinitionKey(final Object processDefinitionKey);

    OptionalStep processDefinitionKey(
        final Object processDefinitionKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep processInstanceKey(final Object processInstanceKey);

    OptionalStep processInstanceKey(
        final Object processInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep retries(final Object retries);

    OptionalStep retries(final Object retries, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep state(final Object state);

    OptionalStep state(final Object state, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep tenantId(final Object tenantId);

    OptionalStep tenantId(final Object tenantId, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep type(final Object type);

    OptionalStep type(final Object type, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep worker(final Object worker);

    OptionalStep worker(final Object worker, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep creationTime(final Object creationTime);

    OptionalStep creationTime(
        final Object creationTime, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep lastUpdateTime(final Object lastUpdateTime);

    OptionalStep lastUpdateTime(
        final Object lastUpdateTime, final ContractPolicy.FieldPolicy<Object> policy);

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
