/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/process-instances.yaml#/components/schemas/CreateProcessInstanceResult
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.gateway.mapping.http.util.KeyUtil;
import jakarta.annotation.Generated;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;


@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedCreateProcessInstanceStrictContract(
    String processDefinitionId,
    Integer processDefinitionVersion,
    String tenantId,
    java.util.Map<String, Object> variables,
    String processDefinitionKey,
    String processInstanceKey,
    java.util.Set<String> tags,
    @Nullable String businessId
) {

  public GeneratedCreateProcessInstanceStrictContract {
    Objects.requireNonNull(processDefinitionId, "processDefinitionId is required and must not be null");
    Objects.requireNonNull(processDefinitionVersion, "processDefinitionVersion is required and must not be null");
    Objects.requireNonNull(tenantId, "tenantId is required and must not be null");
    Objects.requireNonNull(variables, "variables is required and must not be null");
    Objects.requireNonNull(processDefinitionKey, "processDefinitionKey is required and must not be null");
    Objects.requireNonNull(processInstanceKey, "processInstanceKey is required and must not be null");
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



  public static ProcessDefinitionIdStep builder() {
    return new Builder();
  }

  public static final class Builder implements ProcessDefinitionIdStep, ProcessDefinitionVersionStep, TenantIdStep, VariablesStep, ProcessDefinitionKeyStep, ProcessInstanceKeyStep, TagsStep, OptionalStep {
    private String processDefinitionId;
    private Integer processDefinitionVersion;
    private String tenantId;
    private java.util.Map<String, Object> variables;
    private Object processDefinitionKey;
    private Object processInstanceKey;
    private java.util.Set<String> tags;
    private String businessId;

    private Builder() {}

    @Override
    public ProcessDefinitionVersionStep processDefinitionId(final String processDefinitionId) {
      this.processDefinitionId = processDefinitionId;
      return this;
    }

    @Override
    public TenantIdStep processDefinitionVersion(final Integer processDefinitionVersion) {
      this.processDefinitionVersion = processDefinitionVersion;
      return this;
    }

    @Override
    public VariablesStep tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    @Override
    public ProcessDefinitionKeyStep variables(final java.util.Map<String, Object> variables) {
      this.variables = variables;
      return this;
    }

    @Override
    public ProcessInstanceKeyStep processDefinitionKey(final Object processDefinitionKey) {
      this.processDefinitionKey = processDefinitionKey;
      return this;
    }

    @Override
    public TagsStep processInstanceKey(final Object processInstanceKey) {
      this.processInstanceKey = processInstanceKey;
      return this;
    }

    @Override
    public OptionalStep tags(final java.util.Set<String> tags) {
      this.tags = tags;
      return this;
    }

    @Override
    public OptionalStep businessId(final @Nullable String businessId) {
      this.businessId = businessId;
      return this;
    }

    @Override
    public OptionalStep businessId(final @Nullable String businessId, final ContractPolicy.FieldPolicy<String> policy) {
      this.businessId = policy.apply(businessId, Fields.BUSINESS_ID, null);
      return this;
    }

    @Override
    public GeneratedCreateProcessInstanceStrictContract build() {
      return new GeneratedCreateProcessInstanceStrictContract(
          this.processDefinitionId,
          this.processDefinitionVersion,
          this.tenantId,
          this.variables,
          coerceProcessDefinitionKey(this.processDefinitionKey),
          coerceProcessInstanceKey(this.processInstanceKey),
          this.tags,
          this.businessId);
    }
  }

  public interface ProcessDefinitionIdStep {
    ProcessDefinitionVersionStep processDefinitionId(final String processDefinitionId);
  }

  public interface ProcessDefinitionVersionStep {
    TenantIdStep processDefinitionVersion(final Integer processDefinitionVersion);
  }

  public interface TenantIdStep {
    VariablesStep tenantId(final String tenantId);
  }

  public interface VariablesStep {
    ProcessDefinitionKeyStep variables(final java.util.Map<String, Object> variables);
  }

  public interface ProcessDefinitionKeyStep {
    ProcessInstanceKeyStep processDefinitionKey(final Object processDefinitionKey);
  }

  public interface ProcessInstanceKeyStep {
    TagsStep processInstanceKey(final Object processInstanceKey);
  }

  public interface TagsStep {
    OptionalStep tags(final java.util.Set<String> tags);
  }

  public interface OptionalStep {
  OptionalStep businessId(final @Nullable String businessId);

  OptionalStep businessId(final @Nullable String businessId, final ContractPolicy.FieldPolicy<String> policy);


    GeneratedCreateProcessInstanceStrictContract build();
  }


  public static final class Fields {
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_ID = ContractPolicy.field("CreateProcessInstanceResult", "processDefinitionId");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_VERSION = ContractPolicy.field("CreateProcessInstanceResult", "processDefinitionVersion");
    public static final ContractPolicy.FieldRef TENANT_ID = ContractPolicy.field("CreateProcessInstanceResult", "tenantId");
    public static final ContractPolicy.FieldRef VARIABLES = ContractPolicy.field("CreateProcessInstanceResult", "variables");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_KEY = ContractPolicy.field("CreateProcessInstanceResult", "processDefinitionKey");
    public static final ContractPolicy.FieldRef PROCESS_INSTANCE_KEY = ContractPolicy.field("CreateProcessInstanceResult", "processInstanceKey");
    public static final ContractPolicy.FieldRef TAGS = ContractPolicy.field("CreateProcessInstanceResult", "tags");
    public static final ContractPolicy.FieldRef BUSINESS_ID = ContractPolicy.field("CreateProcessInstanceResult", "businessId");

    private Fields() {}
  }


}
