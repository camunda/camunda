/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Implementations must be thread-safe: {@link #resolve} and {@link #list} may be called
 * concurrently from multiple threads.
 */
public interface SecretStore extends AutoCloseable {

  /**
   * Resolves a set of secret names in a single call.
   *
   * <p>Returns a result for <em>every</em> name in the input set. Per-secret failures (e.g. secret
   * not found, access denied) are reported as {@link SecretResolutionResult.Failed}.
   *
   * <p>Implementations must be thread-safe.
   *
   * @throws SecretStoreUnavailableException if the backing store cannot be accessed or its content
   *     is malformed
   */
  Map<String, SecretResolutionResult> resolve(Set<String> names);

  /**
   * Lists all secret names known to this store.
   *
   * <p>Implementations must be thread-safe.
   *
   * @throws SecretStoreUnavailableException if the backing store cannot be accessed or its content
   *     is malformed
   */
  List<String> list();

  @Override
  default void close() {}
}
