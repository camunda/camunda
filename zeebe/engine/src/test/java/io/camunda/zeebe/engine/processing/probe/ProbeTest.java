/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.probe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordingProbeNotifier;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProbeIntent;
import io.camunda.zeebe.protocol.record.value.ClusterVariableKind;
import io.camunda.zeebe.protocol.record.value.JobRecordValue.JobSecretReferenceValue;
import io.camunda.zeebe.protocol.record.value.ProbeEndReason;
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
    probeId = "validate-configuration/io.camunda:test-" + UUID.randomUUID() + ":1";
  }

  @Test
  public void shouldCreateAProbeAndAnnounceIt() {
    // given
    final long deadline = ENGINE.getClock().getCurrentTimeInMillis() + 5_000;

    // when
    final var created =
        ENGINE
            .probe()
            .withProbeId(probeId)
            .withInput("={region: \"eu-west-1\", nested: {flag: true}}")
            .withRequestTimeout(5_000)
            .withDeadline(deadline)
            .create();

    // then
    assertThat(created.getIntent()).isEqualTo(ProbeIntent.CREATED);
    assertThat(created.getValue().getProbeId()).isEqualTo(probeId);
    assertThat(created.getValue().getInput())
        .isEqualTo(Map.of("region", "eu-west-1", "nested", Map.of("flag", true)));
    assertThat(created.getValue().getSecretReferences()).isEmpty();
    assertThat(created.getValue().getDeadline())
        .describedAs("the deadline the gateway sent, not one computed by the engine")
        .isEqualTo(deadline);
    await().untilAsserted(() -> assertThat(NOTIFIER.getNotifiedProbeIds()).contains(probeId));
  }

  @Test
  public void shouldDetectSecretReferencesWithJsonPointers() {
    // when
    final var created =
        ENGINE
            .probe()
            .withProbeId(probeId)
            .withInput(
                "={auth: {token: camunda.secrets.apiToken}, `a/b`: camunda.secrets.other,"
                    + " region: \"eu\"}")
            .create();

    // then
    assertThat(created.getValue().getInput())
        .describedAs("a secret evaluates to its placeholder, never to its value")
        .isEqualTo(
            Map.of(
                "auth",
                Map.of("token", "camunda.secrets.apiToken"),
                "a/b",
                "camunda.secrets.other",
                "region",
                "eu"));
    assertThat(created.getValue().getSecretReferences())
        .extracting(JobSecretReferenceValue::getSecretReference, JobSecretReferenceValue::getPath)
        .containsExactlyInAnyOrder(tuple("apiToken", "/auth/token"), tuple("other", "/a~1b"));
  }

  @Test
  public void shouldDetectSecretsReachedThroughASecretReferenceClusterVariable() {
    // given
    final var name = "creds_" + UUID.randomUUID().toString().replace("-", "");
    ENGINE
        .clusterVariables()
        .withName(name)
        .setGlobalScope()
        .withKind(ClusterVariableKind.SECRET_REFERENCE)
        .withValue(Map.of("token", "camunda.secrets.cvToken"))
        .create();

    // when
    final var created =
        ENGINE
            .probe()
            .withProbeId(probeId)
            .withInput("={auth: camunda.vars.cluster.%s}".formatted(name))
            .create();

    // then
    assertThat(created.getValue().getInput())
        .isEqualTo(Map.of("auth", Map.of("token", "camunda.secrets.cvToken")));
    assertThat(created.getValue().getSecretReferences())
        .extracting(JobSecretReferenceValue::getSecretReference, JobSecretReferenceValue::getPath)
        .containsExactly(tuple("cvToken", "/auth/token"));
  }

  @Test
  public void shouldNotDetectASecretSpelledInAJsonClusterVariableOrAStringLiteral() {
    // given
    final var name = "json_" + UUID.randomUUID().toString().replace("-", "");
    ENGINE
        .clusterVariables()
        .withName(name)
        .setGlobalScope()
        .withKind(ClusterVariableKind.JSON)
        .withValue(Map.of("token", "camunda.secrets.notASecret"))
        .create();

    // when
    final var created =
        ENGINE
            .probe()
            .withProbeId(probeId)
            .withInput(
                "={auth: camunda.vars.cluster.%s, text: \"camunda.secrets.literal\"}"
                    .formatted(name))
            .create();

    // then
    assertThat(created.getValue().getSecretReferences()).isEmpty();
  }

  @Test
  public void shouldEndWithNoWorkerAtTheDeadlineAndAnswerTheClient() {
    // given
    final var created =
        ENGINE
            .probe()
            .withProbeId(probeId)
            .withInput("={token: camunda.secrets.apiToken}")
            .withRequestTimeout(1_000)
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
            "No worker for '%s' collected the probe within 1000 ms. Check that a worker polls this"
                    .formatted(probeId)
                + " probe ID and holds PROBE:ANSWER on it, then retry.");
    assertThat(timedOut.getValue().getProbeId()).isEqualTo(probeId);
    assertThat(timedOut.getValue().getInput()).isEmpty();
    assertThat(timedOut.getValue().getSecretReferences()).isEmpty();
    verify(ENGINE.getCommandResponseWriter(), timeout(5000)).tryWriteResponse(33, 333L);
  }

  @Test
  public void shouldEndAtOnceWhenProcessedAfterItsDeadline() {
    // given the CREATE queued past the deadline the gateway set
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

    // then
    final var timedOut =
        RecordingExporter.probeRecords(ProbeIntent.TIMED_OUT)
            .withRecordKey(created.getKey())
            .getFirst();
    assertThat(timedOut.getSourceRecordPosition())
        .describedAs("ended by the CREATE itself")
        .isEqualTo(created.getSourceRecordPosition());
    assertThat(timedOut.getValue().getEndReason()).isEqualTo(ProbeEndReason.NO_WORKER);
    verify(ENGINE.getCommandResponseWriter(), timeout(5000)).tryWriteResponse(55, 555L);
  }

  @Test
  public void shouldRejectAnEmptyProbeId() {
    assertRejectedProbeId("");
  }

  @Test
  public void shouldRejectTheWildcardAsProbeId() {
    assertRejectedProbeId("*");
  }

  @Test
  public void shouldRejectAProbeIdLongerThan256Characters() {
    assertRejectedProbeId("p".repeat(257));
  }

  @Test
  public void shouldRejectAProbeIdWithWhitespace() {
    assertRejectedProbeId("validate configuration");
  }

  @Test
  public void shouldAcceptAProbeIdOf256Characters() {
    // when
    final var created = ENGINE.probe().withProbeId("p".repeat(256)).withInput("={}").create();

    // then
    assertThat(created.getIntent()).isEqualTo(ProbeIntent.CREATED);
  }

  @Test
  public void shouldRejectABlankInput() {
    // when
    final var rejection =
        ENGINE.probe().withProbeId(probeId).withInput(" ").expectRejection().create();

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason("Expected an input, but got a blank one.");
  }

  @Test
  public void shouldRejectANegativeRequestTimeout() {
    assertRejectedRequestTimeout(-1);
  }

  @Test
  public void shouldRejectARequestTimeoutAboveTheMaximum() {
    assertRejectedRequestTimeout(30_001);
  }

  @Test
  public void shouldRejectACommandWithoutDeadline() {
    // when
    final var rejection =
        ENGINE
            .probe()
            .withProbeId(probeId)
            .withInput("={}")
            .withDeadline(-1)
            .expectRejection()
            .create();

    // then
    Assertions.assertThat(rejection).hasRejectionType(RejectionType.INVALID_ARGUMENT);
  }

  @Test
  public void shouldRejectAnInputThatIsNotValidFeel() {
    // when
    final var rejection =
        ENGINE.probe().withProbeId(probeId).withInput("={region: ").expectRejection().create();

    // then
    Assertions.assertThat(rejection).hasRejectionType(RejectionType.INVALID_ARGUMENT);
    assertThat(rejection.getRejectionReason())
        .startsWith("Expected the input to be a valid FEEL expression, but parsing failed");
  }

  @Test
  public void shouldRejectAnInputThatIsNotAnExpression() {
    // when
    final var rejection =
        ENGINE.probe().withProbeId(probeId).withInput("{}").expectRejection().create();

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            "Expected the input to be a FEEL expression starting with '=', but got '{}'.");
  }

  @Test
  public void shouldRejectASecretReferenceInAList() {
    // when
    final var rejection =
        ENGINE
            .probe()
            .withProbeId(probeId)
            .withInput("={tokens: [camunda.secrets.apiToken]}")
            .expectRejection()
            .create();

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            "Expected each secret reference in the input to be the whole value of a context entry,"
                + " but one is inside a list or a context produced by a branch.");
  }

  @Test
  public void shouldRejectAnInputThatIsNotAContext() {
    // when
    final var rejection =
        ENGINE.probe().withProbeId(probeId).withInput("=\"text\"").expectRejection().create();

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason("Expected the input to evaluate to a context, but got 'STRING'.");
  }

  @Test
  public void shouldRejectAnInputWhoseEvaluationFails() {
    // when
    final var rejection =
        ENGINE
            .probe()
            .withProbeId(probeId)
            .withInput("={value: assert(1, 1 > 2)}")
            .expectRejection()
            .create();

    // then
    Assertions.assertThat(rejection).hasRejectionType(RejectionType.INVALID_ARGUMENT);
    assertThat(rejection.getRejectionReason())
        .startsWith("Expected the input to evaluate to a context, but the evaluation failed");
  }

  private void assertRejectedProbeId(final String invalidProbeId) {
    // when
    final var rejection =
        ENGINE.probe().withProbeId(invalidProbeId).withInput("={}").expectRejection().create();

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            "Expected a probe ID of 1 to 256 characters, without whitespace and other than '*',"
                + " but got '%s'.".formatted(invalidProbeId));
  }

  private void assertRejectedRequestTimeout(final long requestTimeout) {
    // when
    final var rejection =
        ENGINE
            .probe()
            .withProbeId(probeId)
            .withInput("={}")
            .withRequestTimeout(requestTimeout)
            .expectRejection()
            .create();

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            "Expected a request timeout from 0 to 30000 ms, but got %d ms."
                .formatted(requestTimeout));
  }
}
