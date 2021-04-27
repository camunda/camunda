/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.snapshots.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class SnapshotChecksumTest {

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  private Path singleFileSnapshot;
  private Path multipleFileSnapshot;
  private Path corruptedSnapshot;

  @Before
  public void setup() throws Exception {
    singleFileSnapshot = temporaryFolder.newFolder().toPath();
    multipleFileSnapshot = temporaryFolder.newFolder().toPath();
    corruptedSnapshot = temporaryFolder.newFolder().toPath();

    Files.createFile(singleFileSnapshot.resolve("singleFile.txt"));
    Files.createFile(multipleFileSnapshot.resolve("file1.txt"));
    Files.createFile(multipleFileSnapshot.resolve("file2.txt"));
    Files.createFile(multipleFileSnapshot.resolve("file3.txt"));

    Files.createFile(corruptedSnapshot.resolve("file1.txt"));
    Files.createFile(corruptedSnapshot.resolve("file2.txt"));
    Files.createFile(corruptedSnapshot.resolve("file3.txt"));
  }

  @Test
  public void shouldGenerateTheSameChecksumForOneFile() throws Exception {
    // given
    final var expectedChecksum = SnapshotChecksum.calculate(singleFileSnapshot);

    // when
    final var actual = SnapshotChecksum.calculate(singleFileSnapshot);

    // then
    assertThat(actual).isEqualTo(expectedChecksum);
  }

  @Test
  public void shouldGenerateTheSameChecksumForMultipleFiles() throws Exception {
    // given
    final var expectedChecksum = SnapshotChecksum.calculate(multipleFileSnapshot);

    // when
    final var actual = SnapshotChecksum.calculate(multipleFileSnapshot);

    // then
    assertThat(actual).isEqualTo(expectedChecksum);
  }

  @Test
  public void shouldGenerateDifferentChecksumForDifferentFiles() throws Exception {
    // given
    final var expectedChecksum = SnapshotChecksum.calculate(singleFileSnapshot);

    // when
    final var actual = SnapshotChecksum.calculate(multipleFileSnapshot);

    // then
    assertThat(actual).isNotEqualTo(expectedChecksum);
  }

  @Test
  public void shouldPersistChecksum() throws Exception {
    // given
    final var expectedChecksum = SnapshotChecksum.calculate(multipleFileSnapshot);
    SnapshotChecksum.persist(multipleFileSnapshot, expectedChecksum);

    // when
    final var actual = SnapshotChecksum.read(multipleFileSnapshot);

    // then
    assertThat(actual).isEqualTo(expectedChecksum);
  }

  @Test
  public void shouldGenerateTheSameWithPersistedChecksum() throws Exception {
    // given
    final var expectedChecksum = SnapshotChecksum.calculate(multipleFileSnapshot);
    SnapshotChecksum.persist(multipleFileSnapshot, expectedChecksum);

    // when
    final var actual = SnapshotChecksum.calculate(multipleFileSnapshot);

    // then
    assertThat(actual).isEqualTo(expectedChecksum);
  }

  @Test
  public void shouldDetectCorruptedSnapshot() throws IOException {
    // given
    final var expectedChecksum = SnapshotChecksum.calculate(corruptedSnapshot);
    SnapshotChecksum.persist(corruptedSnapshot, expectedChecksum);

    // when
    Files.delete(corruptedSnapshot.resolve("file1.txt"));

    // then
    assertThat(SnapshotChecksum.verify(corruptedSnapshot)).isFalse();
  }
}
