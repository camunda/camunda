/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.zeebe.connector;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.impl.record.CopiedRecord;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import org.junit.jupiter.api.Test;

final class ZeebeRecordCodecTest {

  private final ZeebeRecordCodec codec = new ZeebeRecordCodec();

  @Test
  void shouldRoundTripRecordMetadataAndValue() {
    // given
    final JobRecord value = new JobRecord().setType("payment").setRetries(3).setWorker("worker-1");
    final RecordMetadata metadata =
        new RecordMetadata()
            .recordType(RecordType.EVENT)
            .valueType(ValueType.JOB)
            .intent(JobIntent.CREATED);
    final Record<JobRecord> record = new CopiedRecord<>(value, metadata, 42L, 1, 100L, 99L, 1234L);

    // when
    final byte[] payload = codec.serialize(record);
    final Record<?> deserialized = codec.deserialize(payload);

    // then
    assertThat(deserialized.getRecordType()).isEqualTo(RecordType.EVENT);
    assertThat(deserialized.getValueType()).isEqualTo(ValueType.JOB);
    assertThat(deserialized.getIntent()).isEqualTo(JobIntent.CREATED);

    assertThat(deserialized.getValue()).isInstanceOf(JobRecordValue.class);
    final JobRecordValue deserializedValue = (JobRecordValue) deserialized.getValue();
    assertThat(deserializedValue.getType()).isEqualTo("payment");
    assertThat(deserializedValue.getRetries()).isEqualTo(3);
    assertThat(deserializedValue.getWorker()).isEqualTo("worker-1");
  }

  @Test
  void shouldPeekTimestampWithoutDecoding() {
    // given — a record with a distinctive event timestamp
    final JobRecord value = new JobRecord().setType("payment");
    final RecordMetadata metadata =
        new RecordMetadata()
            .recordType(RecordType.EVENT)
            .valueType(ValueType.JOB)
            .intent(JobIntent.CREATED);
    final Record<JobRecord> record = new CopiedRecord<>(value, metadata, 42L, 1, 100L, 99L, 1234L);

    // when
    final long timestamp = codec.timestamp(codec.serialize(record));

    // then — the payload's leading field is the event timestamp
    assertThat(timestamp).isEqualTo(1234L);
  }

  @Test
  void shouldRoundTripTheZeebeOriginCoordinateFromThePayload() {
    // given a record with a distinctive Zeebe origin coordinate (partition 1, position 100)
    final JobRecord value = new JobRecord().setType("payment");
    final RecordMetadata metadata =
        new RecordMetadata()
            .recordType(RecordType.EVENT)
            .valueType(ValueType.JOB)
            .intent(JobIntent.CREATED);
    final Record<JobRecord> record = new CopiedRecord<>(value, metadata, 42L, 1, 100L, 99L, 1234L);

    // when — carried through the Event Bridge, whose envelope assigns its own coordinates
    final Record<?> deserialized = codec.deserialize(codec.serialize(record));

    // then — the record keeps its real Zeebe coordinates; the Event Bridge envelope coordinates
    // do not leak into the record (they flow separately as consumption coordinates)
    assertThat(deserialized.getPartitionId()).isEqualTo(1);
    assertThat(deserialized.getPosition()).isEqualTo(100L);
  }

  @Test
  void shouldPreserveOriginalEventTimestamp() {
    // given
    final JobRecord value = new JobRecord().setType("payment");
    final RecordMetadata metadata =
        new RecordMetadata()
            .recordType(RecordType.EVENT)
            .valueType(ValueType.JOB)
            .intent(JobIntent.CREATED);
    final Record<JobRecord> record = new CopiedRecord<>(value, metadata, 42L, 1, 100L, 99L, 1234L);

    // when
    final Record<?> deserialized = codec.deserialize(codec.serialize(record));

    // then — the event timestamp is a property of the event, preserved from the payload
    assertThat(deserialized.getTimestamp()).isEqualTo(1234L);
  }

