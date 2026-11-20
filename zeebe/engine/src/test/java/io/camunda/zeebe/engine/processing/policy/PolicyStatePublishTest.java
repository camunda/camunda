/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.policy;

import static io.camunda.zeebe.engine.processing.policy.PolicyUpdateApplyTest.apply;
import static io.camunda.zeebe.engine.processing.policy.PolicyUpdateApplyTest.uid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.zeebe.engine.processing.policy.PolicyStatus.UpdateStatus;
import io.camunda.zeebe.engine.state.immutable.PolicyState.CommittedPolicy;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyUpdateRecord;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.Rule;
import org.junit.Test;

/**
 * The engine publishes the policy state of partition 1, so the broker's Hub receiver knows when it
 * may send the next update without reading the engine's state. Each test gets its own engine, so
 * the published states start with those of the test.
 */
public final class PolicyStatePublishTest {

  @Rule public final EngineRule engine;

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private final RecordingPolicyStateNotifier notifier = new RecordingPolicyStateNotifier();

  public PolicyStatePublishTest() {
    engine = EngineRule.singlePartition().withPolicyStateNotifier(notifier);
  }

  @Test
  public void shouldPublishTheStateOnceRecovered() {
    // then
    await().untilAsserted(() -> assertThat(notifier.published).isNotEmpty());
    assertThat(notifier.published.getFirst())
        .isEqualTo(new PolicyStatus(null, UpdateStatus.NONE, null));
  }

  @Test
  public void shouldPublishAPendingUpdateOnceAChunkIsBuffered() {
    // given
    final var updateId = "update-" + uid();

    // when
    apply(engine, chunk(updateId, 0, 2));

    // then
    awaitLastPublished(new PolicyStatus(null, UpdateStatus.PENDING, updateId));
  }

  @Test
  public void shouldPublishTheCommittedPolicyOnceTheUpdateIsCommitted() {
    // given
    final var updateId = "update-" + uid();

    // when
    apply(engine, chunk(updateId, 0, 1));

    // then the update was applying before it was committed
    awaitLastPublished(
        new PolicyStatus(new CommittedPolicy("policy", 2, 7), UpdateStatus.NONE, null));
    assertThat(notifier.published)
        .contains(new PolicyStatus(null, UpdateStatus.APPLYING, updateId));
  }

  @Test
  public void shouldPublishNoOpenUpdateOnceTheUpdateFailed() {
    // given
    final var updateId = "update-" + uid();
    apply(engine, chunk(updateId, 0, 2));
    awaitLastPublished(new PolicyStatus(null, UpdateStatus.PENDING, updateId));

    // when a chunk is received again, which fails the update
    apply(engine, chunk(updateId, 0, 2));

    // then
    awaitLastPublished(new PolicyStatus(null, UpdateStatus.NONE, null));
  }

  @Test
  public void shouldPublishTheStateAgainAfterARestart() {
    // given
    final var updateId = "update-" + uid();
    apply(engine, chunk(updateId, 0, 2));
    awaitLastPublished(new PolicyStatus(null, UpdateStatus.PENDING, updateId));

    // when
    engine.stop();
    notifier.published.clear();
    engine.start();

    // then the new leader publishes the update it recovered, without a new command
    awaitLastPublished(new PolicyStatus(null, UpdateStatus.PENDING, updateId));
  }

  @Test
  public void shouldWithdrawTheStateWhenTheEngineStops() {
    // given
    await().untilAsserted(() -> assertThat(notifier.published).isNotEmpty());

    // when
    engine.stop();

    // then
    assertThat(notifier.cleared).isTrue();
  }

  private void awaitLastPublished(final PolicyStatus expected) {
    await()
        .untilAsserted(
            () -> assertThat(notifier.published).isNotEmpty().last().isEqualTo(expected));
  }

  private static PolicyUpdateRecord chunk(
      final String updateId, final int chunkIndex, final int chunkCount) {
    return new PolicyUpdateRecord()
        .setPolicyId("policy")
        .setPolicyVersion(2)
        .setBasePolicyVersion(1)
        .setSeqNo(7)
        .setUpdateId(updateId)
        .setChunkIndex(chunkIndex)
        .setChunkCount(chunkCount);
  }

  private static final class RecordingPolicyStateNotifier implements PolicyStateNotifier {

    private final List<PolicyStatus> published = new CopyOnWriteArrayList<>();
    private volatile boolean cleared;

    @Override
    public void publish(final PolicyStatus status) {
      cleared = false;
      published.add(status);
    }

    @Override
    public void clear() {
      cleared = true;
    }
  }
}
