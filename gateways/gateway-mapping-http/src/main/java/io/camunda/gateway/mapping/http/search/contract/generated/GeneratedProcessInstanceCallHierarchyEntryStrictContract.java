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
import io.camunda.gateway.mapping.http.util.KeyUtil;
import jakarta.annotation.Generated;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedProcessInstanceCallHierarchyEntryStrictContract(
    @JsonProperty("processInstanceKey") String processInstanceKey,
    @JsonProperty("processDefinitionKey") String processDefinitionKey,
    @JsonProperty("processDefinitionName") String processDefinitionName) {

  public GeneratedProcessInstanceCallHierarchyEntryStrictContract {
    Objects.requireNonNull(processInstanceKey, "No processInstanceKey provided.");
    Objects.requireNonNull(processDefinitionKey, "No processDefinitionKey provided.");
    Objects.requireNonNull(processDefinitionName, "No processDefinitionName provided.");
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

  public static ProcessInstanceKeyStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements ProcessInstanceKeyStep,
          ProcessDefinitionKeyStep,
          ProcessDefinitionNameStep,
          OptionalStep {
    private Object processInstanceKey;
    private Object processDefinitionKey;
    private String processDefinitionName;

    private Builder() {}

    @Override
    public ProcessDefinitionKeyStep processInstanceKey(final Object processInstanceKey) {
      this.processInstanceKey = processInstanceKey;
      return this;
    }

    @Override
    public ProcessDefinitionNameStep processDefinitionKey(final Object processDefinitionKey) {
      this.processDefinitionKey = processDefinitionKey;
      return this;
    }

    @Override
    public OptionalStep processDefinitionName(final String processDefinitionName) {
      this.processDefinitionName = processDefinitionName;
      return this;
    }

    @Override
    public GeneratedProcessInstanceCallHierarchyEntryStrictContract build() {
      return new GeneratedProcessInstanceCallHierarchyEntryStrictContract(
          coerceProcessInstanceKey(this.processInstanceKey),
          coerceProcessDefinitionKey(this.processDefinitionKey),
          this.processDefinitionName);
    }
  }

  public interface ProcessInstanceKeyStep {
    ProcessDefinitionKeyStep processInstanceKey(final Object processInstanceKey);
  }

  public interface ProcessDefinitionKeyStep {
    ProcessDefinitionNameStep processDefinitionKey(final Object processDefinitionKey);
  }

  public interface ProcessDefinitionNameStep {
    OptionalStep processDefinitionName(final String processDefinitionName);
  }

  public interface OptionalStep {
    GeneratedProcessInstanceCallHierarchyEntryStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef PROCESS_INSTANCE_KEY =
        ContractPolicy.field("ProcessInstanceCallHierarchyEntry", "processInstanceKey");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_KEY =
        ContractPolicy.field("ProcessInstanceCallHierarchyEntry", "processDefinitionKey");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_NAME =
        ContractPolicy.field("ProcessInstanceCallHierarchyEntry", "processDefinitionName");

    private Fields() {}
  }
}
