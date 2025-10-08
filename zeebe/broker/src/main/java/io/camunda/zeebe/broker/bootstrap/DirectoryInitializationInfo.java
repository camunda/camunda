/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.bootstrap;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * Represents the contents of the directory-initialized.json file that tracks when a data directory
 * was initialized and optionally from which version it was copied.
 *
 * @param initialized The timestamp when the directory was initialized
 * @param initializedFrom The version from which this directory was copied, null if created empty
 */
public record DirectoryInitializationInfo(
    @JsonProperty("initialized") long initialized,
    @JsonProperty("initializedFrom") Long initializedFrom) {

  @JsonCreator
  public DirectoryInitializationInfo(
      @JsonProperty("initialized") final long initialized,
      @JsonProperty("initializedFrom") final Long initializedFrom) {
    this.initialized = initialized;
    this.initializedFrom = initializedFrom;
  }

  /**
   * Creates a new DirectoryInitializationInfo for a directory that was copied from a previous
   * version.
   *
   * @param initializedFrom The version from which the directory was copied
   * @return A new DirectoryInitializationInfo instance
   */
  public static DirectoryInitializationInfo copiedFrom(final long initializedFrom) {
    return new DirectoryInitializationInfo(Instant.now().toEpochMilli(), initializedFrom);
  }

  /**
   * Creates a new DirectoryInitializationInfo for a directory that was created empty.
   *
   * @return A new DirectoryInitializationInfo instance
   */
  public static DirectoryInitializationInfo createdEmpty() {
    return new DirectoryInitializationInfo(Instant.now().toEpochMilli(), null);
  }
}
