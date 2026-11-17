/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.snapshots.impl;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.snapshots.SnapshotChunk;
import java.nio.ByteBuffer;
import org.junit.Test;

public final class IncrementalChecksumsTest {

  private final IncrementalChecksums checksums = new IncrementalChecksums();

  @Test
  public void shouldCalculateChecksumFromSequentialChunks() {
    // when
    checksums.update(chunk("file", "ab", 0, 4));
    checksums.update(chunk("file", "cd", 2, 4));

    // then
    assertThat(checksums.complete().getChecksums())
        .containsExactlyEntriesOf(
            new java.util.TreeMap<>(java.util.Map.of("file", checksum("abcd"))));
  }

  @Test
  public void shouldCalculateChecksumsForInterleavedFiles() {
    // when
    checksums.update(chunk("file1", "ab", 0, 4));
    checksums.update(chunk("file2", "12", 0, 4));
    checksums.update(chunk("file1", "cd", 2, 4));
    checksums.update(chunk("file2", "34", 2, 4));

    // then
    assertThat(checksums.complete().getChecksums())
        .containsExactlyEntriesOf(
            new java.util.TreeMap<>(
                java.util.Map.of(
                    "file1", checksum("abcd"),
                    "file2", checksum("1234"))));
  }

  @Test
  public void shouldRejectFirstChunkWithNonZeroOffset() {
    assertThatThrownBy(() -> checksums.update(chunk("file", "ab", 1, 3)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Expected first chunk at offset 0 but got 1");
  }

  @Test
  public void shouldRejectNonSequentialChunk() {
    // given
    checksums.update(chunk("file", "ab", 0, 4));

    // when/then
    assertThatThrownBy(() -> checksums.update(chunk("file", "d", 3, 4)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Expected next chunk at offset 2 but got 3");
  }

  @Test
  public void shouldRejectDuplicatedChunk() {
    // given
    final var chunk = chunk("file", "ab", 0, 4);
    checksums.update(chunk);

    // when/then
    assertThatThrownBy(() -> checksums.update(chunk))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Expected next chunk at offset 2 but got 0");
  }

  @Test
  public void shouldRejectChangedTotalFileSize() {
    // given
    checksums.update(chunk("file", "ab", 0, 4));

    // when/then
    assertThatThrownBy(() -> checksums.update(chunk("file", "cd", 2, 5)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Expected chunk to match totalSize 4 but got 5");
  }

  @Test
  public void shouldRejectChunkExceedingTotalFileSize() {
    assertThatThrownBy(() -> checksums.update(chunk("file", "abc", 0, 2)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Chunk size 3 + current size 0 exceeds total size 2");
  }

  @Test
  public void shouldRejectIncompleteChecksumCollection() {
    // given
    checksums.update(chunk("file", "ab", 0, 4));

    // when/then
    assertThatThrownBy(checksums::complete)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Checksum for file file is not complete (had size 2, expected size 4)");
  }

  @Test
  public void shouldHandleEmptyFile() {
    // when
    checksums.update(chunk("file", "", 0, 0));

    // then
    assertThat(checksums.complete().getChecksums()).containsEntry("file", checksum(""));
  }

  private static SnapshotChunk chunk(
      final String fileName, final String content, final long offset, final long totalSize) {
    return SnapshotChunkUtil.createSnapshotChunkFromFileChunk(
        "1-0-0-0-0-0", 1, fileName, ByteBuffer.wrap(content.getBytes(UTF_8)), offset, totalSize);
  }

  private static long checksum(final String content) {
    return SnapshotChunkUtil.createChecksum(content.getBytes(UTF_8));
  }
}
