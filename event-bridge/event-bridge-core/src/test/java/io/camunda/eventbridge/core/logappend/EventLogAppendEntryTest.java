/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.core.logappend;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class EventLogAppendEntryTest {

  @Nested
  class ContractTests {

    @Test
    void shouldReturnNeutralRecordType() {
      // given
      final var entry = new EventLogAppendEntry();
      entry.wrap(1L, new byte[] {0x01});

      // then
      assertThat(entry.recordMetadata().getRecordType()).isEqualTo(RecordType.NULL_VAL);
    }

    @Test
    void shouldReturnNullValueType() {
      // given
      final var entry = new EventLogAppendEntry();
      entry.wrap(1L, new byte[] {0x01});

      // then
      assertThat(entry.recordMetadata().getValueType()).isEqualTo(ValueType.NULL_VAL);
    }

    @Test
    void shouldReturnConfiguredKey() {
      // given
      final var entry = new EventLogAppendEntry();
      entry.wrap(42L, new byte[] {0x01});

      // then
      assertThat(entry.key()).isEqualTo(42L);
    }

    @Test
    void shouldReturnMinusOneSourceIndex() {
      // given
      final var entry = new EventLogAppendEntry();
      entry.wrap(1L, new byte[] {0x01});

      // then
      assertThat(entry.sourceIndex()).isEqualTo(-1);
    }

    @Test
    void shouldNotBeMarkedAsProcessed() {
      // given
      final var entry = new EventLogAppendEntry();
      entry.wrap(1L, new byte[] {0x01});

      // then
      assertThat(entry.isProcessed()).isFalse();
    }

    @Test
    void shouldHaveNonZeroLength() {
      // given
      final var entry = new EventLogAppendEntry();
      entry.wrap(1L, new byte[] {0x01, 0x02, 0x03});

      // then — length must be > 0 for serializer to accept it
      assertThat(entry.getLength()).isGreaterThan(0);
    }

    @Test
    void shouldHaveNonZeroMetadataLength() {
      // given
      final var entry = new EventLogAppendEntry();
      entry.wrap(1L, new byte[] {0x01});

      // then — serializer rejects entries with zero-length metadata
      assertThat(entry.recordMetadata().getLength()).isGreaterThan(0);
    }

    @Test
    void shouldHaveNonZeroRecordValueLength() {
      // given
      final var entry = new EventLogAppendEntry();
      entry.wrap(1L, new byte[] {0x01});

      // then — serializer rejects entries with zero-length value
      assertThat(entry.recordValue().getLength()).isGreaterThan(0);
    }

    @Test
    void shouldAcceptDirectBufferWithOffset() {
      // given — payload bytes starting at offset 2 in a larger buffer
      final byte[] raw = {0x00, 0x00, 0x0A, 0x0B};
      final var buf = new UnsafeBuffer(raw);
      final var entry = new EventLogAppendEntry();
      entry.wrap(1L, buf, 2, 2);

      // then — length = metadata + key/sourceIndex + msgpack(2-byte payload) = metadata + 2 bytes
      // overhead + 4
      assertThat(entry.recordValue().getLength()).isEqualTo(4); // bin8 header (2) + 2 payload bytes
    }

    @Test
    void shouldBeReusable() {
      // given
      final var entry = new EventLogAppendEntry();
      entry.wrap(1L, new byte[] {0x01});
      final int firstLength = entry.getLength();

      // when — rewrap with larger payload
      entry.wrap(2L, new byte[] {0x01, 0x02, 0x03, 0x04, 0x05});
      final int secondLength = entry.getLength();

      // then — entry reflects new payload
      assertThat(entry.key()).isEqualTo(2L);
      assertThat(secondLength).isGreaterThan(firstLength);
    }

    @Test
    void shouldImplementLogAppendEntryInterface() {
      // given / then — compile-time check is sufficient; cast confirms it at runtime
      final LogAppendEntry entry = new EventLogAppendEntry();
      assertThat(entry).isInstanceOf(LogAppendEntry.class);
    }
  }

  @Nested
  class MetadataShareTest {

    @Test
    void shouldShareSingleNeutralMetadataInstanceAcrossEntries() {
      // given — two entries must share the same static metadata (no per-instance allocation)
      final var a = new EventLogAppendEntry();
      final var b = new EventLogAppendEntry();
      a.wrap(1L, new byte[] {0x01});
      b.wrap(2L, new byte[] {0x02});

      // then — same reference: neutral metadata is shared / stateless
      assertThat(a.recordMetadata()).isSameAs(b.recordMetadata());
    }
  }
}
