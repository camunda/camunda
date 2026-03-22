/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
public record GeneratedElementInstanceSearchQuerySortRequestStrictContract(
    @JsonProperty("field") FieldEnum field,
    @JsonProperty("order")
        io.camunda.gateway.mapping.http.search.contract.generated.@Nullable GeneratedSortOrderEnum
            order) {

  public GeneratedElementInstanceSearchQuerySortRequestStrictContract {
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
    public GeneratedElementInstanceSearchQuerySortRequestStrictContract build() {
      return new GeneratedElementInstanceSearchQuerySortRequestStrictContract(
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

    GeneratedElementInstanceSearchQuerySortRequestStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef FIELD =
        ContractPolicy.field("ElementInstanceSearchQuerySortRequest", "field");
    public static final ContractPolicy.FieldRef ORDER =
        ContractPolicy.field("ElementInstanceSearchQuerySortRequest", "order");

    private Fields() {}
  }

  public enum FieldEnum {
    ELEMENT_INSTANCE_KEY("elementInstanceKey"),

    PROCESS_INSTANCE_KEY("processInstanceKey"),

    PROCESS_DEFINITION_KEY("processDefinitionKey"),

    PROCESS_DEFINITION_ID("processDefinitionId"),

    START_DATE("startDate"),

    END_DATE("endDate"),

    ELEMENT_ID("elementId"),

    ELEMENT_NAME("elementName"),

    TYPE("type"),

    STATE("state"),

    INCIDENT_KEY("incidentKey"),

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
