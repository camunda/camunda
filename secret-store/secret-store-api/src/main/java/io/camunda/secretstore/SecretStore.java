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

public interface SecretStore<T extends SecretReference> extends AutoCloseable {

  /**
   * Resolves a set of secret references in a single call.
   *
   * <p>Returns a result for <em>every</em> ref in the input set. Per-secret failures (e.g. secret
   * not found, access denied) are reported as {@link SecretResolutionResult.Failed}.
   *
   * @throws SecretStoreUnavailableException if the backing store cannot be accessed or its content
   *     is malformed
   */
  Map<T, SecretResolutionResult> resolve(Set<T> refs);

  /**
   * Lists all secret references known to this store.
   *
   * @throws SecretStoreUnavailableException if the backing store cannot be accessed or its content
   *     is malformed
   */
  Collection<T> list();

  @Override
  default void close() {}
}
