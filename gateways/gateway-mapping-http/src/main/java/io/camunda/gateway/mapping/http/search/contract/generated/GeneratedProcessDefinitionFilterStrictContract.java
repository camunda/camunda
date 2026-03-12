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
import org.springframework.lang.Nullable;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedProcessDefinitionFilterStrictContract(
    @Nullable Object name,
    @Nullable Boolean isLatestVersion,
    @Nullable String resourceName,
    @Nullable Integer version,
    @Nullable String versionTag,
    @Nullable Object processDefinitionId,
    @Nullable String tenantId,
    @Nullable String processDefinitionKey,
    @Nullable Boolean hasStartForm) {

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

  public static OptionalStep builder() {
    return new Builder();
  }

  public static final class Builder implements OptionalStep {
    private Object name;
    private Boolean isLatestVersion;
    private String resourceName;
    private Integer version;
    private String versionTag;
    private Object processDefinitionId;
    private String tenantId;
    private Object processDefinitionKey;
    private Boolean hasStartForm;

    private Builder() {}

    @Override
    public OptionalStep name(final Object name) {
      this.name = name;
      return this;
    }

    @Override
    public OptionalStep name(final Object name, final ContractPolicy.FieldPolicy<Object> policy) {
      this.name = policy.apply(name, Fields.NAME, null);
      return this;
    }

    @Override
    public OptionalStep isLatestVersion(final Boolean isLatestVersion) {
      this.isLatestVersion = isLatestVersion;
      return this;
    }

    @Override
    public OptionalStep isLatestVersion(
        final Boolean isLatestVersion, final ContractPolicy.FieldPolicy<Boolean> policy) {
      this.isLatestVersion = policy.apply(isLatestVersion, Fields.IS_LATEST_VERSION, null);
      return this;
    }

    @Override
    public OptionalStep resourceName(final String resourceName) {
      this.resourceName = resourceName;
      return this;
    }

    @Override
    public OptionalStep resourceName(
        final String resourceName, final ContractPolicy.FieldPolicy<String> policy) {
      this.resourceName = policy.apply(resourceName, Fields.RESOURCE_NAME, null);
      return this;
    }

    @Override
    public OptionalStep version(final Integer version) {
      this.version = version;
      return this;
    }

    @Override
    public OptionalStep version(
        final Integer version, final ContractPolicy.FieldPolicy<Integer> policy) {
      this.version = policy.apply(version, Fields.VERSION, null);
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
    public OptionalStep tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    @Override
    public OptionalStep tenantId(
        final String tenantId, final ContractPolicy.FieldPolicy<String> policy) {
      this.tenantId = policy.apply(tenantId, Fields.TENANT_ID, null);
      return this;
    }

    @Override
    public OptionalStep processDefinitionKey(final String processDefinitionKey) {
      this.processDefinitionKey = processDefinitionKey;
      return this;
    }

    @Override
    public OptionalStep processDefinitionKey(final Object processDefinitionKey) {
      this.processDefinitionKey = processDefinitionKey;
      return this;
    }

    public Builder processDefinitionKey(
        final String processDefinitionKey, final ContractPolicy.FieldPolicy<String> policy) {
      this.processDefinitionKey =
          policy.apply(processDefinitionKey, Fields.PROCESS_DEFINITION_KEY, null);
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
    public OptionalStep hasStartForm(final Boolean hasStartForm) {
      this.hasStartForm = hasStartForm;
      return this;
    }

    @Override
    public OptionalStep hasStartForm(
        final Boolean hasStartForm, final ContractPolicy.FieldPolicy<Boolean> policy) {
      this.hasStartForm = policy.apply(hasStartForm, Fields.HAS_START_FORM, null);
      return this;
    }

    @Override
    public GeneratedProcessDefinitionFilterStrictContract build() {
      return new GeneratedProcessDefinitionFilterStrictContract(
          this.name,
          this.isLatestVersion,
          this.resourceName,
          this.version,
          this.versionTag,
          this.processDefinitionId,
          this.tenantId,
          coerceProcessDefinitionKey(this.processDefinitionKey),
          this.hasStartForm);
    }
  }

  public interface OptionalStep {
    OptionalStep name(final Object name);

    OptionalStep name(final Object name, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep isLatestVersion(final Boolean isLatestVersion);

    OptionalStep isLatestVersion(
        final Boolean isLatestVersion, final ContractPolicy.FieldPolicy<Boolean> policy);

    OptionalStep resourceName(final String resourceName);

    OptionalStep resourceName(
        final String resourceName, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep version(final Integer version);

    OptionalStep version(final Integer version, final ContractPolicy.FieldPolicy<Integer> policy);

    OptionalStep versionTag(final String versionTag);

    OptionalStep versionTag(
        final String versionTag, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep processDefinitionId(final Object processDefinitionId);

    OptionalStep processDefinitionId(
        final Object processDefinitionId, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep tenantId(final String tenantId);

    OptionalStep tenantId(final String tenantId, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep processDefinitionKey(final String processDefinitionKey);

    OptionalStep processDefinitionKey(final Object processDefinitionKey);

    OptionalStep processDefinitionKey(
        final String processDefinitionKey, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep processDefinitionKey(
        final Object processDefinitionKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep hasStartForm(final Boolean hasStartForm);

    OptionalStep hasStartForm(
        final Boolean hasStartForm, final ContractPolicy.FieldPolicy<Boolean> policy);

    GeneratedProcessDefinitionFilterStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef NAME =
        ContractPolicy.field("ProcessDefinitionFilter", "name");
    public static final ContractPolicy.FieldRef IS_LATEST_VERSION =
        ContractPolicy.field("ProcessDefinitionFilter", "isLatestVersion");
    public static final ContractPolicy.FieldRef RESOURCE_NAME =
        ContractPolicy.field("ProcessDefinitionFilter", "resourceName");
    public static final ContractPolicy.FieldRef VERSION =
        ContractPolicy.field("ProcessDefinitionFilter", "version");
    public static final ContractPolicy.FieldRef VERSION_TAG =
        ContractPolicy.field("ProcessDefinitionFilter", "versionTag");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_ID =
        ContractPolicy.field("ProcessDefinitionFilter", "processDefinitionId");
    public static final ContractPolicy.FieldRef TENANT_ID =
        ContractPolicy.field("ProcessDefinitionFilter", "tenantId");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_KEY =
        ContractPolicy.field("ProcessDefinitionFilter", "processDefinitionKey");
    public static final ContractPolicy.FieldRef HAS_START_FORM =
        ContractPolicy.field("ProcessDefinitionFilter", "hasStartForm");

    private Fields() {}
  }
}
