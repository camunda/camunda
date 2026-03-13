/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.config;

import java.util.Set;

/** Immutable CSRF configuration for the domain layer. */
public record CsrfConfig(boolean enabled, Set<String> allowedPaths) {

  public CsrfConfig {
    allowedPaths = allowedPaths != null ? Set.copyOf(allowedPaths) : Set.of();
  }
}
