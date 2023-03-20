/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.testkit.support;

import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import io.camunda.zeebe.backup.api.Backup;
import io.camunda.zeebe.backup.common.BackupDescriptorImpl;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.common.BackupImpl;
import io.camunda.zeebe.backup.common.NamedFileSetImpl;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.commons.lang3.RandomUtils;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;

public final class TestBackupProvider implements ArgumentsProvider {

  @Override
  public Stream<? extends Arguments> provideArguments(final ExtensionContext context)
      throws Exception {
    return Stream.of(
        arguments(named("stub", simpleBackup())),
        arguments(named("stub without snapshot", backupWithoutSnapshot())));
  }

  public Backup backupWithoutSnapshot() throws IOException {
    final var tempDir = Files.createTempDirectory("backup");
    Files.createDirectory(tempDir.resolve("segments/"));
    final var seg1 = Files.createFile(tempDir.resolve("segments/segment-file-1"));
    final var seg2 = Files.createFile(tempDir.resolve("segments/segment-file-2"));
    Files.write(seg1, RandomUtils.nextBytes(1024));
    Files.write(seg2, RandomUtils.nextBytes(1024));

    return new BackupImpl(
        new BackupIdentifierImpl(1, 2, 3),
        new BackupDescriptorImpl(Optional.empty(), 4, 5, "test"),
        new NamedFileSetImpl(Map.of()),
        new NamedFileSetImpl(Map.of("segment-file-1", seg1, "segment-file-2", seg2)));
  }

  public Backup simpleBackup() throws IOException {
    return simpleBackupWithId(new BackupIdentifierImpl(1, 2, 3));
  }

  public Backup simpleBackupWithId(final BackupIdentifierImpl id) throws IOException {
    final var tempDir = Files.createTempDirectory("backup");
    Files.createDirectory(tempDir.resolve("segments/"));
    final var seg1 = Files.createFile(tempDir.resolve("segments/segment-file-1"));
    final var seg2 = Files.createFile(tempDir.resolve("segments/segment-file-2"));
    Files.write(seg1, RandomUtils.nextBytes(1024));
    Files.write(seg2, RandomUtils.nextBytes(1024));

    Files.createDirectory(tempDir.resolve("snapshot/"));
    final var s1 = Files.createFile(tempDir.resolve("snapshot/snapshot-file-1"));
    final var s2 = Files.createFile(tempDir.resolve("snapshot/snapshot-file-2"));
    Files.write(s1, RandomUtils.nextBytes(1024));
    Files.write(s2, RandomUtils.nextBytes(1024));

    return new BackupImpl(
        id,
        new BackupDescriptorImpl(Optional.of("test-snapshot-id"), 4, 5, "test"),
        new NamedFileSetImpl(Map.of("snapshot-file-1", s1, "snapshot-file-2", s2)),
        new NamedFileSetImpl(Map.of("segment-file-1", seg1, "segment-file-2", seg2)));
  }

  public Backup minimalBackupWithId(final BackupIdentifierImpl id) throws IOException {
    final var tempDir = Files.createTempDirectory("backup");
    Files.createDirectory(tempDir.resolve("segments/"));
    final var seg1 = Files.createFile(tempDir.resolve("segments/segment-file-1"));
    Files.write(seg1, RandomUtils.nextBytes(1));

    return new BackupImpl(
        id,
        new BackupDescriptorImpl(Optional.empty(), 4, 5, "test"),
        new NamedFileSetImpl(Map.of()),
        new NamedFileSetImpl(Map.of("segment-file-1", seg1)));
  }
}
