/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.rdbms;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

/**
 * The snapshot of a partition state: one small file that names the state, which itself stays in the
 * database. The snapshot store checksums and moves the file like any other snapshot file.
 */
record StateMarker(long stateId, StateIdentity identity) {

  static final String FILE_NAME = "zeebe-state.properties";

  private static final String FORMAT_VERSION = "1";
  private static final String VERSION = "version";
  private static final String STATE_ID = "stateId";
  private static final String PARTITION_GROUP = "partitionGroup";
  private static final String PARTITION_ID = "partitionId";

  /** Writes the marker into the directory, which is created if it doesn't exist yet. */
  void writeTo(final Path directory) {
    final var properties = new Properties();
    properties.setProperty(VERSION, FORMAT_VERSION);
    properties.setProperty(STATE_ID, Long.toString(stateId));
    properties.setProperty(PARTITION_GROUP, identity.partitionGroup());
    properties.setProperty(PARTITION_ID, Integer.toString(identity.partitionId()));
    try {
      Files.createDirectories(directory);
      try (final var output = Files.newOutputStream(directory.resolve(FILE_NAME))) {
        properties.store(output, "Names the partition state in the relational database");
      }
    } catch (final IOException e) {
      throw new UncheckedIOException(
          "Failed to write the marker of the partition state %d to '%s'"
              .formatted(stateId, directory),
          e);
    }
  }

  /** Reads the marker from the directory, or returns empty if it has none. */
  static Optional<StateMarker> readIfPresent(final Path directory) {
    return Files.exists(directory.resolve(FILE_NAME))
        ? Optional.of(readFrom(directory))
        : Optional.empty();
  }

  /**
   * @throws IllegalStateException if the directory has no marker or the marker isn't valid
   */
  static StateMarker readFrom(final Path directory) {
    final var file = directory.resolve(FILE_NAME);
    if (!Files.exists(file)) {
      throw new IllegalStateException(
          "Expected a snapshot of a partition state in '%s', but it has no %s"
              .formatted(directory, FILE_NAME));
    }

    final var properties = new Properties();
    try (final var input = Files.newInputStream(file)) {
      properties.load(input);
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to read the snapshot marker '%s'".formatted(file), e);
    }

    final var version = properties.getProperty(VERSION);
    if (!FORMAT_VERSION.equals(version)) {
      throw new IllegalStateException(
          "Expected a snapshot marker of version %s, but '%s' has version %s"
              .formatted(FORMAT_VERSION, file, version));
    }
    try {
      return new StateMarker(
          Long.parseLong(required(properties, STATE_ID, file)),
          new StateIdentity(
              required(properties, PARTITION_GROUP, file),
              Integer.parseInt(required(properties, PARTITION_ID, file))));
    } catch (final NumberFormatException e) {
      throw new IllegalStateException(
          "Expected a valid snapshot marker, but '%s' has a value that is not a number"
              .formatted(file),
          e);
    }
  }

  private static String required(final Properties properties, final String key, final Path file) {
    final var value = properties.getProperty(key);
    if (value == null) {
      throw new IllegalStateException(
          "Expected the snapshot marker '%s' to contain '%s', but it doesn't".formatted(file, key));
    }
    return value;
  }
}
