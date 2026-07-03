/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.NullMarked;

@NullMarked
public interface SecretStore extends AutoCloseable {

  /**
   * Resolves a set of secret references in a single call.
   *
   * <p>Returns a result for every ref in the input set. Never throws — store-level failures
   * (unreachable backend, missing file, etc.) are reported as {@link SecretResolutionResult.Failed}
   * with code {@link SecretErrorCode#STORE_UNAVAILABLE}.
   *
   * <p>Implementations must be thread-safe.
   */
  Map<SecretRef, SecretResolutionResult> resolve(Set<SecretRef> refs);

  /**
   * Lists all secret references known to this store. May throw {@link java.io.UncheckedIOException}
   * on store-level failures.
   */
  Collection<SecretRef> list();

  @Override
  default void close() {}
}
