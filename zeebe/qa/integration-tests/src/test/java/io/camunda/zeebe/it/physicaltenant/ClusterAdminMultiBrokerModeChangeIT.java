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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestClusterBuilder;
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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
final class ClusterAdminMultiBrokerModeChangeIT {

  private static final String TENANT_A = "tenanta";
  private static final List<String> GROUPS =
      List.of(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, TENANT_A);
  private static final int BROKERS_COUNT = 3;
  private static final String CLUSTER_ADMIN_USER = "cluster-operator";
  private static final String CLUSTER_ADMIN_PASSWORD = "cluster-secret";
  private static final Duration TRANSITION_TIMEOUT = Duration.ofSeconds(90);
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

  // Routing through PhysicalTenantsITHelper both supplies a real config diff (secondary storage =
  // none) so the tenant is discovered and bootstrapped, and declares the per-tenant
  // security.initialization block that PhysicalTenantRequiredOverrideValidation requires once a
  // non-default tenant exists.
  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.none())
          .withTenant(TENANT_A, Storage.none())
          .build();

  private static final ObjectMapper JSON = new ObjectMapper();

  @TestZeebe
  private final TestCluster cluster =
      new TestClusterBuilder()
          .withBrokersCount(BROKERS_COUNT)
          .withReplicationFactor(BROKERS_COUNT)
          .withPartitionsCount(2)
          .withBrokerConfig(
              broker ->
                  TENANTS.configure(
                      broker
                          .withUnauthenticatedAccess()
                          .withProperty(
                              "camunda.security.cluster-admin.basic.users[0].name",
                              CLUSTER_ADMIN_USER)
                          .withProperty(
                              "camunda.security.cluster-admin.basic.users[0].password",
                              CLUSTER_ADMIN_PASSWORD)))
          .build();

  @AutoClose private final HttpClient httpClient = HttpClient.newHttpClient();

  @Test
  void shouldRecoverEveryPhysicalTenantOnEveryBrokerWhenNoTenantIsRequested() throws Exception {
    // given — both physical tenants process commands on their own group, replicated over every
    // broker
    try (final var defaultClient = newClient(PhysicalTenantsITHelper.DEFAULT_TENANT_ID);
        final var tenantAClient = newClient(TENANT_A)) {
      final String defaultProcessId = "multi-broker-default";
      final String tenantAProcessId = "multi-broker-tenanta";
      deployProcess(defaultClient, defaultProcessId);
      deployProcess(tenantAClient, tenantAProcessId);
      assertThat(createInstance(defaultClient, defaultProcessId)).isPositive();
      assertThat(createInstance(tenantAClient, tenantAProcessId)).isPositive();

      // when — the cluster admin moves the whole cluster into recovery, naming no tenant
      final var response = changeMode("RECOVERING", null);
      assertThat(response.statusCode())
          .as("mode change rejected: %s", response.body())
          .isEqualTo(HttpURLConnection.HTTP_OK);

      // then — the plan covers every broker of every group: each is asked to change mode and then
      // awaited, so no broker and no tenant is silently left behind
      final var plannedChanges = plannedChanges(response.body());
      assertThat(plannedChanges)
          .as("the plan names every physical tenant")
          .containsOnlyKeys(GROUPS.toArray(String[]::new));
      assertThat(plannedChanges.values())
          .allSatisfy(
              operations -> {
                assertThat(operations)
                    .as("one ModeChangeOperation and one AwaitModeChangeOperation per broker")
                    .hasSize(2 * BROKERS_COUNT)
                    .allSatisfy(
                        operation ->
                            assertThat(operation.get("mode").asText()).isEqualTo("RECOVERING"));
                assertThat(operations)
                    .filteredOn(
                        operation ->
                            "ModeChangeOperation".equals(operation.get("operation").asText()))
                    .hasSize(BROKERS_COUNT);
                assertThat(operations)
                    .filteredOn(
                        operation ->
                            "AwaitModeChangeOperation".equals(operation.get("operation").asText()))
                    .hasSize(BROKERS_COUNT);
              });

      // and — both tenants really stop processing, on every broker
      awaitCommandsRejected(defaultClient, defaultProcessId);
      awaitCommandsRejected(tenantAClient, tenantAProcessId);

      // and — the cluster-wide transition is reversible for both tenants at once
      awaitModeChangeAccepted("PROCESSING", null);
      awaitCommandsAccepted(defaultClient, defaultProcessId);
      awaitCommandsAccepted(tenantAClient, tenantAProcessId);
    }
  }

  @Test
  void shouldRecoverOneTenantOnEveryBrokerWhileTheOtherKeepsProcessing() throws Exception {
    // given — both physical tenants process commands on their own group
    try (final var defaultClient = newClient(PhysicalTenantsITHelper.DEFAULT_TENANT_ID);
        final var tenantAClient = newClient(TENANT_A)) {
      final String defaultProcessId = "multi-broker-isolated-default";
      final String tenantAProcessId = "multi-broker-isolated-tenanta";
      deployProcess(defaultClient, defaultProcessId);
      deployProcess(tenantAClient, tenantAProcessId);
      assertThat(createInstance(defaultClient, defaultProcessId)).isPositive();
      assertThat(createInstance(tenantAClient, tenantAProcessId)).isPositive();

      // when — only tenant A is moved into recovery
      final var response = changeMode("RECOVERING", TENANT_A);
      assertThat(response.statusCode())
          .as("mode change rejected: %s", response.body())
          .isEqualTo(HttpURLConnection.HTTP_OK);

      // then — the plan stops at tenant A's group: every broker transitions, but only for that
      // tenant
      assertThat(plannedChanges(response.body()))
          .as("one ModeChangeOperation and one AwaitModeChangeOperation per broker, one group only")
          .containsOnlyKeys(TENANT_A)
          .hasEntrySatisfying(
              TENANT_A, operations -> assertThat(operations).hasSize(2 * BROKERS_COUNT));

      // and — the isolation holds across brokers: tenant A stops, the default tenant does not
      awaitCommandsRejected(tenantAClient, tenantAProcessId);
      assertThat(createInstance(defaultClient, defaultProcessId)).isPositive();

      // and — tenant A can be handed back to processing on its own
      awaitModeChangeAccepted("PROCESSING", TENANT_A);
      awaitCommandsAccepted(tenantAClient, tenantAProcessId);
    }
  }

  private HttpResponse<String> changeMode(final String mode, final String physicalTenantId)
      throws Exception {
    final var query =
        physicalTenantId == null
            ? "mode=" + mode
            : "mode=%s&physicalTenantId=%s".formatted(mode, physicalTenantId);
    // The cluster-admin API is cluster-wide, so it is always addressed at the gateway root — never
    // under a /physical-tenants/<id> prefix.
    final URI uri = cluster.availableGateway().restAddress().resolve("cluster/v2/mode?" + query);
    final HttpRequest request =
        HttpRequest.newBuilder(uri)
            .header("Authorization", basicAuth(CLUSTER_ADMIN_USER, CLUSTER_ADMIN_PASSWORD))
            .method("PATCH", BodyPublishers.noBody())
            .build();
    return httpClient.send(request, BodyHandlers.ofString());
  }

  /** The planned operations of the change, keyed by the physical tenant they apply to. */
  private static Map<String, List<JsonNode>> plannedChanges(final String body) throws Exception {
    final var changes = JSON.readTree(body).get("plannedChanges");
    assertThat(changes).as("response carries a plan: %s", body).isNotNull();
    return StreamSupport.stream(changes.spliterator(), false)
        .collect(
            Collectors.toMap(
                change -> change.get("physicalTenantId").asText(),
                change ->
                    StreamSupport.stream(change.get("operations").spliterator(), false).toList()));
  }

  private static String basicAuth(final String user, final String password) {
    return "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes());
  }

  private CamundaClient newClient(final String tenantId) {
    return TENANTS
        .newClientBuilder(cluster.availableGateway(), tenantId)
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
  private void awaitModeChangeAccepted(final String mode, final String physicalTenantId) {
    Awaitility.await("the cluster accepts the mode change")
        .atMost(TRANSITION_TIMEOUT)
        .untilAsserted(
            () -> {
              final var response = changeMode(mode, physicalTenantId);
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
        .atMost(Duration.ofSeconds(60))
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
