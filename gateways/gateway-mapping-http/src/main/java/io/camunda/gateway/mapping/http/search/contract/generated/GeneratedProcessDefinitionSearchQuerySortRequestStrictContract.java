/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/process-definitions.yaml#/components/schemas/ProcessDefinitionSearchQuerySortRequest
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import jakarta.annotation.Generated;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedProcessDefinitionSearchQuerySortRequestStrictContract(
    @JsonProperty("field") FieldEnum field,
    @JsonProperty("order")
        io.camunda.gateway.mapping.http.search.contract.generated.@Nullable GeneratedSortOrderEnum
            order) {

  public GeneratedProcessDefinitionSearchQuerySortRequestStrictContract {
    Objects.requireNonNull(field, "Sort field must not be null");
  }

  public static FieldStep builder() {
    return new Builder();
  }

  public static final class Builder implements FieldStep, OptionalStep {
    private FieldEnum field;
    private io.camunda.gateway.mapping.http.search.contract.generated.GeneratedSortOrderEnum order;

    private Builder() {}

    @Override
    public OptionalStep field(final FieldEnum field) {
      this.field = field;
      return this;
    }

    @Override
    public OptionalStep order(
        final io.camunda.gateway.mapping.http.search.contract.generated.@Nullable
            GeneratedSortOrderEnum
            order) {
      this.order = order;
      return this;
    }

    @Override
    public OptionalStep order(
        final io.camunda.gateway.mapping.http.search.contract.generated.@Nullable
            GeneratedSortOrderEnum
            order,
        final ContractPolicy.FieldPolicy<
                io.camunda.gateway.mapping.http.search.contract.generated.GeneratedSortOrderEnum>
            policy) {
      this.order = policy.apply(order, Fields.ORDER, null);
      return this;
    }

    @Override
    public GeneratedProcessDefinitionSearchQuerySortRequestStrictContract build() {
      return new GeneratedProcessDefinitionSearchQuerySortRequestStrictContract(
          this.field, this.order);
    }
  }

  public interface FieldStep {
    OptionalStep field(final FieldEnum field);
  }

  public interface OptionalStep {
    OptionalStep order(
        final io.camunda.gateway.mapping.http.search.contract.generated.@Nullable
            GeneratedSortOrderEnum
            order);

    OptionalStep order(
        final io.camunda.gateway.mapping.http.search.contract.generated.@Nullable
            GeneratedSortOrderEnum
            order,
        final ContractPolicy.FieldPolicy<
                io.camunda.gateway.mapping.http.search.contract.generated.GeneratedSortOrderEnum>
            policy);

    GeneratedProcessDefinitionSearchQuerySortRequestStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef FIELD =
        ContractPolicy.field("ProcessDefinitionSearchQuerySortRequest", "field");
    public static final ContractPolicy.FieldRef ORDER =
        ContractPolicy.field("ProcessDefinitionSearchQuerySortRequest", "order");

    private Fields() {}
  }

  public enum FieldEnum {
    PROCESS_DEFINITION_KEY("processDefinitionKey"),

    NAME("name"),

    RESOURCE_NAME("resourceName"),

    VERSION("version"),

    VERSION_TAG("versionTag"),

    PROCESS_DEFINITION_ID("processDefinitionId"),

    TENANT_ID("tenantId");

    private final String value;

    FieldEnum(String value) {
      this.value = value;
    }

    @JsonValue
    public String getValue() {
      return value;
    }

    @Override
    public String toString() {
      return String.valueOf(value);
    }

    @JsonCreator
    public static FieldEnum fromValue(String value) {
      for (FieldEnum b : FieldEnum.values()) {
        if (b.value.equalsIgnoreCase(value)) {
          return b;
        }
      }
      throw new IllegalArgumentException("Unexpected value '" + value + "'");
    }
  }
}
