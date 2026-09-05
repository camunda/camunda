/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.serializer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.protobuf.Timestamp;
import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.gossip.ClusterConfigurationGossipState;
import io.camunda.zeebe.dynamic.config.protocol.Topology;
import io.camunda.zeebe.dynamic.config.state.BrokerPartitionState;
import io.camunda.zeebe.dynamic.config.state.BrokerState;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.dynamic.config.state.OperationGraph;
import io.camunda.zeebe.dynamic.config.state.OperationId;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ModeChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionPreRestoreOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionRestoreOperation;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.PartitionGroupPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangeState;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Edge cases for the {@code CurrentClusterConfiguration} serialization that the property test
 * ({@code ProtoBufSerializerPropertyTest#shouldEncodeAndDecodeCurrentClusterConfiguration}) cannot
 * exercise: decoding invalid bytes, decoding a proto that carries a broker lifecycle state which
 * the domain model cannot represent, a graph whose edges must survive exactly, and the decode-only
 * legacy phase shape that only a pre-upgrade peer can put on the wire. The remaining happy-path
 * round-trips are covered by the property test.
 */
final class CurrentClusterConfigurationSerializerTest {

  private static final MemberId MEMBER = MemberId.from("0");
  private static final MemberId OTHER_MEMBER = MemberId.from("1");

  private final ProtoBufSerializer serializer = new ProtoBufSerializer();

  @Test
  void shouldFailToDecodeInvalidBytes() {
    // given — a truncated protobuf message (tag for field 1 with no value)
    final byte[] invalid = {0x08};

    // when / then
    assertThatThrownBy(() -> serializer.decodeCurrentClusterConfiguration(invalid))
        .isInstanceOf(DecodingFailed.class);
  }

  @ParameterizedTest
  @EnumSource(
      value = Topology.State.class,
      names = {"BOOTSTRAPPING", "RECOVERING"})
  void shouldRejectBrokerStateWithNonLifecycleState(final Topology.State state) {
    // given — a global configuration whose broker carries a state that is not a broker lifecycle
    // state (BrokerState.State has no BOOTSTRAPPING/RECOVERING)
    final var proto =
        Topology.GlobalConfiguration.newBuilder()
            .setVersion(1)
            .putMembers(
                "0",
                Topology.BrokerState.newBuilder()
                    .setVersion(0)
                    .setLastUpdated(Timestamp.newBuilder().build())
                    .setState(state)
                    .build())
            .build();

    // when / then
    assertThatThrownBy(() -> serializer.decodeGlobalConfiguration(proto))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldRoundTripGossipStateWithBothFields() {
    // given — a gossip state carrying both the legacy view (field 1) and the new model (field 2)
    final var legacy =
        ClusterConfiguration.init()
            .addMember(MemberId.from("0"), MemberState.initializeAsActive(Map.of()));
    final var state = new ClusterConfigurationGossipState();
    state.setClusterConfiguration(legacy);
    state.setCurrentClusterConfiguration(CurrentClusterConfiguration.fromLegacy(legacy));

    // when
    final var decoded = serializer.decode(serializer.encode(state));

    // then — both fields survive the round-trip
    assertThat(decoded).isEqualTo(state);
    assertThat(decoded.getClusterConfiguration()).isEqualTo(legacy);
    assertThat(decoded.getCurrentClusterConfiguration())
        .isEqualTo(CurrentClusterConfiguration.fromLegacy(legacy));
  }

  @Test
  void shouldRoundTripGossipStateWithOnlyLegacyField() {
    // given — an old broker gossips only field 1
    final var legacy =
        ClusterConfiguration.init()
            .addMember(MemberId.from("0"), MemberState.initializeAsActive(Map.of()));
    final var state = new ClusterConfigurationGossipState();
    state.setClusterConfiguration(legacy);

    // when
    final var decoded = serializer.decode(serializer.encode(state));

    // then
    assertThat(decoded.getClusterConfiguration()).isEqualTo(legacy);
    assertThat(decoded.getCurrentClusterConfiguration()).isNull();
  }

  @Test
  void shouldRejectADecodedGraphWithADependencyCycle() {
    // given — a proto whose two operations depend on each other. Nothing produces this from the
    // domain model (OperationGraph.of rejects the cycle before it could ever be encoded), so the
    // only way it reaches decode is corruption or a future encoding bug -- exactly what a decode
    // path has to distrust rather than assume.
    final var operationA =
        Topology.PartitionGroupChangeOperation.newBuilder()
            .setMemberId(MEMBER.id())
            .setModeChange(
                Topology.ModeChangeOperation.newBuilder().setMode(Topology.Mode.MODE_PROCESSING))
            .build();
    final var plannedA =
        Topology.PlannedOperation.newBuilder()
            .setId(0)
            .setPartitionGroupOperation(operationA)
            .addDependsOn(1);
    final var plannedB =
        Topology.PlannedOperation.newBuilder()
            .setId(1)
            .setPartitionGroupOperation(operationA)
            .addDependsOn(0);
    final var graph =
        Topology.OperationGraph.newBuilder()
            .addOperations(plannedA)
            .addOperations(plannedB)
            .build();
    final var plan =
        Topology.DependencyChangePlan.newBuilder()
            .setId(1)
            .setStatus(Topology.ChangeStatus.IN_PROGRESS)
            .setStartedAt(Timestamp.newBuilder().build())
            .setGraph(graph)
            .build();
    final var group =
        Topology.PartitionGroupConfiguration.newBuilder()
            .setVersion(1)
            .setIncarnationNumber(0)
            .setPendingChanges(plan)
            .build();
    final var proto =
        Topology.CurrentClusterConfiguration.newBuilder()
            .setVersion(0)
            .setGlobalConfiguration(Topology.GlobalConfiguration.newBuilder().setVersion(1).build())
            .putPartitionGroups("tenant", group)
            .build();

    // when / then
    assertThatThrownBy(() -> serializer.decodeCurrentClusterConfiguration(proto.toByteArray()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("dependency cycle");
  }

  @Test
  void shouldRoundTripAGroupCarryingADependencyGraphChange() {
    // given — a group carrying a graph with real edges. The property test generates graphs too, but
    // only ones whose shape its generator happens to build; this pins a two-stage fan-in/fan-out
    // explicitly, since a lost edge is invisible in anything that only compares operation lists.
    final var graph = restoreGraph();
    final var configuration = configurationWithGraph(graph);

    // when
    final var decoded =
        serializer.decodeCurrentClusterConfiguration(
            serializer.encodeCurrentClusterConfiguration(configuration));

    // then — the operations and the edges between them both survive; an edge lost in transit would
    // let a peer start an operation whose dependency has not run
    assertThat(decoded).isEqualTo(configuration);
    assertThat(decoded.partitionGroups().values())
        .singleElement()
        .satisfies(
            group -> assertThat(group.pendingChanges().orElseThrow().graph()).isEqualTo(graph));
  }

  @Test
  void shouldRoundTripPartialProgressThroughAGraph() {
    // given — one operation of the graph has completed, which is the state a broker gossips while a
    // graph change is in flight and the state a peer must be able to read to know what is runnable
    final var started = groupWithGraph(restoreGraph());
    final var firstOperation =
        started.pendingChanges().orElseThrow().graph().operations().firstKey();
    final var inFlight =
        configurationWith(started.completeOperation(firstOperation, UnaryOperator.identity()));

    // when
    final var decoded =
        serializer.decodeCurrentClusterConfiguration(
            serializer.encodeCurrentClusterConfiguration(inFlight));

    // then
    assertThat(decoded).isEqualTo(inFlight);
    assertThat(decoded.partitionGroups().values())
        .singleElement()
        .satisfies(
            group ->
                assertThat(group.pendingChanges().orElseThrow().completed())
                    .containsKey(firstOperation));
  }

  @Test
  void shouldDecodeALegacyParallelPhaseAsASequentialGraph() {
    // given — a phase in the shape written before the queue phase and the graph phase became one
    // type. Nothing encodes this any more; it reaches decode only from a configuration persisted or
    // gossiped by a broker that predates the merge, which is the rolling-upgrade path.
    final var first = modeChange(MEMBER);
    final var second = modeChange(OTHER_MEMBER);
    final var legacyPhase =
        Topology.PhasedChangePlanPhase.newBuilder()
            .setPartitionGroupParallelPhase(
                Topology.PartitionGroupParallelPhase.newBuilder()
                    .putGroupOperations(
                        "tenant",
                        Topology.PartitionGroupOperationList.newBuilder()
                            .addOperations(first)
                            .addOperations(second)
                            .build()))
            .build();
    final var proto =
        Topology.CurrentClusterConfiguration.newBuilder()
            .setVersion(0)
            .setGlobalConfiguration(Topology.GlobalConfiguration.newBuilder().setVersion(1).build())
            .setPhasedChangeState(
                Topology.PhasedChangeState.newBuilder()
                    .setNextId(2)
                    .addPending(
                        Topology.PhasedChangePlan.newBuilder()
                            .setId(1)
                            .setCurrentPhaseIndex(0)
                            .addPhases(legacyPhase)
                            .setStartedAt(Timestamp.newBuilder().build())))
            .build();

    // when
    final var decoded = serializer.decodeCurrentClusterConfiguration(proto.toByteArray());

    // then — the flat list becomes a *chain*, not a free graph. Asserting only on the operations
    // would pass just as well for a fully-parallel decode, which would let both operations start at
    // once on a plan whose author never claimed they were independent.
    final var phase =
        (PartitionGroupPhase) decoded.phasedChangeState().pending().get(1L).phases().getFirst();
    final var graph = phase.groupGraphs().get("tenant");
    assertThat(graph.operations().get(OperationId.of(0)).dependsOn()).isEmpty();
    assertThat(graph.operations().get(OperationId.of(1)).dependsOn())
        .containsExactly(OperationId.of(0));
    assertThat(graph.inOrder())
        .containsExactly(
            new ModeChangeOperation(MEMBER, Mode.PROCESSING),
            new ModeChangeOperation(OTHER_MEMBER, Mode.PROCESSING));
  }

  private static Topology.PartitionGroupChangeOperation modeChange(final MemberId memberId) {
    return Topology.PartitionGroupChangeOperation.newBuilder()
        .setMemberId(memberId.id())
        .setModeChange(
            Topology.ModeChangeOperation.newBuilder().setMode(Topology.Mode.MODE_PROCESSING))
        .build();
  }

  /** A two-stage graph: both pre-restores may run at once, both restores wait for both of them. */
  private static OperationGraph restoreGraph() {
    final var builder = OperationGraph.builder();
    final var preRestores =
        Set.of(
            builder.add(new PartitionPreRestoreOperation(MEMBER, 1)),
            builder.add(new PartitionPreRestoreOperation(OTHER_MEMBER, 1)));
    builder.add(new PartitionRestoreOperation(MEMBER, 1, new TreeSet<>(Set.of(1L))), preRestores);
    builder.add(
        new PartitionRestoreOperation(OTHER_MEMBER, 1, new TreeSet<>(Set.of(1L))), preRestores);
    return builder.build();
  }

  private static CurrentClusterConfiguration configurationWithGraph(final OperationGraph graph) {
    return configurationWith(groupWithGraph(graph));
  }

  private static PartitionGroupConfiguration groupWithGraph(final OperationGraph graph) {
    final var members =
        Map.of(
            MEMBER, BrokerPartitionState.initialize(Map.of()),
            OTHER_MEMBER, BrokerPartitionState.initialize(Map.of()));
    return new PartitionGroupConfiguration(
            1, 0, members, Optional.empty(), Optional.empty(), Optional.empty())
        .startGraphConfigurationChange(graph);
  }

  private static CurrentClusterConfiguration configurationWith(
      final PartitionGroupConfiguration group) {
    return new CurrentClusterConfiguration(
        CurrentClusterConfiguration.INITIAL_VERSION,
        globalConfiguration(),
        Map.of("tenant", group),
        PhasedChangeState.empty());
  }

  private static GlobalConfiguration globalConfiguration() {
    return new GlobalConfiguration(
        1,
        Optional.empty(),
        Map.of(
            MEMBER, new BrokerState(0, Instant.EPOCH, BrokerState.State.ACTIVE),
            OTHER_MEMBER, new BrokerState(0, Instant.EPOCH, BrokerState.State.ACTIVE)),
        Optional.empty(),
        Optional.empty(),
        Optional.empty());
  }
}
