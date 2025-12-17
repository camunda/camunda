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
    incidentKey,
    processDefinitionKey,
    processDefinitionId,
    processInstanceKey,
    errorType,
    errorMessage,
    elementId,
    elementInstanceKey,
    creationTime,
    state,
    jobKey,
    tenantId;

    @JsonCreator
    public static FieldEnum fromValue(final String value) {
      for (final FieldEnum field : FieldEnum.values()) {
        if (field.name().equalsIgnoreCase(value)) {
          return field;
        }
      }
      throw new IllegalArgumentException("Unexpected value '" + value + "'");
    }
  }
}
