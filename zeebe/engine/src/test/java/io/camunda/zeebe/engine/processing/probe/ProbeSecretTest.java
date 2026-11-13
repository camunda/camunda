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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordingProbeNotifier;
import io.camunda.zeebe.engine.util.SecretStoreRegistries;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.impl.record.value.probe.ProbeBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.probe.ProbeRecord;
import io.camunda.zeebe.protocol.record.intent.ProbeIntent;
import io.camunda.zeebe.protocol.record.intent.SecretReferenceIntent;
import io.camunda.zeebe.protocol.record.value.ProbeEndReason;
import io.camunda.zeebe.protocol.record.value.ProbeOutcome;
import io.camunda.zeebe.stream.api.CommandResponseWriter;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.stubbing.Answer;

/**
 * The secrets of a probe against a real store and the real background resolution (D12, D13, D39): a
 * poll that finds a secret uncached parks the probe, its resolution makes it activatable again and
 * announces it, the next poll gets the value in its response only, and a secret that cannot be
 * resolved cancels the probe.
 */
public final class ProbeSecretTest {

  private static final String SECRET_VALUE = "s3cr3t-value";

  private static final RecordingProbeNotifier NOTIFIER = new RecordingProbeNotifier();

  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition()
          .withProbeNotifier(NOTIFIER)
          .withSecretStoreRegistry(
              SecretStoreRegistries.resolvingFromStore(Map.of("apiToken", SECRET_VALUE)))
          // the default interval is 5 s, which the assertions below would wait on
          .withEngineConfig(config -> config.setSecretResolutionInterval(Duration.ofMillis(100)));

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  /** Every value the engine wrote to a response, as JSON: the activation response among them. */
  private final List<UnifiedRecordValue> responses = new CopyOnWriteArrayList<>();

  private String probeId;

  @Before
  public void setUp() {
    probeId = "validate-configuration/io.camunda:test-" + UUID.randomUUID() + ":1";
    final CommandResponseWriter responseWriter = engine.getCommandResponseWriter();
    doAnswer(
            (Answer<CommandResponseWriter>)
                invocation -> {
                  // copied: the engine reuses its record instances
                  if (invocation.getArgument(0) instanceof final ProbeBatchRecord batch) {
                    final var copy = new ProbeBatchRecord();
                    copy.copyFrom(batch);
                    responses.add(copy);
                  } else if (invocation.getArgument(0) instanceof final ProbeRecord probe) {
                    responses.add(new ProbeRecord().copyFrom(probe));
                  }
                  return responseWriter;
                })
        .when(responseWriter)
        .valueWriter(any());
  }

  @Test
  public void shouldParkTheProbeAndCollectItWithTheSecretOnceResolved() {
    // given
    final long probeKey =
        engine
            .probe()
            .withProbeId(probeId)
            .withInput("={auth: {token: camunda.secrets.apiToken}}")
            .create()
            .getKey();

    // when - the first poll finds the secret uncached
    final var firstPoll = engine.probe().activate(List.of(probeId), "worker-a", 1);
    final int announcementsBefore = announcements();

    // then - skipped, parked and its resolution requested
    assertThat(firstPoll.getValue().getProbeKeys()).isEmpty();
    assertThat(
            RecordingExporter.secretReferenceRecords(SecretReferenceIntent.RESOLUTION_REQUESTED)
                .withSecretReference("apiToken")
                .getFirst()
                .getValue()
                .getProbeKeys())
        .containsExactly(probeKey);
    awaitResolutionAndItsSideEffects();
    assertThat(announcements()).describedAs("announced again").isEqualTo(announcementsBefore + 1);

    // when - the poll after the announcement
    final var secondPoll = engine.probe().activate(List.of(probeId), "worker-a", 1);

    // then - collected; the event keeps the placeholder, the response carries the value
    assertThat(secondPoll.getValue().getProbeKeys()).containsExactly(probeKey);
    assertThat(secondPoll.getValue().getProbes().getFirst().getInput())
        .isEqualTo(Map.of("auth", Map.of("token", "camunda.secrets.apiToken")));
    await("until the activation response is written")
        .untilAsserted(
            () ->
                assertThat(activationResponses())
                    .anySatisfy(
                        response ->
                            assertThat(response.getProbes())
                                .singleElement()
                                .satisfies(
                                    probe ->
                                        assertThat(probe.getInput())
                                            .isEqualTo(
                                                Map.of("auth", Map.of("token", SECRET_VALUE))))));
  }

