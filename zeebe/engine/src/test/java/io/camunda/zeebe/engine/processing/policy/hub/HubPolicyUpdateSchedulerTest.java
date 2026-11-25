/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.policy.hub;

import static io.camunda.zeebe.engine.processing.policy.PolicyUpdateApplyTest.apply;
import static io.camunda.zeebe.engine.processing.policy.PolicyUpdateApplyTest.uid;
import static io.camunda.zeebe.engine.processing.policy.hub.PolicyChangeSets.delta;
import static io.camunda.zeebe.engine.processing.policy.hub.PolicyChangeSets.noUpserts;
import static io.camunda.zeebe.engine.processing.policy.hub.PolicyChangeSets.tenants;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.fanout.protocol.model.ApplyReportV1;
import io.camunda.fanout.protocol.model.ApplyResult;
import io.camunda.fanout.protocol.model.PolicyChangeSetV1;
import io.camunda.fanout.protocol.model.PolicyTenant;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyUpdateRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.PolicyUpdateIntent;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import io.camunda.zeebe.protocol.record.value.PolicyUpdateRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import org.awaitility.Awaitility;
import org.junit.Rule;
import org.junit.Test;

public final class HubPolicyUpdateSchedulerTest {

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private final StubFanoutClient.Factory clients = new StubFanoutClient.Factory();

  @Rule
  public final EngineRule engine = EngineRule.singlePartition().withFanoutClientFactory(clients);

  @Test
  public void shouldStartTheClientFromTheCommittedSequence() {
    // given
    final var client = awaitClient(0);

    // then
    assertThat(client.startState()).isNotNull();
    assertThat(client.startState().lastSequence()).isZero();
    assertThat(client.startState().lastReport()).isNull();
  }

  @Test
  public void shouldReportAnUpdateAsAppliedOnceItsVersionIsCommitted() {
    // given
    final var client = awaitClient(0);
    final var tenantId = "tenant-" + uid();
    final var changeSet =
        delta("policy-" + uid(), 1, tenants(new PolicyTenant(tenantId, "Tenant", null)));

    // when
    final var report = client.send(changeSet, 1L);

    // then the update is applied and committed, and reported after that
    final var committed = committed(changeSet, 1L);
    assertThat(
            RecordingExporter.tenantRecords(TenantIntent.CREATED)
                .withTenantId(tenantId)
                .getFirst()
                .getPosition())
        .isLessThan(committed.getPosition());
    assertThat(awaitReport(report))
        .isEqualTo(
            new ApplyReportV1(
                1L,
                0L,
                1L,
                ApplyResult.APPLIED,
                null,
                io.camunda.fanout.protocol.model.LifecycleState.MANAGED,
                io.camunda.zeebe.util.VersionUtil.getVersion()));
  }

  @Test
  public void shouldReportAnUpdateThatIsReceivedAgainWithoutApplyingItAgain() {
    // given an update that is applied and reported
    final var client = awaitClient(0);
    final var changeSet = delta("policy-" + uid(), 1, noUpserts());
    assertThat(awaitReport(client.send(changeSet, 1L)).result()).isEqualTo(ApplyResult.APPLIED);

    // when Hub sends it again
    final var again = client.send(changeSet, 1L);

    // then it is reported as applied again; a marker update sent after it shows that none of its
    // chunks was written a second time
    assertThat(awaitReport(again).result()).isEqualTo(ApplyResult.APPLIED);
    final var marker = delta(changeSet.policyId(), 2, noUpserts());
    assertThat(awaitReport(client.send(marker, 2L)).result()).isEqualTo(ApplyResult.APPLIED);
    final var updateId = PolicyChangeSetMapper.updateId(changeSet, 1L);
    final var markerId = PolicyChangeSetMapper.updateId(marker, 2L);
    assertThat(
            RecordingExporter.policyUpdateRecords()
                .limit(
                    record ->
                        record.getIntent() == PolicyUpdateIntent.VERSION_COMMITTED
                            && record.getValue().getUpdateId().equals(markerId))
                .filter(
                    record ->
                        record.getIntent() == PolicyUpdateIntent.APPEND_CHUNK
                            && record.getValue().getUpdateId().equals(updateId)))
        .hasSize(1);
  }

  @Test
  public void shouldSendTheUpdateOnlyOnceAnotherOpenUpdateEnded() {
    // given another update that is open while Hub sends the next one
    final var client = awaitClient(0);
    final var openUpdateId = "update-" + uid();
    apply(engine, chunk(openUpdateId, 1, 0, 2));
    final var changeSet = delta("policy-" + uid(), 2, noUpserts());
    final var report = client.send(changeSet, 2L);
    passCycles(3);

    // when
    apply(engine, chunk(openUpdateId, 1, 1, 2));

    // then
    final var openCommitted =
        RecordingExporter.policyUpdateRecords(PolicyUpdateIntent.VERSION_COMMITTED)
            .filter(record -> record.getValue().getUpdateId().equals(openUpdateId))
            .getFirst();
    final var nextAppended =
        RecordingExporter.policyUpdateRecords(PolicyUpdateIntent.CHUNK_APPENDED)
            .filter(
                record ->
                    record
                        .getValue()
                        .getUpdateId()
                        .equals(PolicyChangeSetMapper.updateId(changeSet, 2L)))
            .getFirst();
    assertThat(nextAppended.getPosition()).isGreaterThan(openCommitted.getPosition());
    assertThat(awaitReport(report).result()).isEqualTo(ApplyResult.APPLIED);
  }

