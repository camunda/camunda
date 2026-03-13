/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.model.session;

import java.util.Map;

/** Input for creating a new session token. */
public record SessionTokenRequest(String subject, Map<String, Object> claims) {

  public SessionTokenRequest {
    claims = claims != null ? Map.copyOf(claims) : Map.of();
  }

  public SessionTokenRequest(final String subject) {
    this(subject, Map.of());
  }
}
