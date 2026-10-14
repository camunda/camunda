/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.systempartition;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.atomix.raft.RaftServer.Role;
import io.camunda.zeebe.broker.Broker;
import io.camunda.zeebe.broker.system.syspartition.SystemKeyValueReader;
import io.camunda.zeebe.broker.system.syspartition.SystemKeyValueWriter;
import io.camunda.zeebe.broker.system.syspartition.SystemPartition;
import io.camunda.zeebe.broker.system.syspartition.SystemPartitionKeyType;
import io.camunda.zeebe.broker.system.syspartition.SystemPartitionListener;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies the key-value API of the system partition: only the leader is handed a writer, values it
 * puts are replicated to every broker, watchers on every broker are notified of changes, and a
 * restarted broker rebuilds the store from its snapshot and the log, without any RocksDB
 * involvement.
 */
@ZeebeIntegration
final class SystemPartitionKeyValueIT {

  private static final Duration TIMEOUT = Duration.ofMinutes(1);
  // the cluster id is put once when the cluster is bootstrapped; tests overwrite it freely
  private static final SystemPartitionKeyType KEY = SystemPartitionKeyType.CLUSTER_ID;

  @TestZeebe
  private final TestCluster cluster =
      TestCluster.builder()
          .withBrokersCount(3)
          .withEmbeddedGateway(true)
          .withPartitionsCount(1)
          .withReplicationFactor(3)
          .withBrokerConfig(
              broker ->
                  broker.withUnifiedConfig(
                      cfg -> cfg.getCluster().getSystemPartition().setEnabled(true)))
          .build();

  // the writer each broker was handed by its latest transition, if it is the leader
  private final Map<MemberId, WriterHolder> writers = new ConcurrentHashMap<>();

  @BeforeEach
  void listenToRoleTransitions() {
    cluster
        .brokers()
        .values()
        .forEach(
            broker -> {
              final var holder = new WriterHolder();
              writers.put(broker.nodeId(), holder);
              systemPartition(broker).addListener(holder);
            });
  }

  @Test
  void shouldHandWriterOnlyToLeader() {
    // when - then
    Awaitility.await("only the leader holds a writer")
        .atMost(TIMEOUT)
        .untilAsserted(
            () -> {
              final var leader = brokerWithRole(Role.LEADER).nodeId();
              assertThat(writers)
                  .allSatisfy(
                      (memberId, holder) ->
                          assertThat(holder.writer != null)
                              .describedAs("whether %s holds a writer", memberId)
                              .isEqualTo(memberId.equals(leader)));
            });
  }

  @Test
  void shouldReplicatePutValueToEveryBroker() {
    // given
    final var value = uniqueValue();

    // when
    put(leaderWriter(), value);

    // then
    awaitValueOnEveryBroker(value);
  }

  @Test
  void shouldKeepLatestValueOfKey() {
    // given
    final var writer = leaderWriter();
    put(writer, uniqueValue());

    // when
    final var latest = uniqueValue();
    put(writer, latest);

    // then
    awaitValueOnEveryBroker(latest);
  }

  @Test
  void shouldNotifyWatchersOnEveryBrokerOfChangedValues() {
    // given
    awaitBootstrapValueOnEveryBroker();
    final var changes = watchOnEveryBroker();
    final var writer = leaderWriter();
    final var first = uniqueValue();
    final var second = uniqueValue();

    // when - putting the same value twice changes it only once
    put(writer, first);
    put(writer, first);
    put(writer, second);

    // then
    Awaitility.await("every broker notifies its watcher of both changes")
        .atMost(TIMEOUT)
        .untilAsserted(
            () ->
                assertThat(changes)
                    .allSatisfy(
                        (broker, values) ->
                            assertThat(values)
                                .describedAs("changes seen on %s", broker)
                                .containsExactly(first, second)));
  }

  @Test
  void shouldStopNotifyingUnwatchedListener() {
    // given
    awaitBootstrapValueOnEveryBroker();
    final var reader = reader(brokerWithRole(Role.FOLLOWER));
    final var unwatchedChanges = new CopyOnWriteArrayList<String>();
    final var openChanges = new CopyOnWriteArrayList<String>();
    final SystemKeyValueReader.ValueListener unwatched =
        (key, value) -> unwatchedChanges.add(decode(value));
    reader.watch(KEY, unwatched);
    reader.watch(KEY, (key, value) -> openChanges.add(decode(value)));
    reader.unwatch(KEY, unwatched);

    // when
    final var value = uniqueValue();
    put(leaderWriter(), value);

    // then
    Awaitility.await("the open watch is notified")
        .atMost(TIMEOUT)
        .untilAsserted(() -> assertThat(openChanges).containsExactly(value));
    assertThat(unwatchedChanges).isEmpty();
  }

  @Test
  void shouldRestoreValuesAfterRestart() {
    // given
    final var value = uniqueValue();
    put(leaderWriter(), value);
    awaitValueOnEveryBroker(value);
    final var restarted = brokerWithRole(Role.FOLLOWER);

    // when
    restarted.stop();
    restarted.start();

    // then
    Awaitility.await("restarted broker restores the value")
        .atMost(TIMEOUT)
        .untilAsserted(() -> assertThat(get(restarted)).contains(value));
  }

