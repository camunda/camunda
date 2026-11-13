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

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordingProbeNotifier;
import io.camunda.zeebe.engine.util.SecretStoreRegistries;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProbeIntent;
import io.camunda.zeebe.protocol.record.value.JobRecordValue.JobSecretReferenceValue;
import io.camunda.zeebe.protocol.record.value.ProbeRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class ProbeBatchActivateTest {

  private static final RecordingProbeNotifier NOTIFIER = new RecordingProbeNotifier();

  @ClassRule
  public static final EngineRule ENGINE =
      EngineRule.singlePartition()
          .withProbeNotifier(NOTIFIER)
          // every secret cached: the parking on an uncached secret is covered by ProbeSecretTest
          .withSecretStoreRegistry(SecretStoreRegistries.resolveAll("s3cr3t"));

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private String probeId;
  private String otherProbeId;

  @Before
  public void setUp() {
    probeId = "validate-configuration/io.camunda:test-" + UUID.randomUUID() + ":1";
    otherProbeId = "validate-configuration/io.camunda:other-" + UUID.randomUUID() + ":1";
  }

  @Test
  public void shouldActivateTheProbesOfSeveralProbeIdsInOnePoll() {
    // given
    final long probeKey = createProbe(probeId, "={token: camunda.secrets.apiToken}");
    final long otherProbeKey = createProbe(otherProbeId, "={region: \"eu\"}");

    // when
    final var activated =
        ENGINE.probe().activate(List.of(probeId, otherProbeId), "connectors-runtime", 10);

    // then
    assertThat(activated.getValue().getWorker()).isEqualTo("connectors-runtime");
    assertThat(activated.getValue().getProbeKeys()).containsExactly(probeKey, otherProbeKey);
    assertThat(activated.getValue().getProbes())
        .extracting(ProbeRecordValue::getProbeId, ProbeRecordValue::getWorker)
        .containsExactly(
            tuple(probeId, "connectors-runtime"), tuple(otherProbeId, "connectors-runtime"));
    assertThat(activated.getValue().getProbes().getFirst().getInput())
        .describedAs("the event keeps the placeholder, never a secret value")
        .isEqualTo(Map.of("token", "camunda.secrets.apiToken"));
    assertThat(activated.getValue().getProbes().getFirst().getSecretReferences())
        .describedAs("the JSON pointers of the secret leaves, for the response's secretPointers")
        .extracting(JobSecretReferenceValue::getPath)
        .containsExactly("/token");
  }

  @Test
  public void shouldCountTheMaximumAcrossAllProbeIds() {
    // given
    final long first = createProbe(probeId, "={}");
    final long second = createProbe(probeId, "={}");
    createProbe(otherProbeId, "={}");

    // when
    final var activated = ENGINE.probe().activate(List.of(probeId, otherProbeId), "worker", 2);

    // then
    assertThat(activated.getValue().getProbeKeys()).containsExactly(first, second);
    assertThat(activated.getValue().isTruncated()).isFalse();
  }

  @Test
  public void shouldNotActivateAnActivatedProbeAgain() {
    // given
    final long probeKey = createProbe(probeId, "={}");
    ENGINE.probe().activate(List.of(probeId), "worker-a", 10);

    // when
    final var activated = ENGINE.probe().activate(List.of(probeId), "worker-b", 10);

    // then
    assertThat(activated.getValue().getProbeKeys()).doesNotContain(probeKey).isEmpty();
  }

  @Test
  public void shouldActivateNothingForAProbeIdWithoutProbes() {
    // when
    final var activated = ENGINE.probe().activate(List.of(probeId), "worker", 10);

    // then
    assertThat(activated.getValue().getProbes()).isEmpty();
  }

  @Test
  public void shouldYieldAProbeSoTheNextPollCollectsItAndAnnounceIt() {
    // given
    final long probeKey = createProbe(probeId, "={}");
    ENGINE.probe().activate(List.of(probeId), "worker-a", 10);
    final int announcements = Collections.frequency(NOTIFIER.getNotifiedProbeIds(), probeId);

    // when
    final var yielded = ENGINE.probe().yield(probeKey);

    // then
    assertThat(yielded.getIntent()).isEqualTo(ProbeIntent.YIELDED);
    assertThat(yielded.getKey()).isEqualTo(probeKey);
    assertThat(yielded.getValue().getProbeId()).isEqualTo(probeId);
    assertThat(yielded.getValue().getInput()).isEmpty();
    await()
        .untilAsserted(
            () ->
                assertThat(Collections.frequency(NOTIFIER.getNotifiedProbeIds(), probeId))
                    .isEqualTo(announcements + 1));
    final var activated = ENGINE.probe().activate(List.of(probeId), "worker-b", 10);
    assertThat(activated.getValue().getProbeKeys()).containsExactly(probeKey);
    assertThat(activated.getValue().getProbes().getFirst().getWorker()).isEqualTo("worker-b");
  }

  @Test
  public void shouldYieldAProbeAnyNumberOfTimes() {
    // given
    final long probeKey = createProbe(probeId, "={}");

    // when
    for (int i = 0; i < 3; i++) {
      ENGINE.probe().activate(List.of(probeId), "worker", 10);
      ENGINE.probe().yield(probeKey);
    }

    // then
    final var activated = ENGINE.probe().activate(List.of(probeId), "worker", 10);
    assertThat(activated.getValue().getProbeKeys()).containsExactly(probeKey);
  }

  @Test
  public void shouldRejectTheYieldOfAMissingProbe() {
    // when
    final var rejection = ENGINE.probe().expectRejection().yield(-1L);

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason("Expected to yield probe with key '-1', but no such probe was found.");
  }

  @Test
  public void shouldRejectTheYieldOfAProbeThatIsNotActivated() {
    // given
    final long probeKey = createProbe(probeId, "={}");

    // when
    final var rejection = ENGINE.probe().expectRejection().yield(probeKey);

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.INVALID_STATE)
        .hasRejectionReason(
            "Expected to yield probe with key '%d', but it is in state 'ACTIVATABLE'."
                .formatted(probeKey));
  }

  @Test
  public void shouldRejectAPollWithoutProbeIds() {
    assertRejectedPoll(
        List.of(), 10, "Expected to activate probes for 1 to 100 probe IDs, but got 0.");
  }

  @Test
  public void shouldRejectAPollWithMoreThan100ProbeIds() {
    final var probeIds = IntStream.rangeClosed(0, 100).mapToObj(i -> probeId + i).toList();
    assertRejectedPoll(
        probeIds, 10, "Expected to activate probes for 1 to 100 probe IDs, but got 101.");
  }

  @Test
  public void shouldAcceptAPollWith100ProbeIds() {
    // given
    final var probeIds = IntStream.range(0, 100).mapToObj(i -> probeId + i).toList();

    // when
    final var activated = ENGINE.probe().activate(probeIds, "worker", 10);

    // then
    assertThat(activated.getValue().getProbeIds()).hasSize(100);
  }

  @Test
  public void shouldRejectAPollWithADuplicateProbeId() {
    assertRejectedPoll(
        List.of(probeId, otherProbeId, probeId),
        10,
        "Expected to activate probes for distinct probe IDs, but got %s."
            .formatted(List.of(probeId, otherProbeId, probeId)));
  }

  @Test
  public void shouldRejectAPollWithTheWildcardAsProbeId() {
    assertRejectedPoll(
        List.of(probeId, "*"),
        10,
        "Expected probe IDs of 1 to 256 characters, without whitespace and other than '*', but got '*'.");
  }

  @Test
  public void shouldRejectAPollWithABlankProbeId() {
    assertRejectedPoll(
        List.of(" "),
        10,
        "Expected probe IDs of 1 to 256 characters, without whitespace and other than '*', but got ' '.");
  }

  @Test
  public void shouldRejectAPollWithoutAPositiveMaximum() {
    assertRejectedPoll(
        List.of(probeId),
        0,
        "Expected to activate probes with max probes to activate greater than zero, but it was '0'.");
  }

  private long createProbe(final String probeId, final String input) {
    return ENGINE.probe().withProbeId(probeId).withInput(input).create().getKey();
  }

  private void assertRejectedPoll(
      final List<String> probeIds, final int maxProbesToActivate, final String reason) {
    // when
    final var rejection =
        ENGINE.probe().expectRejection().activate(probeIds, "worker", maxProbesToActivate);

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(reason);
  }
}
