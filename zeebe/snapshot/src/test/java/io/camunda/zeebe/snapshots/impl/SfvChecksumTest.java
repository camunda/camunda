/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.snapshots.impl;

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
  public void shouldReadAndWriteSameValues() throws IOException {
    // given
    final String[] givenSfvLines = {"file1   aabbccdd"};
    sfvChecksum.updateFromSfvFile(givenSfvLines);

    final var arrayOutputStream = new ByteArrayOutputStream();
    sfvChecksum.write(arrayOutputStream);
    final String serialized = arrayOutputStream.toString(StandardCharsets.UTF_8);

    // when
    final String[] actualSfVlines = serialized.split(System.lineSeparator());

    // then
    assertThat(actualSfVlines).contains(givenSfvLines[0]);
  }

  @Test
  public void shouldThrowExceptionOnWriteWhenFlushFails() throws IOException {
    // given
    final String[] givenSfvLines = {"file1   aabbccdd"};
    sfvChecksum.updateFromSfvFile(givenSfvLines);

    // when then throw
    try (final var failingStream = new FailingFlushOutputStream()) {
      assertThatThrownBy(() -> sfvChecksum.write(failingStream)).isInstanceOf(IOException.class);
    }
  }

  @Test
  public void shouldThrowExceptionOnWriteWhenWriteFails() throws IOException {
    // given
    final String[] givenSfvLines = {"file1   aabbccdd"};
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
