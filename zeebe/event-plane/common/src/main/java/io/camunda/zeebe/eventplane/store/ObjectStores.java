/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.store;

import java.util.Objects;

/** Opens the store that a configuration describes. */
public final class ObjectStores {

  private ObjectStores() {}

  /**
   * Opens a store. The caller closes it. An S3 store makes no request until it is used, so a wrong
   * bucket or endpoint shows then.
   *
   * @throws InvalidStoreConfigException if the configuration lacks what its type needs
   */
  public static ObjectStore open(final StoreConfig config) {
    return switch (config.type()) {
      case FILESYSTEM ->
          new FileSystemStore(
              Objects.requireNonNull(config.path(), "A filesystem store needs a path"));
      case S3 -> new S3Store(config.toS3());
    };
  }
}
