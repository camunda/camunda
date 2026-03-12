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

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedDeploymentFormStrictContract(
    String formId, Integer version, String resourceName, String tenantId, String formKey) {

  public GeneratedDeploymentFormStrictContract {
    Objects.requireNonNull(formId, "formId is required and must not be null");
    Objects.requireNonNull(version, "version is required and must not be null");
    Objects.requireNonNull(resourceName, "resourceName is required and must not be null");
    Objects.requireNonNull(tenantId, "tenantId is required and must not be null");
    Objects.requireNonNull(formKey, "formKey is required and must not be null");
  }

  public static String coerceFormKey(final Object value) {
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
        "formKey must be a String or Number, but was " + value.getClass().getName());
  }

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
  }

  public static FormIdStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements FormIdStep,
          VersionStep,
          ResourceNameStep,
          TenantIdStep,
          FormKeyStep,
          OptionalStep {
    private String formId;
    private ContractPolicy.FieldPolicy<String> formIdPolicy;
    private Integer version;
    private ContractPolicy.FieldPolicy<Integer> versionPolicy;
    private String resourceName;
    private ContractPolicy.FieldPolicy<String> resourceNamePolicy;
    private String tenantId;
    private ContractPolicy.FieldPolicy<String> tenantIdPolicy;
    private Object formKey;
    private ContractPolicy.FieldPolicy<Object> formKeyPolicy;

    private Builder() {}

    @Override
    public VersionStep formId(
        final String formId, final ContractPolicy.FieldPolicy<String> policy) {
      this.formId = formId;
      this.formIdPolicy = policy;
      return this;
    }

    @Override
    public ResourceNameStep version(
        final Integer version, final ContractPolicy.FieldPolicy<Integer> policy) {
      this.version = version;
      this.versionPolicy = policy;
      return this;
    }

    @Override
    public TenantIdStep resourceName(
        final String resourceName, final ContractPolicy.FieldPolicy<String> policy) {
      this.resourceName = resourceName;
      this.resourceNamePolicy = policy;
      return this;
    }

    @Override
    public FormKeyStep tenantId(
        final String tenantId, final ContractPolicy.FieldPolicy<String> policy) {
      this.tenantId = tenantId;
      this.tenantIdPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep formKey(
        final Object formKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.formKey = formKey;
      this.formKeyPolicy = policy;
      return this;
    }

    @Override
    public GeneratedDeploymentFormStrictContract build() {
      return new GeneratedDeploymentFormStrictContract(
          applyRequiredPolicy(this.formId, this.formIdPolicy, Fields.FORM_ID),
          applyRequiredPolicy(this.version, this.versionPolicy, Fields.VERSION),
          applyRequiredPolicy(this.resourceName, this.resourceNamePolicy, Fields.RESOURCE_NAME),
          applyRequiredPolicy(this.tenantId, this.tenantIdPolicy, Fields.TENANT_ID),
          coerceFormKey(applyRequiredPolicy(this.formKey, this.formKeyPolicy, Fields.FORM_KEY)));
    }
  }

  public interface FormIdStep {
    VersionStep formId(final String formId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface VersionStep {
    ResourceNameStep version(
        final Integer version, final ContractPolicy.FieldPolicy<Integer> policy);
  }

  public interface ResourceNameStep {
    TenantIdStep resourceName(
        final String resourceName, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface TenantIdStep {
    FormKeyStep tenantId(final String tenantId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface FormKeyStep {
    OptionalStep formKey(final Object formKey, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface OptionalStep {
    GeneratedDeploymentFormStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef FORM_ID =
        ContractPolicy.field("DeploymentFormResult", "formId");
    public static final ContractPolicy.FieldRef VERSION =
        ContractPolicy.field("DeploymentFormResult", "version");
    public static final ContractPolicy.FieldRef RESOURCE_NAME =
        ContractPolicy.field("DeploymentFormResult", "resourceName");
    public static final ContractPolicy.FieldRef TENANT_ID =
        ContractPolicy.field("DeploymentFormResult", "tenantId");
    public static final ContractPolicy.FieldRef FORM_KEY =
        ContractPolicy.field("DeploymentFormResult", "formKey");

    private Fields() {}
  }
}
