/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.ecv;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.broker.ecv.CommitEvaluator.MemberData;
import io.camunda.zeebe.broker.ecv.EcvApi.CheckResult;
import io.camunda.zeebe.broker.ecv.EcvProtocol.PartitionProgress;
import io.camunda.zeebe.broker.ecv.EcvProtocol.ReadinessData;
import io.camunda.zeebe.broker.ecv.EcvProtocol.SupportedData;
import io.camunda.zeebe.broker.ecv.UpgradeReadinessRegistry.ComponentReadiness;
import io.camunda.zeebe.util.ecv.Capability;
import io.camunda.zeebe.util.ecv.CapabilitySet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class CommitEvaluatorTest {
  private static final MemberId B0 = MemberId.from("0");
  private static final MemberId B1 = MemberId.from("1");
  private static final CapabilitySet BASE = Capability.base();
  private static final CapabilitySet ALL = Capability.all();

  @Test
  void shouldMakeCommitAvailableWhenAllBrokersSupportMore() {
    // given
    final var members = Map.of(B0, ready("a", ALL), B1, ready("b", ALL));

    // when
    final var evaluation = evaluate(BASE, members, appliedEverywhere(BASE));

    // then
    assertThat(evaluation.commitAvailable()).isTrue();
    assertThat(evaluation.failedChecks()).isEmpty();
    assertThat(evaluation.decision()).isEqualTo(ALL);
  }

  @Test
  void shouldNotMakeCommitAvailableWhileOneBrokerSupportsLess() {
    // when
    final var evaluation =
        evaluate(BASE, Map.of(B0, ready("a", ALL), B1, ready("b", BASE)), appliedEverywhere(BASE));

    // then - R5
    assertThat(evaluation.ready()).isEqualTo(BASE);
    assertThat(evaluation.commitAvailable()).isFalse();
  }

  @Test
  void shouldFailWhenBrokerIsDown() {
    // when - R4
    final var evaluation = evaluate(BASE, Map.of(B0, ready("a", ALL)), appliedEverywhere(BASE));

    // then
    assertThat(failed(evaluation)).containsExactly("brokers-available");
  }

  @Test
  void shouldIgnoreReadinessFromEarlierStart() {
    // given - broker 1 restarted; its old readiness key is still in the gossip data
    final var stale =
        new MemberData(
            new SupportedData("new-start", ALL.encode(), true),
            new ReadinessData("old-start", Map.of("engine", new ComponentReadiness(true, null))));

    // when
    final var evaluation =
        evaluate(BASE, Map.of(B0, ready("a", ALL), B1, stale), appliedEverywhere(BASE));

    // then
    assertThat(failed(evaluation)).containsExactly("components-ready");
  }

  @Test
  void shouldShowRefusedBrokerAfterCommit() {
    // when - R15, R21
    final var evaluation =
        evaluate(ALL, Map.of(B0, ready("a", ALL), B1, ready("b", BASE)), appliedEverywhere(ALL));

    // then
    assertThat(failed(evaluation)).containsExactly("no-refused-broker");
    assertThat(evaluation.state().members().get("1").refused()).isTrue();
    assertThat(evaluation.state().members().get("1").unsupportedEnabled())
        .containsExactly("POC_812_FORMAT_CHANGE", "POC_812_DEPENDENT_BEHAVIOR");
  }

  @Test
  void shouldShowPendingDistributionUntilEveryPartitionApplied() {
    // given - partition 2 did not apply the decision yet, partition 3 has no leader
    final var progress = new HashMap<Integer, PartitionProgress>();
    progress.put(1, new PartitionProgress(ALL.encode(), null, null));
    progress.put(2, new PartitionProgress(BASE.encode(), null, null));

    // when
    final var evaluation =
        CommitEvaluator.evaluate(
            "0",
            "MANUAL",
            ALL,
            Set.of(B0, B1),
            Map.of(B0, ready("a", ALL), B1, ready("b", ALL)),
            Map.of("default", Set.of(1, 2, 3)),
            Map.of("default", progress));

    // then - R27
    final var tenant = evaluation.state().physicalTenants().get("default");
    assertThat(tenant.complete()).isFalse();
    assertThat(tenant.partitions().get(1).status()).isEqualTo("APPLIED");
    assertThat(tenant.partitions().get(2).status()).isEqualTo("PENDING");
    assertThat(tenant.partitions().get(3).status()).isEqualTo("UNKNOWN");
    assertThat(evaluation.state().distributionPending()).isTrue();
    assertThat(evaluation.state().commitPending()).isTrue();
    assertThat(failed(evaluation)).containsExactly("physical-tenants-available");
  }

  @Test
  void shouldCountGatewaysForReadyCapabilities() {
    // given - a member outside the cluster configuration that reports its capabilities
    final var gateway = MemberId.from("gateway-0");
    final var members =
        Map.of(
            B0,
            ready("a", ALL),
            B1,
            ready("b", ALL),
            gateway,
            new MemberData(new SupportedData("g", BASE.encode(), false), null));

    // when
    final var evaluation = evaluate(BASE, members, appliedEverywhere(BASE));

    // then
    assertThat(evaluation.ready()).isEqualTo(BASE);
    assertThat(evaluation.state().members().get("gateway-0").type()).isEqualTo("gateway");
  }

  @Test
  void shouldRejectCommitWhileClusterConfigurationIsUnknown() {
    // given - without the cluster configuration every member would count as a gateway
    final var members = Map.of(B0, ready("a", ALL), B1, ready("b", ALL));

    // when
    final var evaluation =
        CommitEvaluator.evaluate("0", "MANUAL", BASE, Set.of(), members, Map.of(), Map.of());

    // then
    assertThat(evaluation.commitAvailable()).isTrue();
    assertThat(failed(evaluation)).containsExactly("cluster-configuration-known");
  }

  private static CommitEvaluator.Evaluation evaluate(
      final CapabilitySet enabled,
      final Map<MemberId, MemberData> members,
      final Map<String, Map<Integer, PartitionProgress>> progress) {
    return CommitEvaluator.evaluate(
        "0", "MANUAL", enabled, Set.of(B0, B1), members, Map.of("default", Set.of(1)), progress);
  }

  private static Map<String, Map<Integer, PartitionProgress>> appliedEverywhere(
      final CapabilitySet applied) {
    return Map.of("default", Map.of(1, new PartitionProgress(applied.encode(), null, null)));
  }

  private static MemberData ready(final String instance, final CapabilitySet supported) {
    return new MemberData(
        new SupportedData(instance, supported.encode(), true),
        new ReadinessData(instance, Map.of("engine", new ComponentReadiness(true, null))));
  }

  private static java.util.List<String> failed(final CommitEvaluator.Evaluation evaluation) {
    return evaluation.failedChecks().stream().map(CheckResult::name).toList();
  }
}
