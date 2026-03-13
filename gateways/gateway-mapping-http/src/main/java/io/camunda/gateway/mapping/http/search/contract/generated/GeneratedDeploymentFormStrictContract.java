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
import org.jspecify.annotations.NullMarked;

@NullMarked
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
    private Integer version;
    private String resourceName;
    private String tenantId;
    private Object formKey;

    private Builder() {}

    @Override
    public VersionStep formId(final String formId) {
      this.formId = formId;
      return this;
    }

    @Override
    public ResourceNameStep version(final Integer version) {
      this.version = version;
      return this;
    }

    @Override
    public TenantIdStep resourceName(final String resourceName) {
      this.resourceName = resourceName;
      return this;
    }

    @Override
    public FormKeyStep tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    @Override
    public OptionalStep formKey(final Object formKey) {
      this.formKey = formKey;
      return this;
    }

    @Override
    public GeneratedDeploymentFormStrictContract build() {
      return new GeneratedDeploymentFormStrictContract(
          this.formId, this.version, this.resourceName, this.tenantId, coerceFormKey(this.formKey));
    }
  }

  public interface FormIdStep {
    VersionStep formId(final String formId);
  }

  public interface VersionStep {
    ResourceNameStep version(final Integer version);
  }

  public interface ResourceNameStep {
    TenantIdStep resourceName(final String resourceName);
  }

  public interface TenantIdStep {
    FormKeyStep tenantId(final String tenantId);
  }

  public interface FormKeyStep {
    OptionalStep formKey(final Object formKey);
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
