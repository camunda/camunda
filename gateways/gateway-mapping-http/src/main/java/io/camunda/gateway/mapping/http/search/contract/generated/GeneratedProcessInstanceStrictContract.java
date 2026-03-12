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
public record GeneratedProcessInstanceStrictContract(
    String processDefinitionId,
    @Nullable String processDefinitionName,
    Integer processDefinitionVersion,
    @Nullable String processDefinitionVersionTag,
    String startDate,
    @Nullable String endDate,
    io.camunda.gateway.protocol.model.ProcessInstanceStateEnum state,
    Boolean hasIncident,
    String tenantId,
    String processInstanceKey,
    String processDefinitionKey,
    @Nullable String parentProcessInstanceKey,
    @Nullable String parentElementInstanceKey,
    @Nullable String rootProcessInstanceKey,
    java.util.Set<String> tags,
    @Nullable String businessId) {

  public GeneratedProcessInstanceStrictContract {
    Objects.requireNonNull(
        processDefinitionId, "processDefinitionId is required and must not be null");
    Objects.requireNonNull(
        processDefinitionVersion, "processDefinitionVersion is required and must not be null");
    Objects.requireNonNull(startDate, "startDate is required and must not be null");
    Objects.requireNonNull(state, "state is required and must not be null");
    Objects.requireNonNull(hasIncident, "hasIncident is required and must not be null");
    Objects.requireNonNull(tenantId, "tenantId is required and must not be null");
    Objects.requireNonNull(
        processInstanceKey, "processInstanceKey is required and must not be null");
    Objects.requireNonNull(
        processDefinitionKey, "processDefinitionKey is required and must not be null");
    Objects.requireNonNull(tags, "tags is required and must not be null");
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

  public static String coerceParentProcessInstanceKey(final Object value) {
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
        "parentProcessInstanceKey must be a String or Number, but was "
            + value.getClass().getName());
  }

  public static String coerceParentElementInstanceKey(final Object value) {
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
        "parentElementInstanceKey must be a String or Number, but was "
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

  public static ProcessDefinitionIdStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements ProcessDefinitionIdStep,
          ProcessDefinitionVersionStep,
          StartDateStep,
          StateStep,
          HasIncidentStep,
          TenantIdStep,
          ProcessInstanceKeyStep,
          ProcessDefinitionKeyStep,
          TagsStep,
          OptionalStep {
    private String processDefinitionId;
    private ContractPolicy.FieldPolicy<String> processDefinitionIdPolicy;
    private String processDefinitionName;
    private Integer processDefinitionVersion;
    private ContractPolicy.FieldPolicy<Integer> processDefinitionVersionPolicy;
    private String processDefinitionVersionTag;
    private String startDate;
    private ContractPolicy.FieldPolicy<String> startDatePolicy;
    private String endDate;
    private io.camunda.gateway.protocol.model.ProcessInstanceStateEnum state;
    private ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.ProcessInstanceStateEnum>
        statePolicy;
    private Boolean hasIncident;
    private ContractPolicy.FieldPolicy<Boolean> hasIncidentPolicy;
    private String tenantId;
    private ContractPolicy.FieldPolicy<String> tenantIdPolicy;
    private Object processInstanceKey;
    private ContractPolicy.FieldPolicy<Object> processInstanceKeyPolicy;
    private Object processDefinitionKey;
    private ContractPolicy.FieldPolicy<Object> processDefinitionKeyPolicy;
    private Object parentProcessInstanceKey;
    private Object parentElementInstanceKey;
    private Object rootProcessInstanceKey;
    private java.util.Set<String> tags;
    private ContractPolicy.FieldPolicy<java.util.Set<String>> tagsPolicy;
    private String businessId;

    private Builder() {}

    @Override
    public ProcessDefinitionVersionStep processDefinitionId(
        final String processDefinitionId, final ContractPolicy.FieldPolicy<String> policy) {
      this.processDefinitionId = processDefinitionId;
      this.processDefinitionIdPolicy = policy;
      return this;
    }

    @Override
    public StartDateStep processDefinitionVersion(
        final Integer processDefinitionVersion, final ContractPolicy.FieldPolicy<Integer> policy) {
      this.processDefinitionVersion = processDefinitionVersion;
      this.processDefinitionVersionPolicy = policy;
      return this;
    }

    @Override
    public StateStep startDate(
        final String startDate, final ContractPolicy.FieldPolicy<String> policy) {
      this.startDate = startDate;
      this.startDatePolicy = policy;
      return this;
    }

    @Override
    public HasIncidentStep state(
        final io.camunda.gateway.protocol.model.ProcessInstanceStateEnum state,
        final ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.ProcessInstanceStateEnum>
            policy) {
      this.state = state;
      this.statePolicy = policy;
      return this;
    }

    @Override
    public TenantIdStep hasIncident(
        final Boolean hasIncident, final ContractPolicy.FieldPolicy<Boolean> policy) {
      this.hasIncident = hasIncident;
      this.hasIncidentPolicy = policy;
      return this;
    }

    @Override
    public ProcessInstanceKeyStep tenantId(
        final String tenantId, final ContractPolicy.FieldPolicy<String> policy) {
      this.tenantId = tenantId;
      this.tenantIdPolicy = policy;
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
    public TagsStep processDefinitionKey(
        final Object processDefinitionKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.processDefinitionKey = processDefinitionKey;
      this.processDefinitionKeyPolicy = policy;
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
    public OptionalStep processDefinitionName(final String processDefinitionName) {
      this.processDefinitionName = processDefinitionName;
      return this;
    }

    @Override
    public OptionalStep processDefinitionName(
        final String processDefinitionName, final ContractPolicy.FieldPolicy<String> policy) {
      this.processDefinitionName =
          policy.apply(processDefinitionName, Fields.PROCESS_DEFINITION_NAME, null);
      return this;
    }

    @Override
    public OptionalStep processDefinitionVersionTag(final String processDefinitionVersionTag) {
      this.processDefinitionVersionTag = processDefinitionVersionTag;
      return this;
    }

    @Override
    public OptionalStep processDefinitionVersionTag(
        final String processDefinitionVersionTag, final ContractPolicy.FieldPolicy<String> policy) {
      this.processDefinitionVersionTag =
          policy.apply(processDefinitionVersionTag, Fields.PROCESS_DEFINITION_VERSION_TAG, null);
      return this;
    }

    @Override
    public OptionalStep endDate(final String endDate) {
      this.endDate = endDate;
      return this;
    }

    @Override
    public OptionalStep endDate(
        final String endDate, final ContractPolicy.FieldPolicy<String> policy) {
      this.endDate = policy.apply(endDate, Fields.END_DATE, null);
      return this;
    }

    @Override
    public OptionalStep parentProcessInstanceKey(final String parentProcessInstanceKey) {
      this.parentProcessInstanceKey = parentProcessInstanceKey;
      return this;
    }

    @Override
    public OptionalStep parentProcessInstanceKey(final Object parentProcessInstanceKey) {
      this.parentProcessInstanceKey = parentProcessInstanceKey;
      return this;
    }

    public Builder parentProcessInstanceKey(
        final String parentProcessInstanceKey, final ContractPolicy.FieldPolicy<String> policy) {
      this.parentProcessInstanceKey =
          policy.apply(parentProcessInstanceKey, Fields.PARENT_PROCESS_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep parentProcessInstanceKey(
        final Object parentProcessInstanceKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.parentProcessInstanceKey =
          policy.apply(parentProcessInstanceKey, Fields.PARENT_PROCESS_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep parentElementInstanceKey(final String parentElementInstanceKey) {
      this.parentElementInstanceKey = parentElementInstanceKey;
      return this;
    }

    @Override
    public OptionalStep parentElementInstanceKey(final Object parentElementInstanceKey) {
      this.parentElementInstanceKey = parentElementInstanceKey;
      return this;
    }

    public Builder parentElementInstanceKey(
        final String parentElementInstanceKey, final ContractPolicy.FieldPolicy<String> policy) {
      this.parentElementInstanceKey =
          policy.apply(parentElementInstanceKey, Fields.PARENT_ELEMENT_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep parentElementInstanceKey(
        final Object parentElementInstanceKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.parentElementInstanceKey =
          policy.apply(parentElementInstanceKey, Fields.PARENT_ELEMENT_INSTANCE_KEY, null);
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
    public OptionalStep businessId(final String businessId) {
      this.businessId = businessId;
      return this;
    }

    @Override
    public OptionalStep businessId(
        final String businessId, final ContractPolicy.FieldPolicy<String> policy) {
      this.businessId = policy.apply(businessId, Fields.BUSINESS_ID, null);
      return this;
    }

    @Override
    public GeneratedProcessInstanceStrictContract build() {
      return new GeneratedProcessInstanceStrictContract(
          applyRequiredPolicy(
              this.processDefinitionId,
              this.processDefinitionIdPolicy,
              Fields.PROCESS_DEFINITION_ID),
          this.processDefinitionName,
          applyRequiredPolicy(
              this.processDefinitionVersion,
              this.processDefinitionVersionPolicy,
              Fields.PROCESS_DEFINITION_VERSION),
          this.processDefinitionVersionTag,
          applyRequiredPolicy(this.startDate, this.startDatePolicy, Fields.START_DATE),
          this.endDate,
          applyRequiredPolicy(this.state, this.statePolicy, Fields.STATE),
          applyRequiredPolicy(this.hasIncident, this.hasIncidentPolicy, Fields.HAS_INCIDENT),
          applyRequiredPolicy(this.tenantId, this.tenantIdPolicy, Fields.TENANT_ID),
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
          coerceParentProcessInstanceKey(this.parentProcessInstanceKey),
          coerceParentElementInstanceKey(this.parentElementInstanceKey),
          coerceRootProcessInstanceKey(this.rootProcessInstanceKey),
          applyRequiredPolicy(this.tags, this.tagsPolicy, Fields.TAGS),
          this.businessId);
    }
  }

  public interface ProcessDefinitionIdStep {
    ProcessDefinitionVersionStep processDefinitionId(
        final String processDefinitionId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface ProcessDefinitionVersionStep {
    StartDateStep processDefinitionVersion(
        final Integer processDefinitionVersion, final ContractPolicy.FieldPolicy<Integer> policy);
  }

  public interface StartDateStep {
    StateStep startDate(final String startDate, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface StateStep {
    HasIncidentStep state(
        final io.camunda.gateway.protocol.model.ProcessInstanceStateEnum state,
        final ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.ProcessInstanceStateEnum>
            policy);
  }

  public interface HasIncidentStep {
    TenantIdStep hasIncident(
        final Boolean hasIncident, final ContractPolicy.FieldPolicy<Boolean> policy);
  }

  public interface TenantIdStep {
    ProcessInstanceKeyStep tenantId(
        final String tenantId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface ProcessInstanceKeyStep {
    ProcessDefinitionKeyStep processInstanceKey(
        final Object processInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface ProcessDefinitionKeyStep {
    TagsStep processDefinitionKey(
        final Object processDefinitionKey, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface TagsStep {
    OptionalStep tags(
        final java.util.Set<String> tags,
        final ContractPolicy.FieldPolicy<java.util.Set<String>> policy);
  }

  public interface OptionalStep {
    OptionalStep processDefinitionName(final String processDefinitionName);

    OptionalStep processDefinitionName(
        final String processDefinitionName, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep processDefinitionVersionTag(final String processDefinitionVersionTag);

    OptionalStep processDefinitionVersionTag(
        final String processDefinitionVersionTag, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep endDate(final String endDate);

    OptionalStep endDate(final String endDate, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep parentProcessInstanceKey(final String parentProcessInstanceKey);

    OptionalStep parentProcessInstanceKey(final Object parentProcessInstanceKey);

    OptionalStep parentProcessInstanceKey(
        final String parentProcessInstanceKey, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep parentProcessInstanceKey(
        final Object parentProcessInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep parentElementInstanceKey(final String parentElementInstanceKey);

    OptionalStep parentElementInstanceKey(final Object parentElementInstanceKey);

    OptionalStep parentElementInstanceKey(
        final String parentElementInstanceKey, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep parentElementInstanceKey(
        final Object parentElementInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep rootProcessInstanceKey(final String rootProcessInstanceKey);

    OptionalStep rootProcessInstanceKey(final Object rootProcessInstanceKey);

    OptionalStep rootProcessInstanceKey(
        final String rootProcessInstanceKey, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep rootProcessInstanceKey(
        final Object rootProcessInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep businessId(final String businessId);

    OptionalStep businessId(
        final String businessId, final ContractPolicy.FieldPolicy<String> policy);

    GeneratedProcessInstanceStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_ID =
        ContractPolicy.field("ProcessInstanceResult", "processDefinitionId");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_NAME =
        ContractPolicy.field("ProcessInstanceResult", "processDefinitionName");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_VERSION =
        ContractPolicy.field("ProcessInstanceResult", "processDefinitionVersion");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_VERSION_TAG =
        ContractPolicy.field("ProcessInstanceResult", "processDefinitionVersionTag");
    public static final ContractPolicy.FieldRef START_DATE =
        ContractPolicy.field("ProcessInstanceResult", "startDate");
    public static final ContractPolicy.FieldRef END_DATE =
        ContractPolicy.field("ProcessInstanceResult", "endDate");
    public static final ContractPolicy.FieldRef STATE =
        ContractPolicy.field("ProcessInstanceResult", "state");
    public static final ContractPolicy.FieldRef HAS_INCIDENT =
        ContractPolicy.field("ProcessInstanceResult", "hasIncident");
    public static final ContractPolicy.FieldRef TENANT_ID =
        ContractPolicy.field("ProcessInstanceResult", "tenantId");
    public static final ContractPolicy.FieldRef PROCESS_INSTANCE_KEY =
        ContractPolicy.field("ProcessInstanceResult", "processInstanceKey");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_KEY =
        ContractPolicy.field("ProcessInstanceResult", "processDefinitionKey");
    public static final ContractPolicy.FieldRef PARENT_PROCESS_INSTANCE_KEY =
        ContractPolicy.field("ProcessInstanceResult", "parentProcessInstanceKey");
    public static final ContractPolicy.FieldRef PARENT_ELEMENT_INSTANCE_KEY =
        ContractPolicy.field("ProcessInstanceResult", "parentElementInstanceKey");
    public static final ContractPolicy.FieldRef ROOT_PROCESS_INSTANCE_KEY =
        ContractPolicy.field("ProcessInstanceResult", "rootProcessInstanceKey");
    public static final ContractPolicy.FieldRef TAGS =
        ContractPolicy.field("ProcessInstanceResult", "tags");
    public static final ContractPolicy.FieldRef BUSINESS_ID =
        ContractPolicy.field("ProcessInstanceResult", "businessId");

    private Fields() {}
  }
}
