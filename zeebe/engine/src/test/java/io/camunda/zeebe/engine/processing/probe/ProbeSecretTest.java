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

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.SecretStoreRegistries;
import io.camunda.zeebe.protocol.impl.record.value.probe.ProbeBatchRecord;
import io.camunda.zeebe.protocol.record.intent.ProbeIntent;
import io.camunda.zeebe.protocol.record.intent.SecretReferenceIntent;
import io.camunda.zeebe.protocol.record.value.ProbeEndReason;
import io.camunda.zeebe.stream.api.CommandResponseWriter;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.stubbing.Answer;

/**
 * Covers the secrets of a probe against a real store and the real background resolution: a poll
 * that finds a secret uncached parks the probe, its resolution makes it activatable again, the next
 * poll gets the value injected into its response only, and a secret that cannot be resolved ends
 * the probe.
 */
public final class ProbeSecretTest {

  private static final String SECRET_VALUE = "s3cr3t";

  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition()
          .withSecretStoreRegistry(
              SecretStoreRegistries.resolvingFromStore(Map.of("apiToken", SECRET_VALUE)))
          // the default interval is 5s, which would make the assertions below wait on it
          .withEngineConfig(config -> config.setSecretResolutionInterval(Duration.ofMillis(100)));

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private final String probeId =
      "validate-configuration/io.camunda:test-" + UUID.randomUUID() + ":1";

  /** The activation responses: the only place the secret values appear. */
  private final List<ProbeBatchRecord> responses = new CopyOnWriteArrayList<>();

  @Before
  public void captureActivationResponses() {
    final CommandResponseWriter responseWriter = engine.getCommandResponseWriter();
    doAnswer(
            (Answer<CommandResponseWriter>)
                invocation -> {
                  if (invocation.getArgument(0) instanceof final ProbeBatchRecord batch) {
                    // copied: the engine reuses its record instances
                    final var copy = new ProbeBatchRecord();
                    copy.copyFrom(batch);
                    responses.add(copy);
                  }
                  return responseWriter;
                })
        .when(responseWriter)
        .valueWriter(any());
  }

  @Test
  public void shouldParkTheProbeThenDeliverItWithTheSecretInjected() {
    // given
    final var created =
        engine
            .probe()
            .withProbeId(probeId)
            .withInput("={auth: {token: camunda.secrets.apiToken}}")
            .create();

    // when - the first poll finds the secret uncached
    final var firstPoll = engine.probe().activate(probeId, "worker-a", 1);

    // then
    assertThat(firstPoll.getValue().getProbeKeys()).isEmpty();
    final var requested =
        RecordingExporter.secretReferenceRecords(SecretReferenceIntent.RESOLUTION_REQUESTED)
            .withSecretReference("apiToken")
            .getFirst();
    assertThat(requested.getValue().getProbeKeys()).containsExactly(created.getKey());
    RecordingExporter.secretReferenceRecords(SecretReferenceIntent.RESOLUTION_COMPLETED)
        .withSecretReference("apiToken")
        .await();

    // when - a poll after the resolution
    final var secondPoll = engine.probe().activate(probeId, "worker-a", 1);

    // then - the value is in the response only; the event keeps the placeholder
    assertThat(secondPoll.getValue().getProbeKeys()).containsExactly(created.getKey());
    assertThat(secondPoll.getValue().getProbes().getFirst().getInput())
        .isEqualTo(Map.of("auth", Map.of("token", "camunda.secrets.apiToken")));
    await()
        .untilAsserted(
            () ->
                assertThat(responses)
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
  public void shouldDeliverRightAwayWhenTheSecretIsCached() {
    // given - a first probe warms the cache
    engine.probe().withProbeId(probeId).withInput("={token: camunda.secrets.apiToken}").create();
    engine.probe().activate(probeId, "worker-a", 1);
    RecordingExporter.secretReferenceRecords(SecretReferenceIntent.RESOLUTION_COMPLETED)
        .withSecretReference("apiToken")
        .await();
    engine.probe().activate(probeId, "worker-a", 1);

    // when
    final var second =
        engine
            .probe()
            .withProbeId(probeId)
            .withInput("={token: camunda.secrets.apiToken}")
            .create();
    final var poll = engine.probe().activate(probeId, "worker-a", 1);

    // then - collected by the first poll, without parking
    assertThat(poll.getValue().getProbeKeys()).containsExactly(second.getKey());
  }

  @Test
  public void shouldCancelWhenTheSecretFailsToResolve() {
    // given
    final var created =
        engine.probe().withProbeId(probeId).withInput("={token: camunda.secrets.missing}").create();

    // when
    engine.probe().activate(probeId, "worker-a", 1);

    // then
    final var canceled =
        RecordingExporter.probeRecords(ProbeIntent.CANCELED)
            .withRecordKey(created.getKey())
            .getFirst();
    assertThat(canceled.getValue().getEndReason())
        .isEqualTo(ProbeEndReason.SECRET_RESOLUTION_ERROR);
    assertThat(canceled.getValue().getMessage())
        .isEqualTo(
            "Failed to resolve secret 'missing' from the configured secret store. Ensure the secret exists and the store is available, then retry.");
  }
}
