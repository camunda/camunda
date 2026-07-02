/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.batch;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the zero-copy {@link BatchBuilder#buildSegments()} encoding is byte-identical to
 * the contiguous {@link BatchBuilder#build()} encoding — same header, same entries, same CRC.
 */
final class BatchBuilderTest {

  @Test
  void shouldProduceSegmentsIdenticalToContiguousBuild() {
    // given: a batch mixing a keyed entry, a keyless entry, and an empty value
    final byte[] contiguous =
        newBatch().add("k1", bytes("v1")).add(bytes("v2")).add("k3", new byte[0]).build();
    final List<byte[]> segments =
        newBatch().add("k1", bytes("v1")).add(bytes("v2")).add("k3", new byte[0]).buildSegments();

    // when: the segments are concatenated
    final byte[] joined = concat(segments);

    // then: the two encodings are byte-for-byte identical (header, framing, payloads, CRC)
    assertThat(joined).isEqualTo(contiguous);
  }

  @Test
  void shouldReportSizeMatchingTheContiguousEncoding() {
    // given
    final BatchBuilder builder = newBatch().add("k1", bytes("value-1")).add(bytes("value-2"));

    // when / then: sizeBytes() predicts the built length exactly
    assertThat(builder.sizeBytes()).isEqualTo(builder.build().length);
  }

  private static BatchBuilder newBatch() {
    return new BatchBuilder();
  }

  private static byte[] bytes(final String s) {
    return s.getBytes(StandardCharsets.UTF_8);
  }

  private static byte[] concat(final List<byte[]> segments) {
    final var out = new ByteArrayOutputStream();
    for (final byte[] segment : segments) {
      out.writeBytes(segment);
    }
    return out.toByteArray();
  }
}
