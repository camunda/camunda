/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.metadata.store.api;

import io.camunda.zeebe.metadata.store.api.ClusterMetadataStoreException.InvalidArgumentException;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The address of a key: the namespace and the dotted key within it, for example {@code
 * StoreKey.of(Namespace.cluster("region-failover"), "regions.eu-west-1.state")}.
 *
 * <p>A key is in canonical form: segments of lowercase Latin letters, digits and {@code -}, joined
 * by {@code .}. Any other key is rejected with {@link InvalidArgumentException}, never rewritten.
 * An empty key is only valid as the prefix of {@link ClusterMetadataStore#list(StoreKey)}, where it
 * addresses the whole namespace; every other operation rejects it.
 */
public record StoreKey(Namespace namespace, String key) {

  private static final char SEPARATOR = '.';
  private static final Pattern CANONICAL_KEY_PATTERN =
      Pattern.compile("[a-z0-9-]+(\\.[a-z0-9-]+)*");

  /**
   * @throws InvalidArgumentException if the key is neither empty nor in canonical form
   */
  public StoreKey {
    Objects.requireNonNull(namespace, "namespace");
    Objects.requireNonNull(key, "key");
    if (!key.isEmpty() && !CANONICAL_KEY_PATTERN.matcher(key).matches()) {
      throw new InvalidArgumentException(
          "Expected a key of lowercase letters, digits and '-' in segments joined by '.', but got '%s'"
              .formatted(key));
    }
  }

  public static StoreKey of(final Namespace namespace, final String key) {
    return new StoreKey(namespace, key);
  }

  /** Returns true if this is the empty key, which addresses the whole namespace. */
  public boolean isEmpty() {
    return key.isEmpty();
  }

  /**
   * Returns true if this key is the prefix or lies underneath it, in the same namespace. The prefix
   * matches on whole segments: {@code a.b} matches {@code a.b} and {@code a.b.c}, not {@code a.bc}.
   * The empty key is a prefix of every key of its namespace.
   */
  public boolean isAtOrUnder(final StoreKey prefix) {
    if (!namespace.equals(prefix.namespace)) {
      return false;
    }
    if (prefix.isEmpty() || key.equals(prefix.key)) {
      return true;
    }
    return key.length() > prefix.key.length()
        && key.startsWith(prefix.key)
        && key.charAt(prefix.key.length()) == SEPARATOR;
  }

  @Override
  public String toString() {
    return namespace + ":" + key;
  }
}
