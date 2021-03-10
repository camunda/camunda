/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.snapshots.broker.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class SnapshotChecksumTest {

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  private File singleFileSnapshot;
  private File multipleFileSnapshot;
  private File corruptedSnapshot;

  @Before
  public void setup() throws Exception {
    singleFileSnapshot = temporaryFolder.newFolder();
    multipleFileSnapshot = temporaryFolder.newFolder();
    corruptedSnapshot = temporaryFolder.newFolder();

    singleFileSnapshot.toPath().resolve("singleFile.txt").toFile().createNewFile();
    multipleFileSnapshot.toPath().resolve("file1.txt").toFile().createNewFile();
    multipleFileSnapshot.toPath().resolve("file2.txt").toFile().createNewFile();
    multipleFileSnapshot.toPath().resolve("file3.txt").toFile().createNewFile();

    corruptedSnapshot.toPath().resolve("file1.txt").toFile().createNewFile();
    corruptedSnapshot.toPath().resolve("file2.txt").toFile().createNewFile();
    corruptedSnapshot.toPath().resolve("file3.txt").toFile().createNewFile();
  }

  @Test
  public void shouldGenerateTheSameChecksumForOneFile() throws Exception {
    // given
    final var expectedChecksum = SnapshotChecksum.calculate(singleFileSnapshot.toPath());

    // when
    final var actual = SnapshotChecksum.calculate(singleFileSnapshot.toPath());

    // then
    assertThat(actual).isEqualTo(expectedChecksum);
  }

  @Test
  public void shouldGenerateTheSameChecksumForMultipleFiles() throws Exception {
    // given
    final var expectedChecksum = SnapshotChecksum.calculate(multipleFileSnapshot.toPath());

    // when
    final var actual = SnapshotChecksum.calculate(multipleFileSnapshot.toPath());

    // then
    assertThat(actual).isEqualTo(expectedChecksum);
  }

  @Test
  public void shouldGenerateDifferentChecksumForDifferentFiles() throws Exception {
    // given
    final var expectedChecksum = SnapshotChecksum.calculate(singleFileSnapshot.toPath());

    // when
    final var actual = SnapshotChecksum.calculate(multipleFileSnapshot.toPath());

    // then
    assertThat(actual).isNotEqualTo(expectedChecksum);
  }

  @Test
  public void shouldPersistChecksum() throws Exception {
    // given
    final var expectedChecksum = SnapshotChecksum.calculate(multipleFileSnapshot.toPath());
    SnapshotChecksum.persist(multipleFileSnapshot.toPath(), expectedChecksum);

    // when
    final var actual = SnapshotChecksum.read(multipleFileSnapshot.toPath());

    // then
    assertThat(actual).isEqualTo(expectedChecksum);
  }

  @Test
  public void shouldGenerateTheSameWithPersistedChecksum() throws Exception {
    // given
    final var expectedChecksum = SnapshotChecksum.calculate(multipleFileSnapshot.toPath());
    SnapshotChecksum.persist(multipleFileSnapshot.toPath(), expectedChecksum);

    // when
    final var actual = SnapshotChecksum.calculate(multipleFileSnapshot.toPath());

    // then
    assertThat(actual).isEqualTo(expectedChecksum);
  }

  @Test
  public void shouldDetectCorruptedSnapshot() throws IOException {
    // given
    final var expectedChecksum = SnapshotChecksum.calculate(corruptedSnapshot.toPath());
    SnapshotChecksum.persist(corruptedSnapshot.toPath(), expectedChecksum);

    // when
    corruptedSnapshot.toPath().resolve("file1.txt").toFile().delete();

    // then
    assertThat(SnapshotChecksum.verify(corruptedSnapshot.toPath())).isFalse();
  }
}
