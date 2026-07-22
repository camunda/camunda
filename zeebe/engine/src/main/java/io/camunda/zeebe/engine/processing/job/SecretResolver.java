/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import java.util.Map;
import java.util.Set;

/**
 * Looks up the values of secret references from the broker's secret cache so they can be injected
 * into the variables of activated jobs. Resolution never blocks: a reference either has a cached
 * value or it does not.
 */
@FunctionalInterface
public interface SecretResolver {

  /**
   * Returns the cached value for every given reference that is currently cached. References without
   * a cached value are absent from the returned map.
   */
  Map<SecretReference, String> resolve(Set<SecretReference> references);

  /** A resolver without any cached values; used until the secret cache is wired in. */
  static SecretResolver noop() {
    return references -> Map.of();
  }

  /** Identifies a secret by the store that holds it and the reference name within that store. */
  record SecretReference(String storeId, String secretReference) {}
}
