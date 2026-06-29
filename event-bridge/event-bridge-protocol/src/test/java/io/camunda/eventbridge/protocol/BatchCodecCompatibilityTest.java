/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.protocol;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.eventbridge.batch.BatchBuilder;
import io.camunda.eventbridge.batch.BatchReader;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * Guards that the pure (client) batch codec in {@code event-bridge-batch-codec} and the Agrona
 * zero-copy codec here produce and consume byte-identical batches — i.e. the same layout, byte
 * order (little-endian), and CRC. If these drift (e.g. an endianness mismatch), one side would
 * fail to read the other and these assertions break.
 */
final class BatchCodecCompatibilityTest {

  @Test
  void agronaReadsWhatThePureBuilderWrote() {
    // given — a batch built by the pure (client) builder
    final byte[] batch =
        new BatchBuilder().add("k1", "v1".getBytes(UTF_8)).add("v2".getBytes(UTF_8)).build();

    // then — the Agrona codec validates the CRC and reads identical entries
    final var buffer = new UnsafeBuffer(batch);
    assertThat(EventBridgeBatch.validateCrc(buffer, 0)).isTrue();

    final var iterator = new EventBridgeBatchIterator();
    iterator.wrap(buffer, 0, batch.length);

    final var first = iterator.next();
    assertThat(first.getPosition()).isZero();
    assertThat(new String(first.getKeyCopy(), UTF_8)).isEqualTo("k1");
    assertThat(new String(first.getValueCopy(), UTF_8)).isEqualTo("v1");

    final var second = iterator.next();
    assertThat(second.getPosition()).isEqualTo(1);
    assertThat(second.hasKey()).isFalse();
    assertThat(new String(second.getValueCopy(), UTF_8)).isEqualTo("v2");

    assertThat(iterator.hasNext()).isFalse();
  }

  @Test
  void pureReaderReadsWhatTheAgronaBuilderWrote() {
    // given — a batch built by the Agrona (broker-side) builder
    final byte[] batch =
        new EventBridgeBatchBuilder()
            .addEntry(new EventBridgeEntryBuilder().key("k1").value("v1".getBytes(UTF_8)).build())
            .addEntry(new EventBridgeEntryBuilder().value("v2".getBytes(UTF_8)).build())
            .build();

    // then — the pure (client) reader decodes identical entries
    final var entries = BatchReader.read(batch, 0, batch.length, Long.MIN_VALUE);
    assertThat(entries).hasSize(2);

    assertThat(entries.get(0).position()).isZero();
    assertThat(new String(entries.get(0).key(), UTF_8)).isEqualTo("k1");
    assertThat(new String(entries.get(0).value(), UTF_8)).isEqualTo("v1");

    assertThat(entries.get(1).position()).isEqualTo(1);
    assertThat(entries.get(1).key()).isEmpty();
    assertThat(new String(entries.get(1).value(), UTF_8)).isEqualTo("v2");
  }
}
