/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.clusterversion;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.protocol.impl.clusterversion.ClusterVersionCatalog.Capability;
import io.camunda.zeebe.protocol.impl.record.value.clusterversion.ClusterVersionRecord;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ClusterVersionIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.ArrayList;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.Rule;
import org.junit.Test;

/**
 * Engine Capability Version PoC — proves Phase A + B mechanics:
 *
 * <ul>
 *   <li>RAISE command produces APPLIED event (Phase A: the activation seam works).
 *   <li>The recordVersion stamped on APPLIED is selected by the processor's gate (Phase B: write
 *       site chooses applier version).
 *   <li>Two registered applier versions for the same intent coexist; the right one runs based on
 *       the stamped version (Phase B: versioned applier registry).
 *   <li>Raising backwards is rejected (the deck's "down is not a direction" rule).
 *   <li>A new feature (PING) gated at its own ordinal is rejected by {@link ClusterVersionGate}
 *       <em>at the command-API layer</em> before any record reaches the log; once the cluster is
 *       raised to that ordinal, the same command is admitted and produces a PINGED event.
 * </ul>
 */
public final class ClusterVersionPocTest {

  // Captures every (line, ordinal) the engine pushes to the broker-side update listener.
  private final List<int[]> ecvNotifications = new ArrayList<>();

  // Per-test isolation: ECV state is monotonic — a raise to (X, Y) blocks any later raise to a
  // lower (line, ordinal), so sibling tests can't share an engine instance.
  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition()
          .withClusterVersionUpdateListener(
              (line, ordinal) -> ecvNotifications.add(new int[] {line, ordinal}));

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldWriteV1Applier_whenActiveEcvIsBelowGate() {
    // given: a fresh engine — active ECV is (0, 0), below the v2 gate at (810, 2)
    final var raise = new ClusterVersionRecord().setLine(810).setOrdinal(1);

    // when
    engine.writeRecords(RecordToWrite.command().clusterVersion(ClusterVersionIntent.RAISE, raise));

    // then: APPLIED is appended at recordVersion 1 — gate not yet activated, fall back to v1
    final var applied =
        RecordingExporter.records()
            .filter(r -> r.getValueType() == ValueType.CLUSTER_VERSION)
            .filter(r -> r.getIntent() == ClusterVersionIntent.APPLIED)
            .getFirst();

    assertThat(applied.getRecordType()).isEqualTo(RecordType.EVENT);
    assertThat(applied.getRecordVersion()).isEqualTo(1);
    final var appliedValue = (ClusterVersionRecord) applied.getValue();
    assertThat(appliedValue.getLine()).isEqualTo(810);
    assertThat(appliedValue.getOrdinal()).isEqualTo(1);
  }

  @Test
  public void shouldWriteV1Applier_onTheVeryRaiseThatReachesTheGate() {
    // given: a fresh engine. The first raise targets the v2 gate's ordinal exactly.
    // when
    engine.writeRecords(
        RecordToWrite.command()
            .clusterVersion(
                ClusterVersionIntent.RAISE, new ClusterVersionRecord().setLine(810).setOrdinal(2)));

    // then: the raise that REACHES the gate is still written as v1 — at the moment the
    // processor consulted the gate, the active ECV was still (0, 0). This is the deliberate
    // "chicken and egg" choice; v2 will be active for subsequent raises.
    final var applied =
        RecordingExporter.records()
            .filter(r -> r.getValueType() == ValueType.CLUSTER_VERSION)
            .filter(r -> r.getIntent() == ClusterVersionIntent.APPLIED)
            .getFirst();

    assertThat(applied.getRecordVersion()).isEqualTo(1);
  }

