/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.probe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.worker.probe.ActivatedProbe;
import io.camunda.client.api.worker.probe.ProbeAnswer;
import io.camunda.client.api.worker.probe.ProbeWorker;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A client creates a probe over REST and waits; the engine resolves the secret its input
 * references, hands the probe to the poll of a Java client probe worker with the value injected,
 * and answers the client with what the worker answered over REST.
 */
@ZeebeIntegration
final class ProbeIT {

  private static final String PROBE_ID = "validate-configuration/io.camunda:aws-credential:1";
  private static final String OTHER_PROBE_ID = "validate-configuration/io.camunda:slack:1";
  private static final String ACCESS_KEY = "AKIA-probe-e2e-secret";
  private static final ObjectMapper JSON = new ObjectMapper();

  @TestZeebe
  private final TestStandaloneBroker broker =
      new TestStandaloneBroker()
          .withUnauthenticatedAccess()
          .withFileBasedSecretStore(
              directory ->
                  Files.writeString(
                      directory.resolve("awsKey"), ACCESS_KEY, StandardCharsets.UTF_8));

  @AutoClose private CamundaClient client;
  @AutoClose private ProbeWorker worker;
  private final HttpClient http = HttpClient.newHttpClient();

  @BeforeEach
  void setUp() {
    client = broker.newClientBuilder().build();
  }

  @Test
  void shouldAnswerAProbeCreatedOverRestWithAJavaClientProbeWorker() throws Exception {
    // given
    final var received = new AtomicReference<ActivatedProbe>();
    worker =
        client
            .newProbeWorker()
            .probeId(
                PROBE_ID,
                probe -> {
                  received.set(probe);
                  final var valid = ACCESS_KEY.equals(probe.getInputAsMap().get("accessKey"));
                  return ProbeAnswer.ok(
                      Map.of(
                          "status",
                          valid ? "SUCCESS" : "FAILURE",
                          "code",
                          valid ? "OK" : "UNAUTHORIZED",
                          "message",
                          valid ? "The credential is valid." : "AWS rejected the credential."));
                })
            .name("e2e-probe-worker")
            .open();

    // when - the first poll may not be held yet, so the first probes may find no worker
    final var response = new AtomicReference<HttpResponse<String>>();
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              response.set(createProbe());
              assertThat(response.get().statusCode()).isEqualTo(200);
            });

    // then
    final Map<?, ?> envelope = JSON.readValue(response.get().body(), Map.class);
    assertThat(envelope.get("outcome")).isEqualTo("OK");
    assertThat(envelope.get("worker")).isEqualTo("e2e-probe-worker");
    assertThat(envelope.get("data"))
        .isEqualTo(
            Map.of("status", "SUCCESS", "code", "OK", "message", "The credential is valid."));

    assertThat(received.get().getProbeId()).isEqualTo(PROBE_ID);
    assertThat(received.get().getInputAsMap())
        .isEqualTo(Map.of("region", "eu-west-1", "accessKey", ACCESS_KEY));
    assertThat(received.get().toString()).doesNotContain(ACCESS_KEY);
  }

  @Test
  void shouldAnswerTheProbesOfEveryProbeIdOfAWorkerWithTheirOwnHandler() throws Exception {
    // given - one worker, one poll, two probe IDs
    worker =
        client
            .newProbeWorker()
            .probeId(PROBE_ID, probe -> ProbeAnswer.ok(Map.of("handler", "aws")))
            .probeId(OTHER_PROBE_ID, probe -> ProbeAnswer.ok(Map.of("handler", "slack")))
            .name("e2e-multi-probe-worker")
            .open();

    // when - the first poll may not be held yet, so the first probes may find no worker
    final var aws = new AtomicReference<HttpResponse<String>>();
    final var slack = new AtomicReference<HttpResponse<String>>();
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              slack.set(createProbe(OTHER_PROBE_ID, "={}"));
              assertThat(slack.get().statusCode()).as(slack.get().body()).isEqualTo(200);
            });
    aws.set(createProbe());

    // then
    assertThat(aws.get().statusCode()).as(aws.get().body()).isEqualTo(200);
    assertThat(JSON.readValue(aws.get().body(), Map.class).get("data"))
        .isEqualTo(Map.of("handler", "aws"));
    assertThat(JSON.readValue(slack.get().body(), Map.class).get("data"))
        .isEqualTo(Map.of("handler", "slack"));
  }

  @Test
  void shouldAnswerNoWorkerWhenNoWorkerAnswersTheProbeId() throws Exception {
    // when
    final long start = System.nanoTime();
    final var response = createProbe();
    final long elapsedMs = (System.nanoTime() - start) / 1_000_000;

    // then
    assertThat(response.statusCode()).as(elapsedMs + " ms: " + response.body()).isEqualTo(404);
    final Map<?, ?> problem = JSON.readValue(response.body(), Map.class);
    assertThat(problem.get("title")).isEqualTo("NO_WORKER");
  }

  private HttpResponse<String> createProbe() throws Exception {
    return createProbe(PROBE_ID, "={region: \"eu-west-1\", accessKey: camunda.secrets.awsKey}");
  }

  private HttpResponse<String> createProbe(final String probeId, final String input)
      throws Exception {
    final var body =
        JSON.writeValueAsString(
            Map.of("probeId", probeId, "input", input, "requestTimeout", 10_000));
    final var base = broker.restAddress().toString();
    final var request =
        HttpRequest.newBuilder(URI.create(base + (base.endsWith("/") ? "" : "/") + "v2/probes"))
            .header("Content-Type", "application/json")
            .POST(BodyPublishers.ofString(body))
            .build();
    return http.send(request, BodyHandlers.ofString());
  }
}
