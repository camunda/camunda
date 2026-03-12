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
public record GeneratedActivatedJobStrictContract(
    String type,
    String processDefinitionId,
    Integer processDefinitionVersion,
    String elementId,
    java.util.Map<String, Object> customHeaders,
    String worker,
    Integer retries,
    Long deadline,
    java.util.Map<String, Object> variables,
    String tenantId,
    String jobKey,
    String processInstanceKey,
    String processDefinitionKey,
    String elementInstanceKey,
    io.camunda.gateway.protocol.model.JobKindEnum kind,
    io.camunda.gateway.protocol.model.JobListenerEventTypeEnum listenerEventType,
    @Nullable GeneratedUserTaskPropertiesStrictContract userTask,
    java.util.Set<String> tags,
    @Nullable String rootProcessInstanceKey) {

  public GeneratedActivatedJobStrictContract {
    Objects.requireNonNull(type, "type is required and must not be null");
    Objects.requireNonNull(
        processDefinitionId, "processDefinitionId is required and must not be null");
    Objects.requireNonNull(
        processDefinitionVersion, "processDefinitionVersion is required and must not be null");
    Objects.requireNonNull(elementId, "elementId is required and must not be null");
    Objects.requireNonNull(customHeaders, "customHeaders is required and must not be null");
    Objects.requireNonNull(worker, "worker is required and must not be null");
    Objects.requireNonNull(retries, "retries is required and must not be null");
    Objects.requireNonNull(deadline, "deadline is required and must not be null");
    Objects.requireNonNull(variables, "variables is required and must not be null");
    Objects.requireNonNull(tenantId, "tenantId is required and must not be null");
    Objects.requireNonNull(jobKey, "jobKey is required and must not be null");
    Objects.requireNonNull(
        processInstanceKey, "processInstanceKey is required and must not be null");
    Objects.requireNonNull(
        processDefinitionKey, "processDefinitionKey is required and must not be null");
    Objects.requireNonNull(
        elementInstanceKey, "elementInstanceKey is required and must not be null");
    Objects.requireNonNull(kind, "kind is required and must not be null");
    Objects.requireNonNull(listenerEventType, "listenerEventType is required and must not be null");
    Objects.requireNonNull(tags, "tags is required and must not be null");
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

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
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
    private ContractPolicy.FieldPolicy<String> typePolicy;
    private String processDefinitionId;
    private ContractPolicy.FieldPolicy<String> processDefinitionIdPolicy;
    private Integer processDefinitionVersion;
    private ContractPolicy.FieldPolicy<Integer> processDefinitionVersionPolicy;
    private String elementId;
    private ContractPolicy.FieldPolicy<String> elementIdPolicy;
    private java.util.Map<String, Object> customHeaders;
    private ContractPolicy.FieldPolicy<java.util.Map<String, Object>> customHeadersPolicy;
    private String worker;
    private ContractPolicy.FieldPolicy<String> workerPolicy;
    private Integer retries;
    private ContractPolicy.FieldPolicy<Integer> retriesPolicy;
    private Long deadline;
    private ContractPolicy.FieldPolicy<Long> deadlinePolicy;
    private java.util.Map<String, Object> variables;
    private ContractPolicy.FieldPolicy<java.util.Map<String, Object>> variablesPolicy;
    private String tenantId;
    private ContractPolicy.FieldPolicy<String> tenantIdPolicy;
    private Object jobKey;
    private ContractPolicy.FieldPolicy<Object> jobKeyPolicy;
    private Object processInstanceKey;
    private ContractPolicy.FieldPolicy<Object> processInstanceKeyPolicy;
    private Object processDefinitionKey;
    private ContractPolicy.FieldPolicy<Object> processDefinitionKeyPolicy;
    private Object elementInstanceKey;
    private ContractPolicy.FieldPolicy<Object> elementInstanceKeyPolicy;
    private io.camunda.gateway.protocol.model.JobKindEnum kind;
    private ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.JobKindEnum> kindPolicy;
    private io.camunda.gateway.protocol.model.JobListenerEventTypeEnum listenerEventType;
    private ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.JobListenerEventTypeEnum>
        listenerEventTypePolicy;
    private Object userTask;
    private java.util.Set<String> tags;
    private ContractPolicy.FieldPolicy<java.util.Set<String>> tagsPolicy;
    private Object rootProcessInstanceKey;

    private Builder() {}

    @Override
    public ProcessDefinitionIdStep type(
        final String type, final ContractPolicy.FieldPolicy<String> policy) {
      this.type = type;
      this.typePolicy = policy;
      return this;
    }

    @Override
    public ProcessDefinitionVersionStep processDefinitionId(
        final String processDefinitionId, final ContractPolicy.FieldPolicy<String> policy) {
      this.processDefinitionId = processDefinitionId;
      this.processDefinitionIdPolicy = policy;
      return this;
    }

    @Override
    public ElementIdStep processDefinitionVersion(
        final Integer processDefinitionVersion, final ContractPolicy.FieldPolicy<Integer> policy) {
      this.processDefinitionVersion = processDefinitionVersion;
      this.processDefinitionVersionPolicy = policy;
      return this;
    }

    @Override
    public CustomHeadersStep elementId(
        final String elementId, final ContractPolicy.FieldPolicy<String> policy) {
      this.elementId = elementId;
      this.elementIdPolicy = policy;
      return this;
    }

    @Override
    public WorkerStep customHeaders(
        final java.util.Map<String, Object> customHeaders,
        final ContractPolicy.FieldPolicy<java.util.Map<String, Object>> policy) {
      this.customHeaders = customHeaders;
      this.customHeadersPolicy = policy;
      return this;
    }

    @Override
    public RetriesStep worker(
        final String worker, final ContractPolicy.FieldPolicy<String> policy) {
      this.worker = worker;
      this.workerPolicy = policy;
      return this;
    }

    @Override
    public DeadlineStep retries(
        final Integer retries, final ContractPolicy.FieldPolicy<Integer> policy) {
      this.retries = retries;
      this.retriesPolicy = policy;
      return this;
    }

    @Override
    public VariablesStep deadline(
        final Long deadline, final ContractPolicy.FieldPolicy<Long> policy) {
      this.deadline = deadline;
      this.deadlinePolicy = policy;
      return this;
    }

    @Override
    public TenantIdStep variables(
        final java.util.Map<String, Object> variables,
        final ContractPolicy.FieldPolicy<java.util.Map<String, Object>> policy) {
      this.variables = variables;
      this.variablesPolicy = policy;
      return this;
    }

    @Override
    public JobKeyStep tenantId(
        final String tenantId, final ContractPolicy.FieldPolicy<String> policy) {
      this.tenantId = tenantId;
      this.tenantIdPolicy = policy;
      return this;
    }

    @Override
    public ProcessInstanceKeyStep jobKey(
        final Object jobKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.jobKey = jobKey;
      this.jobKeyPolicy = policy;
      return this;
    }

    @Override
    public ProcessDefinitionKeyStep processInstanceKey(
        final Object processInstanceKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.processInstanceKey = processInstanceKey;
      this.processInstanceKeyPolicy = policy;
      return this;
    }

    @Override
    public ElementInstanceKeyStep processDefinitionKey(
        final Object processDefinitionKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.processDefinitionKey = processDefinitionKey;
      this.processDefinitionKeyPolicy = policy;
      return this;
    }

    @Override
    public KindStep elementInstanceKey(
        final Object elementInstanceKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.elementInstanceKey = elementInstanceKey;
      this.elementInstanceKeyPolicy = policy;
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
    public TagsStep listenerEventType(
        final io.camunda.gateway.protocol.model.JobListenerEventTypeEnum listenerEventType,
        final ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.JobListenerEventTypeEnum>
            policy) {
      this.listenerEventType = listenerEventType;
      this.listenerEventTypePolicy = policy;
      return this;
    }

    @Override
    public OptionalStep tags(
        final java.util.Set<String> tags,
        final ContractPolicy.FieldPolicy<java.util.Set<String>> policy) {
      this.tags = tags;
      this.tagsPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep userTask(final GeneratedUserTaskPropertiesStrictContract userTask) {
      this.userTask = userTask;
      return this;
    }

    @Override
    public OptionalStep userTask(final Object userTask) {
      this.userTask = userTask;
      return this;
    }

    public Builder userTask(
        final GeneratedUserTaskPropertiesStrictContract userTask,
        final ContractPolicy.FieldPolicy<GeneratedUserTaskPropertiesStrictContract> policy) {
      this.userTask = policy.apply(userTask, Fields.USER_TASK, null);
      return this;
    }

    @Override
    public OptionalStep userTask(
        final Object userTask, final ContractPolicy.FieldPolicy<Object> policy) {
      this.userTask = policy.apply(userTask, Fields.USER_TASK, null);
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
    public GeneratedActivatedJobStrictContract build() {
      return new GeneratedActivatedJobStrictContract(
          applyRequiredPolicy(this.type, this.typePolicy, Fields.TYPE),
          applyRequiredPolicy(
              this.processDefinitionId,
              this.processDefinitionIdPolicy,
              Fields.PROCESS_DEFINITION_ID),
          applyRequiredPolicy(
              this.processDefinitionVersion,
              this.processDefinitionVersionPolicy,
              Fields.PROCESS_DEFINITION_VERSION),
          applyRequiredPolicy(this.elementId, this.elementIdPolicy, Fields.ELEMENT_ID),
          applyRequiredPolicy(this.customHeaders, this.customHeadersPolicy, Fields.CUSTOM_HEADERS),
          applyRequiredPolicy(this.worker, this.workerPolicy, Fields.WORKER),
          applyRequiredPolicy(this.retries, this.retriesPolicy, Fields.RETRIES),
          applyRequiredPolicy(this.deadline, this.deadlinePolicy, Fields.DEADLINE),
          applyRequiredPolicy(this.variables, this.variablesPolicy, Fields.VARIABLES),
          applyRequiredPolicy(this.tenantId, this.tenantIdPolicy, Fields.TENANT_ID),
          coerceJobKey(applyRequiredPolicy(this.jobKey, this.jobKeyPolicy, Fields.JOB_KEY)),
          coerceProcessInstanceKey(
              applyRequiredPolicy(
                  this.processInstanceKey,
                  this.processInstanceKeyPolicy,
                  Fields.PROCESS_INSTANCE_KEY)),
          coerceProcessDefinitionKey(
              applyRequiredPolicy(
                  this.processDefinitionKey,
                  this.processDefinitionKeyPolicy,
                  Fields.PROCESS_DEFINITION_KEY)),
          coerceElementInstanceKey(
              applyRequiredPolicy(
                  this.elementInstanceKey,
                  this.elementInstanceKeyPolicy,
                  Fields.ELEMENT_INSTANCE_KEY)),
          applyRequiredPolicy(this.kind, this.kindPolicy, Fields.KIND),
          applyRequiredPolicy(
              this.listenerEventType, this.listenerEventTypePolicy, Fields.LISTENER_EVENT_TYPE),
          coerceUserTask(this.userTask),
          applyRequiredPolicy(this.tags, this.tagsPolicy, Fields.TAGS),
          coerceRootProcessInstanceKey(this.rootProcessInstanceKey));
    }
  }

  public interface TypeStep {
    ProcessDefinitionIdStep type(
        final String type, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface ProcessDefinitionIdStep {
    ProcessDefinitionVersionStep processDefinitionId(
        final String processDefinitionId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface ProcessDefinitionVersionStep {
    ElementIdStep processDefinitionVersion(
        final Integer processDefinitionVersion, final ContractPolicy.FieldPolicy<Integer> policy);
  }

  public interface ElementIdStep {
    CustomHeadersStep elementId(
        final String elementId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface CustomHeadersStep {
    WorkerStep customHeaders(
        final java.util.Map<String, Object> customHeaders,
        final ContractPolicy.FieldPolicy<java.util.Map<String, Object>> policy);
  }

  public interface WorkerStep {
    RetriesStep worker(final String worker, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface RetriesStep {
    DeadlineStep retries(final Integer retries, final ContractPolicy.FieldPolicy<Integer> policy);
  }

  public interface DeadlineStep {
    VariablesStep deadline(final Long deadline, final ContractPolicy.FieldPolicy<Long> policy);
  }

  public interface VariablesStep {
    TenantIdStep variables(
        final java.util.Map<String, Object> variables,
        final ContractPolicy.FieldPolicy<java.util.Map<String, Object>> policy);
  }

  public interface TenantIdStep {
    JobKeyStep tenantId(final String tenantId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface JobKeyStep {
    ProcessInstanceKeyStep jobKey(
        final Object jobKey, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface ProcessInstanceKeyStep {
    ProcessDefinitionKeyStep processInstanceKey(
        final Object processInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface ProcessDefinitionKeyStep {
    ElementInstanceKeyStep processDefinitionKey(
        final Object processDefinitionKey, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface ElementInstanceKeyStep {
    KindStep elementInstanceKey(
        final Object elementInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface KindStep {
    ListenerEventTypeStep kind(
        final io.camunda.gateway.protocol.model.JobKindEnum kind,
        final ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.JobKindEnum> policy);
  }

  public interface ListenerEventTypeStep {
    TagsStep listenerEventType(
        final io.camunda.gateway.protocol.model.JobListenerEventTypeEnum listenerEventType,
        final ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.JobListenerEventTypeEnum>
            policy);
  }

  public interface TagsStep {
    OptionalStep tags(
        final java.util.Set<String> tags,
        final ContractPolicy.FieldPolicy<java.util.Set<String>> policy);
  }

  public interface OptionalStep {
    OptionalStep userTask(final GeneratedUserTaskPropertiesStrictContract userTask);

    OptionalStep userTask(final Object userTask);

    OptionalStep userTask(
        final GeneratedUserTaskPropertiesStrictContract userTask,
        final ContractPolicy.FieldPolicy<GeneratedUserTaskPropertiesStrictContract> policy);

    OptionalStep userTask(final Object userTask, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep rootProcessInstanceKey(final String rootProcessInstanceKey);

    OptionalStep rootProcessInstanceKey(final Object rootProcessInstanceKey);

    OptionalStep rootProcessInstanceKey(
        final String rootProcessInstanceKey, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep rootProcessInstanceKey(
        final Object rootProcessInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);

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
