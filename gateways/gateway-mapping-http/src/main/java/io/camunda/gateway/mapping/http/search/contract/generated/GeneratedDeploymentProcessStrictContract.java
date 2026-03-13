/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/deployments.yaml#/components/schemas/DeploymentProcessResult
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.gateway.mapping.http.util.KeyUtil;
import jakarta.annotation.Generated;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;


@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedDeploymentProcessStrictContract(
    String processDefinitionId,
    Integer processDefinitionVersion,
    String resourceName,
    String tenantId,
    String processDefinitionKey
) {

  public GeneratedDeploymentProcessStrictContract {
    Objects.requireNonNull(processDefinitionId, "processDefinitionId is required and must not be null");
    Objects.requireNonNull(processDefinitionVersion, "processDefinitionVersion is required and must not be null");
    Objects.requireNonNull(resourceName, "resourceName is required and must not be null");
    Objects.requireNonNull(tenantId, "tenantId is required and must not be null");
    Objects.requireNonNull(processDefinitionKey, "processDefinitionKey is required and must not be null");
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



  public static ProcessDefinitionIdStep builder() {
    return new Builder();
  }

  public static final class Builder implements ProcessDefinitionIdStep, ProcessDefinitionVersionStep, ResourceNameStep, TenantIdStep, ProcessDefinitionKeyStep, OptionalStep {
    private String processDefinitionId;
    private Integer processDefinitionVersion;
    private String resourceName;
    private String tenantId;
    private Object processDefinitionKey;

    private Builder() {}

    @Override
    public ProcessDefinitionVersionStep processDefinitionId(final String processDefinitionId) {
      this.processDefinitionId = processDefinitionId;
      return this;
    }

    @Override
    public ResourceNameStep processDefinitionVersion(final Integer processDefinitionVersion) {
      this.processDefinitionVersion = processDefinitionVersion;
      return this;
    }

    @Override
    public TenantIdStep resourceName(final String resourceName) {
      this.resourceName = resourceName;
      return this;
    }

    @Override
    public ProcessDefinitionKeyStep tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    @Override
    public OptionalStep processDefinitionKey(final Object processDefinitionKey) {
      this.processDefinitionKey = processDefinitionKey;
      return this;
    }
    @Override
    public GeneratedDeploymentProcessStrictContract build() {
      return new GeneratedDeploymentProcessStrictContract(
          this.processDefinitionId,
          this.processDefinitionVersion,
          this.resourceName,
          this.tenantId,
          coerceProcessDefinitionKey(this.processDefinitionKey));
    }
  }

  public interface ProcessDefinitionIdStep {
    ProcessDefinitionVersionStep processDefinitionId(final String processDefinitionId);
  }

  public interface ProcessDefinitionVersionStep {
    ResourceNameStep processDefinitionVersion(final Integer processDefinitionVersion);
  }

  public interface ResourceNameStep {
    TenantIdStep resourceName(final String resourceName);
  }

  public interface TenantIdStep {
    ProcessDefinitionKeyStep tenantId(final String tenantId);
  }

  public interface ProcessDefinitionKeyStep {
    OptionalStep processDefinitionKey(final Object processDefinitionKey);
  }

  public interface OptionalStep {
    GeneratedDeploymentProcessStrictContract build();
  }


  public static final class Fields {
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_ID = ContractPolicy.field("DeploymentProcessResult", "processDefinitionId");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_VERSION = ContractPolicy.field("DeploymentProcessResult", "processDefinitionVersion");
    public static final ContractPolicy.FieldRef RESOURCE_NAME = ContractPolicy.field("DeploymentProcessResult", "resourceName");
    public static final ContractPolicy.FieldRef TENANT_ID = ContractPolicy.field("DeploymentProcessResult", "tenantId");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_KEY = ContractPolicy.field("DeploymentProcessResult", "processDefinitionKey");

    private Fields() {}
  }


}
