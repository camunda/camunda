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
public record GeneratedProcessInstanceStrictContract(
    @JsonProperty("processDefinitionId") String processDefinitionId,
    @JsonProperty("processDefinitionName") @Nullable String processDefinitionName,
    @JsonProperty("processDefinitionVersion") Integer processDefinitionVersion,
    @JsonProperty("processDefinitionVersionTag") @Nullable String processDefinitionVersionTag,
    @JsonProperty("startDate") String startDate,
    @JsonProperty("endDate") @Nullable String endDate,
    @JsonProperty("state")
        io.camunda.gateway.mapping.http.search.contract.generated.GeneratedProcessInstanceStateEnum
            state,
    @JsonProperty("hasIncident") Boolean hasIncident,
    @JsonProperty("tenantId") String tenantId,
    @JsonProperty("processInstanceKey") String processInstanceKey,
    @JsonProperty("processDefinitionKey") String processDefinitionKey,
    @JsonProperty("parentProcessInstanceKey") @Nullable String parentProcessInstanceKey,
    @JsonProperty("parentElementInstanceKey") @Nullable String parentElementInstanceKey,
    @JsonProperty("rootProcessInstanceKey") @Nullable String rootProcessInstanceKey,
    @JsonProperty("tags") java.util.Set<String> tags,
    @JsonProperty("businessId") @Nullable String businessId) {

  public GeneratedProcessInstanceStrictContract {
    Objects.requireNonNull(processDefinitionId, "No processDefinitionId provided.");
    Objects.requireNonNull(processDefinitionVersion, "No processDefinitionVersion provided.");
    Objects.requireNonNull(startDate, "No startDate provided.");
    Objects.requireNonNull(state, "No state provided.");
    Objects.requireNonNull(hasIncident, "No hasIncident provided.");
    Objects.requireNonNull(tenantId, "No tenantId provided.");
    Objects.requireNonNull(processInstanceKey, "No processInstanceKey provided.");
    Objects.requireNonNull(processDefinitionKey, "No processDefinitionKey provided.");
    Objects.requireNonNull(tags, "No tags provided.");
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
    private String processDefinitionName;
    private Integer processDefinitionVersion;
    private String processDefinitionVersionTag;
    private String startDate;
    private String endDate;
    private io.camunda.gateway.mapping.http.search.contract.generated
            .GeneratedProcessInstanceStateEnum
        state;
    private Boolean hasIncident;
    private String tenantId;
    private Object processInstanceKey;
    private Object processDefinitionKey;
    private Object parentProcessInstanceKey;
    private Object parentElementInstanceKey;
    private Object rootProcessInstanceKey;
    private java.util.Set<String> tags;
    private String businessId;

    private Builder() {}

    @Override
    public ProcessDefinitionVersionStep processDefinitionId(final String processDefinitionId) {
      this.processDefinitionId = processDefinitionId;
      return this;
    }

    @Override
    public StartDateStep processDefinitionVersion(final Integer processDefinitionVersion) {
      this.processDefinitionVersion = processDefinitionVersion;
      return this;
    }

    @Override
    public StateStep startDate(final String startDate) {
      this.startDate = startDate;
      return this;
    }

    @Override
    public HasIncidentStep state(
        final io.camunda.gateway.mapping.http.search.contract.generated
                .GeneratedProcessInstanceStateEnum
            state) {
      this.state = state;
      return this;
    }

    @Override
    public TenantIdStep hasIncident(final Boolean hasIncident) {
      this.hasIncident = hasIncident;
      return this;
    }

    @Override
    public ProcessInstanceKeyStep tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    @Override
    public ProcessDefinitionKeyStep processInstanceKey(final Object processInstanceKey) {
      this.processInstanceKey = processInstanceKey;
      return this;
    }

    @Override
    public TagsStep processDefinitionKey(final Object processDefinitionKey) {
      this.processDefinitionKey = processDefinitionKey;
      return this;
    }

    @Override
    public OptionalStep tags(final java.util.Set<String> tags) {
      this.tags = tags;
      return this;
    }

    @Override
    public OptionalStep processDefinitionName(final @Nullable String processDefinitionName) {
      this.processDefinitionName = processDefinitionName;
      return this;
    }

    @Override
    public OptionalStep processDefinitionName(
        final @Nullable String processDefinitionName,
        final ContractPolicy.FieldPolicy<String> policy) {
      this.processDefinitionName =
          policy.apply(processDefinitionName, Fields.PROCESS_DEFINITION_NAME, null);
      return this;
    }

    @Override
    public OptionalStep processDefinitionVersionTag(
        final @Nullable String processDefinitionVersionTag) {
      this.processDefinitionVersionTag = processDefinitionVersionTag;
      return this;
    }

    @Override
    public OptionalStep processDefinitionVersionTag(
        final @Nullable String processDefinitionVersionTag,
        final ContractPolicy.FieldPolicy<String> policy) {
      this.processDefinitionVersionTag =
          policy.apply(processDefinitionVersionTag, Fields.PROCESS_DEFINITION_VERSION_TAG, null);
      return this;
    }

    @Override
    public OptionalStep endDate(final @Nullable String endDate) {
      this.endDate = endDate;
      return this;
    }

    @Override
    public OptionalStep endDate(
        final @Nullable String endDate, final ContractPolicy.FieldPolicy<String> policy) {
      this.endDate = policy.apply(endDate, Fields.END_DATE, null);
      return this;
    }

    @Override
    public OptionalStep parentProcessInstanceKey(final @Nullable String parentProcessInstanceKey) {
      this.parentProcessInstanceKey = parentProcessInstanceKey;
      return this;
    }

    @Override
    public OptionalStep parentProcessInstanceKey(final @Nullable Object parentProcessInstanceKey) {
      this.parentProcessInstanceKey = parentProcessInstanceKey;
      return this;
    }

    public Builder parentProcessInstanceKey(
        final @Nullable String parentProcessInstanceKey,
        final ContractPolicy.FieldPolicy<String> policy) {
      this.parentProcessInstanceKey =
          policy.apply(parentProcessInstanceKey, Fields.PARENT_PROCESS_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep parentProcessInstanceKey(
        final @Nullable Object parentProcessInstanceKey,
        final ContractPolicy.FieldPolicy<Object> policy) {
      this.parentProcessInstanceKey =
          policy.apply(parentProcessInstanceKey, Fields.PARENT_PROCESS_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep parentElementInstanceKey(final @Nullable String parentElementInstanceKey) {
      this.parentElementInstanceKey = parentElementInstanceKey;
      return this;
    }

    @Override
    public OptionalStep parentElementInstanceKey(final @Nullable Object parentElementInstanceKey) {
      this.parentElementInstanceKey = parentElementInstanceKey;
      return this;
    }

    public Builder parentElementInstanceKey(
        final @Nullable String parentElementInstanceKey,
        final ContractPolicy.FieldPolicy<String> policy) {
      this.parentElementInstanceKey =
          policy.apply(parentElementInstanceKey, Fields.PARENT_ELEMENT_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep parentElementInstanceKey(
        final @Nullable Object parentElementInstanceKey,
        final ContractPolicy.FieldPolicy<Object> policy) {
      this.parentElementInstanceKey =
          policy.apply(parentElementInstanceKey, Fields.PARENT_ELEMENT_INSTANCE_KEY, null);
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
    public OptionalStep businessId(final @Nullable String businessId) {
      this.businessId = businessId;
      return this;
    }

    @Override
    public OptionalStep businessId(
        final @Nullable String businessId, final ContractPolicy.FieldPolicy<String> policy) {
      this.businessId = policy.apply(businessId, Fields.BUSINESS_ID, null);
      return this;
    }

    @Override
    public GeneratedProcessInstanceStrictContract build() {
      return new GeneratedProcessInstanceStrictContract(
          this.processDefinitionId,
          this.processDefinitionName,
          this.processDefinitionVersion,
          this.processDefinitionVersionTag,
          this.startDate,
          this.endDate,
          this.state,
          this.hasIncident,
          this.tenantId,
          coerceProcessInstanceKey(this.processInstanceKey),
          coerceProcessDefinitionKey(this.processDefinitionKey),
          coerceParentProcessInstanceKey(this.parentProcessInstanceKey),
          coerceParentElementInstanceKey(this.parentElementInstanceKey),
          coerceRootProcessInstanceKey(this.rootProcessInstanceKey),
          this.tags,
          this.businessId);
    }
  }

  public interface ProcessDefinitionIdStep {
    ProcessDefinitionVersionStep processDefinitionId(final String processDefinitionId);
  }

  public interface ProcessDefinitionVersionStep {
    StartDateStep processDefinitionVersion(final Integer processDefinitionVersion);
  }

  public interface StartDateStep {
    StateStep startDate(final String startDate);
  }

  public interface StateStep {
    HasIncidentStep state(
        final io.camunda.gateway.mapping.http.search.contract.generated
                .GeneratedProcessInstanceStateEnum
            state);
  }

  public interface HasIncidentStep {
    TenantIdStep hasIncident(final Boolean hasIncident);
  }

  public interface TenantIdStep {
    ProcessInstanceKeyStep tenantId(final String tenantId);
  }

  public interface ProcessInstanceKeyStep {
    ProcessDefinitionKeyStep processInstanceKey(final Object processInstanceKey);
  }

  public interface ProcessDefinitionKeyStep {
    TagsStep processDefinitionKey(final Object processDefinitionKey);
  }

  public interface TagsStep {
    OptionalStep tags(final java.util.Set<String> tags);
  }

  public interface OptionalStep {
    OptionalStep processDefinitionName(final @Nullable String processDefinitionName);

    OptionalStep processDefinitionName(
        final @Nullable String processDefinitionName,
        final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep processDefinitionVersionTag(final @Nullable String processDefinitionVersionTag);

    OptionalStep processDefinitionVersionTag(
        final @Nullable String processDefinitionVersionTag,
        final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep endDate(final @Nullable String endDate);

    OptionalStep endDate(
        final @Nullable String endDate, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep parentProcessInstanceKey(final @Nullable String parentProcessInstanceKey);

    OptionalStep parentProcessInstanceKey(final @Nullable Object parentProcessInstanceKey);

    OptionalStep parentProcessInstanceKey(
        final @Nullable String parentProcessInstanceKey,
        final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep parentProcessInstanceKey(
        final @Nullable Object parentProcessInstanceKey,
        final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep parentElementInstanceKey(final @Nullable String parentElementInstanceKey);

    OptionalStep parentElementInstanceKey(final @Nullable Object parentElementInstanceKey);

    OptionalStep parentElementInstanceKey(
        final @Nullable String parentElementInstanceKey,
        final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep parentElementInstanceKey(
        final @Nullable Object parentElementInstanceKey,
        final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep rootProcessInstanceKey(final @Nullable String rootProcessInstanceKey);

    OptionalStep rootProcessInstanceKey(final @Nullable Object rootProcessInstanceKey);

    OptionalStep rootProcessInstanceKey(
        final @Nullable String rootProcessInstanceKey,
        final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep rootProcessInstanceKey(
        final @Nullable Object rootProcessInstanceKey,
        final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep businessId(final @Nullable String businessId);

    OptionalStep businessId(
        final @Nullable String businessId, final ContractPolicy.FieldPolicy<String> policy);

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
