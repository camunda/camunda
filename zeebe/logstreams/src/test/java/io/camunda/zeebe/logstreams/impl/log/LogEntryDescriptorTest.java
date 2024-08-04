/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.logstreams.impl.log;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.Protocol;
import java.util.Arrays;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

final class LogEntryDescriptorTest {
  @Test
  void shouldBeNonProcessedAsDefault() {
    // given
    final var buffer = new UnsafeBuffer(new byte[128]);

    // when
    final boolean processed = LogEntryDescriptor.shouldSkipProcessing(buffer, 0);

    // then
    assertThat(processed).isFalse();
  }

  @Test
  void shouldMarkAsProcessed() {
    // given
    final var buffer = new UnsafeBuffer(new byte[128]);

    // when
    LogEntryDescriptor.skipProcessing(buffer, 0);

    // then
    assertThat(LogEntryDescriptor.shouldSkipProcessing(buffer, 0)).isTrue();
  }

  @Test
  void shouldReadShortMetadataLength() {
    // given
    final var buffer = new UnsafeBuffer(new byte[128]);

    // when
    LogEntryDescriptor.setVersion(buffer, 0);
    LogEntryDescriptor.setMetadataLength(buffer, 0, 34);

    // then
    assertThat(LogEntryDescriptor.getMetadataLength(buffer, 0)).isEqualTo(34);
  }

  @Test
  void shouldReadLargeMetadataLength() {
    // given
    final var buffer = new UnsafeBuffer(new byte[128]);

    // when
    LogEntryDescriptor.setVersion(buffer, 0);
    LogEntryDescriptor.setMetadataLength(buffer, 0, Short.MAX_VALUE + 10);

    // then
    assertThat(LogEntryDescriptor.getMetadataLength(buffer, 0)).isEqualTo(Short.MAX_VALUE + 10);
  }

  @Test
  void shouldReadMetadataLengthFromOldVersion() {
    // given
    final byte[] byteArray = new byte[128];
    Arrays.fill(byteArray, (byte) 8);
    final var buffer = new UnsafeBuffer(byteArray);

    // when
    // set version 0
    buffer.putShort(LogEntryDescriptor.versionOffset(0), (short) 0, Protocol.ENDIANNESS);
    LogEntryDescriptor.setMetadataLength(buffer, 0, 34);

    // then
    assertThat(LogEntryDescriptor.getMetadataLength(buffer, 0)).isEqualTo(34);
  }

  @Test
  void shouldReadMetadataLengthFromOldVersionWhenVersionNotSet() {
    // given
    final byte[] byteArray = new byte[128];
    Arrays.fill(byteArray, (byte) 8);
    final var buffer = new UnsafeBuffer(byteArray);

    // when
    // set garbage value for version
    buffer.putShort(LogEntryDescriptor.versionOffset(0), (short) 123, Protocol.ENDIANNESS);
    LogEntryDescriptor.setMetadataLength(buffer, 0, 34);

    // then
    assertThat(LogEntryDescriptor.getMetadataLength(buffer, 0)).isEqualTo(34);
  }
}
