/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.clustermetadata.state.topic;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.eventbridge.protocol.request.coordination.CleanupPolicy;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import java.nio.ByteBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * Guards decode-compatibility for the {@code cleanupPolicy} column added to {@link PersistedTopic}
 * (event-bridge ADR 0001, decision 2): a registry entry already durable on disk before the column
 * existed must still decode, defaulting to {@link CleanupPolicy#DELETE} — today's behavior. Mirrors
 * the {@code SchemaEvolution} pattern in {@code UnpackedObjectTest}: a stand-in for the pre-change
 * shape is encoded (missing the {@code cleanupPolicy} key entirely), then decoded with the current
 * {@link PersistedTopic}.
 */
final class PersistedTopicCompatibilityTest {

  @Test
  void shouldDefaultCleanupPolicyToDeleteWhenDecodingAnEntryWithoutTheColumn() {
    // given — a registry entry encoded in the pre-cleanupPolicy shape
    final var legacyEntry = new LegacyPersistedTopic();
    legacyEntry.partitionCount.setValue(8);
    legacyEntry.replicationFactor.setValue(3);
    legacyEntry.status.setValue(TopicMetadata.TopicStatus.ACTIVE);

    final var buffer = new UnsafeBuffer(ByteBuffer.allocate(256));
    final int length = legacyEntry.write(buffer, 0);

    // when — decoded with the current type
    final var persistedTopic = new PersistedTopic();
    persistedTopic.wrap(buffer, 0, length);

    // then — the fields that existed before decode unchanged, and the new field defaults
    final var metadata = persistedTopic.toMetadata();
    assertThat(metadata.partitionCount()).isEqualTo(8);
    assertThat(metadata.replicationFactor()).isEqualTo(3);
    assertThat(metadata.status()).isEqualTo(TopicMetadata.TopicStatus.ACTIVE);
    assertThat(metadata.cleanupPolicy()).isEqualTo(CleanupPolicy.DELETE);
  }

  /** Stand-in for {@link PersistedTopic} before the {@code cleanupPolicy} property existed. */
  private static final class LegacyPersistedTopic extends UnpackedObject {
    private final IntegerProperty partitionCount = new IntegerProperty("partitionCount", 0);
    private final IntegerProperty replicationFactor = new IntegerProperty("replicationFactor", 0);
    private final EnumProperty<TopicMetadata.TopicStatus> status =
        new EnumProperty<>(
            "status", TopicMetadata.TopicStatus.class, TopicMetadata.TopicStatus.CREATING);

    private LegacyPersistedTopic() {
      super(3);
      declareProperty(partitionCount).declareProperty(replicationFactor).declareProperty(status);
    }
  }
}
