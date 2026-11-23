/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.metadata.store.api;

import io.camunda.zeebe.metadata.store.api.NamespaceApi.NamespaceScope;
import java.util.Objects;

/**
 * An isolated key space, identified by its scope and its name, written {@code SCOPE/NAME}. The name
 * is the physical tenant id in {@link NamespaceScope#PHYSICAL_TENANT} scope and a fixed name in
 * {@link NamespaceScope#CLUSTER} scope.
 */
public record Namespace(NamespaceScope scope, String name) {

  public Namespace {
    Objects.requireNonNull(scope, "scope");
    Objects.requireNonNull(name, "name");
  }

  /** Returns the namespace {@code CLUSTER/name}. */
  public static Namespace cluster(final String name) {
    return new Namespace(NamespaceScope.CLUSTER, name);
  }

  /** Returns the namespace {@code PHYSICAL_TENANT/tenantId}. */
  public static Namespace tenant(final String tenantId) {
    return new Namespace(NamespaceScope.PHYSICAL_TENANT, tenantId);
  }

  @Override
  public String toString() {
    return scope + "/" + name;
  }
}
