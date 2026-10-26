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
import io.camunda.zeebe.engine.util.RecordingProbeStreamer;
import io.camunda.zeebe.engine.util.RecordingProbeStreamer.RecordingProbeStream;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProbeIntent;
import io.camunda.zeebe.protocol.record.value.ProbeEndReason;
import io.camunda.zeebe.protocol.record.value.ProbeOutcome;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class ProbeTest {

  private static final RecordingProbeStreamer STREAMER = new RecordingProbeStreamer();

  @ClassRule
  public static final EngineRule ENGINE = EngineRule.singlePartition().withProbeStreamer(STREAMER);

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private String template;

  @Before
  public void setUp() {
    // a template per test keeps the streams of one test from answering the probes of another
    template = "io.camunda:test-" + UUID.randomUUID() + ":1";
  }

  @Test
  public void shouldPushProbeAndAnswerWorkerAndClient() {
    // given
    final var stream = addStream("worker-a");

    // when
    final var created =
        ENGINE
            .probe()
            .withTemplate(template)
            .withInput("={region: \"eu-west-1\", nested: {flag: true}}")
            .withRequest(11, 111L)
            .create();
    final long probeKey = created.getKey();

    // then
    assertThat(created.getIntent()).isEqualTo(ProbeIntent.CREATED);
    assertThat(created.getValue().getInput())
        .isEqualTo(Map.of("region", "eu-west-1", "nested", Map.of("flag", true)));
    assertThat(created.getValue().getDeadline()).isPositive();

    final var activated =
        RecordingExporter.probeRecords(ProbeIntent.ACTIVATED).withRecordKey(probeKey).getFirst();
    assertThat(activated.getValue().getWorker()).isEqualTo("worker-a");
    assertThat(activated.getValue().getInput()).isEmpty();

    await().untilAsserted(() -> assertThat(stream.getPushedProbes()).hasSize(1));
    final var pushed = stream.getPushedProbes().getFirst();
    assertThat(pushed.probeKey()).isEqualTo(probeKey);
    assertThat(pushed.record().getInput()).containsEntry("region", "eu-west-1");

    // when
    final var answered =
        ENGINE
            .probe()
            .withRequest(22, 222L)
            .answer(probeKey, ProbeOutcome.OK, Map.of("status", "SUCCESS"));

    // then
    assertThat(answered.getIntent()).isEqualTo(ProbeIntent.ANSWERED);
    assertThat(answered.getValue().getOutcome()).isEqualTo(ProbeOutcome.OK);
    assertThat(answered.getValue().getData()).isEqualTo(Map.of("status", "SUCCESS"));
    assertThat(answered.getValue().getWorker()).isEqualTo("worker-a");
    assertThat(
            RecordingExporter.probeRecords(ProbeIntent.RESPOND)
                .withRecordKey(probeKey)
                .onlyCommands()
                .exists())
        .isTrue();
    verify(ENGINE.getCommandResponseWriter(), timeout(5000)).tryWriteResponse(22, 222L);
    verify(ENGINE.getCommandResponseWriter(), timeout(5000)).tryWriteResponse(11, 111L);
  }

  @Test
  public void shouldDetectSecretReferencesWithJsonPointers() {
    // given
    addStream("worker-a");

    // when
    final var created =
        ENGINE
            .probe()
            .withTemplate(template)
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
  public void shouldRejectCreateWhenNoWorkerSubscribed() {
    // when
    final var rejection =
        ENGINE.probe().withTemplate(template).withInput("={}").expectRejection().create();

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "No worker can answer VALIDATE_CONFIGURATION for template %s. Start a connector runtime that supports it, then retry."
                .formatted(template));
  }

  @Test
  public void shouldRejectCreateWhenWorkerServesAnotherTenant() {
    // given
    STREAMER.addStream("VALIDATE_CONFIGURATION:" + template, "worker-a", List.of("other"));

    // when
    final var rejection =
        ENGINE.probe().withTemplate(template).withInput("={}").expectRejection().create();

    // then
    Assertions.assertThat(rejection).hasRejectionType(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldRejectInputThatIsNotAContext() {
    // given
    addStream("worker-a");

    // when
    final var rejection =
        ENGINE.probe().withTemplate(template).withInput("=\"text\"").expectRejection().create();

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason("Expected the input to evaluate to a context, but was 'STRING'.");
  }

  @Test
  public void shouldRejectInputThatIsNotAnExpression() {
    // given
    addStream("worker-a");

    // when
    final var rejection =
        ENGINE.probe().withTemplate(template).withInput("{}").expectRejection().create();

    // then
    Assertions.assertThat(rejection).hasRejectionType(RejectionType.INVALID_ARGUMENT);
  }

  @Test
  public void shouldRejectImpreciseSecretReference() {
    // given
    addStream("worker-a");

    // when
    final var rejection =
        ENGINE
            .probe()
            .withTemplate(template)
            .withInput("={tokens: [camunda.secrets.apiToken]}")
            .expectRejection()
            .create();

    // then
    Assertions.assertThat(rejection).hasRejectionType(RejectionType.INVALID_ARGUMENT);
  }

  @Test
  public void shouldTimeOutWhenWorkerDoesNotAnswer() {
    // given
    addStream("worker-a");
    final var created =
        ENGINE
            .probe()
            .withTemplate(template)
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
    assertThat(timedOut.getValue().getEndReason()).isEqualTo(ProbeEndReason.WORKER_DID_NOT_ANSWER);
    verify(ENGINE.getCommandResponseWriter(), timeout(5000)).tryWriteResponse(33, 333L);
  }

  @Test
  public void shouldRedispatchOnceOnYieldThenCancel() {
    // given
    final var first = addStream("worker-a");
    final var second = addStream("worker-b");
    final long probeKey = ENGINE.probe().withTemplate(template).withInput("={}").create().getKey();
    await().untilAsserted(() -> assertThat(first.getPushedProbes()).hasSize(1));

    // when
    final var yielded = ENGINE.probe().yield(probeKey);

    // then
    assertThat(yielded.getIntent()).isEqualTo(ProbeIntent.YIELDED);
    await().untilAsserted(() -> assertThat(second.getPushedProbes()).hasSize(1));
    assertThat(
            RecordingExporter.probeRecords(ProbeIntent.ACTIVATED)
                .withRecordKey(probeKey)
                .limit(2)
                .map(record -> record.getValue().getWorker()))
        .containsExactly("worker-a", "worker-b");

    // when
    final var canceled = ENGINE.probe().yield(probeKey);

    // then
    assertThat(canceled.getIntent()).isEqualTo(ProbeIntent.CANCELED);
    assertThat(canceled.getValue().getEndReason()).isEqualTo(ProbeEndReason.NO_WORKER);
  }

  @Test
  public void shouldCancelOnYieldWhenNoOtherWorker() {
    // given
    addStream("worker-a");
    final long probeKey = ENGINE.probe().withTemplate(template).withInput("={}").create().getKey();

    // when
    final var canceled = ENGINE.probe().yield(probeKey);

    // then
    assertThat(canceled.getIntent()).isEqualTo(ProbeIntent.CANCELED);
    assertThat(canceled.getValue().getEndReason()).isEqualTo(ProbeEndReason.NO_WORKER);
  }

  @Test
  public void shouldRejectAnswerForEndedProbe() {
    // given
    addStream("worker-a");
    final long probeKey = ENGINE.probe().withTemplate(template).withInput("={}").create().getKey();
    ENGINE.probe().answer(probeKey, ProbeOutcome.UNSUPPORTED, Map.of());

    // when
    final var rejection =
        ENGINE.probe().expectRejection().answer(probeKey, ProbeOutcome.UNSUPPORTED, Map.of());

    // then
    Assertions.assertThat(rejection).hasRejectionType(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldRejectAnswerWhoseDataIsNotAValidationResult() {
    // given
    addStream("worker-a");
    final long probeKey = ENGINE.probe().withTemplate(template).withInput("={}").create().getKey();

    // when
    final var rejection =
        ENGINE
            .probe()
            .expectRejection()
            .answer(probeKey, ProbeOutcome.OK, Map.of("status", "SUCCESS", "extra", 1));

    // then
    Assertions.assertThat(rejection).hasRejectionType(RejectionType.INVALID_ARGUMENT);
  }

  @Test
  public void shouldRejectRequestTimeoutAboveTheMaximum() {
    // given
    addStream("worker-a");

    // when
    final var rejection =
        ENGINE
            .probe()
            .withTemplate(template)
            .withInput("={}")
            .withRequestTimeout(30_001)
            .expectRejection()
            .create();

    // then
    Assertions.assertThat(rejection).hasRejectionType(RejectionType.INVALID_ARGUMENT);
  }

  private RecordingProbeStream addStream(final String worker) {
    return STREAMER.addStream(
        "VALIDATE_CONFIGURATION:" + template,
        worker,
        List.of(TenantOwned.DEFAULT_TENANT_IDENTIFIER));
  }
}