  @Test
  public void shouldWriteV2Applier_onlyAfterClusterHasReachedGate() {
    // given: chain two raises — first ACTIVATES the v2 gate at (810, 2)
    engine.writeRecords(
        RecordToWrite.command()
            .clusterVersion(
                ClusterVersionIntent.RAISE, new ClusterVersionRecord().setLine(810).setOrdinal(2)));
    // wait until APPLIED so active ECV is (810, 2) before the next raise hits the processor
    RecordingExporter.records()
        .filter(r -> r.getValueType() == ValueType.CLUSTER_VERSION)
        .filter(r -> r.getIntent() == ClusterVersionIntent.APPLIED)
        .filter(r -> ((ClusterVersionRecord) r.getValue()).getOrdinal() == 2)
        .getFirst();

    // when: a second raise is processed
    engine.writeRecords(
        RecordToWrite.command()
            .clusterVersion(
                ClusterVersionIntent.RAISE, new ClusterVersionRecord().setLine(810).setOrdinal(5)));

    // then: with active ECV now at the gate, the second raise is written as v2
    final var second =
        RecordingExporter.records()
            .filter(r -> r.getValueType() == ValueType.CLUSTER_VERSION)
            .filter(r -> r.getIntent() == ClusterVersionIntent.APPLIED)
            .filter(r -> ((ClusterVersionRecord) r.getValue()).getOrdinal() == 5)
            .getFirst();

    assertThat(second.getRecordVersion()).isEqualTo(2);
    assertThat(((ClusterVersionRecord) second.getValue()).getGatedField())
        .isEqualTo("gated-default");
  }

  @Test
  public void shouldWalkApplierVersionChain_v1_then_v2_then_v3() {
    // The registry holds three APPLIED appliers: v1 (no requirement), v2 (≥ 810,2), v3 (≥ 810,3).
    // A chain of raises walks the cluster through each gate; each subsequent APPLIED is stamped
    // with the highest version whose requirement the CURRENT active ECV satisfies.

    // raise 1: active (0, 0). v2 req unmet, v3 req unmet → selectVersionFor picks v1.
    engine.writeRecords(
        RecordToWrite.command()
            .clusterVersion(
                ClusterVersionIntent.RAISE, new ClusterVersionRecord().setLine(810).setOrdinal(2)));
    final var firstApplied =
        RecordingExporter.records()
            .filter(r -> r.getValueType() == ValueType.CLUSTER_VERSION)
            .filter(r -> r.getIntent() == ClusterVersionIntent.APPLIED)
            .filter(r -> ((ClusterVersionRecord) r.getValue()).getOrdinal() == 2)
            .getFirst();
    assertThat(firstApplied.getRecordVersion()).isEqualTo(1);

    // raise 2: active (810, 2). v2 req met, v3 req unmet → picks v2.
    engine.writeRecords(
        RecordToWrite.command()
            .clusterVersion(
                ClusterVersionIntent.RAISE, new ClusterVersionRecord().setLine(810).setOrdinal(3)));
    final var secondApplied =
        RecordingExporter.records()
            .filter(r -> r.getValueType() == ValueType.CLUSTER_VERSION)
            .filter(r -> r.getIntent() == ClusterVersionIntent.APPLIED)
            .filter(r -> ((ClusterVersionRecord) r.getValue()).getOrdinal() == 3)
            .getFirst();
    assertThat(secondApplied.getRecordVersion()).isEqualTo(2);

    // raise 3: active (810, 3). v2 req met, v3 req met → picks v3 (highest eligible).
    engine.writeRecords(
        RecordToWrite.command()
            .clusterVersion(
                ClusterVersionIntent.RAISE, new ClusterVersionRecord().setLine(810).setOrdinal(5)));
    final var thirdApplied =
        RecordingExporter.records()
            .filter(r -> r.getValueType() == ValueType.CLUSTER_VERSION)
            .filter(r -> r.getIntent() == ClusterVersionIntent.APPLIED)
            .filter(r -> ((ClusterVersionRecord) r.getValue()).getOrdinal() == 5)
            .getFirst();
    assertThat(thirdApplied.getRecordVersion()).isEqualTo(3);
  }

