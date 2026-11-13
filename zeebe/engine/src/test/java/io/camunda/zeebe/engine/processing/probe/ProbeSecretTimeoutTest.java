/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.probe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import io.camunda.secretstore.SecretResolutionResult;
import io.camunda.secretstore.SecretStore;
import io.camunda.secretstore.SecretStoreRegistry;
import io.camunda.secretstore.SecretStoreUnavailableException;
import io.camunda.zeebe.engine.state.immutable.ProbeState.State;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.impl.record.value.probe.ProbeRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProbeIntent;
import io.camunda.zeebe.protocol.record.intent.SecretReferenceIntent;
import io.camunda.zeebe.protocol.record.value.ProbeEndReason;
import io.camunda.zeebe.protocol.record.value.ProbeOutcome;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * A probe parked on its secrets. The store here is always unavailable and retried far less often
 * than the probe's deadline, so the probe stays parked whatever the timing of the background
 * resolution.
 */
public final class ProbeSecretTimeoutTest {

  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition()
          .withSecretStoreRegistry(
              new SecretStoreRegistry(
                  Map.of(SecretStoreRegistry.DEFAULT_STORE_ID, new UnavailableSecretStore())))
          .withEngineConfig(
              config ->
                  config
                      .setSecretResolutionRetryInitialDelay(Duration.ofHours(1))
                      .setSecretResolutionRetryMaxDelay(Duration.ofHours(1))
                      .setSecretResolutionRetryMaxAttempts(1000));

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private String probeId;

  @Before
  public void setUp() {
    probeId = "validate-configuration/io.camunda:test-" + UUID.randomUUID() + ":1";
  }

  @Test
  public void shouldTimeOutWhileWaitingForTheSecret() {
    // given
    final long probeKey =
        engine
            .probe()
            .withProbeId(probeId)
            .withInput("={token: camunda.secrets.apiToken, key: camunda.secrets.apiKey}")
            .withRequestTimeout(1_000)
            .withRequest(44, 444L)
            .create()
            .getKey();
    park();
    assertThat(parkedReferencesOf(probeKey))
        .containsExactlyInAnyOrder("default/apiToken", "default/apiKey");

    // when
    engine.increaseTime(Duration.ofSeconds(2));

    // then - the deadline covers the time parked
    final var timedOut =
        RecordingExporter.probeRecords(ProbeIntent.TIMED_OUT).withRecordKey(probeKey).getFirst();
    assertThat(timedOut.getValue().getEndReason())
        .isEqualTo(ProbeEndReason.SECRET_RESOLUTION_TIMEOUT);
    verify(engine.getCommandResponseWriter(), timeout(5_000)).tryWriteResponse(44, 444L);
    assertThat(parkedReferencesOf(probeKey)).isEmpty();
  }

  @Test
  public void shouldRejectTheAnswerForAParkedProbe() {
    // given
    final long probeKey =
        engine
            .probe()
            .withProbeId(probeId)
            .withInput("={token: camunda.secrets.apiToken}")
            .create()
            .getKey();
    park();
    assertThat(engine.getProcessingState().getProbeState().getState(probeKey))
        .isEqualTo(State.WAITING_FOR_SECRET_RESOLUTION);

    // when
    final var rejection =
        engine
            .probe()
            .expectRejection()
            .answer(probeKey, new ProbeRecord().setOutcome(ProbeOutcome.UNSUPPORTED));

    // then - the worker gets 409 (D31)
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
  }

  @Test
  public void shouldNotCollectAParkedProbe() {
    // given
    engine.probe().withProbeId(probeId).withInput("={token: camunda.secrets.apiToken}").create();
    park();

    // when
    final var poll = engine.probe().activate(List.of(probeId), "worker-a", 1);

    // then - not even checked again: no new resolution request
    assertThat(poll.getValue().getProbeKeys()).isEmpty();
    assertThat(
            RecordingExporter.records()
                .limit(record -> record.getPosition() >= poll.getPosition())
                .filter(record -> record.getIntent() == SecretReferenceIntent.RESOLUTION_REQUESTED))
        .hasSize(1);
  }

  /** The first poll finds the secrets uncached and parks the probe. */
  private void park() {
    engine.probe().activate(List.of(probeId), "worker-a", 1);
    RecordingExporter.secretReferenceRecords(SecretReferenceIntent.RESOLUTION_REQUESTED).await();
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

  private static final class UnavailableSecretStore implements SecretStore {

    @Override
    public Map<String, SecretResolutionResult> resolve(final Set<String> names) {
      throw new SecretStoreUnavailableException("the store is down");
    }

    @Override
    public List<String> list() {
      return List.of();
    }
  }
}
