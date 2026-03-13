/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import jakarta.annotation.Generated;
import java.util.ArrayList;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedProcessInstanceCreationInstructionByIdStrictContract(
    String processDefinitionId,
    @Nullable Integer processDefinitionVersion,
    java.util.@Nullable Map<String, Object> variables,
    @Nullable String tenantId,
    @Nullable Long operationReference,
    java.util.@Nullable List<GeneratedProcessInstanceCreationStartInstructionStrictContract>
        startInstructions,
    java.util.@Nullable List<Object> runtimeInstructions,
    @Nullable Boolean awaitCompletion,
    java.util.@Nullable List<String> fetchVariables,
    @Nullable Long requestTimeout,
    java.util.@Nullable Set<String> tags,
    @Nullable String businessId) {

  public GeneratedProcessInstanceCreationInstructionByIdStrictContract {
    Objects.requireNonNull(
        processDefinitionId, "processDefinitionId is required and must not be null");
  }

  public static java.util.List<GeneratedProcessInstanceCreationStartInstructionStrictContract>
      coerceStartInstructions(final Object value) {
    if (value == null) {
      return null;
    }
    if (!(value instanceof java.util.List<?> listValue)) {
      throw new IllegalArgumentException(
          "startInstructions must be a List of GeneratedProcessInstanceCreationStartInstructionStrictContract, but was "
              + value.getClass().getName());
    }

    final var result =
        new ArrayList<GeneratedProcessInstanceCreationStartInstructionStrictContract>(
            listValue.size());
    for (final var item : listValue) {
      if (item == null) {
        result.add(null);
      } else if (item
          instanceof GeneratedProcessInstanceCreationStartInstructionStrictContract strictItem) {
        result.add(strictItem);

      } else {
        throw new IllegalArgumentException(
            "startInstructions must contain only GeneratedProcessInstanceCreationStartInstructionStrictContract items, but got "
                + item.getClass().getName());
      }
    }
    return java.util.List.copyOf(result);
  }

  public static ProcessDefinitionIdStep builder() {
    return new Builder();
  }

  public static final class Builder implements ProcessDefinitionIdStep, OptionalStep {
    private String processDefinitionId;
    private Integer processDefinitionVersion;
    private java.util.Map<String, Object> variables;
    private String tenantId;
    private Long operationReference;
    private Object startInstructions;
    private java.util.List<Object> runtimeInstructions;
    private Boolean awaitCompletion;
    private java.util.List<String> fetchVariables;
    private Long requestTimeout;
    private java.util.Set<String> tags;
    private String businessId;

    private Builder() {}

    @Override
    public OptionalStep processDefinitionId(final String processDefinitionId) {
      this.processDefinitionId = processDefinitionId;
      return this;
    }

    @Override
    public OptionalStep processDefinitionVersion(final @Nullable Integer processDefinitionVersion) {
      this.processDefinitionVersion = processDefinitionVersion;
      return this;
    }

    @Override
    public OptionalStep processDefinitionVersion(
        final @Nullable Integer processDefinitionVersion,
        final ContractPolicy.FieldPolicy<Integer> policy) {
      this.processDefinitionVersion =
          policy.apply(processDefinitionVersion, Fields.PROCESS_DEFINITION_VERSION, null);
      return this;
    }

    @Override
    public OptionalStep variables(final java.util.@Nullable Map<String, Object> variables) {
      this.variables = variables;
      return this;
    }

    @Override
    public OptionalStep variables(
        final java.util.@Nullable Map<String, Object> variables,
        final ContractPolicy.FieldPolicy<java.util.Map<String, Object>> policy) {
      this.variables = policy.apply(variables, Fields.VARIABLES, null);
      return this;
    }

    @Override
    public OptionalStep tenantId(final @Nullable String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    @Override
    public OptionalStep tenantId(
        final @Nullable String tenantId, final ContractPolicy.FieldPolicy<String> policy) {
      this.tenantId = policy.apply(tenantId, Fields.TENANT_ID, null);
      return this;
    }

    @Override
    public OptionalStep operationReference(final @Nullable Long operationReference) {
      this.operationReference = operationReference;
      return this;
    }

    @Override
    public OptionalStep operationReference(
        final @Nullable Long operationReference, final ContractPolicy.FieldPolicy<Long> policy) {
      this.operationReference = policy.apply(operationReference, Fields.OPERATION_REFERENCE, null);
      return this;
    }

    @Override
    public OptionalStep startInstructions(
        final java.util.@Nullable List<
                GeneratedProcessInstanceCreationStartInstructionStrictContract>
            startInstructions) {
      this.startInstructions = startInstructions;
      return this;
    }

    @Override
    public OptionalStep startInstructions(final @Nullable Object startInstructions) {
      this.startInstructions = startInstructions;
      return this;
    }

    public Builder startInstructions(
        final java.util.@Nullable List<
                GeneratedProcessInstanceCreationStartInstructionStrictContract>
            startInstructions,
        final ContractPolicy.FieldPolicy<
                java.util.List<GeneratedProcessInstanceCreationStartInstructionStrictContract>>
            policy) {
      this.startInstructions = policy.apply(startInstructions, Fields.START_INSTRUCTIONS, null);
      return this;
    }

    @Override
    public OptionalStep startInstructions(
        final @Nullable Object startInstructions, final ContractPolicy.FieldPolicy<Object> policy) {
      this.startInstructions = policy.apply(startInstructions, Fields.START_INSTRUCTIONS, null);
      return this;
    }

    @Override
    public OptionalStep runtimeInstructions(
        final java.util.@Nullable List<Object> runtimeInstructions) {
      this.runtimeInstructions = runtimeInstructions;
      return this;
    }

    @Override
    public OptionalStep runtimeInstructions(
        final java.util.@Nullable List<Object> runtimeInstructions,
        final ContractPolicy.FieldPolicy<java.util.List<Object>> policy) {
      this.runtimeInstructions =
          policy.apply(runtimeInstructions, Fields.RUNTIME_INSTRUCTIONS, null);
      return this;
    }

    @Override
    public OptionalStep awaitCompletion(final @Nullable Boolean awaitCompletion) {
      this.awaitCompletion = awaitCompletion;
      return this;
    }

    @Override
    public OptionalStep awaitCompletion(
        final @Nullable Boolean awaitCompletion, final ContractPolicy.FieldPolicy<Boolean> policy) {
      this.awaitCompletion = policy.apply(awaitCompletion, Fields.AWAIT_COMPLETION, null);
      return this;
    }

    @Override
    public OptionalStep fetchVariables(final java.util.@Nullable List<String> fetchVariables) {
      this.fetchVariables = fetchVariables;
      return this;
    }

    @Override
    public OptionalStep fetchVariables(
        final java.util.@Nullable List<String> fetchVariables,
        final ContractPolicy.FieldPolicy<java.util.List<String>> policy) {
      this.fetchVariables = policy.apply(fetchVariables, Fields.FETCH_VARIABLES, null);
      return this;
    }

    @Override
    public OptionalStep requestTimeout(final @Nullable Long requestTimeout) {
      this.requestTimeout = requestTimeout;
      return this;
    }

    @Override
    public OptionalStep requestTimeout(
        final @Nullable Long requestTimeout, final ContractPolicy.FieldPolicy<Long> policy) {
      this.requestTimeout = policy.apply(requestTimeout, Fields.REQUEST_TIMEOUT, null);
      return this;
    }

    @Override
    public OptionalStep tags(final java.util.@Nullable Set<String> tags) {
      this.tags = tags;
      return this;
    }

    @Override
    public OptionalStep tags(
        final java.util.@Nullable Set<String> tags,
        final ContractPolicy.FieldPolicy<java.util.Set<String>> policy) {
      this.tags = policy.apply(tags, Fields.TAGS, null);
      return this;
    }

    @Override
    public OptionalStep businessId(final @Nullable String businessId) {
      this.businessId = businessId;
      return this;
    }

    @Override
    public OptionalStep businessId(
        final @Nullable String businessId, final ContractPolicy.FieldPolicy<String> policy) {
      this.businessId = policy.apply(businessId, Fields.BUSINESS_ID, null);
      return this;
    }

    @Override
    public GeneratedProcessInstanceCreationInstructionByIdStrictContract build() {
      return new GeneratedProcessInstanceCreationInstructionByIdStrictContract(
          this.processDefinitionId,
          this.processDefinitionVersion,
          this.variables,
          this.tenantId,
          this.operationReference,
          coerceStartInstructions(this.startInstructions),
          this.runtimeInstructions,
          this.awaitCompletion,
          this.fetchVariables,
          this.requestTimeout,
          this.tags,
          this.businessId);
    }
  }

  public interface ProcessDefinitionIdStep {
    OptionalStep processDefinitionId(final String processDefinitionId);
  }

  public interface OptionalStep {
    OptionalStep processDefinitionVersion(final @Nullable Integer processDefinitionVersion);

    OptionalStep processDefinitionVersion(
        final @Nullable Integer processDefinitionVersion,
        final ContractPolicy.FieldPolicy<Integer> policy);

    OptionalStep variables(final java.util.@Nullable Map<String, Object> variables);

    OptionalStep variables(
        final java.util.@Nullable Map<String, Object> variables,
        final ContractPolicy.FieldPolicy<java.util.Map<String, Object>> policy);

    OptionalStep tenantId(final @Nullable String tenantId);

    OptionalStep tenantId(
        final @Nullable String tenantId, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep operationReference(final @Nullable Long operationReference);

    OptionalStep operationReference(
        final @Nullable Long operationReference, final ContractPolicy.FieldPolicy<Long> policy);

    OptionalStep startInstructions(
        final java.util.@Nullable List<
                GeneratedProcessInstanceCreationStartInstructionStrictContract>
            startInstructions);

    OptionalStep startInstructions(final @Nullable Object startInstructions);

    OptionalStep startInstructions(
        final java.util.@Nullable List<
                GeneratedProcessInstanceCreationStartInstructionStrictContract>
            startInstructions,
        final ContractPolicy.FieldPolicy<
                java.util.List<GeneratedProcessInstanceCreationStartInstructionStrictContract>>
            policy);

    OptionalStep startInstructions(
        final @Nullable Object startInstructions, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep runtimeInstructions(final java.util.@Nullable List<Object> runtimeInstructions);

    OptionalStep runtimeInstructions(
        final java.util.@Nullable List<Object> runtimeInstructions,
        final ContractPolicy.FieldPolicy<java.util.List<Object>> policy);

    OptionalStep awaitCompletion(final @Nullable Boolean awaitCompletion);

    OptionalStep awaitCompletion(
        final @Nullable Boolean awaitCompletion, final ContractPolicy.FieldPolicy<Boolean> policy);

    OptionalStep fetchVariables(final java.util.@Nullable List<String> fetchVariables);

    OptionalStep fetchVariables(
        final java.util.@Nullable List<String> fetchVariables,
        final ContractPolicy.FieldPolicy<java.util.List<String>> policy);

    OptionalStep requestTimeout(final @Nullable Long requestTimeout);

    OptionalStep requestTimeout(
        final @Nullable Long requestTimeout, final ContractPolicy.FieldPolicy<Long> policy);

    OptionalStep tags(final java.util.@Nullable Set<String> tags);

    OptionalStep tags(
        final java.util.@Nullable Set<String> tags,
        final ContractPolicy.FieldPolicy<java.util.Set<String>> policy);

    OptionalStep businessId(final @Nullable String businessId);

    OptionalStep businessId(
        final @Nullable String businessId, final ContractPolicy.FieldPolicy<String> policy);

    GeneratedProcessInstanceCreationInstructionByIdStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_ID =
        ContractPolicy.field("ProcessInstanceCreationInstructionById", "processDefinitionId");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_VERSION =
        ContractPolicy.field("ProcessInstanceCreationInstructionById", "processDefinitionVersion");
    public static final ContractPolicy.FieldRef VARIABLES =
        ContractPolicy.field("ProcessInstanceCreationInstructionById", "variables");
    public static final ContractPolicy.FieldRef TENANT_ID =
        ContractPolicy.field("ProcessInstanceCreationInstructionById", "tenantId");
    public static final ContractPolicy.FieldRef OPERATION_REFERENCE =
        ContractPolicy.field("ProcessInstanceCreationInstructionById", "operationReference");
    public static final ContractPolicy.FieldRef START_INSTRUCTIONS =
        ContractPolicy.field("ProcessInstanceCreationInstructionById", "startInstructions");
    public static final ContractPolicy.FieldRef RUNTIME_INSTRUCTIONS =
        ContractPolicy.field("ProcessInstanceCreationInstructionById", "runtimeInstructions");
    public static final ContractPolicy.FieldRef AWAIT_COMPLETION =
        ContractPolicy.field("ProcessInstanceCreationInstructionById", "awaitCompletion");
    public static final ContractPolicy.FieldRef FETCH_VARIABLES =
        ContractPolicy.field("ProcessInstanceCreationInstructionById", "fetchVariables");
    public static final ContractPolicy.FieldRef REQUEST_TIMEOUT =
        ContractPolicy.field("ProcessInstanceCreationInstructionById", "requestTimeout");
    public static final ContractPolicy.FieldRef TAGS =
        ContractPolicy.field("ProcessInstanceCreationInstructionById", "tags");
    public static final ContractPolicy.FieldRef BUSINESS_ID =
        ContractPolicy.field("ProcessInstanceCreationInstructionById", "businessId");

    private Fields() {}
  }
}
