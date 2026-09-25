/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.s3.util;

import io.camunda.zeebe.backup.api.Backup;
import io.camunda.zeebe.backup.common.BackupDescriptorImpl;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.common.BackupImpl;
import io.camunda.zeebe.backup.common.NamedFileSetImpl;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.RandomUtils;

/**
 * Builds a {@link Backup} with a given broker version, for tests that need to assert on the exact
 * version stamped on the backup. This mirrors {@code
 * TestBackupProvider#simpleBackupWithId(BackupIdentifierImpl)}, which hardcodes the version.
 */
public final class S3TestBackupProvider {

  private S3TestBackupProvider() {}

  public static Backup simpleBackupWithId(final BackupIdentifierImpl id, final String version)
      throws IOException {
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
        new BackupDescriptorImpl(Optional.of("test-snapshot-id"), 4, 5, version),
        new NamedFileSetImpl(Map.of("snapshot-file-1", s1, "snapshot-file-2", s2)),
        new NamedFileSetImpl(Map.of("segment-file-1", seg1, "segment-file-2", seg2)));
  }
}