  @Test
  void shouldRevokeWriterAndHandOutNewOneAfterLeaderChange() {
    // given
    final var oldLeader = brokerWithRole(Role.LEADER);
    final var oldWriter = leaderWriter();
    put(oldWriter, uniqueValue());

    // when
    oldLeader.stop();
    writers.remove(oldLeader.nodeId());
    final var value = uniqueValue();
    put(leaderWriter(), value);

    // then
    assertThat(oldWriter.put(KEY, new TestValue().setValue(uniqueValue())))
        .failsWithin(TIMEOUT)
        .withThrowableThat()
        .withMessageContaining("leadership");
    Awaitility.await("survivors hold the value put by the new leader")
        .atMost(TIMEOUT)
        .untilAsserted(
            () ->
                assertThat(cluster.brokers().values())
                    .filteredOn(broker -> broker != oldLeader)
                    .allSatisfy(broker -> assertThat(get(broker)).contains(value)));
  }

  private SystemKeyValueWriter leaderWriter() {
    return Awaitility.await("a broker is handed the leader's writer")
        .atMost(TIMEOUT)
        .until(
            () ->
                writers.values().stream()
                    .map(holder -> holder.writer)
                    .filter(writer -> writer != null)
                    .findFirst(),
            Optional::isPresent)
        .orElseThrow();
  }

  /** Waits until the value put when the cluster is bootstrapped no longer notifies watchers. */
  private void awaitBootstrapValueOnEveryBroker() {
    Awaitility.await("every broker applies the bootstrap value of the key")
        .atMost(TIMEOUT)
        .untilAsserted(
            () ->
                assertThat(cluster.brokers().values())
                    .allSatisfy(broker -> assertThat(get(broker)).isPresent()));
  }

  private Map<String, List<String>> watchOnEveryBroker() {
    return cluster.brokers().values().stream()
        .collect(
            Collectors.toMap(
                broker -> broker.nodeId().id(),
                broker -> {
                  final var values = new CopyOnWriteArrayList<String>();
                  reader(broker).watch(KEY, (key, value) -> values.add(decode(value)));
                  return values;
                }));
  }

  private void awaitValueOnEveryBroker(final String value) {
    Awaitility.await("every broker has %s".formatted(value))
        .atMost(TIMEOUT)
        .untilAsserted(
            () ->
                assertThat(cluster.brokers().values())
                    .allSatisfy(broker -> assertThat(get(broker)).contains(value)));
  }

  private TestStandaloneBroker brokerWithRole(final Role role) {
    return Awaitility.await("system partition has a %s".formatted(role))
        .atMost(TIMEOUT)
        .until(
            () ->
                cluster.brokers().values().stream()
                    .filter(TestStandaloneBroker::isStarted)
                    .filter(broker -> systemPartition(broker).raftPartition().getRole() == role)
                    .findFirst(),
            Optional::isPresent)
        .orElseThrow();
  }

  private static void put(final SystemKeyValueWriter writer, final String value) {
    writer.put(KEY, new TestValue().setValue(value)).join(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
  }

  private static Optional<String> get(final TestStandaloneBroker broker) {
    return reader(broker).get(KEY, new TestValue()).map(TestValue::getValue);
  }

  private static String decode(final DirectBuffer value) {
    final var decoded = new TestValue();
    decoded.wrap(value, 0, value.capacity());
    return decoded.getValue();
  }

  private static SystemKeyValueReader reader(final TestStandaloneBroker broker) {
    return systemPartition(broker).keyValueReader();
  }

  private static SystemPartition systemPartition(final TestStandaloneBroker broker) {
    final var systemPartition = broker.bean(Broker.class).getBrokerContext().getSystemPartition();
    assertThat(systemPartition).describedAs("system partition of %s", broker.nodeId()).isNotNull();
    return systemPartition;
  }

  private static String uniqueValue() {
    return UUID.randomUUID().toString();
  }

  /** Keeps the writer handed out by the latest transition, if it made the broker the leader. */
  private static final class WriterHolder implements SystemPartitionListener {
    private volatile SystemKeyValueWriter writer;

    @Override
    public void onBecomingLeader(final long term, final SystemKeyValueWriter writer) {
      this.writer = writer;
    }

    @Override
    public void onBecomingFollower(final long term) {
      writer = null;
    }

    @Override
    public void onBecomingInactive(final long term) {
      writer = null;
    }
  }

  /** A value known only to this test; the store holds any serialized value. */
  private static final class TestValue extends UnpackedObject {
    private final StringProperty valueProp = new StringProperty("value", "");

    private TestValue() {
      super(1);
      declareProperty(valueProp);
    }

    private String getValue() {
      return BufferUtil.bufferAsString(valueProp.getValue());
    }

    private TestValue setValue(final String value) {
      valueProp.setValue(value);
      return this;
    }
  }
}