  @Test
  public void shouldReportAnUpdateThatTheEngineFailsAsRejected() {
    // given an earlier copy of the update, of two chunks, which is still open
    final var client = awaitClient(0);
    final var changeSet = delta("policy-" + uid(), 1, noUpserts());
    final var updateId = PolicyChangeSetMapper.updateId(changeSet, 1L);
    apply(engine, chunk(updateId, 1, 0, 2));
    final var report = client.send(changeSet, 1L);
    passCycles(3);

    // when its second chunk has another policy version, which fails the update
    apply(engine, chunk(updateId, 1, 1, 2).setPolicyVersion(3));

    // then
    final var failed =
        RecordingExporter.policyUpdateRecords(PolicyUpdateIntent.FAILED)
            .filter(record -> record.getValue().getUpdateId().equals(updateId))
            .getFirst();
    final var rejected = awaitReport(report);
    assertThat(rejected.result()).isEqualTo(ApplyResult.REJECTED);
    assertThat(rejected.reason()).isEqualTo(failed.getValue().getErrorMessage());
  }

  @Test
  public void shouldCloseTheClientAndGiveTheUpdateUpWhenProcessingPauses() {
    // given an update that waits for another open update
    final var client = awaitClient(0);
    apply(engine, chunk("update-" + uid(), 1, 0, 2));
    final var report = client.send(delta("policy-" + uid(), 2, noUpserts()), 2L);
    passCycles(2);

    // when
    engine.pauseProcessing(Protocol.DEPLOYMENT_PARTITION);

    // then the client sends the event again to the next client
    assertThat(client.isClosed()).isTrue();
    assertThat(report).failsWithin(java.time.Duration.ofSeconds(5));
  }

  @Test
  public void shouldStartANewClientWhenProcessingResumes() {
    // given an update that is applied
    final var client = awaitClient(0);
    assertThat(awaitReport(client.send(delta("policy-" + uid(), 1, noUpserts()), 7L)).result())
        .isEqualTo(ApplyResult.APPLIED);
    engine.pauseProcessing(Protocol.DEPLOYMENT_PARTITION);

    // when
    engine.resumeProcessing(Protocol.DEPLOYMENT_PARTITION);

    // then the new client starts after the committed update
    final var next = awaitClient(1);
    assertThat(next.startState().lastSequence()).isEqualTo(7L);
  }

  private StubFanoutClient awaitClient(final int index) {
    Awaitility.await()
        .untilAsserted(
            () -> {
              engine.increaseTime(HubPolicyUpdateScheduler.WAIT_INTERVAL);
              assertThat(clients.clients).hasSizeGreaterThan(index);
              assertThat(clients.clients.get(index).startState()).isNotNull();
            });
    return clients.clients.get(index);
  }

  /** Moves the clock until the scheduler reported the update. */
  private ApplyReportV1 awaitReport(final CompletableFuture<Optional<ApplyReportV1>> report) {
    Awaitility.await()
        .untilAsserted(
            () -> {
              engine.increaseTime(HubPolicyUpdateScheduler.WAIT_INTERVAL);
              assertThat(report).isDone();
            });
    return report.join().orElseThrow();
  }

  private Record<PolicyUpdateRecordValue> committed(
      final PolicyChangeSetV1 changeSet, final long seqNo) {
    final var updateId = PolicyChangeSetMapper.updateId(changeSet, seqNo);
    return RecordingExporter.policyUpdateRecords(PolicyUpdateIntent.VERSION_COMMITTED)
        .filter(record -> record.getValue().getUpdateId().equals(updateId))
        .getFirst();
  }

  private void passCycles(final int cycles) {
    IntStream.range(0, cycles)
        .forEach(cycle -> engine.increaseTime(HubPolicyUpdateScheduler.WAIT_INTERVAL));
  }

  private static PolicyUpdateRecord chunk(
      final String updateId, final long seqNo, final int chunkIndex, final int chunkCount) {
    return new PolicyUpdateRecord()
        .setPolicyId("policy-" + updateId)
        .setPolicyVersion(seqNo)
        .setBasePolicyVersion(seqNo - 1)
        .setSeqNo(seqNo)
        .setUpdateId(updateId)
        .setChunkIndex(chunkIndex)
        .setChunkCount(chunkCount);
  }
}
