/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.snapshots.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.zip.CRC32C;
import java.util.zip.Checksum;
import org.agrona.IoUtil;
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

    createChunk(singleFileSnapshot, "file1.txt");

    createChunk(multipleFileSnapshot, "file1.txt");
    createChunk(multipleFileSnapshot, "file2.txt");
    createChunk(multipleFileSnapshot, "file3.txt");

    createChunk(corruptedSnapshot, "file1.txt");
    createChunk(corruptedSnapshot, "file2.txt");
    createChunk(corruptedSnapshot, "file3.txt");
  }

  static void createChunk(final Path snapshot, final String chunkName) throws IOException {
    Files.writeString(
        snapshot.resolve(chunkName),
        chunkName,
        StandardOpenOption.CREATE,
        StandardOpenOption.WRITE);
  }

  @Test
  public void shouldGenerateTheSameChecksumForOneFile() throws Exception {
    // given
    final var expectedChecksum = SnapshotChecksum.calculate(singleFileSnapshot).getCombinedValue();

    // when
    final var actual = SnapshotChecksum.calculate(singleFileSnapshot).getCombinedValue();

    // then
    assertThat(actual).isEqualTo(expectedChecksum);
  }

  @Test
  public void shouldGenerateDifferentChecksumWhenFileNameIsDifferent() throws Exception {
    // given
    final var expectedChecksum = SnapshotChecksum.calculate(singleFileSnapshot).getCombinedValue();

    // when
    Files.move(singleFileSnapshot.resolve("file1.txt"), singleFileSnapshot.resolve("renamed"));
    final var actual = SnapshotChecksum.calculate(singleFileSnapshot).getCombinedValue();

    // then
    assertThat(actual).isNotEqualTo(expectedChecksum);
  }

  @Test
  public void shouldGenerateTheSameChecksumForMultipleFiles() throws Exception {
    // given
    final var expectedChecksum =
        SnapshotChecksum.calculate(multipleFileSnapshot).getCombinedValue();

    // when
    final var actual = SnapshotChecksum.calculate(multipleFileSnapshot).getCombinedValue();

    // then
    assertThat(actual).isEqualTo(expectedChecksum);
  }

  @Test
  public void shouldGenerateDifferentChecksumForDifferentFiles() throws Exception {
    // given
    final var expectedChecksum = SnapshotChecksum.calculate(singleFileSnapshot).getCombinedValue();

    // when
    final var actual = SnapshotChecksum.calculate(multipleFileSnapshot).getCombinedValue();

    // then
    assertThat(actual).isNotEqualTo(expectedChecksum);
  }

  @Test
  public void shouldPersistChecksum() throws Exception {
    // given
    final var expectedChecksum = SnapshotChecksum.calculate(multipleFileSnapshot);
    final var checksumPath = multipleFileSnapshot.resolveSibling("checksum");
    SnapshotChecksum.persist(checksumPath, expectedChecksum);

    // when
    final var actual = SnapshotChecksum.read(checksumPath);

    // then
    assertThat(actual.getCombinedValue()).isEqualTo(expectedChecksum.getCombinedValue());
  }

  @Test
  public void shouldDetectCorruptedSnapshot() throws IOException {
    // given
    final var expectedChecksum = SnapshotChecksum.calculate(corruptedSnapshot);
    final var checksumPath = corruptedSnapshot.resolveSibling("checksum");
    SnapshotChecksum.persist(checksumPath, expectedChecksum);

    // when
    Files.delete(corruptedSnapshot.resolve("file1.txt"));
    final var actualChecksum = SnapshotChecksum.calculate(corruptedSnapshot).getCombinedValue();

    // then
    assertThat(actualChecksum).isNotEqualTo(expectedChecksum);
  }

  @Test
  public void shouldCalculateSameChecksumOfLargeFile() throws IOException {
    // given
    final var largeSnapshot = temporaryFolder.newFolder().toPath();
    final Path file = largeSnapshot.resolve("file");
    final String largeData = "a".repeat(4 * IoUtil.BLOCK_SIZE + 100);
    Files.writeString(file, largeData, StandardOpenOption.CREATE, StandardOpenOption.WRITE);

    final Checksum checksum = new CRC32C();
    checksum.update("file".getBytes(StandardCharsets.UTF_8));
    checksum.update(Files.readAllBytes(file));
    final var expected = checksum.getValue();

    // when
    final var actual = SnapshotChecksum.calculate(largeSnapshot).getCombinedValue();

    // then
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void shouldReadFormerSimpleChecksumFile() throws IOException {
    // given
    final Path temp = temporaryFolder.newFolder().toPath();
    final File tempFile = new File(temp.toFile(), "checksum");
    final long expectedChecksum = 0xccaaffeeL;
    try (final RandomAccessFile file = new RandomAccessFile(tempFile, "rw"); ) {
      file.writeLong(expectedChecksum);
    }

    // when
    final SfvChecksum sfvChecksum = SnapshotChecksum.read(tempFile.toPath());
    final long combinedValue = sfvChecksum.getCombinedValue();

    // then
    assertThat(combinedValue).isEqualTo(expectedChecksum);
  }

  @Test
  public void shouldWriteTheNumberOfFiles() throws IOException {
    // given
    final var folder = temporaryFolder.newFolder().toPath();
    createChunk(folder, "file1.txt");
    createChunk(folder, "file2.txt");
    createChunk(folder, "file3.txt");
    final SfvChecksum sfvChecksum = SnapshotChecksum.calculate(folder);

    // when
    final String serialized =
        new String(sfvChecksum.serializeSfvFileData(), StandardCharsets.UTF_8);

    // then
    assertThat(serialized).contains("; number of files used for combined value = 3");
  }
}
