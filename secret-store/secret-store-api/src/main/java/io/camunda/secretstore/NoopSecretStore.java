/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore;

import static java.util.stream.Collectors.toMap;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.NullMarked;

/** A no-op secret store returned when no store is configured for a physical tenant. */
@NullMarked
public final class NoopSecretStore implements SecretStore {

  @Override
  public Map<String, SecretResolutionResult> resolve(final Set<String> names) {
    return names.stream()
        .collect(
            toMap(
                name -> name,
                name ->
                    new SecretResolutionResult.Failed(
                        SecretErrorCode.NOT_FOUND, "No secret store configured", null)));
  }

  @Override
  public List<String> list() {
    return List.of();
  }
}
