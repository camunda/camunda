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
import jakarta.annotation.Generated;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedDeploymentResourceStrictContract(
    @JsonProperty("resourceId") String resourceId,
    @JsonProperty("resourceName") String resourceName,
    @JsonProperty("version") Integer version,
    @JsonProperty("tenantId") String tenantId,
    @JsonProperty("resourceKey") String resourceKey) {

  public GeneratedDeploymentResourceStrictContract {
    Objects.requireNonNull(resourceId, "No resourceId provided.");
    Objects.requireNonNull(resourceName, "No resourceName provided.");
    Objects.requireNonNull(version, "No version provided.");
    Objects.requireNonNull(tenantId, "No tenantId provided.");
    Objects.requireNonNull(resourceKey, "No resourceKey provided.");
  }

  public static ResourceIdStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements ResourceIdStep,
          ResourceNameStep,
          VersionStep,
          TenantIdStep,
          ResourceKeyStep,
          OptionalStep {
    private String resourceId;
    private String resourceName;
    private Integer version;
    private String tenantId;
    private String resourceKey;

    private Builder() {}

    @Override
    public ResourceNameStep resourceId(final String resourceId) {
      this.resourceId = resourceId;
      return this;
    }

    @Override
    public VersionStep resourceName(final String resourceName) {
      this.resourceName = resourceName;
      return this;
    }

    @Override
    public TenantIdStep version(final Integer version) {
      this.version = version;
      return this;
    }

    @Override
    public ResourceKeyStep tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    @Override
    public OptionalStep resourceKey(final String resourceKey) {
      this.resourceKey = resourceKey;
      return this;
    }

    @Override
    public GeneratedDeploymentResourceStrictContract build() {
      return new GeneratedDeploymentResourceStrictContract(
          this.resourceId, this.resourceName, this.version, this.tenantId, this.resourceKey);
    }
  }

  public interface ResourceIdStep {
    ResourceNameStep resourceId(final String resourceId);
  }

  public interface ResourceNameStep {
    VersionStep resourceName(final String resourceName);
  }

  public interface VersionStep {
    TenantIdStep version(final Integer version);
  }

  public interface TenantIdStep {
    ResourceKeyStep tenantId(final String tenantId);
  }

  public interface ResourceKeyStep {
    OptionalStep resourceKey(final String resourceKey);
  }

  public interface OptionalStep {
    GeneratedDeploymentResourceStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef RESOURCE_ID =
        ContractPolicy.field("DeploymentResourceResult", "resourceId");
    public static final ContractPolicy.FieldRef RESOURCE_NAME =
        ContractPolicy.field("DeploymentResourceResult", "resourceName");
    public static final ContractPolicy.FieldRef VERSION =
        ContractPolicy.field("DeploymentResourceResult", "version");
    public static final ContractPolicy.FieldRef TENANT_ID =
        ContractPolicy.field("DeploymentResourceResult", "tenantId");
    public static final ContractPolicy.FieldRef RESOURCE_KEY =
        ContractPolicy.field("DeploymentResourceResult", "resourceKey");

    private Fields() {}
  }
}
