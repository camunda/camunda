/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.clustermetadata.record;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.eventbridge.protocol.request.coordination.CleanupPolicy;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import java.nio.ByteBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * Guards decode-compatibility for the {@code cleanupPolicy} field added to {@link TopicRecord}
 * (event-bridge ADR 0001, decision 2): a {@code TOPIC_REGISTERED} command/event durably persisted
 * in the Raft log before the field existed must still replay, defaulting to {@link
 * CleanupPolicy#DELETE} — today's behavior. Mirrors the {@code SchemaEvolution} pattern in {@code
 * UnpackedObjectTest}: a stand-in for the pre-change shape is encoded (missing the {@code
 * cleanupPolicy} key entirely), then decoded with the current {@link TopicRecord}.
 */
final class TopicRecordCompatibilityTest {

  @Test
  void shouldDefaultCleanupPolicyToDeleteWhenReplayingARecordWithoutTheField() {
    // given — a record encoded in the pre-cleanupPolicy shape
    final var legacyRecord = new LegacyTopicRecord();
    legacyRecord.name.setValue("orders");
    legacyRecord.op.setValue(TopicRecord.OP_REGISTER);
    legacyRecord.status.setValue("ACTIVE");

    final var buffer = new UnsafeBuffer(ByteBuffer.allocate(256));
    final int length = legacyRecord.write(buffer, 0);

    // when — decoded with the current record type
    final var record = new TopicRecord();
    record.wrap(buffer, 0, length);

    // then — the fields that existed before decode unchanged, and the new field defaults
    assertThat(record.getName()).isEqualTo("orders");
    assertThat(record.getOp()).isEqualTo(TopicRecord.OP_REGISTER);
    assertThat(record.getStatus()).isEqualTo("ACTIVE");
    assertThat(record.getCleanupPolicy()).isEqualTo(CleanupPolicy.DELETE);
  }

  /** Stand-in for {@link TopicRecord} before the {@code cleanupPolicy} property existed. */
  private static final class LegacyTopicRecord extends UnpackedObject {
    private final StringProperty name = new StringProperty("name", "");
    private final StringProperty op = new StringProperty("op", TopicRecord.OP_REGISTER);
    private final StringProperty status = new StringProperty("status", "CREATING");
    private final IntegerProperty partitionCount = new IntegerProperty("partitionCount", 0);

    private LegacyTopicRecord() {
      super(4);
      declareProperty(name)
          .declareProperty(op)
          .declareProperty(status)
          .declareProperty(partitionCount);
    }
  }
}
