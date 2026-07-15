/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.secrets;

import io.camunda.secretstore.SecretStore;
import java.util.Map;
import org.jspecify.annotations.NullMarked;

/**
 * Provides the configured {@link SecretStore}s for the current physical tenant, keyed by store ID.
 */
@NullMarked
public final class SecretStoreRegistry {

  private final Map<String, SecretStore<?>> stores;

  public SecretStoreRegistry(final Map<String, SecretStore<?>> stores) {
    this.stores = stores;
  }

  /** Returns all configured secret stores, keyed by store ID. */
  public Map<String, SecretStore<?>> getStores() {
    return stores;
  }
}
