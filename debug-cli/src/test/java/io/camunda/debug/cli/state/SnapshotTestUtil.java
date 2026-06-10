/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.debug.cli.state;

import io.camunda.zeebe.db.AccessMetricsConfiguration;
import io.camunda.zeebe.db.AccessMetricsConfiguration.Kind;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration;
import io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStoreImpl;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Helpers shared by the offline snapshot-edit command tests. */
final class SnapshotTestUtil {

  private SnapshotTestUtil() {}

  static ZeebeRocksDbFactory<ZbColumnFamilies> newDbFactory() {
    return new ZeebeRocksDbFactory<>(
        new RocksDbConfiguration().setWalDisabled(false),
        new ConsistencyChecksSettings(true, true),
        new AccessMetricsConfiguration(Kind.NONE, 1),
        SimpleMeterRegistry::new);
  }

  /** Returns the snapshot directory created next to the given source snapshot by a command run. */
  static Path newSnapshotPath(final Path partitionRoot, final String sourceSnapshotId) {
    try (final var stream =
        Files.list(partitionRoot.resolve(FileBasedSnapshotStoreImpl.SNAPSHOTS_DIRECTORY))) {
      return stream
          .filter(Files::isDirectory)
          .filter(dir -> !dir.getFileName().toString().equals(sourceSnapshotId))
          .findFirst()
          .orElseThrow();
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static List<String> listSnapshots(final Path partitionRoot) {
    try (final var stream =
        Files.list(partitionRoot.resolve(FileBasedSnapshotStoreImpl.SNAPSHOTS_DIRECTORY))) {
      return stream.filter(Files::isDirectory).map(dir -> dir.getFileName().toString()).toList();
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