  @Test
  void shouldKeepEarlierRecordsIntactWhenReusingTheCodecAcrossRecords() {
    // given two distinct records, (de)serialized through the same codec instance — the codec
    // reuses internal buffer wrappers, so an earlier decoded record must not alias codec state
    final Record<JobRecord> first =
        new CopiedRecord<>(
            new JobRecord().setType("payment").setRetries(3).setWorker("worker-1"),
            new RecordMetadata()
                .recordType(RecordType.EVENT)
                .valueType(ValueType.JOB)
                .intent(JobIntent.CREATED),
            42L,
            1,
            100L,
            99L,
            1234L);
    final Record<JobRecord> second =
        new CopiedRecord<>(
            new JobRecord().setType("shipping").setRetries(7).setWorker("worker-2"),
            new RecordMetadata()
                .recordType(RecordType.EVENT)
                .valueType(ValueType.JOB)
                .intent(JobIntent.COMPLETED),
            43L,
            1,
            101L,
            99L,
            5678L);

    // when: serialize twice, then decode both payloads with the same instance
    final byte[] firstPayload = codec.serialize(first);
    final byte[] secondPayload = codec.serialize(second);
    final Record<?> firstDecoded = codec.deserialize(firstPayload);
    final Record<?> secondDecoded = codec.deserialize(secondPayload);

    // then: serialization is stable across reuse, and the first decode survives the second
    assertThat(codec.serialize(first)).isEqualTo(firstPayload);
    assertThat(codec.serialize(second)).isEqualTo(secondPayload);
    assertThat(firstDecoded.getIntent()).isEqualTo(JobIntent.CREATED);
    assertThat(firstDecoded.getKey()).isEqualTo(42L);
    assertThat(firstDecoded.getTimestamp()).isEqualTo(1234L);
    final JobRecordValue firstValue = (JobRecordValue) firstDecoded.getValue();
    assertThat(firstValue.getType()).isEqualTo("payment");
    assertThat(firstValue.getWorker()).isEqualTo("worker-1");
    assertThat(firstValue.getRetries()).isEqualTo(3);
    final JobRecordValue secondValue = (JobRecordValue) secondDecoded.getValue();
    assertThat(secondValue.getType()).isEqualTo("shipping");
    assertThat(secondValue.getWorker()).isEqualTo("worker-2");
  }

  @Test
  void shouldPeekAcceptsRepeatedlyWithoutStateLeakingBetweenRecords() {
    // given payloads of two different intents peeked through the same codec instance
    final byte[] created =
        codec.serialize(
            new CopiedRecord<>(
                new JobRecord().setType("payment"),
                new RecordMetadata()
                    .recordType(RecordType.EVENT)
                    .valueType(ValueType.JOB)
                    .intent(JobIntent.CREATED),
                42L,
                1,
                100L,
                99L,
                1234L));
    final byte[] completed =
        codec.serialize(
            new CopiedRecord<>(
                new JobRecord().setType("payment"),
                new RecordMetadata()
                    .recordType(RecordType.EVENT)
                    .valueType(ValueType.JOB)
                    .intent(JobIntent.COMPLETED),
                42L,
                1,
                100L,
                99L,
                1234L));

    // when / then: each peek sees exactly its own record's metadata, in any order
    assertThat(codec.accepts(created, (type, intent) -> intent == JobIntent.CREATED)).isTrue();
    assertThat(codec.accepts(completed, (type, intent) -> intent == JobIntent.CREATED)).isFalse();
    assertThat(codec.accepts(created, (type, intent) -> intent == JobIntent.CREATED)).isTrue();
    assertThat(codec.timestamp(created)).isEqualTo(1234L);
  }

  @Test
  void shouldPreserveRecordKey() {
    // given a record whose key is its entity identity (e.g. an element instance key)
    final JobRecord value = new JobRecord().setType("payment");
    final RecordMetadata metadata =
        new RecordMetadata()
            .recordType(RecordType.EVENT)
            .valueType(ValueType.JOB)
            .intent(JobIntent.CREATED);
    final Record<JobRecord> record = new CopiedRecord<>(value, metadata, 42L, 1, 100L, 99L, 1234L);

    // when
    final Record<?> deserialized = codec.deserialize(codec.serialize(record));

    // then — the key is carried in the payload so downstream can correlate on entity identity
    assertThat(deserialized.getKey()).isEqualTo(42L);
  }
}
