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
import io.camunda.gateway.mcp.tool.ToolDescriptions;

public record DateTimeFilterProperty(
    @JsonProperty
        @JsonPropertyDescription(
            "Filter from this time (inclusive). " + ToolDescriptions.DATE_TIME_FORMAT)
        String from,
    @JsonProperty
        @JsonPropertyDescription(
            "Filter up to this time (exclusive). " + ToolDescriptions.DATE_TIME_FORMAT)
        String to) {}
