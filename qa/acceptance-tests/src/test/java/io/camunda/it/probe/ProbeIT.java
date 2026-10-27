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
import io.camunda.client.api.worker.probe.ProbeOperation;
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
 * references, pushes the probe to a Java client probe worker with the value injected, and answers
 * the client with what the worker answered over REST.
 */
@ZeebeIntegration
final class ProbeIT {

  private static final String TEMPLATE = "io.camunda:aws-credential:1";
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
            .capability(
                ProbeOperation.VALIDATE_CONFIGURATION,
                TEMPLATE,
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

    // when - the stream registers asynchronously, so the first probes may find no worker yet
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

    assertThat(received.get().getTemplate()).isEqualTo(TEMPLATE);
    assertThat(received.get().getInputAsMap())
        .isEqualTo(Map.of("region", "eu-west-1", "accessKey", ACCESS_KEY));
    assertThat(received.get().toString()).doesNotContain(ACCESS_KEY);
  }

  @Test
  void shouldAnswerNoWorkerWhenNoWorkerAnswersTheTemplate() throws Exception {
    // when
    final var response = createProbe();

    // then
    assertThat(response.statusCode()).isEqualTo(404);
    final Map<?, ?> problem = JSON.readValue(response.body(), Map.class);
    assertThat(problem.get("title")).isEqualTo("NO_WORKER");
  }

  private HttpResponse<String> createProbe() throws Exception {
    final var body =
        JSON.writeValueAsString(
            Map.of(
                "operation",
                "VALIDATE_CONFIGURATION",
                "template",
                TEMPLATE,
                "input",
                "={region: \"eu-west-1\", accessKey: camunda.secrets.awsKey}",
                "requestTimeout",
                10_000));
    final var base = broker.restAddress().toString();
    final var request =
        HttpRequest.newBuilder(URI.create(base + (base.endsWith("/") ? "" : "/") + "v2/probes"))
            .header("Content-Type", "application/json")
            .POST(BodyPublishers.ofString(body))
            .build();
    return http.send(request, BodyHandlers.ofString());
  }
}
