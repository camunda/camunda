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
public record GeneratedActivatedJobStrictContract(
    @JsonProperty("type") String type,
    @JsonProperty("processDefinitionId") String processDefinitionId,
    @JsonProperty("processDefinitionVersion") Integer processDefinitionVersion,
    @JsonProperty("elementId") String elementId,
    @JsonProperty("customHeaders") java.util.Map<String, Object> customHeaders,
    @JsonProperty("worker") String worker,
    @JsonProperty("retries") Integer retries,
    @JsonProperty("deadline") Long deadline,
    @JsonProperty("variables") java.util.Map<String, Object> variables,
    @JsonProperty("tenantId") String tenantId,
    @JsonProperty("jobKey") String jobKey,
    @JsonProperty("processInstanceKey") String processInstanceKey,
    @JsonProperty("processDefinitionKey") String processDefinitionKey,
    @JsonProperty("elementInstanceKey") String elementInstanceKey,
    @JsonProperty("kind")
        io.camunda.gateway.mapping.http.search.contract.generated.GeneratedJobKindEnum kind,
    @JsonProperty("listenerEventType")
        io.camunda.gateway.mapping.http.search.contract.generated.GeneratedJobListenerEventTypeEnum
            listenerEventType,
    @JsonProperty("userTask") @Nullable GeneratedUserTaskPropertiesStrictContract userTask,
    @JsonProperty("tags") java.util.Set<String> tags,
    @JsonProperty("rootProcessInstanceKey") @Nullable String rootProcessInstanceKey) {

  public GeneratedActivatedJobStrictContract {
    Objects.requireNonNull(type, "No type provided.");
    Objects.requireNonNull(processDefinitionId, "No processDefinitionId provided.");
    Objects.requireNonNull(processDefinitionVersion, "No processDefinitionVersion provided.");
    Objects.requireNonNull(elementId, "No elementId provided.");
    Objects.requireNonNull(customHeaders, "No customHeaders provided.");
    Objects.requireNonNull(worker, "No worker provided.");
    Objects.requireNonNull(retries, "No retries provided.");
    Objects.requireNonNull(deadline, "No deadline provided.");
    Objects.requireNonNull(variables, "No variables provided.");
    Objects.requireNonNull(tenantId, "No tenantId provided.");
    Objects.requireNonNull(jobKey, "No jobKey provided.");
    Objects.requireNonNull(processInstanceKey, "No processInstanceKey provided.");
    Objects.requireNonNull(processDefinitionKey, "No processDefinitionKey provided.");
    Objects.requireNonNull(elementInstanceKey, "No elementInstanceKey provided.");
    Objects.requireNonNull(kind, "No kind provided.");
    Objects.requireNonNull(listenerEventType, "No listenerEventType provided.");
    Objects.requireNonNull(tags, "No tags provided.");
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

  public static GeneratedUserTaskPropertiesStrictContract coerceUserTask(final Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof GeneratedUserTaskPropertiesStrictContract strictValue) {
      return strictValue;
    }

    throw new IllegalArgumentException(
        "userTask must be a GeneratedUserTaskPropertiesStrictContract, but was "
            + value.getClass().getName());
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

  public static TypeStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements TypeStep,
          ProcessDefinitionIdStep,
          ProcessDefinitionVersionStep,
          ElementIdStep,
          CustomHeadersStep,
          WorkerStep,
          RetriesStep,
          DeadlineStep,
          VariablesStep,
          TenantIdStep,
          JobKeyStep,
          ProcessInstanceKeyStep,
          ProcessDefinitionKeyStep,
          ElementInstanceKeyStep,
          KindStep,
          ListenerEventTypeStep,
          TagsStep,
          OptionalStep {
    private String type;
    private String processDefinitionId;
    private Integer processDefinitionVersion;
    private String elementId;
    private java.util.Map<String, Object> customHeaders;
    private String worker;
    private Integer retries;
    private Long deadline;
    private java.util.Map<String, Object> variables;
    private String tenantId;
    private Object jobKey;
    private Object processInstanceKey;
    private Object processDefinitionKey;
    private Object elementInstanceKey;
    private io.camunda.gateway.mapping.http.search.contract.generated.GeneratedJobKindEnum kind;
    private io.camunda.gateway.mapping.http.search.contract.generated
            .GeneratedJobListenerEventTypeEnum
        listenerEventType;
    private Object userTask;
    private java.util.Set<String> tags;
    private Object rootProcessInstanceKey;

    private Builder() {}

    @Override
    public ProcessDefinitionIdStep type(final String type) {
      this.type = type;
      return this;
    }

    @Override
    public ProcessDefinitionVersionStep processDefinitionId(final String processDefinitionId) {
      this.processDefinitionId = processDefinitionId;
      return this;
    }

    @Override
    public ElementIdStep processDefinitionVersion(final Integer processDefinitionVersion) {
      this.processDefinitionVersion = processDefinitionVersion;
      return this;
    }

    @Override
    public CustomHeadersStep elementId(final String elementId) {
      this.elementId = elementId;
      return this;
    }

    @Override
    public WorkerStep customHeaders(final java.util.Map<String, Object> customHeaders) {
      this.customHeaders = customHeaders;
      return this;
    }

    @Override
    public RetriesStep worker(final String worker) {
      this.worker = worker;
      return this;
    }

    @Override
    public DeadlineStep retries(final Integer retries) {
      this.retries = retries;
      return this;
    }

    @Override
    public VariablesStep deadline(final Long deadline) {
      this.deadline = deadline;
      return this;
    }

    @Override
    public TenantIdStep variables(final java.util.Map<String, Object> variables) {
      this.variables = variables;
      return this;
    }

    @Override
    public JobKeyStep tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    @Override
    public ProcessInstanceKeyStep jobKey(final Object jobKey) {
      this.jobKey = jobKey;
      return this;
    }

    @Override
    public ProcessDefinitionKeyStep processInstanceKey(final Object processInstanceKey) {
      this.processInstanceKey = processInstanceKey;
      return this;
    }

    @Override
    public ElementInstanceKeyStep processDefinitionKey(final Object processDefinitionKey) {
      this.processDefinitionKey = processDefinitionKey;
      return this;
    }

    @Override
    public KindStep elementInstanceKey(final Object elementInstanceKey) {
      this.elementInstanceKey = elementInstanceKey;
      return this;
    }

    @Override
    public ListenerEventTypeStep kind(
        final io.camunda.gateway.mapping.http.search.contract.generated.GeneratedJobKindEnum kind) {
      this.kind = kind;
      return this;
    }

    @Override
    public TagsStep listenerEventType(
        final io.camunda.gateway.mapping.http.search.contract.generated
                .GeneratedJobListenerEventTypeEnum
            listenerEventType) {
      this.listenerEventType = listenerEventType;
      return this;
    }

    @Override
    public OptionalStep tags(final java.util.Set<String> tags) {
      this.tags = tags;
      return this;
    }

    @Override
    public OptionalStep userTask(
        final @Nullable GeneratedUserTaskPropertiesStrictContract userTask) {
      this.userTask = userTask;
      return this;
    }

    @Override
    public OptionalStep userTask(final @Nullable Object userTask) {
      this.userTask = userTask;
      return this;
    }

    public Builder userTask(
        final @Nullable GeneratedUserTaskPropertiesStrictContract userTask,
        final ContractPolicy.FieldPolicy<GeneratedUserTaskPropertiesStrictContract> policy) {
      this.userTask = policy.apply(userTask, Fields.USER_TASK, null);
      return this;
    }

    @Override
    public OptionalStep userTask(
        final @Nullable Object userTask, final ContractPolicy.FieldPolicy<Object> policy) {
      this.userTask = policy.apply(userTask, Fields.USER_TASK, null);
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
    public GeneratedActivatedJobStrictContract build() {
      return new GeneratedActivatedJobStrictContract(
          this.type,
          this.processDefinitionId,
          this.processDefinitionVersion,
          this.elementId,
          this.customHeaders,
          this.worker,
          this.retries,
          this.deadline,
          this.variables,
          this.tenantId,
          coerceJobKey(this.jobKey),
          coerceProcessInstanceKey(this.processInstanceKey),
          coerceProcessDefinitionKey(this.processDefinitionKey),
          coerceElementInstanceKey(this.elementInstanceKey),
          this.kind,
          this.listenerEventType,
          coerceUserTask(this.userTask),
          this.tags,
          coerceRootProcessInstanceKey(this.rootProcessInstanceKey));
    }
  }

  public interface TypeStep {
    ProcessDefinitionIdStep type(final String type);
  }

  public interface ProcessDefinitionIdStep {
    ProcessDefinitionVersionStep processDefinitionId(final String processDefinitionId);
  }

  public interface ProcessDefinitionVersionStep {
    ElementIdStep processDefinitionVersion(final Integer processDefinitionVersion);
  }

  public interface ElementIdStep {
    CustomHeadersStep elementId(final String elementId);
  }

  public interface CustomHeadersStep {
    WorkerStep customHeaders(final java.util.Map<String, Object> customHeaders);
  }

  public interface WorkerStep {
    RetriesStep worker(final String worker);
  }

  public interface RetriesStep {
    DeadlineStep retries(final Integer retries);
  }

  public interface DeadlineStep {
    VariablesStep deadline(final Long deadline);
  }

  public interface VariablesStep {
    TenantIdStep variables(final java.util.Map<String, Object> variables);
  }

  public interface TenantIdStep {
    JobKeyStep tenantId(final String tenantId);
  }

  public interface JobKeyStep {
    ProcessInstanceKeyStep jobKey(final Object jobKey);
  }

  public interface ProcessInstanceKeyStep {
    ProcessDefinitionKeyStep processInstanceKey(final Object processInstanceKey);
  }

  public interface ProcessDefinitionKeyStep {
    ElementInstanceKeyStep processDefinitionKey(final Object processDefinitionKey);
  }

  public interface ElementInstanceKeyStep {
    KindStep elementInstanceKey(final Object elementInstanceKey);
  }

  public interface KindStep {
    ListenerEventTypeStep kind(
        final io.camunda.gateway.mapping.http.search.contract.generated.GeneratedJobKindEnum kind);
  }

  public interface ListenerEventTypeStep {
    TagsStep listenerEventType(
        final io.camunda.gateway.mapping.http.search.contract.generated
                .GeneratedJobListenerEventTypeEnum
            listenerEventType);
  }

  public interface TagsStep {
    OptionalStep tags(final java.util.Set<String> tags);
  }

  public interface OptionalStep {
    OptionalStep userTask(final @Nullable GeneratedUserTaskPropertiesStrictContract userTask);

    OptionalStep userTask(final @Nullable Object userTask);

    OptionalStep userTask(
        final @Nullable GeneratedUserTaskPropertiesStrictContract userTask,
        final ContractPolicy.FieldPolicy<GeneratedUserTaskPropertiesStrictContract> policy);

    OptionalStep userTask(
        final @Nullable Object userTask, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep rootProcessInstanceKey(final @Nullable String rootProcessInstanceKey);

    OptionalStep rootProcessInstanceKey(final @Nullable Object rootProcessInstanceKey);

    OptionalStep rootProcessInstanceKey(
        final @Nullable String rootProcessInstanceKey,
        final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep rootProcessInstanceKey(
        final @Nullable Object rootProcessInstanceKey,
        final ContractPolicy.FieldPolicy<Object> policy);

    GeneratedActivatedJobStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef TYPE =
        ContractPolicy.field("ActivatedJobResult", "type");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_ID =
        ContractPolicy.field("ActivatedJobResult", "processDefinitionId");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_VERSION =
        ContractPolicy.field("ActivatedJobResult", "processDefinitionVersion");
    public static final ContractPolicy.FieldRef ELEMENT_ID =
        ContractPolicy.field("ActivatedJobResult", "elementId");
    public static final ContractPolicy.FieldRef CUSTOM_HEADERS =
        ContractPolicy.field("ActivatedJobResult", "customHeaders");
    public static final ContractPolicy.FieldRef WORKER =
        ContractPolicy.field("ActivatedJobResult", "worker");
    public static final ContractPolicy.FieldRef RETRIES =
        ContractPolicy.field("ActivatedJobResult", "retries");
    public static final ContractPolicy.FieldRef DEADLINE =
        ContractPolicy.field("ActivatedJobResult", "deadline");
    public static final ContractPolicy.FieldRef VARIABLES =
        ContractPolicy.field("ActivatedJobResult", "variables");
    public static final ContractPolicy.FieldRef TENANT_ID =
        ContractPolicy.field("ActivatedJobResult", "tenantId");
    public static final ContractPolicy.FieldRef JOB_KEY =
        ContractPolicy.field("ActivatedJobResult", "jobKey");
    public static final ContractPolicy.FieldRef PROCESS_INSTANCE_KEY =
        ContractPolicy.field("ActivatedJobResult", "processInstanceKey");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_KEY =
        ContractPolicy.field("ActivatedJobResult", "processDefinitionKey");
    public static final ContractPolicy.FieldRef ELEMENT_INSTANCE_KEY =
        ContractPolicy.field("ActivatedJobResult", "elementInstanceKey");
    public static final ContractPolicy.FieldRef KIND =
        ContractPolicy.field("ActivatedJobResult", "kind");
    public static final ContractPolicy.FieldRef LISTENER_EVENT_TYPE =
        ContractPolicy.field("ActivatedJobResult", "listenerEventType");
    public static final ContractPolicy.FieldRef USER_TASK =
        ContractPolicy.field("ActivatedJobResult", "userTask");
    public static final ContractPolicy.FieldRef TAGS =
        ContractPolicy.field("ActivatedJobResult", "tags");
    public static final ContractPolicy.FieldRef ROOT_PROCESS_INSTANCE_KEY =
        ContractPolicy.field("ActivatedJobResult", "rootProcessInstanceKey");

    private Fields() {}
  }
}
