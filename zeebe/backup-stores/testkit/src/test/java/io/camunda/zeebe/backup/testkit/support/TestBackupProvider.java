/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.testkit.support;

import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import io.camunda.zeebe.backup.api.Backup;
import io.camunda.zeebe.backup.common.BackupDescriptorImpl;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.common.BackupImpl;
import io.camunda.zeebe.backup.common.NamedFileSetImpl;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import io.camunda.zeebe.util.FileUtil;
import io.camunda.zeebe.util.VersionUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.commons.lang3.RandomUtils;
import org.junit.jupiter.params.provider.Arguments;

public final class TestBackupProvider {

  private static final Path tempDir;

  static {
    try {
      tempDir = Files.createTempDirectory(TestBackupProvider.class.getSimpleName());
      Runtime.getRuntime()
          .addShutdownHook(
              new Thread(
                  () -> {
                    try {
                      FileUtil.deleteFolderIfExists(tempDir);
                    } catch (final IOException e) {
                      throw new RuntimeException(e);
                    }
                  }));
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static Stream<? extends Arguments> provideArguments() throws Exception {
    return Stream.of(
        arguments(named("stub", simpleBackup())),
        arguments(named("stub without snapshot", backupWithoutSnapshot())));
  }

  public static Backup backupWithoutSnapshot() throws IOException {
    final var backupDir = Files.createTempDirectory(tempDir, "backup");
    Files.createDirectory(backupDir.resolve("segments/"));
    final var seg1 = Files.createFile(backupDir.resolve("segments/segment-file-1"));
    final var seg2 = Files.createFile(backupDir.resolve("segments/segment-file-2"));
    Files.write(seg1, RandomUtils.nextBytes(1024));
    Files.write(seg2, RandomUtils.nextBytes(1024));

    return new BackupImpl(
        new BackupIdentifierImpl(1, 2, 3),
        new BackupDescriptorImpl(
            4,
            5,
            VersionUtil.getVersion(),
            Instant.now().truncatedTo(ChronoUnit.MILLIS),
            CheckpointType.MANUAL_BACKUP),
        new NamedFileSetImpl(Map.of()),
        new NamedFileSetImpl(Map.of("segment-file-1", seg1, "segment-file-2", seg2)));
  }

  public static Backup simpleBackup() throws IOException {
    return simpleBackupWithId(new BackupIdentifierImpl(1, 2, 3));
  }

  public static Backup simpleBackupWithId(final BackupIdentifierImpl id) throws IOException {
    final var backupDir = Files.createTempDirectory(tempDir, "backup");
    Files.createDirectory(backupDir.resolve("segments/"));
    final var seg1 = Files.createFile(backupDir.resolve("segments/segment-file-1"));
    final var seg2 = Files.createFile(backupDir.resolve("segments/segment-file-2"));
    Files.write(seg1, RandomUtils.nextBytes(1024));
    Files.write(seg2, RandomUtils.nextBytes(1024));

    Files.createDirectory(backupDir.resolve("snapshot/"));
    final var s1 = Files.createFile(backupDir.resolve("snapshot/snapshot-file-1"));
    final var s2 = Files.createFile(backupDir.resolve("snapshot/snapshot-file-2"));
    Files.write(s1, RandomUtils.nextBytes(1024));
    Files.write(s2, RandomUtils.nextBytes(1024));

    return new BackupImpl(
        id,
        new BackupDescriptorImpl(
            "test-snapshot-id",
            4,
            5,
            VersionUtil.getVersion(),
            Instant.now().truncatedTo(ChronoUnit.MILLIS),
            CheckpointType.MANUAL_BACKUP),
        new NamedFileSetImpl(Map.of("snapshot-file-1", s1, "snapshot-file-2", s2)),
        new NamedFileSetImpl(Map.of("segment-file-1", seg1, "segment-file-2", seg2)));
  }

  public static Backup minimalBackupWithId(final BackupIdentifierImpl id) throws IOException {
    final var backupDir = Files.createTempDirectory(tempDir, "backup");
    Files.createDirectory(backupDir.resolve("segments/"));
    final var seg1 = Files.createFile(backupDir.resolve("segments/segment-file-1"));
    Files.write(seg1, RandomUtils.nextBytes(1));

    return new BackupImpl(
        id,
        new BackupDescriptorImpl(
            4,
            5,
            VersionUtil.getVersion(),
            Instant.now().truncatedTo(ChronoUnit.MILLIS),
            CheckpointType.MANUAL_BACKUP),
        new NamedFileSetImpl(Map.of()),
        new NamedFileSetImpl(Map.of("segment-file-1", seg1)));
  }
}
