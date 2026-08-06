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
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Base64;
import org.agrona.CloseHelper;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Proves that the cluster-admin mode change ({@code PATCH /cluster/v2/mode}) transitions a single
 * physical tenant's partition group while every other tenant keeps processing. That isolation is
 * the whole point of scoping the endpoint by {@code physicalTenantId}: one tenant's secondary
 * storage can be restored without stopping the rest of the cluster.
 *
 * <p>Complements {@code ClusterAdminModeChangeIT}, which covers the endpoint's authentication and
 * request validation on a single-tenant broker, and {@code NewModelManagementApiEndpointsTest},
 * which covers the same isolation at the cluster configuration level with stubbed executors. This
 * test is the only place where the real broker-side {@code PartitionModeHandler} of a non-default
 * tenant is exercised.
 */
@ZeebeIntegration
final class ClusterAdminPhysicalTenantModeChangeIT {

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

  // The data plane is left unauthenticated so the tenant clients need no credentials; the
  // cluster-admin chain is independent of that and still requires these basic-auth credentials.
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

  private static CamundaClient defaultTenantClient;
  private static CamundaClient tenantBClient;

  @BeforeAll
  static void start() {
    BROKER.start();
    defaultTenantClient = newClient(PhysicalTenantsITHelper.DEFAULT_TENANT_ID);
    tenantBClient = newClient(TENANT_B);
  }

  @AfterAll
  static void close() {
    CloseHelper.quietCloseAll(defaultTenantClient, tenantBClient);
  }

  @Test
  void shouldRecoverOneTenantWhileTheOtherKeepsProcessing() throws Exception {
    // given — both physical tenants are processing commands on their own partition group
    final String defaultProcessId = "mode-change-default";
    final String tenantBProcessId = "mode-change-tenantb";
    deployProcess(defaultTenantClient, defaultProcessId);
    deployProcess(tenantBClient, tenantBProcessId);
    assertThat(createInstance(defaultTenantClient, defaultProcessId)).isPositive();
    assertThat(createInstance(tenantBClient, tenantBProcessId)).isPositive();

    // when — the cluster admin moves only tenant B into recovery
    final var recoveringResponse = changeMode(TENANT_B, "RECOVERING");
    assertThat(recoveringResponse.statusCode())
        .as("mode change rejected: %s", recoveringResponse.body())
        .isEqualTo(HttpURLConnection.HTTP_OK);

    // then — tenant B stops accepting commands; the default tenant keeps processing on its own
    // group
    awaitCommandsRejected(tenantBClient, tenantBProcessId);
    assertThat(createInstance(defaultTenantClient, defaultProcessId)).isPositive();

    // and — the transition is reversible, so the tenant can be handed back to processing
    awaitModeChangeAccepted(TENANT_B, "PROCESSING");
    awaitCommandsAccepted(tenantBClient, tenantBProcessId);
  }

  private static HttpResponse<String> changeMode(final String physicalTenantId, final String mode)
      throws Exception {
    final URI uri =
        URI.create(
            "%scluster/v2/mode?mode=%s&physicalTenantId=%s"
                .formatted(BROKER.restAddress(), mode, physicalTenantId));
    final HttpRequest request =
        HttpRequest.newBuilder(uri)
            .header("Authorization", basicAuth(CLUSTER_ADMIN_USER, CLUSTER_ADMIN_PASSWORD))
            .method("PATCH", BodyPublishers.noBody())
            .build();
    return HTTP_CLIENT.send(request, BodyHandlers.ofString());
  }

  private static String basicAuth(final String user, final String password) {
    return "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes());
  }

  private static CamundaClient newClient(final String tenantId) {
    return TENANTS
        .newClientBuilder(BROKER, tenantId)
        .preferRestOverGrpc(true)
        .defaultRequestTimeout(REQUEST_TIMEOUT)
        .build();
  }

  /**
   * Sends the mode change until the cluster accepts it.
   *
   * <p>A change is rejected with {@code 409} while another one is still pending, and commands start
   * failing as soon as the partitions stop — which is before the change that stopped them has
   * completed. Waiting for the pending change to clear would be the deterministic alternative, but
   * a change targeting a non-default physical tenant is not observable: the topology API projects
   * {@code pendingChanges} from the global configuration and the default partition group only
   * ({@code CurrentClusterConfiguration#toLegacyDefault}), so a plan scoped to another tenant's
   * group never appears there. Retry until the platform exposes per-group change status.
   */
  private static void awaitModeChangeAccepted(final String physicalTenantId, final String mode) {
    Awaitility.await("the cluster accepts the mode change")
        .atMost(TRANSITION_TIMEOUT)
        .untilAsserted(
            () -> {
              final var response = changeMode(physicalTenantId, mode);
              assertThat(response.statusCode())
                  .as("mode change rejected: %s", response.body())
                  .isEqualTo(HttpURLConnection.HTTP_OK);
            });
  }

  // The mode change is acknowledged once accepted, so the partitions may still be transitioning.
  private static void awaitCommandsRejected(final CamundaClient client, final String processId) {
    Awaitility.await("recovering tenant stops accepting commands")
        .atMost(TRANSITION_TIMEOUT)
        .untilAsserted(
            () ->
                assertThatThrownBy(() -> createInstance(client, processId))
                    .as("a recovering tenant has no partition that can process a command")
                    .isInstanceOf(Exception.class));
  }

  private static void awaitCommandsAccepted(final CamundaClient client, final String processId) {
    Awaitility.await("tenant handed back to processing accepts commands again")
        .atMost(TRANSITION_TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(() -> assertThat(createInstance(client, processId)).isPositive());
  }

  private static void deployProcess(final CamundaClient client, final String processId) {
    final BpmnModelInstance model =
        Bpmn.createExecutableProcess(processId).startEvent().endEvent().done();
    Awaitility.await("process deployed to the tenant's partitions")
        .atMost(Duration.ofSeconds(30))
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(
                        client
                            .newDeployResourceCommand()
                            .addProcessModel(model, processId + ".bpmn")
                            .send()
                            .join()
                            .getProcesses())
                    .isNotEmpty());
  }

  private static long createInstance(final CamundaClient client, final String processId) {
    return client
        .newCreateInstanceCommand()
        .bpmnProcessId(processId)
        .latestVersion()
        .send()
        .join()
        .getProcessInstanceKey();
  }
}
