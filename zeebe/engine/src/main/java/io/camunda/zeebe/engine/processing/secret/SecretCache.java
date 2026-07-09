/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.secret;

import io.camunda.zeebe.engine.processing.deployment.model.element.SecretReference;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;

/**
 * A broker-shared cache of resolved secret values, keyed by the {@link SecretReference} they were
 * resolved from. A single instance is shared by every partition on a broker (not created per
 * partition), so implementations must be thread-safe.
 *
 * <p>Secret stores are configured per physical tenant, so the same secret name can resolve to
 * different values in different physical tenants. Callers therefore read and write through a view
 * scoped to their physical tenant ({@link #withPhysicalTenant}); all views are backed by the one
 * broker-shared instance.
 *
 * <p>Implementations are swapped behind this interface without touching callers: {@link
 * InMemorySecretCache} is the minimal version with no eviction; a TTL/eviction variant replaces it
 * later.
 */
@NullMarked
public interface SecretCache {

  /**
   * Returns the cached resolved value for the given reference.
   *
   * @return the cached value, or empty if the reference is not cached
   */
  Optional<String> get(SecretReference reference);

  /** Stores the resolved value for the reference, overwriting any previously cached value. */
  void put(SecretReference reference, String value);

  /**
   * Returns a view of this cache scoped to the given physical tenant, so entries never collide with
   * another physical tenant that resolves the same secret name to a different value.
   */
  SecretCache withPhysicalTenant(String physicalTenantId);
}
