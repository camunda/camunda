/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.snapshots.impl;

import static io.camunda.zeebe.snapshots.impl.SnapshotChecksumTest.createChunk;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class SfvChecksumTest {

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  private SfvChecksumImpl sfvChecksum;

  @Before
  public void setUp() throws Exception {
    sfvChecksum = new SfvChecksumImpl();
  }

  @Test
  public void shouldUseDefaultValueZeroWhenInitialized() {
    // given
    // when
    // then
    assertThat(sfvChecksum.getCombinedValue()).isEqualTo(0);
  }

  @Test
  public void shouldReadCombinedValueFromComment() {
    // given
    final String[] sfvLines = {"; a simple comment to be ignored", "; combinedValue = bbaaccdd"};

    // when
    sfvChecksum.updateFromSfvFile(sfvLines);

    // then
    assertThat(sfvChecksum.getCombinedValue()).isEqualTo(0xbbaaccddL);
  }

  @Test
  public void shouldReadAndWriteSameValues() throws IOException {
    // given
    final String[] givenSfvLines = {"; combinedValue = 12345678", "file1   aabbccdd"};
    sfvChecksum.updateFromSfvFile(givenSfvLines);

    final var arrayOutputStream = new ByteArrayOutputStream();
    sfvChecksum.write(arrayOutputStream);
    final String serialized = arrayOutputStream.toString(StandardCharsets.UTF_8);

    // when
    final String[] actualSfVlines = serialized.split(System.lineSeparator());

    // then
    assertThat(actualSfVlines).contains(givenSfvLines[0]);
    assertThat(actualSfVlines).contains(givenSfvLines[1]);
  }

  @Test
  public void shouldThrowExceptionOnWriteWhenFlushFails() throws IOException {
    // given
    final String[] givenSfvLines = {"; combinedValue = 12345678", "file1   aabbccdd"};
    sfvChecksum.updateFromSfvFile(givenSfvLines);

    // when then throw
    try (final var failingStream = new FailingFlushOutputStream()) {
      assertThatThrownBy(() -> sfvChecksum.write(failingStream)).isInstanceOf(IOException.class);
    }
  }

  @Test
  public void shouldThrowExceptionOnWriteWhenWriteFails() throws IOException {
    // given
    final String[] givenSfvLines = {"; combinedValue = 12345678", "file1   aabbccdd"};
    sfvChecksum.updateFromSfvFile(givenSfvLines);

    // when then throw
    try (final var failingStream = new FailingWriteOutputStream()) {
      assertThatThrownBy(() -> sfvChecksum.write(failingStream)).isInstanceOf(IOException.class);
    }
  }

  @Test
  public void shouldWriteSnapshotDirectoryCommentIfPresent() throws IOException {
    // given
    sfvChecksum.setSnapshotDirectoryComment("/foo/bar");
    final var arrayOutputStream = new ByteArrayOutputStream();
    sfvChecksum.write(arrayOutputStream);

    // when
    final String serialized = arrayOutputStream.toString(StandardCharsets.UTF_8);

    // then
    assertThat(serialized).contains("; snapshot directory = /foo/bar");
  }

  @Test
  public void shouldContainHumanReadableInstructions() throws IOException {
    // given
    final var arrayOutputStream = new ByteArrayOutputStream();
    sfvChecksum.write(arrayOutputStream);

    // when
    final String serialized = arrayOutputStream.toString(StandardCharsets.UTF_8);

    // then
    assertThat(serialized)
        .contains("; This is an SFC checksum file for all files in the given directory.");
    assertThat(serialized)
        .contains("; You might use cksfv or another tool to validate these files manually.");
    assertThat(serialized)
        .contains("; This is an automatically created file - please do NOT modify.");
  }

  @Test
  public void shouldThrowExceptionWhenUsingPreDefinedChecksumFromSfv() throws IOException {
    // given
    final var folder = temporaryFolder.newFolder().toPath();
    createChunk(folder, "file1.txt");

    // when
    sfvChecksum.updateFromSfvFile("; combinedValue = 12341234");

    // then
    assertThatThrownBy(() -> sfvChecksum.updateFromFile(folder))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  private static final class FailingFlushOutputStream extends OutputStream {

    @Override
    public void write(final int i) throws IOException {
      // do nothing
    }

    @Override
    public void flush() throws IOException {
      throw new IOException("expected");
    }
  }

  private static final class FailingWriteOutputStream extends OutputStream {
    @Override
    public void write(final int i) throws IOException {
      throw new IOException("expected");
    }
  }
}
