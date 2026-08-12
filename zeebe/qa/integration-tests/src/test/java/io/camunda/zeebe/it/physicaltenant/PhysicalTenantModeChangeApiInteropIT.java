/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.physicaltenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Base64;
import org.agrona.CloseHelper;
import org.awaitility.Awaitility;
import org.awaitility.core.ThrowingRunnable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the two mode-change APIs are interchangeable on the same physical tenant: a
 * transition started through the tenant-scoped {@code PATCH /physical-tenants/{id}/v2/mode} can be
 * reverted through the cluster-admin {@code PATCH /cluster/v2/mode}, and the other way round.
 */
@ZeebeIntegration
final class PhysicalTenantModeChangeApiInteropIT {

  private static final String TENANT_B = "tenantb";
  private static final String CLUSTER_ADMIN_USER = "cluster-operator";
  private static final String CLUSTER_ADMIN_PASSWORD = "cluster-secret";
  private static final Duration TRANSITION_TIMEOUT = Duration.ofSeconds(60);
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.none())
          .withTenant(TENANT_B, Storage.none())
          .build();

  // The data plane is unauthenticated so the tenant-scoped endpoint needs no credentials of its
  // own; the cluster-admin chain is independent of that and still requires these.
  @TestZeebe(autoStart = false, purgeAfterEach = false)
  private static final TestStandaloneBroker BROKER =
      TENANTS.configure(
          new TestStandaloneBroker()
              .withUnauthenticatedAccess()
              .withProperty(
                  "camunda.security.cluster-admin.basic.users[0].name", CLUSTER_ADMIN_USER)
              .withProperty(
                  "camunda.security.cluster-admin.basic.users[0].password",
                  CLUSTER_ADMIN_PASSWORD));

  @AutoClose private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

  private static CamundaClient tenantBClient;

  @BeforeAll
  static void start() {
    BROKER.start();
    tenantBClient =
        TENANTS
            .newClientBuilder(BROKER, TENANT_B)
            .preferRestOverGrpc(true)
            .defaultRequestTimeout(REQUEST_TIMEOUT)
            .build();
  }

  @AfterAll
  static void close() {
    CloseHelper.quietCloseAll(tenantBClient);
  }

  @Test
  void shouldRevertTenantScopedRecoveryWithTheClusterAdminApi() throws Exception {
    // given — the tenant is processing and its own API puts it into recovery
    final String processId = "interop-tenant-first";
    deployProcess(processId);
    awaitAccepted(() -> tenantModeChange("RECOVERING"));
    awaitCommandsRejected(processId);

    // when — the cluster admin reverts a transition it did not start
    awaitAccepted(() -> clusterAdminModeChange("PROCESSING"));

    // then — the tenant processes again, so the cluster-admin API planned against the same
    // partition group the tenant-scoped API had transitioned
    awaitCommandsAccepted(processId);
  }

  @Test
  void shouldRevertClusterAdminRecoveryWithTheTenantScopedApi() throws Exception {
    // given — the tenant is processing and the cluster admin puts it into recovery
    final String processId = "interop-cluster-admin-first";
    deployProcess(processId);
    awaitAccepted(() -> clusterAdminModeChange("RECOVERING"));
    awaitCommandsRejected(processId);

    // when — the tenant's own API reverts it. The endpoint stays reachable while its partitions are
    // inactive, because the mode change is served from the cluster configuration rather than from
    // the tenant's own partitions.
    awaitAccepted(() -> tenantModeChange("PROCESSING"));

    // then
    awaitCommandsAccepted(processId);
  }

  /**
   * {@code PATCH /physical-tenants/<id>/v2/mode} — the per-tenant, authorization-checked API.
   *
   * <p>The tenant is put in the path explicitly rather than reusing {@code
   * InProcessRestoreTestUtil#changeMode}: that helper derives the URL from the client's configured
   * REST address, which for a {@link PhysicalTenantsITHelper} client is the bare gateway root (the
   * client applies the tenant prefix per request). It would therefore address the <em>default</em>
   * tenant and return 200 while leaving this one untouched.
   */
  private static void tenantModeChange(final String mode) throws Exception {
    patchAndAssertAccepted(
        "%sphysical-tenants/%s/v2/mode?mode=%s".formatted(BROKER.restAddress(), TENANT_B, mode),
        null);
  }

  /** {@code PATCH /cluster/v2/mode} — the cluster-wide API, authenticated as a cluster admin. */
  private static void clusterAdminModeChange(final String mode) throws Exception {
    patchAndAssertAccepted(
        "%scluster/v2/mode?mode=%s&physicalTenantId=%s"
            .formatted(BROKER.restAddress(), mode, TENANT_B),
        "Basic "
            + Base64.getEncoder()
                .encodeToString((CLUSTER_ADMIN_USER + ":" + CLUSTER_ADMIN_PASSWORD).getBytes()));
  }

  private static void patchAndAssertAccepted(final String uri, final String authorizationHeader)
      throws Exception {
    final HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(uri));
    if (authorizationHeader != null) {
      builder.header("Authorization", authorizationHeader);
    }
    final var response =
        HTTP_CLIENT.send(
            builder.method("PATCH", BodyPublishers.noBody()).build(), BodyHandlers.ofString());
    assertThat(response.statusCode())
        .describedAs("mode change REST response: %s", response.body())
        .isEqualTo(HttpURLConnection.HTTP_OK);
  }

  /**
   * Sends a mode change until the cluster accepts it.
   *
   * <p>TODO: This is required now since the topology response is not tenant aware and does not
   * include the change plan for non default tenant
   */
  private static void awaitAccepted(final ThrowingRunnable modeChange) {
    Awaitility.await("the cluster accepts the mode change")
        .atMost(TRANSITION_TIMEOUT)
        .untilAsserted(modeChange);
  }

  private static void awaitCommandsRejected(final String processId) {
    Awaitility.await("the recovering tenant stops accepting commands")
        .atMost(TRANSITION_TIMEOUT)
        .untilAsserted(
            () ->
                assertThatThrownBy(() -> createInstance(processId))
                    .as("a recovering tenant has no partition that can process a command")
                    .isInstanceOf(Exception.class));
  }

  private static void awaitCommandsAccepted(final String processId) {
    Awaitility.await("the tenant accepts commands again")
        .atMost(TRANSITION_TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(() -> assertThat(createInstance(processId)).isPositive());
  }

  private static void deployProcess(final String processId) {
    final BpmnModelInstance model =
        Bpmn.createExecutableProcess(processId).startEvent().endEvent().done();
    Awaitility.await("process deployed to the tenant's partitions")
        .atMost(Duration.ofSeconds(30))
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(
                        tenantBClient
                            .newDeployResourceCommand()
                            .addProcessModel(model, processId + ".bpmn")
                            .send()
                            .join()
                            .getProcesses())
                    .isNotEmpty());
  }

  private static long createInstance(final String processId) {
    return tenantBClient
        .newCreateInstanceCommand()
        .bpmnProcessId(processId)
        .latestVersion()
        .send()
        .join()
        .getProcessInstanceKey();
  }
}
