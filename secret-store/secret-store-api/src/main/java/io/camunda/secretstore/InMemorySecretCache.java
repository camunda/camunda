/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A minimal {@link SecretCache} backed by a {@link ConcurrentHashMap}, with no eviction or expiry.
 * The concurrent map makes it safe to use in front of a thread-safe {@link SecretStore} and rejects
 * {@code null} keys and values.
 */
public final class InMemorySecretCache implements SecretCache {

  private final Map<String, String> values = new ConcurrentHashMap<>();

  @Override
  public Optional<String> get(final String name) {
    return Optional.ofNullable(values.get(name));
  }

  @Override
  public void put(final String name, final String value) {
    values.put(name, value);
  }
}
