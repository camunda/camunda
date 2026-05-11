/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.secret;

import java.util.Optional;

/**
 * SPI for resolving secret values referenced via the {@code camunda.secret.*} FEEL namespace.
 *
 * <p>The PoC ships a single implementation ({@link EnvVarSecretStore}) backed by process
 * environment variables. Implementations must be safe to call synchronously from the engine
 * processing thread — i.e. fast, non-blocking, and free of I/O that could stall stream processing.
 *
 * <p>Resolved values are kept in memory only and never written to the variable store. The {@code
 * camunda.secret.*} namespace is intentionally inert in normal FEEL evaluation (input mappings,
 * output mappings, gateway conditions, …): in those contexts a path lookup yields the literal
 * reference string, not the secret value. The store is consulted only when a job worker lists a
 * {@code camunda.secret.X} entry in {@code fetchVariables} or when the standalone FEEL evaluation
 * endpoint is invoked with such a reference.
 */
public interface SecretStore {

  /**
   * Returns the secret value for {@code secretName}, or {@link Optional#empty()} if no secret by
   * that name is defined.
   *
   * @param secretName the bare secret name (the suffix after {@code camunda.secret.}); never {@code
   *     null}
   */
  Optional<String> resolve(String secretName);

  /** A {@link SecretStore} that resolves nothing — useful for tests and for the disabled state. */
  SecretStore EMPTY = name -> Optional.empty();
}
