/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/process-instances.yaml#/components/schemas/ProcessInstanceSequenceFlowResult
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
public record GeneratedProcessInstanceSequenceFlowStrictContract(
    String sequenceFlowId,
    String processInstanceKey,
    @Nullable String rootProcessInstanceKey,
    String processDefinitionKey,
    String processDefinitionId,
    String elementId,
    String tenantId
) {

  public GeneratedProcessInstanceSequenceFlowStrictContract {
    Objects.requireNonNull(sequenceFlowId, "sequenceFlowId is required and must not be null");
    Objects.requireNonNull(processInstanceKey, "processInstanceKey is required and must not be null");
    Objects.requireNonNull(processDefinitionKey, "processDefinitionKey is required and must not be null");
    Objects.requireNonNull(processDefinitionId, "processDefinitionId is required and must not be null");
    Objects.requireNonNull(elementId, "elementId is required and must not be null");
    Objects.requireNonNull(tenantId, "tenantId is required and must not be null");
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



  public static SequenceFlowIdStep builder() {
    return new Builder();
  }

  public static final class Builder implements SequenceFlowIdStep, ProcessInstanceKeyStep, ProcessDefinitionKeyStep, ProcessDefinitionIdStep, ElementIdStep, TenantIdStep, OptionalStep {
    private String sequenceFlowId;
    private Object processInstanceKey;
    private Object rootProcessInstanceKey;
    private Object processDefinitionKey;
    private String processDefinitionId;
    private String elementId;
    private String tenantId;

    private Builder() {}

    @Override
    public ProcessInstanceKeyStep sequenceFlowId(final String sequenceFlowId) {
      this.sequenceFlowId = sequenceFlowId;
      return this;
    }

    @Override
    public ProcessDefinitionKeyStep processInstanceKey(final Object processInstanceKey) {
      this.processInstanceKey = processInstanceKey;
      return this;
    }

    @Override
    public ProcessDefinitionIdStep processDefinitionKey(final Object processDefinitionKey) {
      this.processDefinitionKey = processDefinitionKey;
      return this;
    }

    @Override
    public ElementIdStep processDefinitionId(final String processDefinitionId) {
      this.processDefinitionId = processDefinitionId;
      return this;
    }

    @Override
    public TenantIdStep elementId(final String elementId) {
      this.elementId = elementId;
      return this;
    }

    @Override
    public OptionalStep tenantId(final String tenantId) {
      this.tenantId = tenantId;
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

    public Builder rootProcessInstanceKey(final @Nullable String rootProcessInstanceKey, final ContractPolicy.FieldPolicy<String> policy) {
      this.rootProcessInstanceKey = policy.apply(rootProcessInstanceKey, Fields.ROOT_PROCESS_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep rootProcessInstanceKey(final @Nullable Object rootProcessInstanceKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.rootProcessInstanceKey = policy.apply(rootProcessInstanceKey, Fields.ROOT_PROCESS_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public GeneratedProcessInstanceSequenceFlowStrictContract build() {
      return new GeneratedProcessInstanceSequenceFlowStrictContract(
          this.sequenceFlowId,
          coerceProcessInstanceKey(this.processInstanceKey),
          coerceRootProcessInstanceKey(this.rootProcessInstanceKey),
          coerceProcessDefinitionKey(this.processDefinitionKey),
          this.processDefinitionId,
          this.elementId,
          this.tenantId);
    }
  }

  public interface SequenceFlowIdStep {
    ProcessInstanceKeyStep sequenceFlowId(final String sequenceFlowId);
  }

  public interface ProcessInstanceKeyStep {
    ProcessDefinitionKeyStep processInstanceKey(final Object processInstanceKey);
  }

  public interface ProcessDefinitionKeyStep {
    ProcessDefinitionIdStep processDefinitionKey(final Object processDefinitionKey);
  }

  public interface ProcessDefinitionIdStep {
    ElementIdStep processDefinitionId(final String processDefinitionId);
  }

  public interface ElementIdStep {
    TenantIdStep elementId(final String elementId);
  }

  public interface TenantIdStep {
    OptionalStep tenantId(final String tenantId);
  }

  public interface OptionalStep {
  OptionalStep rootProcessInstanceKey(final @Nullable String rootProcessInstanceKey);

  OptionalStep rootProcessInstanceKey(final @Nullable Object rootProcessInstanceKey);

  OptionalStep rootProcessInstanceKey(final @Nullable String rootProcessInstanceKey, final ContractPolicy.FieldPolicy<String> policy);

  OptionalStep rootProcessInstanceKey(final @Nullable Object rootProcessInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);


    GeneratedProcessInstanceSequenceFlowStrictContract build();
  }


  public static final class Fields {
    public static final ContractPolicy.FieldRef SEQUENCE_FLOW_ID = ContractPolicy.field("ProcessInstanceSequenceFlowResult", "sequenceFlowId");
    public static final ContractPolicy.FieldRef PROCESS_INSTANCE_KEY = ContractPolicy.field("ProcessInstanceSequenceFlowResult", "processInstanceKey");
    public static final ContractPolicy.FieldRef ROOT_PROCESS_INSTANCE_KEY = ContractPolicy.field("ProcessInstanceSequenceFlowResult", "rootProcessInstanceKey");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_KEY = ContractPolicy.field("ProcessInstanceSequenceFlowResult", "processDefinitionKey");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_ID = ContractPolicy.field("ProcessInstanceSequenceFlowResult", "processDefinitionId");
    public static final ContractPolicy.FieldRef ELEMENT_ID = ContractPolicy.field("ProcessInstanceSequenceFlowResult", "elementId");
    public static final ContractPolicy.FieldRef TENANT_ID = ContractPolicy.field("ProcessInstanceSequenceFlowResult", "tenantId");

    private Fields() {}
  }


}
