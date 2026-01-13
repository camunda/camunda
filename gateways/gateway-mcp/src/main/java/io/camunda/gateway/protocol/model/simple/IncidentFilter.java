/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.protocol.model.simple;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.camunda.gateway.protocol.model.IncidentErrorTypeEnum;
import io.camunda.gateway.protocol.model.IncidentStateEnum;

public record IncidentFilter(
    @JsonProperty @JsonPropertyDescription("The process definition ID associated to the incident.")
        String processDefinitionId,
    @JsonProperty @JsonPropertyDescription("Incident error type.") IncidentErrorTypeEnum errorType,
    @JsonProperty
        @JsonPropertyDescription(
            "The element ID associated to the incident - the BPMN element ID in the process.")
        String elementId,
    @JsonProperty @JsonPropertyDescription("The date and time the incident was created at.")
        DateTimeFilterProperty creationTime,
    @JsonProperty @JsonPropertyDescription("State of the incident.") IncidentStateEnum state,
    @JsonProperty @JsonPropertyDescription("The process definition key associated to the incident.")
        Long processDefinitionKey,
    @JsonProperty @JsonPropertyDescription("The process instance key associated to the incident.")
        Long processInstanceKey) {}