  @Test
  public void shouldRejectRaiseGoingBackwards() {
    // given an initial raise to (810, 5)
    engine.writeRecords(
        RecordToWrite.command()
            .clusterVersion(
                ClusterVersionIntent.RAISE, new ClusterVersionRecord().setLine(810).setOrdinal(5)));

    // wait for it to be applied
    RecordingExporter.records()
        .filter(r -> r.getValueType() == ValueType.CLUSTER_VERSION)
        .filter(r -> r.getIntent() == ClusterVersionIntent.APPLIED)
        .getFirst();

    // when a second RAISE goes backwards to (810, 3)
    engine.writeRecords(
        RecordToWrite.command()
            .clusterVersion(
                ClusterVersionIntent.RAISE, new ClusterVersionRecord().setLine(810).setOrdinal(3)));

    // then the second RAISE is rejected
    final var rejection =
        RecordingExporter.records()
            .filter(r -> r.getValueType() == ValueType.CLUSTER_VERSION)
            .filter(r -> r.getRecordType() == RecordType.COMMAND_REJECTION)
            .getFirst();

    assertThat(rejection.getIntent()).isEqualTo(ClusterVersionIntent.RAISE);
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason()).contains("(810, 3)").contains("(810, 5)");
  }

  @Test
  public void shouldTrackActiveEcvInState() {
    // given a RAISE
    engine.writeRecords(
        RecordToWrite.command()
            .clusterVersion(
                ClusterVersionIntent.RAISE, new ClusterVersionRecord().setLine(810).setOrdinal(4)));

    // when the APPLIED event has been processed
    RecordingExporter.records()
        .filter(r -> r.getValueType() == ValueType.CLUSTER_VERSION)
        .filter(r -> r.getIntent() == ClusterVersionIntent.APPLIED)
        .getFirst();

    // then state reflects the new active ECV
    final var state = engine.getProcessingState().getClusterVersionState();
    assertThat(state.getActiveLine()).isEqualTo(810);
    assertThat(state.getActiveOrdinal()).isEqualTo(4);
    // Ordinal-only check. The introduction line of a feature is metadata, not a requirement —
    // backports carry the same ordinal across lines, so only the global ordinal number gates.
    assertThat(state.isAtLeast(4)).isTrue();
    assertThat(state.isAtLeast(5)).isFalse();
    assertThat(state.isAtLeast(99)).isFalse();
    assertThat(state.isAtLeast(2)).isTrue();
  }

  @Test
  public void shouldRejectPingAtAdmissionLayer_whenEcvBelowGate() {
    // given the cluster has not been raised — active ECV is (0, 0), well below the PING gate
    final var gate = new ClusterVersionGate(engine.getProcessingState().getClusterVersionState());

    // when an admission layer (REST/gRPC) checks whether PING may be admitted
    final boolean admitted = gate.isAvailable(ValueType.CLUSTER_VERSION, ClusterVersionIntent.PING);

    // then the gate rejects: the gateway would respond synchronously with a rejection and never
    // call writeRecords. The PING never enters the log.
    assertThat(admitted).isFalse();
    final var requirement =
        ClusterVersionGate.requirementFor(ValueType.CLUSTER_VERSION, ClusterVersionIntent.PING)
            .orElseThrow();
    assertThat(requirement).isEqualTo(10);

    // and: no PING (and no PINGED) record reached the log stream.
    RecordingExporter.expectNoMatchingRecords(
        stream ->
            stream
                .filter(r -> r.getValueType() == ValueType.CLUSTER_VERSION)
                .filter(
                    r ->
                        r.getIntent() == ClusterVersionIntent.PING
                            || r.getIntent() == ClusterVersionIntent.PINGED)
                .limit(1)
                .toList());
  }

  @Test
  public void shouldInvokeUpdateListenerOnRecovery_seed() {
    // given: the engine just started — onRecovered has fired during @Rule setup. The seed
    // listener inside the engine reads ClusterVersionState (line=0, ordinal=BASELINE=1 by
    // default) and pushes once.
    Awaitility.await()
        .untilAsserted(
            () ->
                assertThat(ecvNotifications)
                    .as("seed notification on recovery")
                    .anySatisfy(
                        n -> {
                          assertThat(n[0]).isEqualTo(0);
                          assertThat(n[1]).isEqualTo(1);
                        }));
  }

  @Test
  public void shouldInvokeUpdateListenerAfterRaise_push() {
    // given: a clean engine. Seed notification (0, 0) has already fired during onRecovered.
    // when: a RAISE is processed
    engine.writeRecords(
        RecordToWrite.command()
            .clusterVersion(
                ClusterVersionIntent.RAISE, new ClusterVersionRecord().setLine(810).setOrdinal(7)));

    // then: the engine pushes the new active ECV after appending APPLIED
    Awaitility.await()
        .untilAsserted(
            () ->
                assertThat(ecvNotifications)
                    .as("push notification on RAISE")
                    .anySatisfy(
                        n -> {
                          assertThat(n[0]).isEqualTo(810);
                          assertThat(n[1]).isEqualTo(7);
                        }));
  }

  @Test
  public void shouldAdmitPing_afterRaisingEcvToGateOrdinal() {
    // given the cluster has been raised to (any line, the PING gate's required ordinal)
    final var requirement =
        ClusterVersionGate.requirementFor(ValueType.CLUSTER_VERSION, ClusterVersionIntent.PING)
            .orElseThrow();
    engine.writeRecords(
        RecordToWrite.command()
            .clusterVersion(
                ClusterVersionIntent.RAISE,
                new ClusterVersionRecord().setLine(810).setOrdinal(requirement)));
    // wait for the raise to be applied so the gate sees the new active ECV
    RecordingExporter.records()
        .filter(r -> r.getValueType() == ValueType.CLUSTER_VERSION)
        .filter(r -> r.getIntent() == ClusterVersionIntent.APPLIED)
        .getFirst();

    final var gate = new ClusterVersionGate(engine.getProcessingState().getClusterVersionState());

    // when admission is checked
    final boolean admitted = gate.isAvailable(ValueType.CLUSTER_VERSION, ClusterVersionIntent.PING);

    // then PING is admitted — the gateway proceeds to write the command
    assertThat(admitted).isTrue();
    engine.writeRecords(
        RecordToWrite.command()
            .clusterVersion(
                ClusterVersionIntent.PING, new ClusterVersionRecord().setGatedField("hello")));

    // and the engine processes PING, producing a PINGED event on the log
    final var pinged =
        RecordingExporter.records()
            .filter(r -> r.getValueType() == ValueType.CLUSTER_VERSION)
            .filter(r -> r.getIntent() == ClusterVersionIntent.PINGED)
            .getFirst();
    assertThat(pinged.getRecordType()).isEqualTo(RecordType.EVENT);
    // PING processor branches on Capability.DEMO_GATED_BRANCH (gated at 810, 3). ECV is now
    // (810, 10), well past that gate, so the flag is active and the processor prefixes the
    // gated field with the v3 marker.
    assertThat(((ClusterVersionRecord) pinged.getValue()).getGatedField())
        .isEqualTo(PingProcessor.DEMO_BRANCH_MARKER + "hello");
  }

  @Test
  public void shouldRunPingProcessorWithoutMarker_whenDemoFlagIsOff() {
    // given the cluster has NOT reached the DEMO_GATED_BRANCH ordinal (810, 3). We write the PING
    // command directly to the log via writeRecords — bypassing the broker admission gate that
    // would normally reject this (PING admission requires 810/10) — so the PING reaches the
    // engine processor while the active ECV is still (0, 0).
    engine.writeRecords(
        RecordToWrite.command()
            .clusterVersion(
                ClusterVersionIntent.PING, new ClusterVersionRecord().setGatedField("hello")));

    final var pinged =
        RecordingExporter.records()
            .filter(r -> r.getValueType() == ValueType.CLUSTER_VERSION)
            .filter(r -> r.getIntent() == ClusterVersionIntent.PINGED)
            .getFirst();

    // Flag is off → processor took the legacy path → gated field unchanged.
    assertThat(((ClusterVersionRecord) pinged.getValue()).getGatedField()).isEqualTo("hello");
  }

  @Test
  public void shouldAcceptIdempotentRaiseToCurrentValue() {
    // given two raises to the SAME (810, 5) — the second is the "repeat" we want to be idempotent
    engine.writeRecords(
        RecordToWrite.command()
            .clusterVersion(
                ClusterVersionIntent.RAISE, new ClusterVersionRecord().setLine(810).setOrdinal(5)),
        RecordToWrite.command()
            .clusterVersion(
                ClusterVersionIntent.RAISE, new ClusterVersionRecord().setLine(810).setOrdinal(5)));

    // then both produce APPLIED — no rejection (the second is a no-op success, applier sets the
    // already-active value again). Retries and operator probes can repeat the command freely.
    final var appliedRecords =
        RecordingExporter.records()
            .filter(r -> r.getValueType() == ValueType.CLUSTER_VERSION)
            .filter(r -> r.getIntent() == ClusterVersionIntent.APPLIED)
            .limit(2)
            .toList();
    assertThat(appliedRecords).hasSize(2);
    assertThat(appliedRecords)
        .allSatisfy(
            r -> assertThat(((ClusterVersionRecord) r.getValue()).getOrdinal()).isEqualTo(5));
  }

  @Test
  public void shouldSuppressBehaviorFlag_thenUnsuppress() {
    final var flagName = Capability.DEMO_GATED_BRANCH.name();
    final var state = engine.getProcessingState().getClusterVersionState();

    // given the cluster has reached the DEMO_GATED_BRANCH ordinal (810, 3)
    engine.writeRecords(
        RecordToWrite.command()
            .clusterVersion(
                ClusterVersionIntent.RAISE, new ClusterVersionRecord().setLine(810).setOrdinal(3)));
    RecordingExporter.records()
        .filter(r -> r.getValueType() == ValueType.CLUSTER_VERSION)
        .filter(r -> r.getIntent() == ClusterVersionIntent.APPLIED)
        .filter(r -> ((ClusterVersionRecord) r.getValue()).getOrdinal() == 3)
        .getFirst();

    // when an operator suppresses the flag
    engine.writeRecords(
        RecordToWrite.command()
            .clusterVersion(
                ClusterVersionIntent.SUPPRESS_FLAG,
                new ClusterVersionRecord().setFlagName(flagName)));
    RecordingExporter.records()
        .filter(r -> r.getIntent() == ClusterVersionIntent.FLAG_SUPPRESSED)
        .getFirst();

    // then state reports the flag as suppressed and a PING run takes the legacy branch
    assertThat(state.isSuppressed(flagName)).isTrue();
    engine.writeRecords(
        RecordToWrite.command()
            .clusterVersion(
                ClusterVersionIntent.PING, new ClusterVersionRecord().setGatedField("hello")));
    final var pingedWhileSuppressed =
        RecordingExporter.records()
            .filter(r -> r.getIntent() == ClusterVersionIntent.PINGED)
            .getFirst();
    // flag was on per ECV but suppressed by operator → legacy branch, no marker
    assertThat(((ClusterVersionRecord) pingedWhileSuppressed.getValue()).getGatedField())
        .isEqualTo("hello");

    // when the operator unsuppresses
    engine.writeRecords(
        RecordToWrite.command()
            .clusterVersion(
                ClusterVersionIntent.UNSUPPRESS_FLAG,
                new ClusterVersionRecord().setFlagName(flagName)));
    RecordingExporter.records()
        .filter(r -> r.getIntent() == ClusterVersionIntent.FLAG_UNSUPPRESSED)
        .getFirst();

    // then state reports the flag as active again
    assertThat(state.isSuppressed(flagName)).isFalse();
  }

  @Test
  public void shouldExposeSnapshot_includingSuppressedFlags() {
    final var flagName = Capability.DEMO_GATED_BRANCH.name();

    // given a raise plus a suppressed flag
    engine.writeRecords(
        RecordToWrite.command()
            .clusterVersion(
                ClusterVersionIntent.RAISE, new ClusterVersionRecord().setLine(810).setOrdinal(3)));
    RecordingExporter.records()
        .filter(r -> r.getIntent() == ClusterVersionIntent.APPLIED)
        .filter(r -> ((ClusterVersionRecord) r.getValue()).getOrdinal() == 3)
        .getFirst();
    engine.writeRecords(
        RecordToWrite.command()
            .clusterVersion(
                ClusterVersionIntent.SUPPRESS_FLAG,
                new ClusterVersionRecord().setFlagName(flagName)));
    RecordingExporter.records()
        .filter(r -> r.getIntent() == ClusterVersionIntent.FLAG_SUPPRESSED)
        .getFirst();

    // when an operator reads the snapshot
    final var snapshot = engine.getProcessingState().getClusterVersionState().getSnapshot();

    // then it carries the active pair, the confirmed pair (== active in single-partition),
    // and the suppressed flag.
    assertThat(snapshot.activeLine()).isEqualTo(810);
    assertThat(snapshot.activeOrdinal()).isEqualTo(3);
    assertThat(snapshot.confirmedLine()).isEqualTo(810);
    assertThat(snapshot.confirmedOrdinal()).isEqualTo(3);
    assertThat(snapshot.suppressedFlags()).containsExactly(flagName);
  }
}