  @Test
  public void shouldAnnounceAResolvedProbeIdOnce() {
    // given - two probes of one probe ID, parked on the same secret by one poll
    createProbe("={token: camunda.secrets.apiToken}");
    createProbe("={token: camunda.secrets.apiToken}");
    engine.probe().activate(List.of(probeId), "worker-a", 10);
    final int announcementsBefore = announcements();

    // when
    awaitResolutionAndItsSideEffects();

    // then
    assertThat(announcements()).isEqualTo(announcementsBefore + 1);
  }

  @Test
  public void shouldKeepSecretValuesOutOfTheLogTheStateAndTheClientAnswer() {
    // given
    final long probeKey =
        engine
            .probe()
            .withProbeId(probeId)
            .withInput("={token: camunda.secrets.apiToken}")
            .withRequest(41, 411L)
            .create()
            .getKey();
    engine.probe().activate(List.of(probeId), "worker-a", 1);
    RecordingExporter.secretReferenceRecords(SecretReferenceIntent.RESOLUTION_COMPLETED)
        .withSecretReference("apiToken")
        .await();
    engine.probe().activate(List.of(probeId), "worker-a", 1);
    assertThat(engine.getProcessingState().getProbeState().getProbe(probeKey).getInput())
        .describedAs("the state keeps the placeholder")
        .isEqualTo(Map.of("token", "camunda.secrets.apiToken"));

    // when
    engine.probe().answer(probeKey, new ProbeRecord().setOutcome(ProbeOutcome.UNSUPPORTED));

    // then
    verify(engine.getCommandResponseWriter(), timeout(5_000)).tryWriteResponse(41, 411L);
    assertThat(activationResponses())
        .describedAs("the activation response is the only place the value appears")
        .anySatisfy(response -> assertThat(response.toJson()).contains(SECRET_VALUE));
    assertThat(responses)
        .filteredOn(response -> !(response instanceof ProbeBatchRecord))
        .isNotEmpty()
        .allSatisfy(response -> assertThat(response.toJson()).doesNotContain(SECRET_VALUE));
    assertThat(RecordingExporter.records().limit(r -> r.getIntent() == ProbeIntent.ANSWERED))
        .isNotEmpty()
        .allSatisfy(record -> assertThat(record.toJson()).doesNotContain(SECRET_VALUE));
  }

  @Test
  public void shouldCollectRightAwayWhenTheSecretIsCached() {
    // given - a first probe warms the cache
    createProbe("={token: camunda.secrets.apiToken}");
    engine.probe().activate(List.of(probeId), "worker-a", 1);
    RecordingExporter.secretReferenceRecords(SecretReferenceIntent.RESOLUTION_COMPLETED)
        .withSecretReference("apiToken")
        .await();
    engine.probe().activate(List.of(probeId), "worker-a", 1);

    // when
    final long probeKey = createProbe("={token: camunda.secrets.apiToken}");
    final var poll = engine.probe().activate(List.of(probeId), "worker-a", 1);

    // then
    assertThat(poll.getValue().getProbeKeys()).containsExactly(probeKey);
  }

  @Test
  public void shouldNotRequestAResolutionForAProbeNobodyPolls() {
    // given
    final String otherProbeId = "validate-configuration/io.camunda:other-" + UUID.randomUUID();
    createProbe("={token: camunda.secrets.apiToken}");

    // when - a poll for another probe ID only
    engine.probe().withProbeId(otherProbeId).withInput("={}").create();
    final var poll = engine.probe().activate(List.of(otherProbeId), "worker-a", 1);

    // then
    assertThat(
            RecordingExporter.records()
                .limit(record -> record.getPosition() >= poll.getPosition())
                .filter(record -> record.getIntent() == SecretReferenceIntent.RESOLUTION_REQUESTED))
        .isEmpty();
  }

