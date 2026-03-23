/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/process-definitions.yaml#/components/schemas/ProcessDefinitionInstanceVersionStatisticsFilter
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import jakarta.annotation.Generated;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedProcessDefinitionInstanceVersionStatisticsFilterStrictContract(
    @JsonProperty("processDefinitionId") String processDefinitionId,
    @JsonProperty("tenantId") @Nullable Object tenantId) {

  public GeneratedProcessDefinitionInstanceVersionStatisticsFilterStrictContract {
    Objects.requireNonNull(processDefinitionId, "No processDefinitionId provided.");
    if (processDefinitionId.isBlank())
      throw new IllegalArgumentException("processDefinitionId must not be blank");
    if (!processDefinitionId.matches("^[a-zA-Z_][a-zA-Z0-9_\\-\\.]*$"))
      throw new IllegalArgumentException(
          "The provided processDefinitionId contains illegal characters. It must match the pattern '^[a-zA-Z_][a-zA-Z0-9_\\-\\.]*$'.");
  }

  public static ProcessDefinitionIdStep builder() {
    return new Builder();
  }

  public static final class Builder implements ProcessDefinitionIdStep, OptionalStep {
    private String processDefinitionId;
    private Object tenantId;

    private Builder() {}

    @Override
    public OptionalStep processDefinitionId(final String processDefinitionId) {
      this.processDefinitionId = processDefinitionId;
      return this;
    }

    @Override
    public OptionalStep tenantId(final @Nullable Object tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    @Override
    public OptionalStep tenantId(
        final @Nullable Object tenantId, final ContractPolicy.FieldPolicy<Object> policy) {
      this.tenantId = policy.apply(tenantId, Fields.TENANT_ID, null);
      return this;
    }

    @Override
    public GeneratedProcessDefinitionInstanceVersionStatisticsFilterStrictContract build() {
      return new GeneratedProcessDefinitionInstanceVersionStatisticsFilterStrictContract(
          this.processDefinitionId, this.tenantId);
    }
  }

  public interface ProcessDefinitionIdStep {
    OptionalStep processDefinitionId(final String processDefinitionId);
  }

  public interface OptionalStep {
    OptionalStep tenantId(final @Nullable Object tenantId);

    OptionalStep tenantId(
        final @Nullable Object tenantId, final ContractPolicy.FieldPolicy<Object> policy);

    GeneratedProcessDefinitionInstanceVersionStatisticsFilterStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_ID =
        ContractPolicy.field(
            "ProcessDefinitionInstanceVersionStatisticsFilter", "processDefinitionId");
    public static final ContractPolicy.FieldRef TENANT_ID =
        ContractPolicy.field("ProcessDefinitionInstanceVersionStatisticsFilter", "tenantId");

    private Fields() {}
  }
}
