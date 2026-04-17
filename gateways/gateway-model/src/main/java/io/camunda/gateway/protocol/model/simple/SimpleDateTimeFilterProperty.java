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

public record SimpleDateTimeFilterProperty(
    @JsonProperty
        @JsonPropertyDescription(
            "Filter from this time (inclusive). RFC 3339 format (e.g., '2024-12-17T10:30:00Z' or '2024-12-17T10:30:00+01:00').")
        String from,
    @JsonProperty
        @JsonPropertyDescription(
            "Filter up to this time (exclusive). RFC 3339 format (e.g., '2024-12-17T10:30:00Z' or '2024-12-17T10:30:00+01:00').")
        String to) {}
