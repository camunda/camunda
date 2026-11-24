/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.metadata.store.api;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One key with its value, as {@link ClusterMetadataStore#get(StoreKey)} and {@link
 * ClusterMetadataStore#list(StoreKey)} return it.
 *
 * @param key the key
 * @param current the current value
 * @param previous the value the last write replaced; absent when the key has none
 */
public record KeyValue(StoreKey key, StoreValue current, @Nullable StoreValue previous) {

  public KeyValue {
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(current, "current");
  }
}
