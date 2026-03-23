/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/deployments.yaml#/components/schemas/DeploymentFormResult
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.gateway.mapping.http.util.KeyUtil;
import jakarta.annotation.Generated;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedDeploymentFormStrictContract(
    @JsonProperty("formId") String formId,
    @JsonProperty("version") Integer version,
    @JsonProperty("resourceName") String resourceName,
    @JsonProperty("tenantId") String tenantId,
    @JsonProperty("formKey") String formKey) {

  public GeneratedDeploymentFormStrictContract {
    Objects.requireNonNull(formId, "No formId provided.");
    Objects.requireNonNull(version, "No version provided.");
    Objects.requireNonNull(resourceName, "No resourceName provided.");
    Objects.requireNonNull(tenantId, "No tenantId provided.");
    Objects.requireNonNull(formKey, "No formKey provided.");
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
