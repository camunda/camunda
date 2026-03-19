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
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedJobSearchStrictContract(
    @JsonProperty("customHeaders") java.util.Map<String, String> customHeaders,
    @JsonProperty("deadline") @Nullable String deadline,
    @JsonProperty("deniedReason") @Nullable String deniedReason,
    @JsonProperty("elementId") @Nullable String elementId,
    @JsonProperty("elementInstanceKey") String elementInstanceKey,
    @JsonProperty("endTime") @Nullable String endTime,
    @JsonProperty("errorCode") @Nullable String errorCode,
    @JsonProperty("errorMessage") @Nullable String errorMessage,
    @JsonProperty("hasFailedWithRetriesLeft") Boolean hasFailedWithRetriesLeft,
    @JsonProperty("isDenied") @Nullable Boolean isDenied,
    @JsonProperty("jobKey") String jobKey,
    @JsonProperty("kind")
        io.camunda.gateway.mapping.http.search.contract.generated.GeneratedJobKindEnum kind,
    @JsonProperty("listenerEventType")
        io.camunda.gateway.mapping.http.search.contract.generated.GeneratedJobListenerEventTypeEnum
            listenerEventType,
    @JsonProperty("processDefinitionId") String processDefinitionId,
    @JsonProperty("processDefinitionKey") String processDefinitionKey,
    @JsonProperty("processInstanceKey") String processInstanceKey,
    @JsonProperty("rootProcessInstanceKey") @Nullable String rootProcessInstanceKey,
    @JsonProperty("retries") Integer retries,
    @JsonProperty("state")
        io.camunda.gateway.mapping.http.search.contract.generated.GeneratedJobStateEnum state,
    @JsonProperty("tenantId") String tenantId,
    @JsonProperty("type") String type,
    @JsonProperty("worker") String worker,
    @JsonProperty("creationTime") @Nullable String creationTime,
    @JsonProperty("lastUpdateTime") @Nullable String lastUpdateTime) {

  public GeneratedJobSearchStrictContract {
    Objects.requireNonNull(customHeaders, "No customHeaders provided.");
    Objects.requireNonNull(elementInstanceKey, "No elementInstanceKey provided.");
    Objects.requireNonNull(hasFailedWithRetriesLeft, "No hasFailedWithRetriesLeft provided.");
    Objects.requireNonNull(jobKey, "No jobKey provided.");
    Objects.requireNonNull(kind, "No kind provided.");
    Objects.requireNonNull(listenerEventType, "No listenerEventType provided.");
    Objects.requireNonNull(processDefinitionId, "No processDefinitionId provided.");
    Objects.requireNonNull(processDefinitionKey, "No processDefinitionKey provided.");
    Objects.requireNonNull(processInstanceKey, "No processInstanceKey provided.");
    Objects.requireNonNull(retries, "No retries provided.");
    Objects.requireNonNull(state, "No state provided.");
    Objects.requireNonNull(tenantId, "No tenantId provided.");
    Objects.requireNonNull(type, "No type provided.");
    Objects.requireNonNull(worker, "No worker provided.");
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

  public static CustomHeadersStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements CustomHeadersStep,
          ElementInstanceKeyStep,
          HasFailedWithRetriesLeftStep,
          JobKeyStep,
          KindStep,
          ListenerEventTypeStep,
          ProcessDefinitionIdStep,
          ProcessDefinitionKeyStep,
          ProcessInstanceKeyStep,
          RetriesStep,
          StateStep,
          TenantIdStep,
          TypeStep,
          WorkerStep,
          OptionalStep {
    private java.util.Map<String, String> customHeaders;
    private String deadline;
    private String deniedReason;
    private String elementId;
    private Object elementInstanceKey;
    private String endTime;
    private String errorCode;
    private String errorMessage;
    private Boolean hasFailedWithRetriesLeft;
    private Boolean isDenied;
    private Object jobKey;
    private io.camunda.gateway.mapping.http.search.contract.generated.GeneratedJobKindEnum kind;
    private io.camunda.gateway.mapping.http.search.contract.generated
            .GeneratedJobListenerEventTypeEnum
        listenerEventType;
    private String processDefinitionId;
    private Object processDefinitionKey;
    private Object processInstanceKey;
    private Object rootProcessInstanceKey;
    private Integer retries;
    private io.camunda.gateway.mapping.http.search.contract.generated.GeneratedJobStateEnum state;
    private String tenantId;
    private String type;
    private String worker;
    private String creationTime;
    private String lastUpdateTime;

    private Builder() {}

    @Override
    public ElementInstanceKeyStep customHeaders(final java.util.Map<String, String> customHeaders) {
      this.customHeaders = customHeaders;
      return this;
    }

    @Override
    public HasFailedWithRetriesLeftStep elementInstanceKey(final Object elementInstanceKey) {
      this.elementInstanceKey = elementInstanceKey;
      return this;
    }

    @Override
    public JobKeyStep hasFailedWithRetriesLeft(final Boolean hasFailedWithRetriesLeft) {
      this.hasFailedWithRetriesLeft = hasFailedWithRetriesLeft;
      return this;
    }

    @Override
    public KindStep jobKey(final Object jobKey) {
      this.jobKey = jobKey;
      return this;
    }

    @Override
    public ListenerEventTypeStep kind(
        final io.camunda.gateway.mapping.http.search.contract.generated.GeneratedJobKindEnum kind) {
      this.kind = kind;
      return this;
    }

    @Override
    public ProcessDefinitionIdStep listenerEventType(
        final io.camunda.gateway.mapping.http.search.contract.generated
                .GeneratedJobListenerEventTypeEnum
            listenerEventType) {
      this.listenerEventType = listenerEventType;
      return this;
    }

    @Override
    public ProcessDefinitionKeyStep processDefinitionId(final String processDefinitionId) {
      this.processDefinitionId = processDefinitionId;
      return this;
    }

    @Override
    public ProcessInstanceKeyStep processDefinitionKey(final Object processDefinitionKey) {
      this.processDefinitionKey = processDefinitionKey;
      return this;
    }

    @Override
    public RetriesStep processInstanceKey(final Object processInstanceKey) {
      this.processInstanceKey = processInstanceKey;
      return this;
    }

    @Override
    public StateStep retries(final Integer retries) {
      this.retries = retries;
      return this;
    }

    @Override
    public TenantIdStep state(
        final io.camunda.gateway.mapping.http.search.contract.generated.GeneratedJobStateEnum
            state) {
      this.state = state;
      return this;
    }

    @Override
    public TypeStep tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    @Override
    public WorkerStep type(final String type) {
      this.type = type;
      return this;
    }

    @Override
    public OptionalStep worker(final String worker) {
      this.worker = worker;
      return this;
    }

    @Override
    public OptionalStep deadline(final @Nullable String deadline) {
      this.deadline = deadline;
      return this;
    }

    @Override
    public OptionalStep deadline(
        final @Nullable String deadline, final ContractPolicy.FieldPolicy<String> policy) {
      this.deadline = policy.apply(deadline, Fields.DEADLINE, null);
      return this;
    }

    @Override
    public OptionalStep deniedReason(final @Nullable String deniedReason) {
      this.deniedReason = deniedReason;
      return this;
    }

    @Override
    public OptionalStep deniedReason(
        final @Nullable String deniedReason, final ContractPolicy.FieldPolicy<String> policy) {
      this.deniedReason = policy.apply(deniedReason, Fields.DENIED_REASON, null);
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
    public OptionalStep endTime(final @Nullable String endTime) {
      this.endTime = endTime;
      return this;
    }

    @Override
    public OptionalStep endTime(
        final @Nullable String endTime, final ContractPolicy.FieldPolicy<String> policy) {
      this.endTime = policy.apply(endTime, Fields.END_TIME, null);
      return this;
    }

    @Override
    public OptionalStep errorCode(final @Nullable String errorCode) {
      this.errorCode = errorCode;
      return this;
    }

    @Override
    public OptionalStep errorCode(
        final @Nullable String errorCode, final ContractPolicy.FieldPolicy<String> policy) {
      this.errorCode = policy.apply(errorCode, Fields.ERROR_CODE, null);
      return this;
    }

    @Override
    public OptionalStep errorMessage(final @Nullable String errorMessage) {
      this.errorMessage = errorMessage;
      return this;
    }

    @Override
    public OptionalStep errorMessage(
        final @Nullable String errorMessage, final ContractPolicy.FieldPolicy<String> policy) {
      this.errorMessage = policy.apply(errorMessage, Fields.ERROR_MESSAGE, null);
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
    public OptionalStep creationTime(final @Nullable String creationTime) {
      this.creationTime = creationTime;
      return this;
    }

    @Override
    public OptionalStep creationTime(
        final @Nullable String creationTime, final ContractPolicy.FieldPolicy<String> policy) {
      this.creationTime = policy.apply(creationTime, Fields.CREATION_TIME, null);
      return this;
    }

    @Override
    public OptionalStep lastUpdateTime(final @Nullable String lastUpdateTime) {
      this.lastUpdateTime = lastUpdateTime;
      return this;
    }

    @Override
    public OptionalStep lastUpdateTime(
        final @Nullable String lastUpdateTime, final ContractPolicy.FieldPolicy<String> policy) {
      this.lastUpdateTime = policy.apply(lastUpdateTime, Fields.LAST_UPDATE_TIME, null);
      return this;
    }

    @Override
    public GeneratedJobSearchStrictContract build() {
      return new GeneratedJobSearchStrictContract(
          this.customHeaders,
          this.deadline,
          this.deniedReason,
          this.elementId,
          coerceElementInstanceKey(this.elementInstanceKey),
          this.endTime,
          this.errorCode,
          this.errorMessage,
          this.hasFailedWithRetriesLeft,
          this.isDenied,
          coerceJobKey(this.jobKey),
          this.kind,
          this.listenerEventType,
          this.processDefinitionId,
          coerceProcessDefinitionKey(this.processDefinitionKey),
          coerceProcessInstanceKey(this.processInstanceKey),
          coerceRootProcessInstanceKey(this.rootProcessInstanceKey),
          this.retries,
          this.state,
          this.tenantId,
          this.type,
          this.worker,
          this.creationTime,
          this.lastUpdateTime);
    }
  }

  public interface CustomHeadersStep {
    ElementInstanceKeyStep customHeaders(final java.util.Map<String, String> customHeaders);
  }

  public interface ElementInstanceKeyStep {
    HasFailedWithRetriesLeftStep elementInstanceKey(final Object elementInstanceKey);
  }

  public interface HasFailedWithRetriesLeftStep {
    JobKeyStep hasFailedWithRetriesLeft(final Boolean hasFailedWithRetriesLeft);
  }

  public interface JobKeyStep {
    KindStep jobKey(final Object jobKey);
  }

  public interface KindStep {
    ListenerEventTypeStep kind(
        final io.camunda.gateway.mapping.http.search.contract.generated.GeneratedJobKindEnum kind);
  }

  public interface ListenerEventTypeStep {
    ProcessDefinitionIdStep listenerEventType(
        final io.camunda.gateway.mapping.http.search.contract.generated
                .GeneratedJobListenerEventTypeEnum
            listenerEventType);
  }

  public interface ProcessDefinitionIdStep {
    ProcessDefinitionKeyStep processDefinitionId(final String processDefinitionId);
  }

  public interface ProcessDefinitionKeyStep {
    ProcessInstanceKeyStep processDefinitionKey(final Object processDefinitionKey);
  }

  public interface ProcessInstanceKeyStep {
    RetriesStep processInstanceKey(final Object processInstanceKey);
  }

  public interface RetriesStep {
    StateStep retries(final Integer retries);
  }

  public interface StateStep {
    TenantIdStep state(
        final io.camunda.gateway.mapping.http.search.contract.generated.GeneratedJobStateEnum
            state);
  }

  public interface TenantIdStep {
    TypeStep tenantId(final String tenantId);
  }

  public interface TypeStep {
    WorkerStep type(final String type);
  }

  public interface WorkerStep {
    OptionalStep worker(final String worker);
  }

  public interface OptionalStep {
    OptionalStep deadline(final @Nullable String deadline);

    OptionalStep deadline(
        final @Nullable String deadline, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep deniedReason(final @Nullable String deniedReason);

    OptionalStep deniedReason(
        final @Nullable String deniedReason, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep elementId(final @Nullable String elementId);

    OptionalStep elementId(
        final @Nullable String elementId, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep endTime(final @Nullable String endTime);

    OptionalStep endTime(
        final @Nullable String endTime, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep errorCode(final @Nullable String errorCode);

    OptionalStep errorCode(
        final @Nullable String errorCode, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep errorMessage(final @Nullable String errorMessage);

    OptionalStep errorMessage(
        final @Nullable String errorMessage, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep isDenied(final @Nullable Boolean isDenied);

    OptionalStep isDenied(
        final @Nullable Boolean isDenied, final ContractPolicy.FieldPolicy<Boolean> policy);

    OptionalStep rootProcessInstanceKey(final @Nullable String rootProcessInstanceKey);

    OptionalStep rootProcessInstanceKey(final @Nullable Object rootProcessInstanceKey);

    OptionalStep rootProcessInstanceKey(
        final @Nullable String rootProcessInstanceKey,
        final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep rootProcessInstanceKey(
        final @Nullable Object rootProcessInstanceKey,
        final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep creationTime(final @Nullable String creationTime);

    OptionalStep creationTime(
        final @Nullable String creationTime, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep lastUpdateTime(final @Nullable String lastUpdateTime);

    OptionalStep lastUpdateTime(
        final @Nullable String lastUpdateTime, final ContractPolicy.FieldPolicy<String> policy);

    GeneratedJobSearchStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef CUSTOM_HEADERS =
        ContractPolicy.field("JobSearchResult", "customHeaders");
    public static final ContractPolicy.FieldRef DEADLINE =
        ContractPolicy.field("JobSearchResult", "deadline");
    public static final ContractPolicy.FieldRef DENIED_REASON =
        ContractPolicy.field("JobSearchResult", "deniedReason");
    public static final ContractPolicy.FieldRef ELEMENT_ID =
        ContractPolicy.field("JobSearchResult", "elementId");
    public static final ContractPolicy.FieldRef ELEMENT_INSTANCE_KEY =
        ContractPolicy.field("JobSearchResult", "elementInstanceKey");
    public static final ContractPolicy.FieldRef END_TIME =
        ContractPolicy.field("JobSearchResult", "endTime");
    public static final ContractPolicy.FieldRef ERROR_CODE =
        ContractPolicy.field("JobSearchResult", "errorCode");
    public static final ContractPolicy.FieldRef ERROR_MESSAGE =
        ContractPolicy.field("JobSearchResult", "errorMessage");
    public static final ContractPolicy.FieldRef HAS_FAILED_WITH_RETRIES_LEFT =
        ContractPolicy.field("JobSearchResult", "hasFailedWithRetriesLeft");
    public static final ContractPolicy.FieldRef IS_DENIED =
        ContractPolicy.field("JobSearchResult", "isDenied");
    public static final ContractPolicy.FieldRef JOB_KEY =
        ContractPolicy.field("JobSearchResult", "jobKey");
    public static final ContractPolicy.FieldRef KIND =
        ContractPolicy.field("JobSearchResult", "kind");
    public static final ContractPolicy.FieldRef LISTENER_EVENT_TYPE =
        ContractPolicy.field("JobSearchResult", "listenerEventType");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_ID =
        ContractPolicy.field("JobSearchResult", "processDefinitionId");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_KEY =
        ContractPolicy.field("JobSearchResult", "processDefinitionKey");
    public static final ContractPolicy.FieldRef PROCESS_INSTANCE_KEY =
        ContractPolicy.field("JobSearchResult", "processInstanceKey");
    public static final ContractPolicy.FieldRef ROOT_PROCESS_INSTANCE_KEY =
        ContractPolicy.field("JobSearchResult", "rootProcessInstanceKey");
    public static final ContractPolicy.FieldRef RETRIES =
        ContractPolicy.field("JobSearchResult", "retries");
    public static final ContractPolicy.FieldRef STATE =
        ContractPolicy.field("JobSearchResult", "state");
    public static final ContractPolicy.FieldRef TENANT_ID =
        ContractPolicy.field("JobSearchResult", "tenantId");
    public static final ContractPolicy.FieldRef TYPE =
        ContractPolicy.field("JobSearchResult", "type");
    public static final ContractPolicy.FieldRef WORKER =
        ContractPolicy.field("JobSearchResult", "worker");
    public static final ContractPolicy.FieldRef CREATION_TIME =
        ContractPolicy.field("JobSearchResult", "creationTime");
    public static final ContractPolicy.FieldRef LAST_UPDATE_TIME =
        ContractPolicy.field("JobSearchResult", "lastUpdateTime");

    private Fields() {}
  }
}
