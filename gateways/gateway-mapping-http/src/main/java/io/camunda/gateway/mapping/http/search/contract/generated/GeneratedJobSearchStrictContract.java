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
public record GeneratedJobSearchStrictContract(
    java.util.Map<String, String> customHeaders,
    @Nullable String deadline,
    @Nullable String deniedReason,
    @Nullable String elementId,
    String elementInstanceKey,
    @Nullable String endTime,
    @Nullable String errorCode,
    @Nullable String errorMessage,
    Boolean hasFailedWithRetriesLeft,
    @Nullable Boolean isDenied,
    String jobKey,
    io.camunda.gateway.protocol.model.JobKindEnum kind,
    io.camunda.gateway.protocol.model.JobListenerEventTypeEnum listenerEventType,
    String processDefinitionId,
    String processDefinitionKey,
    String processInstanceKey,
    @Nullable String rootProcessInstanceKey,
    Integer retries,
    io.camunda.gateway.protocol.model.JobStateEnum state,
    String tenantId,
    String type,
    String worker,
    @Nullable String creationTime,
    @Nullable String lastUpdateTime) {

  public GeneratedJobSearchStrictContract {
    Objects.requireNonNull(customHeaders, "customHeaders is required and must not be null");
    Objects.requireNonNull(
        elementInstanceKey, "elementInstanceKey is required and must not be null");
    Objects.requireNonNull(
        hasFailedWithRetriesLeft, "hasFailedWithRetriesLeft is required and must not be null");
    Objects.requireNonNull(jobKey, "jobKey is required and must not be null");
    Objects.requireNonNull(kind, "kind is required and must not be null");
    Objects.requireNonNull(listenerEventType, "listenerEventType is required and must not be null");
    Objects.requireNonNull(
        processDefinitionId, "processDefinitionId is required and must not be null");
    Objects.requireNonNull(
        processDefinitionKey, "processDefinitionKey is required and must not be null");
    Objects.requireNonNull(
        processInstanceKey, "processInstanceKey is required and must not be null");
    Objects.requireNonNull(retries, "retries is required and must not be null");
    Objects.requireNonNull(state, "state is required and must not be null");
    Objects.requireNonNull(tenantId, "tenantId is required and must not be null");
    Objects.requireNonNull(type, "type is required and must not be null");
    Objects.requireNonNull(worker, "worker is required and must not be null");
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

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
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
    private ContractPolicy.FieldPolicy<java.util.Map<String, String>> customHeadersPolicy;
    private String deadline;
    private String deniedReason;
    private String elementId;
    private Object elementInstanceKey;
    private ContractPolicy.FieldPolicy<Object> elementInstanceKeyPolicy;
    private String endTime;
    private String errorCode;
    private String errorMessage;
    private Boolean hasFailedWithRetriesLeft;
    private ContractPolicy.FieldPolicy<Boolean> hasFailedWithRetriesLeftPolicy;
    private Boolean isDenied;
    private Object jobKey;
    private ContractPolicy.FieldPolicy<Object> jobKeyPolicy;
    private io.camunda.gateway.protocol.model.JobKindEnum kind;
    private ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.JobKindEnum> kindPolicy;
    private io.camunda.gateway.protocol.model.JobListenerEventTypeEnum listenerEventType;
    private ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.JobListenerEventTypeEnum>
        listenerEventTypePolicy;
    private String processDefinitionId;
    private ContractPolicy.FieldPolicy<String> processDefinitionIdPolicy;
    private Object processDefinitionKey;
    private ContractPolicy.FieldPolicy<Object> processDefinitionKeyPolicy;
    private Object processInstanceKey;
    private ContractPolicy.FieldPolicy<Object> processInstanceKeyPolicy;
    private Object rootProcessInstanceKey;
    private Integer retries;
    private ContractPolicy.FieldPolicy<Integer> retriesPolicy;
    private io.camunda.gateway.protocol.model.JobStateEnum state;
    private ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.JobStateEnum> statePolicy;
    private String tenantId;
    private ContractPolicy.FieldPolicy<String> tenantIdPolicy;
    private String type;
    private ContractPolicy.FieldPolicy<String> typePolicy;
    private String worker;
    private ContractPolicy.FieldPolicy<String> workerPolicy;
    private String creationTime;
    private String lastUpdateTime;

    private Builder() {}

    @Override
    public ElementInstanceKeyStep customHeaders(
        final java.util.Map<String, String> customHeaders,
        final ContractPolicy.FieldPolicy<java.util.Map<String, String>> policy) {
      this.customHeaders = customHeaders;
      this.customHeadersPolicy = policy;
      return this;
    }

    @Override
    public HasFailedWithRetriesLeftStep elementInstanceKey(
        final Object elementInstanceKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.elementInstanceKey = elementInstanceKey;
      this.elementInstanceKeyPolicy = policy;
      return this;
    }

    @Override
    public JobKeyStep hasFailedWithRetriesLeft(
        final Boolean hasFailedWithRetriesLeft, final ContractPolicy.FieldPolicy<Boolean> policy) {
      this.hasFailedWithRetriesLeft = hasFailedWithRetriesLeft;
      this.hasFailedWithRetriesLeftPolicy = policy;
      return this;
    }

    @Override
    public KindStep jobKey(final Object jobKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.jobKey = jobKey;
      this.jobKeyPolicy = policy;
      return this;
    }

    @Override
    public ListenerEventTypeStep kind(
        final io.camunda.gateway.protocol.model.JobKindEnum kind,
        final ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.JobKindEnum> policy) {
      this.kind = kind;
      this.kindPolicy = policy;
      return this;
    }

    @Override
    public ProcessDefinitionIdStep listenerEventType(
        final io.camunda.gateway.protocol.model.JobListenerEventTypeEnum listenerEventType,
        final ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.JobListenerEventTypeEnum>
            policy) {
      this.listenerEventType = listenerEventType;
      this.listenerEventTypePolicy = policy;
      return this;
    }

    @Override
    public ProcessDefinitionKeyStep processDefinitionId(
        final String processDefinitionId, final ContractPolicy.FieldPolicy<String> policy) {
      this.processDefinitionId = processDefinitionId;
      this.processDefinitionIdPolicy = policy;
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
    public RetriesStep processInstanceKey(
        final Object processInstanceKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.processInstanceKey = processInstanceKey;
      this.processInstanceKeyPolicy = policy;
      return this;
    }

    @Override
    public StateStep retries(
        final Integer retries, final ContractPolicy.FieldPolicy<Integer> policy) {
      this.retries = retries;
      this.retriesPolicy = policy;
      return this;
    }

    @Override
    public TenantIdStep state(
        final io.camunda.gateway.protocol.model.JobStateEnum state,
        final ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.JobStateEnum> policy) {
      this.state = state;
      this.statePolicy = policy;
      return this;
    }

    @Override
    public TypeStep tenantId(
        final String tenantId, final ContractPolicy.FieldPolicy<String> policy) {
      this.tenantId = tenantId;
      this.tenantIdPolicy = policy;
      return this;
    }

    @Override
    public WorkerStep type(final String type, final ContractPolicy.FieldPolicy<String> policy) {
      this.type = type;
      this.typePolicy = policy;
      return this;
    }

    @Override
    public OptionalStep worker(
        final String worker, final ContractPolicy.FieldPolicy<String> policy) {
      this.worker = worker;
      this.workerPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep deadline(final String deadline) {
      this.deadline = deadline;
      return this;
    }

    @Override
    public OptionalStep deadline(
        final String deadline, final ContractPolicy.FieldPolicy<String> policy) {
      this.deadline = policy.apply(deadline, Fields.DEADLINE, null);
      return this;
    }

    @Override
    public OptionalStep deniedReason(final String deniedReason) {
      this.deniedReason = deniedReason;
      return this;
    }

    @Override
    public OptionalStep deniedReason(
        final String deniedReason, final ContractPolicy.FieldPolicy<String> policy) {
      this.deniedReason = policy.apply(deniedReason, Fields.DENIED_REASON, null);
      return this;
    }

    @Override
    public OptionalStep elementId(final String elementId) {
      this.elementId = elementId;
      return this;
    }

    @Override
    public OptionalStep elementId(
        final String elementId, final ContractPolicy.FieldPolicy<String> policy) {
      this.elementId = policy.apply(elementId, Fields.ELEMENT_ID, null);
      return this;
    }

    @Override
    public OptionalStep endTime(final String endTime) {
      this.endTime = endTime;
      return this;
    }

    @Override
    public OptionalStep endTime(
        final String endTime, final ContractPolicy.FieldPolicy<String> policy) {
      this.endTime = policy.apply(endTime, Fields.END_TIME, null);
      return this;
    }

    @Override
    public OptionalStep errorCode(final String errorCode) {
      this.errorCode = errorCode;
      return this;
    }

    @Override
    public OptionalStep errorCode(
        final String errorCode, final ContractPolicy.FieldPolicy<String> policy) {
      this.errorCode = policy.apply(errorCode, Fields.ERROR_CODE, null);
      return this;
    }

    @Override
    public OptionalStep errorMessage(final String errorMessage) {
      this.errorMessage = errorMessage;
      return this;
    }

    @Override
    public OptionalStep errorMessage(
        final String errorMessage, final ContractPolicy.FieldPolicy<String> policy) {
      this.errorMessage = policy.apply(errorMessage, Fields.ERROR_MESSAGE, null);
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
    public OptionalStep creationTime(final String creationTime) {
      this.creationTime = creationTime;
      return this;
    }

    @Override
    public OptionalStep creationTime(
        final String creationTime, final ContractPolicy.FieldPolicy<String> policy) {
      this.creationTime = policy.apply(creationTime, Fields.CREATION_TIME, null);
      return this;
    }

    @Override
    public OptionalStep lastUpdateTime(final String lastUpdateTime) {
      this.lastUpdateTime = lastUpdateTime;
      return this;
    }

    @Override
    public OptionalStep lastUpdateTime(
        final String lastUpdateTime, final ContractPolicy.FieldPolicy<String> policy) {
      this.lastUpdateTime = policy.apply(lastUpdateTime, Fields.LAST_UPDATE_TIME, null);
      return this;
    }

    @Override
    public GeneratedJobSearchStrictContract build() {
      return new GeneratedJobSearchStrictContract(
          applyRequiredPolicy(this.customHeaders, this.customHeadersPolicy, Fields.CUSTOM_HEADERS),
          this.deadline,
          this.deniedReason,
          this.elementId,
          coerceElementInstanceKey(
              applyRequiredPolicy(
                  this.elementInstanceKey,
                  this.elementInstanceKeyPolicy,
                  Fields.ELEMENT_INSTANCE_KEY)),
          this.endTime,
          this.errorCode,
          this.errorMessage,
          applyRequiredPolicy(
              this.hasFailedWithRetriesLeft,
              this.hasFailedWithRetriesLeftPolicy,
              Fields.HAS_FAILED_WITH_RETRIES_LEFT),
          this.isDenied,
          coerceJobKey(applyRequiredPolicy(this.jobKey, this.jobKeyPolicy, Fields.JOB_KEY)),
          applyRequiredPolicy(this.kind, this.kindPolicy, Fields.KIND),
          applyRequiredPolicy(
              this.listenerEventType, this.listenerEventTypePolicy, Fields.LISTENER_EVENT_TYPE),
          applyRequiredPolicy(
              this.processDefinitionId,
              this.processDefinitionIdPolicy,
              Fields.PROCESS_DEFINITION_ID),
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
          applyRequiredPolicy(this.retries, this.retriesPolicy, Fields.RETRIES),
          applyRequiredPolicy(this.state, this.statePolicy, Fields.STATE),
          applyRequiredPolicy(this.tenantId, this.tenantIdPolicy, Fields.TENANT_ID),
          applyRequiredPolicy(this.type, this.typePolicy, Fields.TYPE),
          applyRequiredPolicy(this.worker, this.workerPolicy, Fields.WORKER),
          this.creationTime,
          this.lastUpdateTime);
    }
  }

  public interface CustomHeadersStep {
    ElementInstanceKeyStep customHeaders(
        final java.util.Map<String, String> customHeaders,
        final ContractPolicy.FieldPolicy<java.util.Map<String, String>> policy);
  }

  public interface ElementInstanceKeyStep {
    HasFailedWithRetriesLeftStep elementInstanceKey(
        final Object elementInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface HasFailedWithRetriesLeftStep {
    JobKeyStep hasFailedWithRetriesLeft(
        final Boolean hasFailedWithRetriesLeft, final ContractPolicy.FieldPolicy<Boolean> policy);
  }

  public interface JobKeyStep {
    KindStep jobKey(final Object jobKey, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface KindStep {
    ListenerEventTypeStep kind(
        final io.camunda.gateway.protocol.model.JobKindEnum kind,
        final ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.JobKindEnum> policy);
  }

  public interface ListenerEventTypeStep {
    ProcessDefinitionIdStep listenerEventType(
        final io.camunda.gateway.protocol.model.JobListenerEventTypeEnum listenerEventType,
        final ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.JobListenerEventTypeEnum>
            policy);
  }

  public interface ProcessDefinitionIdStep {
    ProcessDefinitionKeyStep processDefinitionId(
        final String processDefinitionId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface ProcessDefinitionKeyStep {
    ProcessInstanceKeyStep processDefinitionKey(
        final Object processDefinitionKey, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface ProcessInstanceKeyStep {
    RetriesStep processInstanceKey(
        final Object processInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface RetriesStep {
    StateStep retries(final Integer retries, final ContractPolicy.FieldPolicy<Integer> policy);
  }

  public interface StateStep {
    TenantIdStep state(
        final io.camunda.gateway.protocol.model.JobStateEnum state,
        final ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.JobStateEnum> policy);
  }

  public interface TenantIdStep {
    TypeStep tenantId(final String tenantId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface TypeStep {
    WorkerStep type(final String type, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface WorkerStep {
    OptionalStep worker(final String worker, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface OptionalStep {
    OptionalStep deadline(final String deadline);

    OptionalStep deadline(final String deadline, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep deniedReason(final String deniedReason);

    OptionalStep deniedReason(
        final String deniedReason, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep elementId(final String elementId);

    OptionalStep elementId(final String elementId, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep endTime(final String endTime);

    OptionalStep endTime(final String endTime, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep errorCode(final String errorCode);

    OptionalStep errorCode(final String errorCode, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep errorMessage(final String errorMessage);

    OptionalStep errorMessage(
        final String errorMessage, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep isDenied(final Boolean isDenied);

    OptionalStep isDenied(final Boolean isDenied, final ContractPolicy.FieldPolicy<Boolean> policy);

    OptionalStep rootProcessInstanceKey(final String rootProcessInstanceKey);

    OptionalStep rootProcessInstanceKey(final Object rootProcessInstanceKey);

    OptionalStep rootProcessInstanceKey(
        final String rootProcessInstanceKey, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep rootProcessInstanceKey(
        final Object rootProcessInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep creationTime(final String creationTime);

    OptionalStep creationTime(
        final String creationTime, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep lastUpdateTime(final String lastUpdateTime);

    OptionalStep lastUpdateTime(
        final String lastUpdateTime, final ContractPolicy.FieldPolicy<String> policy);

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
