/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.entity;

import java.util.Arrays;
import java.util.Optional;

public enum AuthenticationMethod {
  BASIC,
  OIDC;

  public static Optional<AuthenticationMethod> parse(final String value) {
    if (value == null) {
      return Optional.empty();
    }
    return Arrays.stream(values())
        .filter(method -> method.name().equalsIgnoreCase(value))
        .findFirst()
        .map(Optional::of)
        .orElseThrow(
            () -> new IllegalArgumentException("unsupported authentication method: " + value));
  }
}
