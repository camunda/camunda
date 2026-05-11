/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.bootstrap;

import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.util.FileUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class PartitionGroupMigrationStep extends AbstractBrokerStartupStep {

  static final String OLD_GROUP_NAME = "raft-partition";
  static final String NEW_GROUP_NAME = "default";

  private static final String OLD_PREFIX_FORMAT = OLD_GROUP_NAME + "-partition-%s";
  private static final String NEW_PREFIX_FORMAT = NEW_GROUP_NAME + "-partition-%s";

  private static final Logger LOG = LoggerFactory.getLogger(PartitionGroupMigrationStep.class);

  @Override
  public String getName() {
    return "Partition Group Migration";
  }

  @Override
  void startupInternal(
      final BrokerStartupContext brokerStartupContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> startupFuture) {
    final var dataDir =
        Path.of(brokerStartupContext.getBrokerConfiguration().getData().getDirectory());
    final var oldGroupDir = dataDir.resolve(OLD_GROUP_NAME);
    final var newGroupDir = dataDir.resolve(NEW_GROUP_NAME);

    final var oldExists = Files.exists(oldGroupDir);
    final var newExists = Files.exists(newGroupDir);

    if (oldExists && newExists) {
      startupFuture.completeExceptionally(
          new IllegalStateException(
              "Both '%s' and '%s' partition group directories exist in %s. "
                  .formatted(OLD_GROUP_NAME, NEW_GROUP_NAME, dataDir)));
      return;
    }

    if (!oldExists) {
      startupFuture.complete(brokerStartupContext);
      return;
    }

    try {
      migrate(oldGroupDir, newGroupDir);
      startupFuture.complete(brokerStartupContext);
    } catch (final IOException e) {
      startupFuture.completeExceptionally(e);
    }
  }

  @Override
  void shutdownInternal(
      final BrokerStartupContext brokerShutdownContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> shutdownFuture) {
    shutdownFuture.complete(brokerShutdownContext);
  }

  private void migrate(final Path oldGroupDir, final Path newGroupDir) throws IOException {
    LOG.info(
        "Migrating partition group directory from '{}' to '{}'", OLD_GROUP_NAME, NEW_GROUP_NAME);

    final var partitionsDir = oldGroupDir.resolve("partitions");
    if (Files.exists(partitionsDir)) {
      try (final var partitions = Files.newDirectoryStream(partitionsDir, Files::isDirectory)) {
        for (final var partitionDir : partitions) {
          migratePartitionFiles(partitionDir);
        }
      }
    }

    FileUtil.moveDurably(oldGroupDir, newGroupDir);
    LOG.info("Completed migration from '{}' to '{}'", OLD_GROUP_NAME, NEW_GROUP_NAME);
  }

  private void migratePartitionFiles(final Path partitionDir) throws IOException {
    final var partitionId = partitionDir.getFileName().toString();
    final var oldPrefix = OLD_PREFIX_FORMAT.formatted(partitionId);
    final var newPrefix = NEW_PREFIX_FORMAT.formatted(partitionId);

    LOG.debug("Migrating files for partition {}", partitionId);

    // rename .meta and .conf files
    renameIfExists(partitionDir, oldPrefix + ".meta", newPrefix + ".meta");
    renameIfExists(partitionDir, oldPrefix + ".conf", newPrefix + ".conf");

    // rename lock files
    renameIfExists(partitionDir, "." + oldPrefix + ".lock", "." + newPrefix + ".lock");
    renameIfExists(partitionDir, "." + oldPrefix + ".lock.tmp", "." + newPrefix + ".lock.tmp");

    // rename .log and -deleted files by prefix match
    try (final var files =
        Files.newDirectoryStream(
            partitionDir, fileName -> journalFileRequiringCopy(fileName, oldPrefix))) {
      for (final var file : files) {
        final var fileName = file.getFileName().toString();
        final var newName = newPrefix + fileName.substring(oldPrefix.length());
        rename(partitionDir, fileName, newName);
      }
    }
    FileUtil.flushDirectory(partitionDir);
  }

  private static boolean journalFileRequiringCopy(final Path file, final String oldPrefix) {
    final var fileName = file.getFileName().toString();
    return fileName.startsWith(oldPrefix + "-")
        && (fileName.endsWith(".log") || fileName.endsWith("-deleted"));
  }

  private static void renameIfExists(final Path dir, final String oldName, final String newName)
      throws IOException {
    final var oldPath = dir.resolve(oldName);
    if (Files.exists(oldPath)) {
      rename(dir, oldName, newName);
    }
  }

  private static void rename(final Path dir, final String oldName, final String newName)
      throws IOException {
    final var oldPath = dir.resolve(oldName);
    final var newPath = dir.resolve(newName);
    LOG.trace("Renaming {} to {}", oldPath, newPath);
    Files.move(oldPath, newPath, StandardCopyOption.ATOMIC_MOVE);
  }
}
