/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.probe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordingProbeNotifier;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProbeIntent;
import io.camunda.zeebe.protocol.record.value.ProbeEndReason;
import io.camunda.zeebe.protocol.record.value.ProbeOutcome;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class ProbeTest {

  private static final RecordingProbeNotifier NOTIFIER = new RecordingProbeNotifier();

  @ClassRule
  public static final EngineRule ENGINE = EngineRule.singlePartition().withProbeNotifier(NOTIFIER);

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private String probeId;

  @Before
  public void setUp() {
    // a probe ID per test keeps the polls of one test from collecting the probes of another
    probeId = "validate-configuration/io.camunda:test-" + UUID.randomUUID() + ":1";
  }

  @Test
  public void shouldCreateAnActivatableProbeAndAnnounceIt() {
    // when
    final var created =
        ENGINE
            .probe()
            .withProbeId(probeId)
            .withInput("={region: \"eu-west-1\", nested: {flag: true}}")
            .create();

    // then
    assertThat(created.getIntent()).isEqualTo(ProbeIntent.CREATED);
    assertThat(created.getValue().getInput())
        .isEqualTo(Map.of("region", "eu-west-1", "nested", Map.of("flag", true)));
    assertThat(created.getValue().getRequestTimeout()).isEqualTo(15_000L);
    assertThat(created.getValue().getDeadline()).isPositive();
    await().untilAsserted(() -> assertThat(NOTIFIER.getNotifiedProbeIds()).contains(probeId));
  }

  @Test
  public void shouldHandTheProbeToAPollAndAnswerTheWorkerAndTheClient() {
    // given
    final long probeKey =
        ENGINE
            .probe()
            .withProbeId(probeId)
            .withInput("={region: \"eu-west-1\"}")
            .withRequest(11, 111L)
            .create()
            .getKey();

    // when
    final var activated = ENGINE.probe().activate(probeId, "worker-a", 4);

    // then
    assertThat(activated.getValue().getProbeKeys()).containsExactly(probeKey);
    assertThat(activated.getValue().getProbes())
        .singleElement()
        .satisfies(
            probe -> {
              assertThat(probe.getWorker()).isEqualTo("worker-a");
              assertThat(probe.getInput()).isEqualTo(Map.of("region", "eu-west-1"));
            });

    // when
    final var answered =
        ENGINE
            .probe()
            .withRequest(22, 222L)
            .answer(probeKey, ProbeOutcome.OK, Map.of("status", "SUCCESS"));

    // then
    assertThat(answered.getIntent()).isEqualTo(ProbeIntent.ANSWERED);
    assertThat(answered.getValue().getOutcome()).isEqualTo(ProbeOutcome.OK);
    assertThat(answered.getValue().getWorker()).isEqualTo("worker-a");
    assertThat(answered.getValue().getData()).isEmpty();
    final var respond =
        RecordingExporter.probeRecords(ProbeIntent.RESPOND)
            .withRecordKey(probeKey)
            .onlyCommands()
            .getFirst();
    assertThat(respond.getValue().getData()).isEqualTo(Map.of("status", "SUCCESS"));
    verify(ENGINE.getCommandResponseWriter(), timeout(5000)).tryWriteResponse(22, 222L);
    verify(ENGINE.getCommandResponseWriter(), timeout(5000)).tryWriteResponse(11, 111L);
  }

  @Test
  public void shouldCollectOnlyProbesOfThePolledProbeId() {
    // given
    ENGINE.probe().withProbeId(probeId).withInput("={}").create();

    // when
    final var activated = ENGINE.probe().activate(probeId + "-other", "worker-a", 4);

    // then
    assertThat(activated.getValue().getProbeKeys()).isEmpty();
  }

  @Test
  public void shouldCollectOnlyProbesOfThePolledTenants() {
    // given
    ENGINE.probe().withProbeId(probeId).withInput("={}").create();

    // when
    final var activated = ENGINE.probe().activate(probeId, "worker-a", 4, "other-tenant");

    // then
    assertThat(activated.getValue().getProbeKeys()).isEmpty();
  }

  @Test
  public void shouldCollectAtMostTheRequestedNumberOfProbes() {
    // given
    final long first = ENGINE.probe().withProbeId(probeId).withInput("={}").create().getKey();
    final long second = ENGINE.probe().withProbeId(probeId).withInput("={}").create().getKey();

    // when
    final var activated = ENGINE.probe().activate(probeId, "worker-a", 1);
    final var next = ENGINE.probe().activate(probeId, "worker-a", 1);

    // then
    assertThat(activated.getValue().getProbeKeys()).containsExactly(first);
    assertThat(activated.getValue().isTruncated()).isTrue();
    assertThat(next.getValue().getProbeKeys()).containsExactly(second);
  }

  @Test
  public void shouldMakeTheProbeActivatableAgainOnEveryYield() {
    // given
    final long probeKey = ENGINE.probe().withProbeId(probeId).withInput("={}").create().getKey();
    ENGINE.probe().activate(probeId, "worker-a", 1);

    // when - yielded twice: a probe has no yield limit, its deadline bounds it
    final var yielded = ENGINE.probe().yield(probeKey);
    final var collectedAgain = ENGINE.probe().activate(probeId, "worker-b", 1);
    final var yieldedAgain = ENGINE.probe().yield(probeKey);
    final var collectedOnceMore = ENGINE.probe().activate(probeId, "worker-c", 1);

    // then
    assertThat(yielded.getIntent()).isEqualTo(ProbeIntent.YIELDED);
    assertThat(collectedAgain.getValue().getProbeKeys()).containsExactly(probeKey);
    assertThat(yieldedAgain.getIntent()).isEqualTo(ProbeIntent.YIELDED);
    assertThat(collectedOnceMore.getValue().getProbes())
        .singleElement()
        .satisfies(probe -> assertThat(probe.getWorker()).isEqualTo("worker-c"));
  }

  @Test
  public void shouldUseTheDeadlineTheGatewaySent() {
    // given - the gateway fixed the deadline when the request arrived
    final long deadline = ENGINE.getClock().getCurrentTimeInMillis() + 5_000;

    // when
    final var created =
        ENGINE
            .probe()
            .withProbeId(probeId)
            .withInput("={}")
            .withRequestTimeout(5_000)
            .withDeadline(deadline)
            .create();

    // then - kept as is, not recomputed from the processing time
    assertThat(created.getValue().getDeadline()).isEqualTo(deadline);
  }

  @Test
  public void shouldEndAtOnceWithNoWorkerWhenProcessedAfterItsDeadline() {
    // given - CREATE queued past the deadline the gateway fixed
    final long deadline = ENGINE.getClock().getCurrentTimeInMillis() - 1;

    // when
    final var created =
        ENGINE
            .probe()
            .withProbeId(probeId)
            .withInput("={}")
            .withRequestTimeout(1_000)
            .withDeadline(deadline)
            .withRequest(55, 555L)
            .create();

    // then - ended by the CREATE itself, so the client is answered before the gateway gives up
    final var timedOut =
        RecordingExporter.probeRecords(ProbeIntent.TIMED_OUT)
            .withRecordKey(created.getKey())
            .getFirst();
    assertThat(timedOut.getSourceRecordPosition()).isEqualTo(created.getSourceRecordPosition());
    assertThat(timedOut.getValue().getEndReason()).isEqualTo(ProbeEndReason.NO_WORKER);
    verify(ENGINE.getCommandResponseWriter(), timeout(5000)).tryWriteResponse(55, 555L);
  }

  @Test
  public void shouldEndWithNoWorkerWhenNoPollCollectedTheProbe() {
    // given
    final var created =
        ENGINE
            .probe()
            .withProbeId(probeId)
            .withInput("={}")
            .withRequestTimeout(1000)
            .withRequest(33, 333L)
            .create();

    // when
    ENGINE.increaseTime(Duration.ofSeconds(2));

    // then
    final var timedOut =
        RecordingExporter.probeRecords(ProbeIntent.TIMED_OUT)
            .withRecordKey(created.getKey())
            .getFirst();
    assertThat(timedOut.getValue().getEndReason()).isEqualTo(ProbeEndReason.NO_WORKER);
    assertThat(timedOut.getValue().getMessage())
        .isEqualTo(
            "No worker for '%s' collected the probe within 1000 ms. Start a worker for this probe ID, then retry."
                .formatted(probeId));
    verify(ENGINE.getCommandResponseWriter(), timeout(5000)).tryWriteResponse(33, 333L);
  }

  @Test
  public void shouldEndWithWorkerDidNotAnswerWhenTheCollectingWorkerIsSilent() {
    // given
    final long probeKey =
        ENGINE
            .probe()
            .withProbeId(probeId)
            .withInput("={}")
            .withRequestTimeout(1000)
            .create()
            .getKey();
    ENGINE.probe().activate(probeId, "worker-a", 1);

    // when
    ENGINE.increaseTime(Duration.ofSeconds(2));

    // then
    final var timedOut =
        RecordingExporter.probeRecords(ProbeIntent.TIMED_OUT).withRecordKey(probeKey).getFirst();
    assertThat(timedOut.getValue().getEndReason()).isEqualTo(ProbeEndReason.WORKER_DID_NOT_ANSWER);
  }

  @Test
  public void shouldDetectSecretReferencesWithJsonPointers() {
    // when
    final var created =
        ENGINE
            .probe()
            .withProbeId(probeId)
            .withInput("={auth: {token: camunda.secrets.apiToken}, region: \"eu\"}")
            .create();

    // then
    assertThat(created.getValue().getInput())
        .isEqualTo(Map.of("auth", Map.of("token", "camunda.secrets.apiToken"), "region", "eu"));
    assertThat(created.getValue().getSecretReferences())
        .singleElement()
        .satisfies(
            reference -> {
              assertThat(reference.getSecretReference()).isEqualTo("apiToken");
              assertThat(reference.getPath()).isEqualTo("/auth/token");
            });
  }

  @Test
  public void shouldRejectInputThatIsNotAContext() {
    // when
    final var rejection =
        ENGINE.probe().withProbeId(probeId).withInput("=\"text\"").expectRejection().create();

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason("Expected the input to evaluate to a context, but was 'STRING'.");
  }

  @Test
  public void shouldRejectInputThatIsNotAnExpression() {
    // when
    final var rejection =
        ENGINE.probe().withProbeId(probeId).withInput("{}").expectRejection().create();

    // then
    Assertions.assertThat(rejection).hasRejectionType(RejectionType.INVALID_ARGUMENT);
  }

  @Test
  public void shouldRejectImpreciseSecretReference() {
    // when
    final var rejection =
        ENGINE
            .probe()
            .withProbeId(probeId)
            .withInput("={tokens: [camunda.secrets.apiToken]}")
            .expectRejection()
            .create();

    // then
    Assertions.assertThat(rejection).hasRejectionType(RejectionType.INVALID_ARGUMENT);
  }

  @Test
  public void shouldRejectRequestTimeoutAboveTheMaximum() {
    // when
    final var rejection =
        ENGINE
            .probe()
            .withProbeId(probeId)
            .withInput("={}")
            .withRequestTimeout(30_001)
            .expectRejection()
            .create();

    // then
    Assertions.assertThat(rejection).hasRejectionType(RejectionType.INVALID_ARGUMENT);
  }

  @Test
  public void shouldRejectAPollWithoutProbeId() {
    // when
    final var rejection = ENGINE.probe().expectRejection().activate("", "worker-a", 1);

    // then
    Assertions.assertThat(rejection).hasRejectionType(RejectionType.INVALID_ARGUMENT);
  }

  @Test
  public void shouldRejectAnAnswerBeforeTheProbeIsCollected() {
    // given
    final long probeKey = ENGINE.probe().withProbeId(probeId).withInput("={}").create().getKey();

    // when
    final var rejection =
        ENGINE.probe().expectRejection().answer(probeKey, ProbeOutcome.UNSUPPORTED, Map.of());

    // then
    Assertions.assertThat(rejection).hasRejectionType(RejectionType.INVALID_STATE);
  }

  @Test
  public void shouldRejectAnswerForEndedProbe() {
    // given
    final long probeKey = ENGINE.probe().withProbeId(probeId).withInput("={}").create().getKey();
    ENGINE.probe().activate(probeId, "worker-a", 1);
    ENGINE.probe().answer(probeKey, ProbeOutcome.UNSUPPORTED, Map.of());

    // when
    final var rejection =
        ENGINE.probe().expectRejection().answer(probeKey, ProbeOutcome.UNSUPPORTED, Map.of());

    // then
    Assertions.assertThat(rejection).hasRejectionType(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldRejectAnswerWithoutOutcome() {
    // given
    final long probeKey = ENGINE.probe().withProbeId(probeId).withInput("={}").create().getKey();
    ENGINE.probe().activate(probeId, "worker-a", 1);

    // when
    final var rejection =
        ENGINE.probe().expectRejection().answer(probeKey, ProbeOutcome.UNSPECIFIED, Map.of());

    // then
    Assertions.assertThat(rejection).hasRejectionType(RejectionType.INVALID_ARGUMENT);
  }
}
