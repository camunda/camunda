/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.clustermetadata.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.camunda.eventbridge.clustermetadata.record.TopicRecord;
import io.camunda.eventbridge.clustermetadata.state.MetadataColumnFamilies;
import io.camunda.eventbridge.clustermetadata.state.broker.BrokerMetadata;
import io.camunda.eventbridge.clustermetadata.state.broker.BrokerMetadata.BrokerStatus;
import io.camunda.eventbridge.clustermetadata.state.broker.DbBrokerState;
import io.camunda.eventbridge.clustermetadata.state.topic.DbTopicState;
import io.camunda.eventbridge.clustermetadata.state.topic.TopicMetadata;
import io.camunda.eventbridge.clustermetadata.state.topic.TopicMetadata.TopicStatus;
import io.camunda.zeebe.db.AccessMetricsConfiguration;
import io.camunda.zeebe.db.AccessMetricsConfiguration.Kind;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration;
import io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.intent.MetadataIntent;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

/** Verifies the re-placement sweep heals topics off non-active brokers and skips healthy ones. */
final class PlacementHealTaskTest {

  @TempDir private Path dbDir;
  private ZeebeDb<MetadataColumnFamilies> db;
  private DbTopicState topicState;
  private DbBrokerState brokerState;
  private PlacementHealTask task;

  @BeforeEach
  void setUp() {
    final var factory =
        new ZeebeRocksDbFactory<MetadataColumnFamilies>(
            new RocksDbConfiguration(),
            new ConsistencyChecksSettings(true, true),
            new AccessMetricsConfiguration(Kind.NONE, 1),
            SimpleMeterRegistry::new);
    db = factory.createDb(dbDir.toFile());
    topicState = new DbTopicState(db, db.createContext());
    brokerState = new DbBrokerState(db, db.createContext());
    task = new PlacementHealTask(Duration.ofSeconds(1), topicState, brokerState);
  }

  @AfterEach
  void tearDown() throws Exception {
    db.close();
  }

  @Test
  void shouldHealTopicByReplacingOnlyTheNonActiveReplica() {
    register(0);
    register(1); // broker 2 is absent (fenced / never registered)
    topicState.put(
        "orders",
        new TopicMetadata(
            2, 2, TopicStatus.ACTIVE, Map.of(1, List.of(2, 0), 2, List.of(0, 1)), Map.of()));

    // when
    final var builder = mock(TaskResultBuilder.class);
    task.execute(builder);

    // then — a REGISTER_TOPIC is appended whose target keeps the surviving replica (0) and replaces
    // only the dead one (2 -> 1); the already-healthy partition 2 is unchanged
    final var captor = ArgumentCaptor.forClass(UnifiedRecordValue.class);
    verify(builder).appendCommandRecord(eq(MetadataIntent.REGISTER_TOPIC), captor.capture());
    final var event = (TopicRecord) captor.getValue();
    assertThat(event.getName()).isEqualTo("orders");
    assertThat(event.getTarget().get(1)).containsExactlyInAnyOrder(0, 1).contains(0);
    assertThat(event.getTarget().get(2)).containsExactlyInAnyOrder(0, 1);
  }

  @Test
  void shouldKeepFencedBrokerWhenNoSpareAvailable() {
    register(0);
    register(1); // broker 2 is fenced/absent, and 0+1 already hold every partition (RF=3, 3 nodes)
    topicState.put(
        "orders",
        new TopicMetadata(1, 3, TopicStatus.ACTIVE, Map.of(1, List.of(0, 1, 2)), Map.of()));

    // when
    final var builder = mock(TaskResultBuilder.class);
    task.execute(builder);

    // then — no spare can take over broker 2, so it is left in the assignment (not dropped); the
    // task does nothing rather than try to remove it
    verify(builder, never()).appendCommandRecord(any(), any());
  }

  @Test
  void shouldNotHealWhenAllReplicasActive() {
    register(0);
    register(1);
    topicState.put(
        "orders", new TopicMetadata(1, 2, TopicStatus.ACTIVE, Map.of(1, List.of(0, 1)), Map.of()));

    // when
    final var builder = mock(TaskResultBuilder.class);
    task.execute(builder);

    // then — nothing to heal
    verify(builder, never()).appendCommandRecord(any(), any());
  }

  @Test
  void shouldSkipTopicAlreadyReconfiguring() {
    register(0);
    register(1);
    // committed has the dead broker 2, but a target is already in flight
    topicState.put(
        "orders",
        new TopicMetadata(
            1, 2, TopicStatus.ACTIVE, Map.of(1, List.of(2, 0)), Map.of(1, List.of(0, 1))));

    // when
    final var builder = mock(TaskResultBuilder.class);
    task.execute(builder);

    // then — the change-coordinator owns it; the heal does not interfere
    verify(builder, never()).appendCommandRecord(any(), any());
  }

  private void register(final int brokerId) {
    brokerState.put(brokerId, new BrokerMetadata(brokerId, 1, BrokerStatus.ACTIVE, 1));
  }
}
