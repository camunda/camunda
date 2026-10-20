/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.eventplane;

import io.camunda.zeebe.broker.system.configuration.EventPlaneCfg.StoreCfg;
import io.camunda.zeebe.eventplane.store.ObjectStore;
import io.camunda.zeebe.eventplane.store.ObjectStores;
import io.camunda.zeebe.eventplane.store.S3StoreConfig;
import io.camunda.zeebe.eventplane.store.StoreConfig;
import java.net.URI;
import java.nio.file.Path;

/**
 * Opens the Event Plane's store as the broker's configuration names it, for the broker and the
 * restore.
 */
public final class EventPlaneStores {

  private EventPlaneStores() {}

  /**
   * Opens the configured store.
   *
   * @throws IllegalArgumentException if the store's type is neither {@code filesystem} nor {@code
   *     s3}
   */
  public static ObjectStore open(final StoreCfg store) {
    return ObjectStores.open(config(store));
  }

  private static StoreConfig config(final StoreCfg store) {
    return switch (store.getType()) {
      case "filesystem" -> StoreConfig.filesystem(Path.of(store.getPath()));
      case "s3" ->
          StoreConfig.s3(
              new S3StoreConfig(
                  store.getBucket(),
                  store.getBasePath(),
                  store.getRegion(),
                  store.getEndpoint() != null ? URI.create(store.getEndpoint()) : null,
                  store.getAccessKeyId(),
                  store.getSecretAccessKey(),
                  store.isForcePathStyle()));
      default ->
          throw new IllegalArgumentException(
              "Expected an Event Plane store of type 'filesystem' or 's3', but got '%s'"
                  .formatted(store.getType()));
    };
  }
}
