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
import io.camunda.eventbridge.clustermetadata.state.appliers.BrokerDeregisteredApplier;
import io.camunda.eventbridge.clustermetadata.state.appliers.BrokerDrainingApplier;
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
 * fresh monotonic epoch on a new incarnation, idempotent re-registration of the same incarnation,
 * the active set placement reads, the self-guarded fence, and the drain/deregister transitions.
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
                    .onCommand(
                        ValueType.EVENT_BRIDGE_BROKER,
                        MetadataIntent.DRAIN_BROKER,
                        new DrainBrokerProcessor(processors.writers(), state))
                    .onCommand(
                        ValueType.EVENT_BRIDGE_BROKER,
                        MetadataIntent.DEREGISTER_BROKER,
                        new DeregisterBrokerProcessor(processors.writers(), state))
                    .withEventApplier(
                        MetadataIntent.BROKER_REGISTERED, new BrokerRegisteredApplier(state))
                    .withEventApplier(MetadataIntent.BROKER_FENCED, new BrokerFencedApplier(state))
                    .withEventApplier(
                        MetadataIntent.BROKER_DRAINING, new BrokerDrainingApplier(state))
                    .withEventApplier(
                        MetadataIntent.BROKER_DEREGISTERED, new BrokerDeregisteredApplier(state)));
  }

  @AfterEach
  void tearDown() throws Exception {
    db.close();
  }

  @Test
  void shouldRegisterBrokerWithAssignedEpoch() {
    // when
    register(0, 100);

    // then — the leader stamped a fresh epoch and ACTIVE status
    final var broker = state.get(0);
    assertThat(broker.status()).isEqualTo(BrokerStatus.ACTIVE);
    assertThat(broker.brokerEpoch()).isEqualTo(1);
    assertThat(state.activeBrokers()).containsExactly(0);
  }

  @Test
  void shouldBeIdempotentForSameIncarnation() {
    register(0, 100);

    // when — the registration RPC is retried with the same incarnation
    register(0, 100);

    // then — the epoch is unchanged, so the broker's in-flight heartbeats are not fenced
    assertThat(state.get(0).brokerEpoch()).isEqualTo(1);
  }

  @Test
  void shouldBumpEpochOnNewIncarnation() {
    register(0, 100);

    // when — the broker restarts and re-registers with a new incarnation
    register(0, 200);

    // then — the epoch is bumped so stale heartbeats from the prior incarnation are fenced
    assertThat(state.get(0).brokerEpoch()).isEqualTo(2);
    assertThat(state.get(0).status()).isEqualTo(BrokerStatus.ACTIVE);
  }

  @Test
  void shouldFenceActiveBroker() {
    register(0, 100);

    // when — the eviction task fences the broker at its current epoch
    fence(0, 1);

    // then — it is FENCED and no longer a placement target
    assertThat(state.get(0).status()).isEqualTo(BrokerStatus.FENCED);
    assertThat(state.activeBrokers()).isEmpty();
  }

  @Test
  void shouldNotFenceOnStaleEpoch() {
    register(0, 100); // epoch 1
    register(0, 200); // epoch 2 (restarted)

    // when — a fence command from the earlier epoch arrives late
    fence(0, 1);

    // then — it is ignored; the broker stays ACTIVE at the current epoch
    assertThat(state.get(0).status()).isEqualTo(BrokerStatus.ACTIVE);
    assertThat(state.get(0).brokerEpoch()).isEqualTo(2);
  }

  @Test
  void shouldReactivateFencedBrokerOnReRegister() {
    register(0, 100);
    fence(0, 1);

    // when — the fenced broker re-registers
    register(0, 100);

    // then — it is ACTIVE again with a bumped epoch
    assertThat(state.get(0).status()).isEqualTo(BrokerStatus.ACTIVE);
    assertThat(state.get(0).brokerEpoch()).isEqualTo(2);
  }

  @Test
  void shouldTrackActiveBrokersForPlacement() {
    register(0, 100);
    register(1, 100);
    register(2, 100);
    assertThat(state.activeBrokers()).containsExactly(0, 1, 2);

    // when — one broker is fenced
    fence(1, 1);

    // then — placement sees only the live brokers
    assertThat(state.activeBrokers()).containsExactly(0, 2);
  }

  @Test
  void shouldDrainAndDeregisterBroker() {
    register(0, 100);

    // when — a draining heartbeat marks it for controlled shutdown
    drain(0, 1);

    // then — it is DRAINING and excluded from placement
    assertThat(state.get(0).status()).isEqualTo(BrokerStatus.DRAINING);
    assertThat(state.activeBrokers()).isEmpty();

    // when — it is deregistered once drained
    deregister(0);

    // then — it is gone from the registry
    assertThat(state.get(0)).isNull();
  }

  private void register(final int brokerId, final long incarnation) {
    process(
        MetadataIntent.REGISTER_BROKER,
        new BrokerRecord().setBrokerId(brokerId).setIncarnation(incarnation));
  }

  private void fence(final int brokerId, final long epoch) {
    process(
        MetadataIntent.FENCE_BROKER,
        new BrokerRecord().setBrokerId(brokerId).setBrokerEpoch(epoch));
  }

  private void drain(final int brokerId, final long epoch) {
    process(
        MetadataIntent.DRAIN_BROKER,
        new BrokerRecord().setBrokerId(brokerId).setBrokerEpoch(epoch));
  }

  private void deregister(final int brokerId) {
    process(MetadataIntent.DEREGISTER_BROKER, new BrokerRecord().setBrokerId(brokerId));
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
