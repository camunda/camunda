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
    final Record<?> deserialized = codec.deserialize(payload, 7, 555L);

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
  void shouldTakeLogCoordinatesFromEnvelopeNotPayload() {
    // given
    final JobRecord value = new JobRecord().setType("payment");
    final RecordMetadata metadata =
        new RecordMetadata()
            .recordType(RecordType.EVENT)
            .valueType(ValueType.JOB)
            .intent(JobIntent.CREATED);
    final Record<JobRecord> record = new CopiedRecord<>(value, metadata, 42L, 1, 100L, 99L, 1234L);

    // when — reconstructed against a different partition/position than the source
    final Record<?> deserialized = codec.deserialize(codec.serialize(record), 7, 555L);

    // then — log coordinates come from the envelope, not the original record
    assertThat(deserialized.getPartitionId()).isEqualTo(7);
    assertThat(deserialized.getPosition()).isEqualTo(555L);
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

    // when — reconstructed against a different partition/position than the source
    final Record<?> deserialized = codec.deserialize(codec.serialize(record), 7, 555L);

    // then — the event timestamp is a property of the event, preserved from the payload
    assertThat(deserialized.getTimestamp()).isEqualTo(1234L);
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

    // when reconstructed against a different partition/position than the source
    final Record<?> deserialized = codec.deserialize(codec.serialize(record), 7, 555L);

    // then — the key is carried in the payload so downstream can correlate on entity identity
    assertThat(deserialized.getKey()).isEqualTo(42L);
  }
}
