/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.metadata.store.api;

import java.util.Objects;

/**
 * The address of a key: the namespace and the dotted key within it, for example {@code
 * StoreKey.of(Namespace.cluster("region-failover"), "regions.eu-west-1.state")}. An empty key is
 * only valid as the prefix of {@link ClusterMetadataStore#list(StoreKey)}, where it addresses the
 * whole namespace.
 */
public record StoreKey(Namespace namespace, String key) {

  public StoreKey {
    Objects.requireNonNull(namespace, "namespace");
    Objects.requireNonNull(key, "key");
  }

  public static StoreKey of(final Namespace namespace, final String key) {
    return new StoreKey(namespace, key);
  }

  @Override
  public String toString() {
    return namespace + ":" + key;
  }
}
