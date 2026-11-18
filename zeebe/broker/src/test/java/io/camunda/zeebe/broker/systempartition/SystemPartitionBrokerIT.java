/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.systempartition;

import static io.atomix.raft.cluster.RaftMember.Type.ACTIVE;
import static io.atomix.raft.cluster.RaftMember.Type.PASSIVE;
import static io.camunda.zeebe.broker.systempartition.SystemPartitionTestCluster.node;
import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.atomix.raft.cluster.FenceEntry;
import io.atomix.raft.cluster.RaftMember;
import io.atomix.raft.cluster.RaftMember.Type;
import io.atomix.raft.storage.system.Configuration;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.partitioning.Scheme;
import io.camunda.zeebe.broker.system.configuration.partitioning.ZoneAwareCfg;
import io.camunda.zeebe.broker.system.configuration.partitioning.ZoneCfg;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.BrokerScaleRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ForceRemoveBrokersRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.RemoveZoneRequest;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * The system partition on real, in-process brokers (design revision 7): bootstrap, scaling, force
 * removal, restart and disk loss, driven by the dynamic configuration where the design says so.
 */
@Timeout(value = 5, unit = TimeUnit.MINUTES)
final class SystemPartitionBrokerIT {

  private static final MemberId B0 = MemberId.from("0");
  private static final MemberId B1 = MemberId.from("1");
  private static final MemberId B2 = MemberId.from("2");
  private static final MemberId B3 = MemberId.from("3");

  @TempDir private Path tmp;
  private SystemPartitionTestCluster cluster;

  @AfterEach
  void tearDown() {
    if (cluster != null) {
      cluster.close();
    }
  }

  private static Map<MemberId, Type> members(final Object... idsAndTypes) {
    final var members = new LinkedHashMap<MemberId, Type>();
    for (int i = 0; i < idsAndTypes.length; i += 2) {
      members.put((MemberId) idsAndTypes[i], (Type) idsAndTypes[i + 1]);
    }
    return members;
  }

  private static Consumer<BrokerCfg> clusterOf(final int size, final int replicationFactor) {
    return cfg -> {
      cfg.getCluster().setClusterSize(size);
      cfg.getCluster().setReplicationFactor(replicationFactor);
    };
  }

  /** Brokers 0 to 3; the first {@code size} brokers form the static configuration. */
  private SystemPartitionTestCluster cluster(final int size, final int replicationFactor) {
    cluster =
        new SystemPartitionTestCluster(
            tmp, List.of(node(0), node(1), node(2), node(3)), clusterOf(size, replicationFactor));
    final var initial = new ArrayList<MemberId>();
    for (int i = 0; i < size; i++) {
      initial.add(MemberId.from(Integer.toString(i)));
    }
    cluster.start(initial);
    cluster.awaitDataPartitionLeader();
    return cluster;
  }

  private static long incarnationOf(final Configuration configuration, final MemberId member) {
    return configuration.newMembers().stream()
        .filter(m -> m.memberId().equals(member))
        .mapToLong(RaftMember::getIncarnation)
        .findFirst()
        .orElseThrow();
  }

