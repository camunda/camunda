/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.mcp.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record IncidentSearchQuerySortRequest(
    @NotNull
        @Schema(
            name = "field",
            description = "The field to sort by.",
            requiredMode = Schema.RequiredMode.REQUIRED)
        FieldEnum field,
    @Schema(name = "order", requiredMode = Schema.RequiredMode.NOT_REQUIRED, defaultValue = "ASC")
        @Valid
        SortOrderEnum order) {

  public enum FieldEnum {
    INCIDENT_KEY("incidentKey"),

    PROCESS_DEFINITION_KEY("processDefinitionKey"),

    PROCESS_DEFINITION_ID("processDefinitionId"),

    PROCESS_INSTANCE_KEY("processInstanceKey"),

    ERROR_TYPE("errorType"),

    ERROR_MESSAGE("errorMessage"),

    ELEMENT_ID("elementId"),

    ELEMENT_INSTANCE_KEY("elementInstanceKey"),

    CREATION_TIME("creationTime"),

    STATE("state"),

    JOB_KEY("jobKey"),

    TENANT_ID("tenantId");

    private final String value;

    FieldEnum(final String value) {
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
    public static FieldEnum fromValue(final String value) {
      for (final FieldEnum b : FieldEnum.values()) {
        if (b.value.equalsIgnoreCase(value)) {
          return b;
        }
      }
      throw new IllegalArgumentException("Unexpected value '" + value + "'");
    }
  }
}
