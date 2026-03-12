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
public record GeneratedProcessDefinitionStrictContract(
    @Nullable String name,
    String resourceName,
    Integer version,
    @Nullable String versionTag,
    String processDefinitionId,
    String tenantId,
    String processDefinitionKey,
    Boolean hasStartForm) {

  public GeneratedProcessDefinitionStrictContract {
    Objects.requireNonNull(resourceName, "resourceName is required and must not be null");
    Objects.requireNonNull(version, "version is required and must not be null");
    Objects.requireNonNull(
        processDefinitionId, "processDefinitionId is required and must not be null");
    Objects.requireNonNull(tenantId, "tenantId is required and must not be null");
    Objects.requireNonNull(
        processDefinitionKey, "processDefinitionKey is required and must not be null");
    Objects.requireNonNull(hasStartForm, "hasStartForm is required and must not be null");
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

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
  }

  public static ResourceNameStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements ResourceNameStep,
          VersionStep,
          ProcessDefinitionIdStep,
          TenantIdStep,
          ProcessDefinitionKeyStep,
          HasStartFormStep,
          OptionalStep {
    private String name;
    private String resourceName;
    private ContractPolicy.FieldPolicy<String> resourceNamePolicy;
    private Integer version;
    private ContractPolicy.FieldPolicy<Integer> versionPolicy;
    private String versionTag;
    private String processDefinitionId;
    private ContractPolicy.FieldPolicy<String> processDefinitionIdPolicy;
    private String tenantId;
    private ContractPolicy.FieldPolicy<String> tenantIdPolicy;
    private Object processDefinitionKey;
    private ContractPolicy.FieldPolicy<Object> processDefinitionKeyPolicy;
    private Boolean hasStartForm;
    private ContractPolicy.FieldPolicy<Boolean> hasStartFormPolicy;

    private Builder() {}

    @Override
    public VersionStep resourceName(
        final String resourceName, final ContractPolicy.FieldPolicy<String> policy) {
      this.resourceName = resourceName;
      this.resourceNamePolicy = policy;
      return this;
    }

    @Override
    public ProcessDefinitionIdStep version(
        final Integer version, final ContractPolicy.FieldPolicy<Integer> policy) {
      this.version = version;
      this.versionPolicy = policy;
      return this;
    }

    @Override
    public TenantIdStep processDefinitionId(
        final String processDefinitionId, final ContractPolicy.FieldPolicy<String> policy) {
      this.processDefinitionId = processDefinitionId;
      this.processDefinitionIdPolicy = policy;
      return this;
    }

    @Override
    public ProcessDefinitionKeyStep tenantId(
        final String tenantId, final ContractPolicy.FieldPolicy<String> policy) {
      this.tenantId = tenantId;
      this.tenantIdPolicy = policy;
      return this;
    }

    @Override
    public HasStartFormStep processDefinitionKey(
        final Object processDefinitionKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.processDefinitionKey = processDefinitionKey;
      this.processDefinitionKeyPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep hasStartForm(
        final Boolean hasStartForm, final ContractPolicy.FieldPolicy<Boolean> policy) {
      this.hasStartForm = hasStartForm;
      this.hasStartFormPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep name(final String name) {
      this.name = name;
      return this;
    }

    @Override
    public OptionalStep name(final String name, final ContractPolicy.FieldPolicy<String> policy) {
      this.name = policy.apply(name, Fields.NAME, null);
      return this;
    }

    @Override
    public OptionalStep versionTag(final String versionTag) {
      this.versionTag = versionTag;
      return this;
    }

    @Override
    public OptionalStep versionTag(
        final String versionTag, final ContractPolicy.FieldPolicy<String> policy) {
      this.versionTag = policy.apply(versionTag, Fields.VERSION_TAG, null);
      return this;
    }

    @Override
    public GeneratedProcessDefinitionStrictContract build() {
      return new GeneratedProcessDefinitionStrictContract(
          this.name,
          applyRequiredPolicy(this.resourceName, this.resourceNamePolicy, Fields.RESOURCE_NAME),
          applyRequiredPolicy(this.version, this.versionPolicy, Fields.VERSION),
          this.versionTag,
          applyRequiredPolicy(
              this.processDefinitionId,
              this.processDefinitionIdPolicy,
              Fields.PROCESS_DEFINITION_ID),
          applyRequiredPolicy(this.tenantId, this.tenantIdPolicy, Fields.TENANT_ID),
          coerceProcessDefinitionKey(
              applyRequiredPolicy(
                  this.processDefinitionKey,
                  this.processDefinitionKeyPolicy,
                  Fields.PROCESS_DEFINITION_KEY)),
          applyRequiredPolicy(this.hasStartForm, this.hasStartFormPolicy, Fields.HAS_START_FORM));
    }
  }

  public interface ResourceNameStep {
    VersionStep resourceName(
        final String resourceName, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface VersionStep {
    ProcessDefinitionIdStep version(
        final Integer version, final ContractPolicy.FieldPolicy<Integer> policy);
  }

  public interface ProcessDefinitionIdStep {
    TenantIdStep processDefinitionId(
        final String processDefinitionId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface TenantIdStep {
    ProcessDefinitionKeyStep tenantId(
        final String tenantId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface ProcessDefinitionKeyStep {
    HasStartFormStep processDefinitionKey(
        final Object processDefinitionKey, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface HasStartFormStep {
    OptionalStep hasStartForm(
        final Boolean hasStartForm, final ContractPolicy.FieldPolicy<Boolean> policy);
  }

  public interface OptionalStep {
    OptionalStep name(final String name);

    OptionalStep name(final String name, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep versionTag(final String versionTag);

    OptionalStep versionTag(
        final String versionTag, final ContractPolicy.FieldPolicy<String> policy);

    GeneratedProcessDefinitionStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef NAME =
        ContractPolicy.field("ProcessDefinitionResult", "name");
    public static final ContractPolicy.FieldRef RESOURCE_NAME =
        ContractPolicy.field("ProcessDefinitionResult", "resourceName");
    public static final ContractPolicy.FieldRef VERSION =
        ContractPolicy.field("ProcessDefinitionResult", "version");
    public static final ContractPolicy.FieldRef VERSION_TAG =
        ContractPolicy.field("ProcessDefinitionResult", "versionTag");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_ID =
        ContractPolicy.field("ProcessDefinitionResult", "processDefinitionId");
    public static final ContractPolicy.FieldRef TENANT_ID =
        ContractPolicy.field("ProcessDefinitionResult", "tenantId");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_KEY =
        ContractPolicy.field("ProcessDefinitionResult", "processDefinitionKey");
    public static final ContractPolicy.FieldRef HAS_START_FORM =
        ContractPolicy.field("ProcessDefinitionResult", "hasStartForm");

    private Fields() {}
  }
}
