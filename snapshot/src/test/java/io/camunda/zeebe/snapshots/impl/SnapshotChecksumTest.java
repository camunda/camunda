/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.snapshots.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.snapshots.ImmutableChecksumsSFV;
import io.camunda.zeebe.test.util.STracer;
import io.camunda.zeebe.test.util.STracer.Syscall;
import io.camunda.zeebe.test.util.asserts.strace.FSyncTraceAssert;
import io.camunda.zeebe.test.util.asserts.strace.STracerAssert;
import io.camunda.zeebe.util.FileUtil;
import java.io.ByteArrayOutputStream;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

public final class SnapshotChecksumTest {

  private @TempDir Path temporaryFolder;

  private Path singleFileSnapshot;
  private Path multipleFileSnapshot;
  private Path corruptedSnapshot;

  @BeforeEach
  void setup() throws Exception {
    singleFileSnapshot = createTempDir("single");
    multipleFileSnapshot = createTempDir("multi");
    corruptedSnapshot = createTempDir("corrupted");

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
  void shouldGenerateTheSameChecksumForOneFile() throws Exception {
    // given
    final var expectedChecksum = SnapshotChecksum.calculate(singleFileSnapshot).getCombinedValue();

    // when
    final var actual = SnapshotChecksum.calculate(singleFileSnapshot).getCombinedValue();

    // then
    assertThat(actual).isEqualTo(expectedChecksum);
  }

  @Test
  void shouldGenerateDifferentChecksumWhenFileNameIsDifferent() throws Exception {
    // given
    final var expectedChecksum = SnapshotChecksum.calculate(singleFileSnapshot).getCombinedValue();

    // when
    Files.move(singleFileSnapshot.resolve("file1.txt"), singleFileSnapshot.resolve("renamed"));
    final var actual = SnapshotChecksum.calculate(singleFileSnapshot).getCombinedValue();

    // then
    assertThat(actual).isNotEqualTo(expectedChecksum);
  }

  @Test
  void shouldGenerateTheSameChecksumForMultipleFiles() throws Exception {
    // given
    final var expectedChecksum =
        SnapshotChecksum.calculate(multipleFileSnapshot).getCombinedValue();

    // when
    final var actual = SnapshotChecksum.calculate(multipleFileSnapshot).getCombinedValue();

    // then
    assertThat(actual).isEqualTo(expectedChecksum);
  }

  @Test
  void shouldGenerateDifferentChecksumForDifferentFiles() throws Exception {
    // given
    final var expectedChecksum = SnapshotChecksum.calculate(singleFileSnapshot).getCombinedValue();

    // when
    final var actual = SnapshotChecksum.calculate(multipleFileSnapshot).getCombinedValue();

    // then
    assertThat(actual).isNotEqualTo(expectedChecksum);
  }

  @Test
  void shouldPersistChecksum() throws Exception {
    // given
    final var expectedChecksum = SnapshotChecksum.calculate(multipleFileSnapshot);
    final var checksumPath = multipleFileSnapshot.resolveSibling("checksum");
    SnapshotChecksum.persist(checksumPath, expectedChecksum);

    // when
    final var actual = SnapshotChecksum.read(checksumPath);

    // then
    assertThat(actual.getCombinedValue()).isEqualTo(expectedChecksum.getCombinedValue());
  }

  @EnabledOnOs(OS.LINUX)
  @Test
  void shouldFlushOnPersist() throws Exception {
    // given
    final var traceFile = temporaryFolder.resolve("traceFile");
    final var expectedChecksum = SnapshotChecksum.calculate(multipleFileSnapshot);
    final var checksumPath = multipleFileSnapshot.resolveSibling("checksum");
    final var tracer = STracer.traceFor(Syscall.FSYNC, traceFile);

    // when
    try (tracer) {
      SnapshotChecksum.persist(checksumPath, expectedChecksum);
    }

    // then
    STracerAssert.assertThat(tracer)
        .fsyncTraces()
        .hasSize(1)
        .first(FSyncTraceAssert.factory())
        .hasPath(checksumPath)
        .isSuccessful();
  }

  @Test
  void shouldDetectCorruptedSnapshot() throws IOException {
    // given
    final var expectedChecksum = SnapshotChecksum.calculate(corruptedSnapshot);
    final var checksumPath = corruptedSnapshot.resolveSibling("checksum");
    SnapshotChecksum.persist(checksumPath, expectedChecksum);

    // when
    Files.delete(corruptedSnapshot.resolve("file1.txt"));
    final var actualChecksum = SnapshotChecksum.calculate(corruptedSnapshot).getCombinedValue();

    // then
    assertThat(actualChecksum).isNotEqualTo(expectedChecksum.getCombinedValue());
  }

  @Test
  void shouldCalculateSameChecksumOfLargeFile() throws IOException {
    // given
    final var largeSnapshot = createTempDir("large");
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
  void shouldReadFormerSimpleChecksumFile() throws IOException {
    // given
    final Path temp = createTempDir("temp");
    final File tempFile = new File(temp.toFile(), "checksum");
    final long expectedChecksum = 0xccaaffeeL;
    try (final RandomAccessFile file = new RandomAccessFile(tempFile, "rw")) {
      file.writeLong(expectedChecksum);
    }

    // when
    final ImmutableChecksumsSFV sfvChecksum = SnapshotChecksum.read(tempFile.toPath());
    final long combinedValue = sfvChecksum.getCombinedValue();

    // then
    assertThat(combinedValue).isEqualTo(expectedChecksum);
  }

  @Test
  void shouldWriteTheNumberOfFiles() throws IOException {
    // given
    final var folder = createTempDir("folder");
    createChunk(folder, "file1.txt");
    createChunk(folder, "file2.txt");
    createChunk(folder, "file3.txt");
    final ImmutableChecksumsSFV sfvChecksum = SnapshotChecksum.calculate(folder);
    final var arrayOutputStream = new ByteArrayOutputStream();
    sfvChecksum.write(arrayOutputStream);

    // when
    final String serialized = arrayOutputStream.toString(StandardCharsets.UTF_8);

    // then
    assertThat(serialized).contains("; number of files used for combined value = 3");
  }

  @Test
  void shouldAddChecksumOfMetadataAtTheEnd() throws IOException {
    // given
    final var folder = createTempDir("folder");
    createChunk(folder, "file1.txt");
    createChunk(folder, "file2.txt");
    createChunk(folder, "file3.txt");
    final var checksumCalculatedInSteps = SnapshotChecksum.calculate(folder);

    // when
    createChunk(folder, FileBasedSnapshotStore.METADATA_FILE_NAME);
    // This is how checksum is calculated when persisting a transient snapshot
    checksumCalculatedInSteps.updateFromFile(
        folder.resolve(FileBasedSnapshotStore.METADATA_FILE_NAME));

    // This is how checksum is calculated when verifying
    final ImmutableChecksumsSFV checksumCalculatedAtOnce = SnapshotChecksum.calculate(folder);

    // then
    assertThat(checksumCalculatedInSteps.getCombinedValue())
        .isEqualTo(checksumCalculatedAtOnce.getCombinedValue());
  }

  private Path createTempDir(final String name) throws IOException {
    final var path = temporaryFolder.resolve(name);
    FileUtil.ensureDirectoryExists(path);
    return path;
  }
}
