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
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordingProbeStreamer;
import io.camunda.zeebe.protocol.record.intent.ProbeIntent;
import io.camunda.zeebe.protocol.record.value.ProbeEndReason;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Rule;
import org.junit.Test;

/**
 * The deadline of a probe covers the wait for its secrets. The store here is always unavailable and
 * retried far less often than the probe's deadline, so the probe is still parked when the deadline
 * passes, whatever the timing of the background resolution.
 */
public final class ProbeSecretTimeoutTest {

  private static final String PROBE_ID = "validate-configuration/io.camunda:test-timeout:1";

  private final RecordingProbeStreamer streamer = new RecordingProbeStreamer();

  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition()
          .withProbeStreamer(streamer)
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

  @Test
  public void shouldTimeOutWhileWaitingForTheSecret() {
    // given
    streamer.addStream(PROBE_ID, "worker-a", List.of(TenantOwned.DEFAULT_TENANT_IDENTIFIER));
    final var created =
        engine
            .probe()
            .withProbeId(PROBE_ID)
            .withInput("={token: camunda.secrets.apiToken}")
            .withRequestTimeout(1000)
            .withRequest(44, 444L)
            .create();

    // when
    engine.increaseTime(Duration.ofSeconds(2));

    // then
    final var timedOut =
        RecordingExporter.probeRecords(ProbeIntent.TIMED_OUT)
            .withRecordKey(created.getKey())
            .getFirst();
    assertThat(timedOut.getValue().getEndReason())
        .isEqualTo(ProbeEndReason.SECRET_RESOLUTION_TIMEOUT);
    verify(engine.getCommandResponseWriter(), timeout(5000)).tryWriteResponse(44, 444L);
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
