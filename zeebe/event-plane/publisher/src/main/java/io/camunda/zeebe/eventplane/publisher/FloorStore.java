/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.publisher;

import java.io.IOException;
import org.jspecify.annotations.Nullable;

/**
 * Keeps a partition's upload floor. In a broker, every replica keeps it, and compaction stops at
 * it.
 */
public interface FloorStore {

  /** The floor, or null if nothing was uploaded yet. */
  @Nullable UploadFloor load() throws IOException;

  void store(UploadFloor floor) throws IOException;

  /** A floor that lives as long as the publisher, for a source that is published once. */
  static FloorStore inMemory() {
    return new FloorStore() {
      private @Nullable UploadFloor floor;

      @Override
      public @Nullable UploadFloor load() {
        return floor;
      }

      @Override
      public void store(final UploadFloor floor) {
        this.floor = floor;
      }
    };
  }
}
