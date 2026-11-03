/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.physicaltenant;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.worker.probe.ProbeAnswer;
import io.camunda.client.api.worker.probe.ProbeHandler;
import io.camunda.client.api.worker.probe.ProbeWorker;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.agrona.CloseHelper;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * A probe created on one physical tenant is pushed only to that tenant's workers, stamped with that
 * tenant, resolves its secrets from that tenant's secret store, and a probe served only on another
 * tenant answers NO_WORKER.
 */
@ZeebeIntegration
final class ProbePhysicalTenantIsolationIT {

  private static final String TENANT_A = "tenanta";
  private static final String PROBE_ID = "validate-configuration/io.camunda:aws-credential:1";
  private static final String TENANT_A_ONLY_PROBE_ID =
      "validate-configuration/io.camunda:tenant-a-only:1";
  private static final ObjectMapper JSON = new ObjectMapper();

  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.none())
          .withTenant(TENANT_A, Storage.none())
          .build();

  private static final Path TENANT_A_SECRETS = secretStore("AKIA-tenant-a");

  @TestZeebe(autoStart = false, purgeAfterEach = false)
  private static final TestStandaloneBroker BROKER =
      TENANTS
          .configure(new TestStandaloneBroker().withUnauthenticatedAccess())
          .withFileBasedSecretStore(dir -> Files.writeString(dir.resolve("awsKey"), "AKIA-default"))
          .withProperty(
              "camunda.physical-tenants." + TENANT_A + ".secrets.stores.file.default.path",
              TENANT_A_SECRETS.toString());

  private static CamundaClient defaultClient;
  private static CamundaClient tenantAClient;
  private static ProbeWorker defaultWorker;
  private static ProbeWorker tenantAWorker;
  private static ProbeWorker tenantAOnlyWorker;
  private static final HttpClient HTTP = HttpClient.newHttpClient();

  @BeforeAll
  static void start() {
    BROKER.start();
    defaultClient =
        TENANTS.newClientBuilder(BROKER, PhysicalTenantsITHelper.DEFAULT_TENANT_ID).build();
    tenantAClient = TENANTS.newClientBuilder(BROKER, TENANT_A).build();

    defaultWorker = echoWorker(defaultClient, "default-worker", PROBE_ID);
    tenantAWorker = echoWorker(tenantAClient, "tenant-a-worker", PROBE_ID);
    tenantAOnlyWorker = echoWorker(tenantAClient, "tenant-a-worker", TENANT_A_ONLY_PROBE_ID);
  }

  @AfterAll
  static void close() {
    CloseHelper.quietCloseAll(
        defaultWorker, tenantAWorker, tenantAOnlyWorker, defaultClient, tenantAClient);
  }

  @Test
  void shouldAnswerFromTheDefaultTenantsWorkerWithTheDefaultTenantsSecret() {
    // when
    final var envelope = awaitAnswer("", PROBE_ID);

    // then
    assertThat(envelope.get("worker")).isEqualTo("default-worker");
    assertThat(envelope.get("data"))
        .isEqualTo(
            Map.of(
                "status",
                "SUCCESS",
                "code",
                PhysicalTenantsITHelper.DEFAULT_TENANT_ID,
                "message",
                "AKIA-default"));
  }

  @Test
  void shouldAnswerFromTenantAsWorkerWithTenantAsSecret() {
    // when
    final var envelope = awaitAnswer("physical-tenants/" + TENANT_A + "/", PROBE_ID);

    // then
    assertThat(envelope.get("worker")).isEqualTo("tenant-a-worker");
    assertThat(envelope.get("data"))
        .isEqualTo(Map.of("status", "SUCCESS", "code", TENANT_A, "message", "AKIA-tenant-a"));
  }

  @Test
  void shouldAnswerNoWorkerWhenOnlyAnotherTenantServesTheProbeId() throws Exception {
    // given - tenant A's worker answers the probe
    final var envelope = awaitAnswer("physical-tenants/" + TENANT_A + "/", TENANT_A_ONLY_PROBE_ID);
    assertThat(envelope.get("worker")).isEqualTo("tenant-a-worker");

    // when
    final var response = createProbe("", TENANT_A_ONLY_PROBE_ID);

    // then
    assertThat(response.statusCode()).as(response.body()).isEqualTo(404);
    assertThat(JSON.readValue(response.body(), Map.class).get("title")).isEqualTo("NO_WORKER");
  }

  private static ProbeWorker echoWorker(
      final CamundaClient client, final String name, final String probeId) {
    final ProbeHandler echo =
        probe ->
            ProbeAnswer.ok(
                Map.of(
                    "status",
                    "SUCCESS",
                    "code",
                    probe.getPhysicalTenantId(),
                    "message",
                    String.valueOf(probe.getInputAsMap().get("accessKey"))));
    return client.newProbeWorker().probeId(probeId).handler(echo).name(name).open();
  }

  private static Map<?, ?> awaitAnswer(final String tenantPrefix, final String probeId) {
    final var envelope = new AtomicReference<Map<?, ?>>();
    // streams and tenant partitions come up asynchronously, so early probes may find no worker
    Awaitility.await()
        .atMost(Duration.ofSeconds(60))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var response = createProbe(tenantPrefix, probeId);
              assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
              envelope.set(JSON.readValue(response.body(), Map.class));
            });
    return envelope.get();
  }

  private static HttpResponse<String> createProbe(final String tenantPrefix, final String probeId)
      throws Exception {
    final var body =
        JSON.writeValueAsString(
            Map.of(
                "probeId",
                probeId,
                "input",
                "={accessKey: camunda.secrets.awsKey}",
                "requestTimeout",
                10_000));
    final var base = BROKER.restAddress().toString();
    final var url = base + (base.endsWith("/") ? "" : "/") + tenantPrefix + "v2/probes";
    return HTTP.send(
        HttpRequest.newBuilder(URI.create(url))
            .header("Content-Type", "application/json")
            .POST(BodyPublishers.ofString(body))
            .build(),
        BodyHandlers.ofString());
  }

  private static Path secretStore(final String accessKey) {
    try {
      final var dir = Files.createTempDirectory("tenant-a-secrets-");
      Files.writeString(dir.resolve("awsKey"), accessKey, StandardCharsets.UTF_8);
      dir.toFile().deleteOnExit();
      dir.resolve("awsKey").toFile().deleteOnExit();
      return dir;
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
