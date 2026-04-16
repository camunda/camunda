/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.protocol.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.annotation.Generated;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedIncidentStrictContract(
    String processDefinitionId,
    IncidentErrorTypeEnum errorType,
    String errorMessage,
    String elementId,
    String creationTime,
    IncidentStateEnum state,
    String tenantId,
    String incidentKey,
    String processDefinitionKey,
    String processInstanceKey,
    @Nullable String rootProcessInstanceKey,
    String elementInstanceKey,
    @Nullable String jobKey) {

  public GeneratedIncidentStrictContract {
    Objects.requireNonNull(
        processDefinitionId, "processDefinitionId is required and must not be null");
    Objects.requireNonNull(errorType, "errorType is required and must not be null");
    Objects.requireNonNull(errorMessage, "errorMessage is required and must not be null");
    Objects.requireNonNull(elementId, "elementId is required and must not be null");
    Objects.requireNonNull(creationTime, "creationTime is required and must not be null");
    Objects.requireNonNull(state, "state is required and must not be null");
    Objects.requireNonNull(tenantId, "tenantId is required and must not be null");
    Objects.requireNonNull(incidentKey, "incidentKey is required and must not be null");
    Objects.requireNonNull(
        processDefinitionKey, "processDefinitionKey is required and must not be null");
    Objects.requireNonNull(
        processInstanceKey, "processInstanceKey is required and must not be null");
    Objects.requireNonNull(
        elementInstanceKey, "elementInstanceKey is required and must not be null");
  }
}
