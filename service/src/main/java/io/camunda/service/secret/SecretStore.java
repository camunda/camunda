/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.secret;

import java.util.Optional;

/** Backend that resolves a secret name (e.g. {@code MY_TOKEN}) to its value. */
public interface SecretStore {

  /** Unique identifier of this store (used for diagnostics and future multi-store routing). */
  String id();

  /**
   * Returns the resolved value for the given secret name, or {@link Optional#empty()} if the store
   * does not know the secret. Implementations must not throw on missing entries.
   */
  Optional<String> resolve(String secretName);
}