  @Test
  void shouldBootstrapNewClusterWithVotersAndPassiveMembers() {
    // when - 4 brokers, replication factor 3: the policy has 3 voters
    cluster(4, 3);

    // then - the initial configuration (5.4.1), and the first leader appended it (R14)
    final var configuration =
        cluster.awaitLeaderConfiguration(members(B0, ACTIVE, B1, ACTIVE, B2, ACTIVE, B3, PASSIVE));
    assertThat(configuration.index()).isPositive();
    assertThat(configuration.newMembers()).allMatch(m -> m.getIncarnation() == 0);
    assertThat(cluster.awaitLeader()).isIn(B0, B1, B2);
    assertThat(List.of(B0, B1, B2, B3))
        .allSatisfy(
            member -> assertThat(cluster.service(member).publishedIncarnation()).contains(0L));
    Awaitility.await("all replicas have the configuration")
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () ->
                assertThat(List.of(B0, B1, B2, B3))
                    .allSatisfy(
                        member ->
                            assertThat(cluster.configuration(member))
                                .hasValueSatisfying(c -> assertThat(c.index()).isPositive())));
  }

  @Test
  void shouldBootstrapZoneLayoutWithControlZoneFirst() {
    // given - zones a and b with 2 brokers each and control zone c with 1 broker (2 + 2 + 1)
    final var zones =
        List.of(new ZoneCfg("c", 1, 1, 3), new ZoneCfg("a", 2, 2, 2), new ZoneCfg("b", 2, 2, 1));
    cluster =
        new SystemPartitionTestCluster(
            tmp,
            List.of(node("a", 0), node("a", 1), node("b", 0), node("b", 1), node("c", 0)),
            cfg -> {
              cfg.getCluster().setClusterSize(5);
              cfg.getCluster().setReplicationFactor(5);
              cfg.getExperimental().getPartitioning().setScheme(Scheme.ZONE_AWARE);
              cfg.getExperimental().getPartitioning().setZoneAware(new ZoneAwareCfg(zones));
            });
    final var all =
        List.of(
            MemberId.from("a", 0),
            MemberId.from("a", 1),
            MemberId.from("b", 0),
            MemberId.from("b", 1),
            MemberId.from("c", 0));

    // when
    cluster.start(all);

    // then - each zone fills its slots; all 5 brokers are voters
    final var types = new LinkedHashMap<MemberId, Type>();
    all.forEach(member -> types.put(member, ACTIVE));
    cluster.awaitLeaderConfiguration(types);
    cluster.awaitLeader();
  }

  @Test
  void shouldAddNewBrokerAsPassiveMemberOnScaleUp() {
    // given - 3 voters
    cluster(3, 3);
    cluster.awaitLeaderConfiguration(members(B0, ACTIVE, B1, ACTIVE, B2, ACTIVE));

    // when - broker 3 starts without data and the cluster scales up
    cluster.start(List.of(B3), cfg -> cfg.getCluster().setClusterSize(4));
    final var response =
        cluster
            .requestSender(B0)
            .scaleMembers(new BrokerScaleRequest(Set.of(B0, B1, B2, B3), false))
            .join();
    assertThat(response.isRight()).describedAs("%s", response).isTrue();
    cluster.awaitNoPendingChange(B0);

    // then - the leader adds it as passive, because the policy has no empty slot (5.6.1)
    final var configuration =
        cluster.awaitLeaderConfiguration(members(B0, ACTIVE, B1, ACTIVE, B2, ACTIVE, B3, PASSIVE));
    final var published = cluster.service(B3).publishedIncarnation().orElseThrow();
    assertThat(published).isNotZero();
    assertThat(incarnationOf(configuration, B3)).isEqualTo(published);
  }

  @Test
  void shouldReplaceLeavingVoterInOneJointChangeOnScaleDown() {
    // given - voters 0, 1 and 2, passive member 3
    cluster(4, 3);
    cluster.awaitLeaderConfiguration(members(B0, ACTIVE, B1, ACTIVE, B2, ACTIVE, B3, PASSIVE));
    final var recorded = new CopyOnWriteArrayList<Configuration>();
    cluster
        .service(B0)
        .partition()
        .orElseThrow()
        .addConfigurationListener((configuration, committed) -> recorded.add(configuration));

    // when - the voter 2 leaves
    final var response =
        cluster
            .requestSender(B0)
            .scaleMembers(new BrokerScaleRequest(Set.of(B0, B1, B3), false))
            .join();
    assertThat(response.isRight()).describedAs("%s", response).isTrue();
    final var topology = cluster.awaitNoPendingChange(B0);

    // then - MemberLeave completed only after the leader committed a configuration without 2
    assertThat(topology.globalConfiguration().members()).doesNotContainKey(B2);
    final var configuration =
        cluster.awaitLeaderConfiguration(members(B0, ACTIVE, B1, ACTIVE, B3, ACTIVE));
    assertThat(configuration.fenced()).containsExactly(new FenceEntry(B2, 0));
    // and one joint change removed 2 and promoted 3 (D9)
    assertThat(recorded)
        .anySatisfy(
            joint -> {
              assertThat(joint.requiresJointConsensus()).isTrue();
              assertThat(joint.oldMembers())
                  .anyMatch(m -> m.memberId().equals(B2) && m.getType() == ACTIVE);
              assertThat(joint.newMembers())
                  .anyMatch(m -> m.memberId().equals(B3) && m.getType() == ACTIVE)
                  .noneMatch(m -> m.memberId().equals(B2));
            });
  }

  @Test
  void shouldForceRemoveBrokersWithNormalChangeWhenRemainingVotersAreQuorum() {
    // given - voters 0, 1 and 2, passive member 3; brokers 2 and 3 fail
    cluster(4, 3);
    cluster.awaitLeaderConfiguration(members(B0, ACTIVE, B1, ACTIVE, B2, ACTIVE, B3, PASSIVE));
    cluster.stop(B2);
    cluster.stop(B3);

    // when
    final var response =
        cluster
            .requestSender(B0)
            .forceRemoveBrokers(new ForceRemoveBrokersRequest(Set.of(B2, B3), false))
            .join();
    assertThat(response.isRight()).describedAs("%s", response).isTrue();
    cluster.awaitNoPendingChange(B0);

    // then - phase 0: the leader removed both in one normal change (5.6.3)
    final var configuration = cluster.awaitLeaderConfiguration(members(B0, ACTIVE, B1, ACTIVE));
    assertThat(configuration.force()).isFalse();
    assertThat(configuration.fenced())
        .containsExactlyInAnyOrder(new FenceEntry(B2, 0), new FenceEntry(B3, 0));
  }

  @Test
  void shouldForceRemoveBrokersWithForcedChangeOnlyWhenLossIsAccepted() {
    // given - voters 0, 1 and 2, passive member 3; voters 1 and 2 fail: no quorum
    cluster(4, 3);
    cluster.awaitLeaderConfiguration(members(B0, ACTIVE, B1, ACTIVE, B2, ACTIVE, B3, PASSIVE));
    cluster.stop(B1);
    cluster.stop(B2);

    // when - force-remove without accepting a possible loss
    final var first =
        cluster
            .requestSender(B0)
            .forceRemoveBrokers(new ForceRemoveBrokersRequest(Set.of(B1, B2), false))
            .join();
    assertThat(first.isRight()).describedAs("%s", first).isTrue();

    // then - the survivor 0 is not a blocking set of {0, 1, 2}: phase 0 fails and is retried,
    // and the system partition does not change (D20)
    Awaitility.await("phase 0 keeps failing")
        .during(Duration.ofSeconds(10))
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(
            () -> {
              final var topology =
                  cluster.requestSender(B0).getTopology().get(10, TimeUnit.SECONDS).get();
              assertThat(topology.phasedChangeState().pending()).isNotEmpty();
              assertThat(cluster.configuration(B0).orElseThrow().hasMember(B1)).isTrue();
            });

    // when - the operator cancels the plan and sends it again with acceptPossibleLoss
    cluster
        .requestSender(B0)
        .cancelTopologyChange(
            new io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest
                .CancelChangeRequest(first.get().changeId()))
        .join();
    final var second =
        cluster
            .requestSender(B0)
            .forceRemoveBrokers(new ForceRemoveBrokersRequest(Set.of(B1, B2), false, true))
            .join();
    assertThat(second.isRight()).describedAs("%s", second).isTrue();
    cluster.awaitNoPendingChange(B0);

    // then - a forced change without 1 and 2, which are fenced; the survivors elect a leader
    Awaitility.await("the forced change is done")
        .atMost(Duration.ofMinutes(1))
        .untilAsserted(
            () -> {
              final var configuration = cluster.configuration(B0).orElseThrow();
              assertThat(configuration.hasMember(B1)).isFalse();
              assertThat(configuration.hasMember(B2)).isFalse();
              assertThat(configuration.fenced())
                  .contains(new FenceEntry(B1, 0), new FenceEntry(B2, 0));
            });
    assertThat(cluster.awaitLeader()).isIn(B0, B3);
  }

  @Test
  void shouldForceRemoveZoneWithoutLossWhenSurvivorsAreBlockingSet() {
    // given - dual region: zones a and b with 2 brokers each, 2 voters each (5.10.1)
    final var zones = List.of(new ZoneCfg("a", 2, 2, 2), new ZoneCfg("b", 2, 2, 1));
    final var a0 = MemberId.from("a", 0);
    final var a1 = MemberId.from("a", 1);
    final var b0 = MemberId.from("b", 0);
    final var b1 = MemberId.from("b", 1);
    cluster =
        new SystemPartitionTestCluster(
            tmp,
            List.of(node("a", 0), node("a", 1), node("b", 0), node("b", 1)),
            cfg -> {
              cfg.getCluster().setClusterSize(4);
              cfg.getCluster().setReplicationFactor(4);
              cfg.getExperimental().getPartitioning().setScheme(Scheme.ZONE_AWARE);
              cfg.getExperimental().getPartitioning().setZoneAware(new ZoneAwareCfg(zones));
            });
    cluster.start(List.of(a0, a1, b0, b1));
    cluster.awaitDataPartitionLeader();
    cluster.awaitLeaderConfiguration(members(a0, ACTIVE, a1, ACTIVE, b0, ACTIVE, b1, ACTIVE));

    // when - zone b fails, and the operator removes it with force
    cluster.stop(b0);
    cluster.stop(b1);
    final var response =
        cluster.requestSender(a0).removeZone(new RemoveZoneRequest("b", false, true)).join();
    assertThat(response.isRight()).describedAs("%s", response).isTrue();
    cluster.awaitNoPendingChange(a0);

    // then - phase 0 made a forced change: a0 and a1 are a blocking set of 4 voters
    final var configuration = cluster.awaitLeaderConfiguration(members(a0, ACTIVE, a1, ACTIVE));
    assertThat(configuration.fenced()).contains(new FenceEntry(b0, 0), new FenceEntry(b1, 0));
  }

  @Test
  void shouldKeepConfigurationWhenAllBrokersRestart() {
    // given
    cluster(4, 3);
    final var before =
        cluster.awaitLeaderConfiguration(members(B0, ACTIVE, B1, ACTIVE, B2, ACTIVE, B3, PASSIVE));

    // when
    List.of(B0, B1, B2, B3).forEach(cluster::stop);
    cluster.start(List.of(B0, B1, B2, B3));

    // then - each broker uses row 1 with its stored configuration (5.6.8)
    final var after =
        cluster.awaitLeaderConfiguration(members(B0, ACTIVE, B1, ACTIVE, B2, ACTIVE, B3, PASSIVE));
    assertThat(after.index()).isGreaterThanOrEqualTo(before.index());
    assertThat(after.newMembers()).allMatch(m -> m.getIncarnation() == 0);
    assertThat(cluster.awaitLeader()).isIn(B0, B1, B2);
  }

  @Test
  void shouldReplaceVoterThatLostItsDiskByPassiveMember() {
    // given - voters 0, 1 and 2, passive member 3
    cluster(4, 3);
    cluster.awaitLeaderConfiguration(members(B0, ACTIVE, B1, ACTIVE, B2, ACTIVE, B3, PASSIVE));

    // when - broker 1 loses its disk and starts without data (row 2)
    cluster.stop(B1);
    cluster.deleteData(B1);
    cluster.start(List.of(B1));

    // then - the leader replaces 1#0 by the new incarnation and promotes 3 in one change (5.6.9)
    final var configuration =
        cluster.awaitLeaderConfiguration(members(B0, ACTIVE, B2, ACTIVE, B3, ACTIVE, B1, PASSIVE));
    final var published = cluster.service(B1).publishedIncarnation().orElseThrow();
    assertThat(published).isNotZero();
    assertThat(incarnationOf(configuration, B1)).isEqualTo(published);
    // a broker that probed only after the first configuration entry started without data at the
    // first start too, thus other entries can be on the fence list (5.4.2, row 2)
    assertThat(configuration.fenced())
        .contains(new FenceEntry(B1, 0))
        .doesNotContain(new FenceEntry(B1, published));
  }

  @Test
  void shouldPromoteVoterThatLostItsDiskIntoEmptySlotOnceCaughtUp() {
    // given - 3 voters, no passive member
    cluster(3, 3);
    cluster.awaitLeaderConfiguration(members(B0, ACTIVE, B1, ACTIVE, B2, ACTIVE));

    // when - broker 1 loses its disk and starts without data
    cluster.stop(B1);
    cluster.deleteData(B1);
    cluster.start(List.of(B1));

    // then - the old incarnation is replaced, its slot stays empty until the new incarnation has
    // caught up, then the leader promotes it (5.6.4)
    final var published =
        Awaitility.await("broker 1 publishes a new incarnation")
            .atMost(Duration.ofSeconds(30))
            .until(() -> cluster.service(B1).publishedIncarnation().orElse(0L), i -> i != 0);
    final var configuration =
        cluster.awaitLeaderConfiguration(
            members(B0, ACTIVE, B1, ACTIVE, B2, ACTIVE), c -> incarnationOf(c, B1) == published);
    assertThat(configuration.fenced())
        .contains(new FenceEntry(B1, 0))
        .doesNotContain(new FenceEntry(B1, published));
  }
}
