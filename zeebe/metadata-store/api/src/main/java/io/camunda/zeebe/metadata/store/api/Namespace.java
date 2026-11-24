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
 * An isolated key space, identified by its scope and its name, written {@code SCOPE/NAME}. The name
 * is the physical tenant id in {@link Scope#PHYSICAL_TENANT} scope and a fixed name in {@link
 * Scope#CLUSTER} scope. A name holds only Latin letters, digits, {@code -} and {@code _}.
 */
public record Namespace(Scope scope, String name) {

  private static final Pattern NAMESPACE_REGEX = Pattern.compile("[A-Za-z0-9_-]+");

  /**
   * @throws InvalidArgumentException if the name is empty or holds a character that is not allowed
   */
  public Namespace {
    Objects.requireNonNull(scope, "scope");
    Objects.requireNonNull(name, "name");
    if (!NAMESPACE_REGEX.matcher(name).matches()) {
      throw new InvalidArgumentException(
          "Expected a namespace name of Latin letters, digits, '-' and '_', but got '%s'"
              .formatted(name));
    }
  }

  /** Returns the namespace {@code CLUSTER/name}. */
  public static Namespace cluster(final String name) {
    return new Namespace(Scope.CLUSTER, name);
  }

  /** Returns the namespace {@code PHYSICAL_TENANT/tenantId}. */
  public static Namespace tenant(final String tenantId) {
    return new Namespace(Scope.PHYSICAL_TENANT, tenantId);
  }

  @Override
  public String toString() {
    return scope + "/" + name;
  }

  /** Whose data a namespace holds. */
  public enum Scope {
    /** The whole cluster; the namespace name is a fixed name chosen by its consumer. */
    CLUSTER,
    /** One physical tenant; the namespace name is the physical tenant id. */
    PHYSICAL_TENANT
  }
}
