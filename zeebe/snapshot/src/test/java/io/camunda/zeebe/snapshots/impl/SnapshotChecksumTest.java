/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.snapshots.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.snapshots.ImmutableChecksumsSFV;
import io.camunda.zeebe.test.util.STracer;
import io.camunda.zeebe.test.util.STracer.Syscall;
import io.camunda.zeebe.test.util.asserts.strace.FSyncTraceAssert;
import io.camunda.zeebe.test.util.asserts.strace.STracerAssert;
import io.camunda.zeebe.test.util.junit.StraceTest;
import io.camunda.zeebe.util.FileUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.zip.CRC32C;
import java.util.zip.Checksum;
import org.agrona.IoUtil;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
    final var expectedChecksums = SnapshotChecksum.calculate(singleFileSnapshot);

    // when
    final var actual = SnapshotChecksum.calculate(singleFileSnapshot);

    // then
    assertThat(expectedChecksums.sameChecksums(actual)).isTrue();
  }

  @Test
  void shouldGenerateDifferentChecksumWhenFileNameIsDifferent() throws Exception {
    // given
    final var expectedChecksum = SnapshotChecksum.calculate(singleFileSnapshot);

    // when
    Files.move(singleFileSnapshot.resolve("file1.txt"), singleFileSnapshot.resolve("renamed"));
    final var actual = SnapshotChecksum.calculate(singleFileSnapshot);

    // then
    assertThat(expectedChecksum.sameChecksums(actual)).isFalse();
  }

  @Test
  void shouldGenerateTheSameChecksumForMultipleFiles() throws Exception {
    // given
    final var expectedChecksum = SnapshotChecksum.calculate(multipleFileSnapshot);

    // when
    final var actual = SnapshotChecksum.calculate(multipleFileSnapshot);

    // then
    assertThat(expectedChecksum.sameChecksums(actual)).isTrue();
  }

  @Test
  void shouldGenerateDifferentChecksumForDifferentFiles() throws Exception {
    // given
    final var expectedChecksum = SnapshotChecksum.calculate(singleFileSnapshot);

    // when
    final var actual = SnapshotChecksum.calculate(multipleFileSnapshot);

    // then
    assertThat(expectedChecksum.sameChecksums(actual)).isFalse();
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
    assertThat(expectedChecksum.sameChecksums(actual)).isTrue();
  }

  /**
   * This test is only enabled on CI as it relies on strace. You can run it manually directly if
   * your system supports strace. See {@link io.camunda.zeebe.test.util.STracer} for more.
   */
  @StraceTest
  void shouldFlushOnPersist() throws Exception {
    // given
    final var traceFile = temporaryFolder.resolve("traceFile");
    final var expectedChecksum = SnapshotChecksum.calculate(multipleFileSnapshot);
    final var checksumPath = multipleFileSnapshot.resolveSibling("checksum");
    final var tracer = STracer.tracerFor(Syscall.FSYNC, traceFile);

    // when
    try (tracer) {
      SnapshotChecksum.persist(checksumPath, expectedChecksum);

      // then
      Awaitility.await("until fsync was called for our checksum file")
          .pollInSameThread()
          .pollInterval(Duration.ofMillis(50))
          .untilAsserted(
              () ->
                  STracerAssert.assertThat(tracer)
                      .fsyncTraces()
                      .hasSize(1)
                      .first(FSyncTraceAssert.factory())
                      .hasPath(checksumPath));
    }
  }

  @Test
  void shouldDetectCorruptedSnapshot() throws IOException {
    // given
    final var expectedChecksum = SnapshotChecksum.calculate(corruptedSnapshot);
    final var checksumPath = corruptedSnapshot.resolveSibling("checksum");
    SnapshotChecksum.persist(checksumPath, expectedChecksum);

    // when
    Files.delete(corruptedSnapshot.resolve("file1.txt"));
    final var actualChecksum = SnapshotChecksum.calculate(corruptedSnapshot);

    // then
    assertThat(expectedChecksum.sameChecksums(actualChecksum)).isFalse();
  }

  @Test
  void shouldCalculateSameChecksumOfLargeFile() throws IOException {
    // given
    final var largeSnapshot = createTempDir("large");
    final Path file = largeSnapshot.resolve("file");
    final String largeData = "a".repeat(4 * IoUtil.BLOCK_SIZE + 100);
    Files.writeString(file, largeData, StandardOpenOption.CREATE, StandardOpenOption.WRITE);

    final Checksum checksum = new CRC32C();
    checksum.update(Files.readAllBytes(file));
    final var expected = checksum.getValue();

    // when
    final var actual = SnapshotChecksum.calculate(largeSnapshot);

    // then
    assertThat(actual.getChecksums().get("file")).isEqualTo(expected);
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
    createChunk(folder, FileBasedSnapshotStoreImpl.METADATA_FILE_NAME);
    // This is how checksum is calculated when persisting a transient snapshot
    checksumCalculatedInSteps.updateFromFile(
        folder.resolve(FileBasedSnapshotStoreImpl.METADATA_FILE_NAME));

    // This is how checksum is calculated when verifying
    final ImmutableChecksumsSFV checksumCalculatedAtOnce = SnapshotChecksum.calculate(folder);

    // then
    assertThat(checksumCalculatedInSteps.sameChecksums(checksumCalculatedAtOnce)).isTrue();
  }

  private Path createTempDir(final String name) throws IOException {
    final var path = temporaryFolder.resolve(name);
    FileUtil.ensureDirectoryExists(path);
    return path;
  }
}
