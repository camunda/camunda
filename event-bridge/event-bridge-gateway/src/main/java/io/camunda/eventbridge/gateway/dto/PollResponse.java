/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.gateway.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PollResponse(
    String status, List<PollEvent> events, Long nextPosition, String error, String message) {

  public static PollResponse ok(final List<PollEvent> events, final long nextPosition) {
    return new PollResponse("OK", events, nextPosition, null, null);
  }

  public static PollResponse error(final String errorCode, final String message) {
    return new PollResponse("ERROR", null, null, errorCode, message);
  }
}
