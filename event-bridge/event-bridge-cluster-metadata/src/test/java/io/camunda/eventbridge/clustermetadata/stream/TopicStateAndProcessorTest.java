/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.clustermetadata.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.eventbridge.clustermetadata.stream.TopicMetadata.TopicStatus;
import io.camunda.eventbridge.stream.RecordProcessingEngine;
import io.camunda.zeebe.db.AccessMetricsConfiguration;
import io.camunda.zeebe.db.AccessMetricsConfiguration.Kind;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration;
import io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies the coordinator's replicated topic registry and its process/replay over RocksDB. */
final class TopicStateAndProcessorTest {

  @TempDir private Path dbDir;
  private ZeebeDb<MetadataColumnFamilies> db;
  private DbTopicState state;

  @BeforeEach
  void setUp() {
    final var factory =
        new ZeebeRocksDbFactory<MetadataColumnFamilies>(
            new RocksDbConfiguration(),
            new ConsistencyChecksSettings(true, true),
            new AccessMetricsConfiguration(Kind.NONE, 1),
            SimpleMeterRegistry::new);
    db = factory.createDb(dbDir.toFile());
    state = new DbTopicState(db, db.createContext());
  }

  @AfterEach
  void tearDown() throws Exception {
    db.close();
  }

  @Test
  void shouldStoreAndReadTopic() {
    // when
    state.put("orders", new TopicMetadata(8, 3, TopicStatus.CREATING));

    // then
    assertThat(state.get("orders")).isEqualTo(new TopicMetadata(8, 3, TopicStatus.CREATING));
  }

  @Test
  void shouldStoreAndReadTopicAssignment() {
    // given a centrally-decided placement carried as data
    final var assignment = java.util.Map.of(1, java.util.List.of(0, 1), 2, java.util.List.of(1, 2));
    final var metadata = new TopicMetadata(2, 2, TopicStatus.CREATING, assignment);

    // when
    state.put("orders", metadata);

    // then the assignment round-trips through the encoded registry entry
    assertThat(state.get("orders")).isEqualTo(metadata);
    assertThat(state.get("orders").assignment()).isEqualTo(assignment);
  }

  @Test
  void shouldReturnNullForUnknownTopic() {
    assertThat(state.get("missing")).isNull();
  }

  @Test
  void shouldOverwriteOnReRegister() {
    // given
    state.put("orders", new TopicMetadata(8, 3, TopicStatus.CREATING));

    // when — same name, advanced status
    state.put("orders", new TopicMetadata(8, 3, TopicStatus.ACTIVE));

    // then
    assertThat(state.get("orders").status()).isEqualTo(TopicStatus.ACTIVE);
  }

  @Test
  void shouldDeleteTopic() {
    // given
    state.put("orders", new TopicMetadata(8, 3, TopicStatus.ACTIVE));

    // when
    state.delete("orders");

    // then
    assertThat(state.get("orders")).isNull();
  }

  @Test
  void shouldListAllTopics() {
    // given
    state.put("orders", new TopicMetadata(8, 3, TopicStatus.ACTIVE));
    state.put("users", new TopicMetadata(4, 1, TopicStatus.CREATING));

    // then
    assertThat(state.readAll())
        .containsOnly(
            java.util.Map.entry("orders", new TopicMetadata(8, 3, TopicStatus.ACTIVE)),
            java.util.Map.entry("users", new TopicMetadata(4, 1, TopicStatus.CREATING)));
  }

  @Test
  void shouldApplyRegisterAndDeleteOnReplay() {
    // given — a follower replaying topic events through the engine's applier registry
    final var registryCache = new java.util.concurrent.ConcurrentHashMap<String, TopicMetadata>();
    final var engine =
        RecordProcessingEngine.builder()
            .withEventApplier(
                MetadataIntent.TOPIC_REGISTERED, new TopicRegisteredApplier(state, registryCache))
            .withEventApplier(
                MetadataIntent.TOPIC_DELETED, new TopicDeletedApplier(state, registryCache))
            .build();

    final var registered =
        new TopicRecord()
            .setName("orders")
            .setOp(TopicRecord.OP_REGISTER)
            .setPartitionCount(8)
            .setReplicationFactor(3)
            .setStatus(TopicStatus.ACTIVE);
    engine.replay(recordOf(registered));
    assertThat(state.get("orders")).isEqualTo(new TopicMetadata(8, 3, TopicStatus.ACTIVE));
    // the thread-safe cache mirrors the durable state in lockstep
    assertThat(registryCache).containsEntry("orders", new TopicMetadata(8, 3, TopicStatus.ACTIVE));

    // when — a delete event replays
    final var deleted = new TopicRecord().setName("orders").setOp(TopicRecord.OP_DELETE);
    engine.replay(recordOf(deleted));

    // then — follower state matches a leader that registered then deleted
    assertThat(state.get("orders")).isNull();
    assertThat(registryCache).doesNotContainKey("orders");
  }

  private static TypedRecord<TopicRecord> recordOf(final TopicRecord value) {
    @SuppressWarnings("unchecked")
    final TypedRecord<TopicRecord> record = mock(TypedRecord.class);
    when(record.getValue()).thenReturn(value);
    when(record.getIntent())
        .thenReturn(
            value.isDelete() ? MetadataIntent.TOPIC_DELETED : MetadataIntent.TOPIC_REGISTERED);
    return record;
  }
}
