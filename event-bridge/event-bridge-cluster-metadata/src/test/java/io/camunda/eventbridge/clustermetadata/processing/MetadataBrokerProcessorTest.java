/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.clustermetadata.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.eventbridge.clustermetadata.record.BrokerRecord;
import io.camunda.eventbridge.clustermetadata.state.MetadataColumnFamilies;
import io.camunda.eventbridge.clustermetadata.state.appliers.BrokerFencedApplier;
import io.camunda.eventbridge.clustermetadata.state.appliers.BrokerRegisteredApplier;
import io.camunda.eventbridge.clustermetadata.state.broker.BrokerMetadata.BrokerStatus;
import io.camunda.eventbridge.clustermetadata.state.broker.DbBrokerState;
import io.camunda.eventbridge.stream.RecordProcessingEngine;
import io.camunda.zeebe.db.AccessMetricsConfiguration;
import io.camunda.zeebe.db.AccessMetricsConfiguration.Kind;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration;
import io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.MetadataIntent;
import io.camunda.zeebe.stream.api.ProcessingResultBuilder;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Drives the broker registration/liveness command path through the {@link RecordProcessingEngine}
 * (validate → resolve → append → apply) and asserts on the resulting replicated broker registry: a
 * fresh monotonic epoch on (re-)registration, the active set placement reads, and the self-guarded
 * fence (a stale fence command is a no-op).
 */
final class MetadataBrokerProcessorTest {

  @TempDir private Path dbDir;
  private ZeebeDb<MetadataColumnFamilies> db;
  private DbBrokerState state;
  private RecordProcessingEngine engine;

  @BeforeEach
  void setUp() {
    final var factory =
        new ZeebeRocksDbFactory<MetadataColumnFamilies>(
            new RocksDbConfiguration(),
            new ConsistencyChecksSettings(true, true),
            new AccessMetricsConfiguration(Kind.NONE, 1),
            SimpleMeterRegistry::new);
    db = factory.createDb(dbDir.toFile());
    state = new DbBrokerState(db, db.createContext());

    engine =
        new RecordProcessingEngine(
            processors ->
                processors
                    .onCommand(
                        ValueType.EVENT_BRIDGE_BROKER,
                        MetadataIntent.REGISTER_BROKER,
                        new RegisterBrokerProcessor(processors.writers(), state))
                    .onCommand(
                        ValueType.EVENT_BRIDGE_BROKER,
                        MetadataIntent.FENCE_BROKER,
                        new FenceBrokerProcessor(processors.writers(), state))
                    .withEventApplier(
                        MetadataIntent.BROKER_REGISTERED, new BrokerRegisteredApplier(state))
                    .withEventApplier(
                        MetadataIntent.BROKER_FENCED, new BrokerFencedApplier(state)));
  }

  @AfterEach
  void tearDown() throws Exception {
    db.close();
  }

  @Test
  void shouldRegisterBrokerWithAssignedEpoch() {
    // when
    register(0);

    // then — the leader stamped a fresh epoch and ACTIVE status
    final var broker = state.get(0);
    assertThat(broker.status()).isEqualTo(BrokerStatus.ACTIVE);
    assertThat(broker.brokerEpoch()).isEqualTo(1);
    assertThat(state.activeBrokers()).containsExactly(0);
  }

  @Test
  void shouldBumpEpochOnReRegister() {
    register(0);

    // when — the broker restarts and re-registers
    register(0);

    // then — the epoch is bumped so stale heartbeats from the prior incarnation are fenced
    assertThat(state.get(0).brokerEpoch()).isEqualTo(2);
    assertThat(state.get(0).status()).isEqualTo(BrokerStatus.ACTIVE);
  }

  @Test
  void shouldFenceActiveBroker() {
    register(0);

    // when — the eviction task fences the broker at its current epoch
    fence(0, 1);

    // then — it is FENCED and no longer a placement target
    assertThat(state.get(0).status()).isEqualTo(BrokerStatus.FENCED);
    assertThat(state.activeBrokers()).isEmpty();
  }

  @Test
  void shouldNotFenceOnStaleEpoch() {
    register(0); // epoch 1
    register(0); // epoch 2 (re-registered)

    // when — a fence command from the earlier epoch arrives late
    fence(0, 1);

    // then — it is ignored; the broker stays ACTIVE at the current epoch
    assertThat(state.get(0).status()).isEqualTo(BrokerStatus.ACTIVE);
    assertThat(state.get(0).brokerEpoch()).isEqualTo(2);
  }

  @Test
  void shouldTrackActiveBrokersForPlacement() {
    register(0);
    register(1);
    register(2);
    assertThat(state.activeBrokers()).containsExactly(0, 1, 2);

    // when — one broker is fenced
    fence(1, 1);

    // then — placement sees only the live brokers
    assertThat(state.activeBrokers()).containsExactly(0, 2);
  }

  private void register(final int brokerId) {
    process(MetadataIntent.REGISTER_BROKER, new BrokerRecord().setBrokerId(brokerId));
  }

  private void fence(final int brokerId, final long epoch) {
    process(
        MetadataIntent.FENCE_BROKER,
        new BrokerRecord().setBrokerId(brokerId).setBrokerEpoch(epoch));
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private void process(final Intent intent, final UnifiedRecordValue value) {
    final TypedRecord record = mock(TypedRecord.class);
    when(record.getValueType()).thenReturn(ValueType.EVENT_BRIDGE_BROKER);
    when(record.getIntent()).thenReturn(intent);
    when(record.getValue()).thenReturn(value);
    when(record.getKey()).thenReturn(1L);
    when(record.getTimestamp()).thenReturn(0L);
    when(record.getRequestId()).thenReturn(1L);
    when(record.getRequestStreamId()).thenReturn(1);
    engine.process(record, mock(ProcessingResultBuilder.class));
  }
}