  @Test
  public void shouldCancelTheProbeWhenItsSecretFailsToResolve() {
    // given
    final long probeKey =
        engine
            .probe()
            .withProbeId(probeId)
            .withInput("={token: camunda.secrets.missing}")
            .withRequest(42, 421L)
            .create()
            .getKey();

    // when
    engine.probe().activate(List.of(probeId), "worker-a", 1);

    // then - no incident: the probe ends, naming the secret, never a value (D13, AT7)
    final var canceled =
        RecordingExporter.probeRecords(ProbeIntent.CANCELED).withRecordKey(probeKey).getFirst();
    assertThat(canceled.getValue().getEndReason())
        .isEqualTo(ProbeEndReason.SECRET_RESOLUTION_ERROR);
    assertThat(canceled.getValue().getMessage())
        .isEqualTo(
            "Failed to resolve secret 'missing' from the configured secret store. Ensure the secret exists and the store is available, then retry.");
    verify(engine.getCommandResponseWriter(), timeout(5_000)).tryWriteResponse(42, 421L);
    assertThat(engine.getProcessingState().getProbeState().getProbe(probeKey)).isNull();
    assertThat(parkedReferencesOf(probeKey)).isEmpty();
  }

  @Test
  public void shouldCancelTheProbeWhenItsSecretCannotBeInjected() {
    // given - the reference's leaf holds text shaped as another placeholder, not its own
    final long probeKey =
        createProbe(
            "={token: if 1 > 2 then camunda.secrets.apiToken else \"camunda.secrets.literal\"}");
    engine.probe().activate(List.of(probeId), "worker-a", 1);
    RecordingExporter.secretReferenceRecords(SecretReferenceIntent.RESOLUTION_COMPLETED)
        .withSecretReference("apiToken")
        .await();

    // when
    final var poll = engine.probe().activate(List.of(probeId), "worker-a", 1);

    // then
    assertThat(poll.getValue().getProbeKeys()).isEmpty();
    final var canceled =
        RecordingExporter.probeRecords(ProbeIntent.CANCELED).withRecordKey(probeKey).getFirst();
    assertThat(canceled.getValue().getEndReason())
        .isEqualTo(ProbeEndReason.SECRET_RESOLUTION_ERROR);
    assertThat(canceled.getValue().getMessage())
        .isEqualTo(
            "The probe with key '%d' can not be activated, because the secret reference 'camunda.secrets.apiToken' could not be resolved at '/token'. Fix the input of the probe, then retry."
                .formatted(probeKey));
  }

  private long createProbe(final String input) {
    return engine.probe().withProbeId(probeId).withInput(input).create().getKey();
  }

  /**
   * Waits until the secret is resolved and the side effects of its processing ran: a command
   * processed after it is the fence, as side effects run before the next command is processed.
   */
  private void awaitResolutionAndItsSideEffects() {
    RecordingExporter.secretReferenceRecords(SecretReferenceIntent.RESOLUTION_COMPLETED)
        .withSecretReference("apiToken")
        .await();
    engine.probe().activate(List.of(probeId + "-fence"), "worker-a", 1);
  }

  private int announcements() {
    return (int) NOTIFIER.getNotifiedProbeIds().stream().filter(probeId::equals).count();
  }

  private List<ProbeBatchRecord> activationResponses() {
    return responses.stream()
        .filter(ProbeBatchRecord.class::isInstance)
        .map(ProbeBatchRecord.class::cast)
        .toList();
  }

  private List<String> parkedReferencesOf(final long probeKey) {
    final List<String> references = new ArrayList<>();
    engine
        .getProcessingState()
        .getProbeState()
        .forEachSecretReferenceParkedOn(
            probeKey,
            (storeId, secretReference) -> references.add(storeId + "/" + secretReference));
    return references;
  }
}
