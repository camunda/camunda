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
public record GeneratedCreateProcessInstanceStrictContract(
    String processDefinitionId,
    Integer processDefinitionVersion,
    String tenantId,
    java.util.Map<String, Object> variables,
    String processDefinitionKey,
    String processInstanceKey,
    java.util.Set<String> tags,
    @Nullable String businessId) {

  public GeneratedCreateProcessInstanceStrictContract {
    Objects.requireNonNull(
        processDefinitionId, "processDefinitionId is required and must not be null");
    Objects.requireNonNull(
        processDefinitionVersion, "processDefinitionVersion is required and must not be null");
    Objects.requireNonNull(tenantId, "tenantId is required and must not be null");
    Objects.requireNonNull(variables, "variables is required and must not be null");
    Objects.requireNonNull(
        processDefinitionKey, "processDefinitionKey is required and must not be null");
    Objects.requireNonNull(
        processInstanceKey, "processInstanceKey is required and must not be null");
    Objects.requireNonNull(tags, "tags is required and must not be null");
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
          TenantIdStep,
          VariablesStep,
          ProcessDefinitionKeyStep,
          ProcessInstanceKeyStep,
          TagsStep,
          OptionalStep {
    private String processDefinitionId;
    private ContractPolicy.FieldPolicy<String> processDefinitionIdPolicy;
    private Integer processDefinitionVersion;
    private ContractPolicy.FieldPolicy<Integer> processDefinitionVersionPolicy;
    private String tenantId;
    private ContractPolicy.FieldPolicy<String> tenantIdPolicy;
    private java.util.Map<String, Object> variables;
    private ContractPolicy.FieldPolicy<java.util.Map<String, Object>> variablesPolicy;
    private Object processDefinitionKey;
    private ContractPolicy.FieldPolicy<Object> processDefinitionKeyPolicy;
    private Object processInstanceKey;
    private ContractPolicy.FieldPolicy<Object> processInstanceKeyPolicy;
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
    public TenantIdStep processDefinitionVersion(
        final Integer processDefinitionVersion, final ContractPolicy.FieldPolicy<Integer> policy) {
      this.processDefinitionVersion = processDefinitionVersion;
      this.processDefinitionVersionPolicy = policy;
      return this;
    }

    @Override
    public VariablesStep tenantId(
        final String tenantId, final ContractPolicy.FieldPolicy<String> policy) {
      this.tenantId = tenantId;
      this.tenantIdPolicy = policy;
      return this;
    }

    @Override
    public ProcessDefinitionKeyStep variables(
        final java.util.Map<String, Object> variables,
        final ContractPolicy.FieldPolicy<java.util.Map<String, Object>> policy) {
      this.variables = variables;
      this.variablesPolicy = policy;
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
    public TagsStep processInstanceKey(
        final Object processInstanceKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.processInstanceKey = processInstanceKey;
      this.processInstanceKeyPolicy = policy;
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
    public GeneratedCreateProcessInstanceStrictContract build() {
      return new GeneratedCreateProcessInstanceStrictContract(
          applyRequiredPolicy(
              this.processDefinitionId,
              this.processDefinitionIdPolicy,
              Fields.PROCESS_DEFINITION_ID),
          applyRequiredPolicy(
              this.processDefinitionVersion,
              this.processDefinitionVersionPolicy,
              Fields.PROCESS_DEFINITION_VERSION),
          applyRequiredPolicy(this.tenantId, this.tenantIdPolicy, Fields.TENANT_ID),
          applyRequiredPolicy(this.variables, this.variablesPolicy, Fields.VARIABLES),
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
          applyRequiredPolicy(this.tags, this.tagsPolicy, Fields.TAGS),
          this.businessId);
    }
  }

  public interface ProcessDefinitionIdStep {
    ProcessDefinitionVersionStep processDefinitionId(
        final String processDefinitionId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface ProcessDefinitionVersionStep {
    TenantIdStep processDefinitionVersion(
        final Integer processDefinitionVersion, final ContractPolicy.FieldPolicy<Integer> policy);
  }

  public interface TenantIdStep {
    VariablesStep tenantId(final String tenantId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface VariablesStep {
    ProcessDefinitionKeyStep variables(
        final java.util.Map<String, Object> variables,
        final ContractPolicy.FieldPolicy<java.util.Map<String, Object>> policy);
  }

  public interface ProcessDefinitionKeyStep {
    ProcessInstanceKeyStep processDefinitionKey(
        final Object processDefinitionKey, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface ProcessInstanceKeyStep {
    TagsStep processInstanceKey(
        final Object processInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface TagsStep {
    OptionalStep tags(
        final java.util.Set<String> tags,
        final ContractPolicy.FieldPolicy<java.util.Set<String>> policy);
  }

  public interface OptionalStep {
    OptionalStep businessId(final String businessId);

    OptionalStep businessId(
        final String businessId, final ContractPolicy.FieldPolicy<String> policy);

    GeneratedCreateProcessInstanceStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_ID =
        ContractPolicy.field("CreateProcessInstanceResult", "processDefinitionId");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_VERSION =
        ContractPolicy.field("CreateProcessInstanceResult", "processDefinitionVersion");
    public static final ContractPolicy.FieldRef TENANT_ID =
        ContractPolicy.field("CreateProcessInstanceResult", "tenantId");
    public static final ContractPolicy.FieldRef VARIABLES =
        ContractPolicy.field("CreateProcessInstanceResult", "variables");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_KEY =
        ContractPolicy.field("CreateProcessInstanceResult", "processDefinitionKey");
    public static final ContractPolicy.FieldRef PROCESS_INSTANCE_KEY =
        ContractPolicy.field("CreateProcessInstanceResult", "processInstanceKey");
    public static final ContractPolicy.FieldRef TAGS =
        ContractPolicy.field("CreateProcessInstanceResult", "tags");
    public static final ContractPolicy.FieldRef BUSINESS_ID =
        ContractPolicy.field("CreateProcessInstanceResult", "businessId");

    private Fields() {}
  }
}
