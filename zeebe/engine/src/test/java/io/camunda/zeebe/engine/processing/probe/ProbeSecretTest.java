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

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordingProbeStreamer;
import io.camunda.zeebe.engine.util.RecordingProbeStreamer.RecordingProbeStream;
import io.camunda.zeebe.engine.util.SecretStoreRegistries;
import io.camunda.zeebe.protocol.record.intent.ProbeIntent;
import io.camunda.zeebe.protocol.record.intent.SecretReferenceIntent;
import io.camunda.zeebe.protocol.record.value.ProbeEndReason;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.Rule;
import org.junit.Test;

/**
 * Covers the secrets of a probe against a real store and the real background resolution: a
 * non-cached secret parks the probe, its resolution pushes it with the value injected, and a secret
 * that cannot be resolved ends it.
 */
public final class ProbeSecretTest {

  private static final String SECRET_VALUE = "s3cr3t";

  private final RecordingProbeStreamer streamer = new RecordingProbeStreamer();

  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition()
          .withProbeStreamer(streamer)
          .withSecretStoreRegistry(
              SecretStoreRegistries.resolvingFromStore(Map.of("apiToken", SECRET_VALUE)))
          // the default interval is 5s, which would make the assertions below wait on it
          .withEngineConfig(config -> config.setSecretResolutionInterval(Duration.ofMillis(100)));

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private final String probeId =
      "validate-configuration/io.camunda:test-" + UUID.randomUUID() + ":1";

  @Test
  public void shouldParkProbeThenPushItWithTheSecretInjected() {
    // given
    final var stream = addStream();

    // when
    final var created =
        engine
            .probe()
            .withProbeId(probeId)
            .withInput("={auth: {token: camunda.secrets.apiToken}}")
            .create();

    // then
    final var requested =
        RecordingExporter.secretReferenceRecords(SecretReferenceIntent.RESOLUTION_REQUESTED)
            .withSecretReference("apiToken")
            .getFirst();
    assertThat(requested.getValue().getProbeKeys()).containsExactly(created.getKey());

    final var activated =
        RecordingExporter.probeRecords(ProbeIntent.ACTIVATED)
            .withRecordKey(created.getKey())
            .getFirst();
    assertThat(activated.getPosition()).isGreaterThan(requested.getPosition());

    await().untilAsserted(() -> assertThat(stream.getPushedProbes()).hasSize(1));
    assertThat(stream.getPushedProbes().getFirst().record().getInput())
        .isEqualTo(Map.of("auth", Map.of("token", SECRET_VALUE)));
    assertThat(created.getValue().getInput())
        .isEqualTo(Map.of("auth", Map.of("token", "camunda.secrets.apiToken")));
  }

  @Test
  public void shouldPushRightAwayWhenTheSecretIsCached() {
    // given - a first probe warms the cache
    final var stream = addStream();
    engine.probe().withProbeId(probeId).withInput("={token: camunda.secrets.apiToken}").create();
    await().untilAsserted(() -> assertThat(stream.getPushedProbes()).hasSize(1));

    // when
    final var second =
        engine
            .probe()
            .withProbeId(probeId)
            .withInput("={token: camunda.secrets.apiToken}")
            .create();

    // then - activated by the create command itself, without parking
    final var activated =
        RecordingExporter.probeRecords(ProbeIntent.ACTIVATED)
            .withRecordKey(second.getKey())
            .getFirst();
    assertThat(activated.getSourceRecordPosition()).isEqualTo(second.getSourceRecordPosition());
    await().untilAsserted(() -> assertThat(stream.getPushedProbes()).hasSize(2));
    assertThat(stream.getPushedProbes().get(1).record().getInput())
        .isEqualTo(Map.of("token", SECRET_VALUE));
  }

  @Test
  public void shouldCancelWhenTheSecretFailsToResolve() {
    // given
    addStream();

    // when
    final var created =
        engine.probe().withProbeId(probeId).withInput("={token: camunda.secrets.missing}").create();

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

  private RecordingProbeStream addStream() {
    return streamer.addStream(probeId, "worker-a", List.of(TenantOwned.DEFAULT_TENANT_IDENTIFIER));
  }
}
