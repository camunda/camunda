/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.probe;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.immutable.ProbeState.State;
import io.camunda.zeebe.engine.state.mutable.MutableProbeState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.probe.ProbeRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
final class ProbeStateTest {

  private MutableProcessingState processingState;
  private MutableProbeState probeState;

  @BeforeEach
  void setUp() {
    probeState = processingState.getProbeState();
  }

  @Test
  void shouldCreateAnActivatableProbe() {
    // when
    probeState.create(1L, probe("probe-a", 1_000));

    // then
    assertThat(probeState.getState(1L)).isEqualTo(State.ACTIVATABLE);
    assertThat(probeState.getProbe(1L))
        .isNotNull()
        .satisfies(
            probe -> {
              assertThat(probe.getProbeId()).isEqualTo("probe-a");
              assertThat(probe.getDeadline()).isEqualTo(1_000);
            });
  }

  @Test
  void shouldRemoveTheProbeAndItsDeadline() {
    // given
    probeState.create(1L, probe("probe-a", 1_000));

    // when
    probeState.remove(1L);

    // then
    assertThat(probeState.getState(1L)).isEqualTo(State.NOT_FOUND);
    assertThat(probeState.getProbe(1L)).isNull();
    assertThat(probeState.forEachTimedOutProbe(Long.MAX_VALUE, (key, probe) -> true)).isEqualTo(-1);
  }

  @Test
  void shouldVisitTimedOutProbesEarliestFirstAndReturnTheNextDeadline() {
    // given
    probeState.create(1L, probe("probe-a", 3_000));
    probeState.create(2L, probe("probe-b", 1_000));
    probeState.create(3L, probe("probe-c", 2_000));
    final List<Long> visited = new ArrayList<>();

    // when
    final long nextDeadline =
        probeState.forEachTimedOutProbe(
            2_000,
            (key, probe) -> {
              visited.add(key);
              return true;
            });

    // then
    assertThat(visited).containsExactly(2L, 3L);
    assertThat(nextDeadline).isEqualTo(3_000);
  }

  @Test
  void shouldVisitTheActivatableProbesOfTheProbeIdAndTenantsOldestFirst() {
    // given
    probeState.create(1L, probe("probe-a", 1_000).setTenantId("tenant-a"));
    probeState.create(2L, probe("probe-ab", 1_000).setTenantId("tenant-a"));
    probeState.create(3L, probe("probe-a", 1_000).setTenantId("tenant-b"));
    probeState.create(4L, probe("probe-a", 1_000).setTenantId("tenant-c"));
    final List<Long> visited = new ArrayList<>();

    // when
    probeState.forEachActivatableProbe(
        BufferUtil.wrapString("probe-a"),
        List.of("tenant-a", "tenant-b"),
        (key, probe) -> visited.add(key));

    // then
    assertThat(visited).containsExactly(1L, 3L);
  }

  @Test
  void shouldActivateTheProbeWithItsWorkerAndNoLongerVisitIt() {
    // given
    probeState.create(1L, probe("probe-a", 1_000));

    // when
    probeState.activate(1L, BufferUtil.wrapString("worker-a"));

    // then
    assertThat(probeState.getState(1L)).isEqualTo(State.ACTIVATED);
    assertThat(probeState.getProbe(1L)).extracting(ProbeRecord::getWorker).isEqualTo("worker-a");
    assertThat(activatableKeys("probe-a")).isEmpty();
  }

  @Test
  void shouldMakeAnActivatedProbeActivatableAgainWithoutWorker() {
    // given
    probeState.create(1L, probe("probe-a", 1_000));
    probeState.activate(1L, BufferUtil.wrapString("worker-a"));

    // when
    probeState.makeActivatable(1L);

    // then
    assertThat(probeState.getState(1L)).isEqualTo(State.ACTIVATABLE);
    assertThat(probeState.getProbe(1L)).extracting(ProbeRecord::getWorker).isEqualTo("");
    assertThat(activatableKeys("probe-a")).containsExactly(1L);
  }

  @Test
  void shouldRemoveAnActivatedProbe() {
    // given
    probeState.create(1L, probe("probe-a", 1_000));
    probeState.activate(1L, BufferUtil.wrapString("worker-a"));

    // when
    probeState.remove(1L);

    // then
    assertThat(probeState.getState(1L)).isEqualTo(State.NOT_FOUND);
    assertThat(probeState.forEachTimedOutProbe(Long.MAX_VALUE, (key, probe) -> true)).isEqualTo(-1);
  }

  @Test
  void shouldParkAProbeOnEachUncachedSecretAndNoLongerVisitIt() {
    // given
    probeState.create(1L, probe("probe-a", 1_000));

    // when
    probeState.park(1L, "default", "token");
    probeState.park(1L, "default", "key");

    // then
    assertThat(probeState.getState(1L)).isEqualTo(State.WAITING_FOR_SECRET_RESOLUTION);
    assertThat(activatableKeys("probe-a")).isEmpty();
    assertThat(parkedReferencesOf(1L)).containsExactlyInAnyOrder("default/token", "default/key");
    assertThat(probesParkedOn("default", "token")).containsExactly(1L);
  }

  @Test
  void shouldReleaseTheParkingWhenTheProbeIsActivatableAgain() {
    // given
    probeState.create(1L, probe("probe-a", 1_000));
    probeState.park(1L, "default", "token");

    // when
    probeState.makeActivatable(1L);

    // then
    assertThat(probeState.getState(1L)).isEqualTo(State.ACTIVATABLE);
    assertThat(activatableKeys("probe-a")).containsExactly(1L);
    assertThat(parkedReferencesOf(1L)).isEmpty();
    assertThat(probesParkedOn("default", "token")).isEmpty();
  }

  @Test
  void shouldReleaseTheParkingWhenTheProbeEnds() {
    // given - ANSWERED, TIMED_OUT and CANCELED all remove the probe
    probeState.create(1L, probe("probe-a", 1_000));
    probeState.create(2L, probe("probe-a", 1_000));
    probeState.park(1L, "default", "token");
    probeState.park(2L, "default", "token");

    // when
    probeState.remove(1L);

    // then
    assertThat(parkedReferencesOf(1L)).isEmpty();
    assertThat(probesParkedOn("default", "token")).containsExactly(2L);
  }

  @Test
  void shouldNotParkAProbeThatEnded() {
    // when
    probeState.park(1L, "default", "token");

    // then
    assertThat(probeState.getState(1L)).isEqualTo(State.NOT_FOUND);
    assertThat(probesParkedOn("default", "token")).isEmpty();
  }

  private List<String> parkedReferencesOf(final long probeKey) {
    final List<String> references = new ArrayList<>();
    probeState.forEachSecretReferenceParkedOn(
        probeKey, (storeId, reference) -> references.add(storeId + "/" + reference));
    return references;
  }

  private List<Long> probesParkedOn(final String storeId, final String secretReference) {
    final List<Long> keys = new ArrayList<>();
    probeState.forEachProbeParkedOn(storeId, secretReference, keys::add);
    return keys;
  }

  private List<Long> activatableKeys(final String probeId) {
    final List<Long> keys = new ArrayList<>();
    probeState.forEachActivatableProbe(
        BufferUtil.wrapString(probeId), List.of("<default>"), (key, probe) -> keys.add(key));
    return keys;
  }

  private static ProbeRecord probe(final String probeId, final long deadline) {
    return new ProbeRecord().setProbeId(probeId).setInputExpression("={}").setDeadline(deadline);
  }
}
