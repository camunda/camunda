/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.physicaltenant;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.it.cluster.backup.InProcessRestoreTestUtil;
import io.camunda.zeebe.management.cluster.BrokerState;
import io.camunda.zeebe.management.cluster.PartitionState;
import io.camunda.zeebe.management.cluster.PartitionStateCode;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

/**
 * A broker that restarts while one of its physical tenants is in recovery must leave that tenant's
 * RDBMS schema alone, without holding up the tenant that is not recovering.
 *
 * <p>Applying the schema mid-recovery would recreate tables in the database the operator is about
 * to restore into; the restore applies it itself, once. The recovering tenant's database is emptied
 * while the broker is down, so any schema it has after startup was created by that startup.
 *
 * <p>Both tenants use in-memory H2 databases with {@code DB_CLOSE_DELAY=-1} and a URL fixed for the
 * test instance, so each survives the broker restart and can be reached directly from the test.
 */
@ZeebeIntegration
final class PhysicalTenantRdbmsRecoveryStartupIT {

  private static final String DEFAULT_TENANT = PhysicalTenantsITHelper.DEFAULT_TENANT_ID;
  private static final String RECOVERING_TENANT = "tenanta";
  private static final Duration TRANSITION_TIMEOUT = Duration.ofSeconds(60);

  private final String defaultTenantUrl = h2Url(DEFAULT_TENANT);
  private final String recoveringTenantUrl = h2Url(RECOVERING_TENANT);

  private final PhysicalTenantsITHelper tenants =
      PhysicalTenantsITHelper.builder()
          .withTenant(DEFAULT_TENANT, Storage.rdbms(defaultTenantUrl, "sa", ""))
          .withTenant(RECOVERING_TENANT, Storage.rdbms(recoveringTenantUrl, "sa", ""))
          .build();

  @TestZeebe
  private final TestStandaloneBroker broker =
      tenants.configure(new TestStandaloneBroker().withUnauthenticatedAccess());

  @Test
  void shouldNotInitializeARecoveringTenantsSchemaOnStartupWhileTheOtherTenantIsServed()
      throws Exception {
    // given - both tenants start in processing mode, so both schemas are applied; on a multi-tenant
    // node that happens per tenant in the background, so readiness does not imply both are done
    Awaitility.await("both tenants' schemas are applied")
        .atMost(TRANSITION_TIMEOUT)
        .untilAsserted(
            () -> {
              assertThat(deployedResourceTableExists(defaultTenantUrl)).isTrue();
              assertThat(deployedResourceTableExists(recoveringTenantUrl)).isTrue();
            });

    // and - one tenant enters recovery, which the broker persists in its cluster configuration
    enterRecovery(RECOVERING_TENANT);

    // and - the broker is down while the recovering tenant's database is emptied
    broker.stop();
    dropAllObjects(recoveringTenantUrl);
    assertThat(deployedResourceTableExists(recoveringTenantUrl)).isFalse();

    // when - the broker starts again with that tenant still recovering
    broker.start();
    broker.awaitCompleteTopology();

    // then - the node is healthy, and each tenant is in the mode it was left in
    broker.healthActuator().ready();
    broker.healthActuator().live();
    assertThat(broker.bean(BrokerTopologyManager.class).isRecovering(RECOVERING_TENANT))
        .as("the tenant is still recovering after the restart")
        .isTrue();
    awaitPartitionsOf(RECOVERING_TENANT, PartitionStateCode.RECOVERING);
    awaitPartitionsOf(DEFAULT_TENANT, PartitionStateCode.ACTIVE);

    // and - the tenant that is not recovering keeps its schema and serves commands
    assertThat(deployedResourceTableExists(defaultTenantUrl)).isTrue();
    try (final var client = tenants.newClientBuilder(broker, DEFAULT_TENANT).build()) {
      awaitCommandsAccepted(client);
    }

    // and - the recovering tenant's schema was left for the restore to apply
    Awaitility.await("the recovering tenant's schema stays untouched")
        .during(Duration.ofSeconds(3))
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> assertThat(deployedResourceTableExists(recoveringTenantUrl)).isFalse());
  }

  @Test
  void shouldReportDegradedReadinessUntilEveryTenantsSchemaIsInitialized() throws Exception {
    // given - both tenants' schemas are applied, then both tenants enter recovery
    Awaitility.await("both tenants' schemas are applied")
        .atMost(TRANSITION_TIMEOUT)
        .untilAsserted(
            () -> {
              assertThat(deployedResourceTableExists(defaultTenantUrl)).isTrue();
              assertThat(deployedResourceTableExists(recoveringTenantUrl)).isTrue();
            });
    enterRecovery(RECOVERING_TENANT);
    enterRecovery(DEFAULT_TENANT);

    // and - the broker is down while both tenants' databases are emptied
    broker.stop();
    dropAllObjects(defaultTenantUrl);
    dropAllObjects(recoveringTenantUrl);

    // when - the broker starts again with every tenant still recovering
    broker.start();

    // then - the node stays routable, but DEGRADED
    broker.healthActuator().live();
    Awaitility.await("the node reports DEGRADED readiness while every tenant is recovering")
        .atMost(TRANSITION_TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var readiness = readiness();
              assertThat(readiness.statusCode()).isEqualTo(200);
              assertThat(readinessStatus(readiness)).isEqualTo("DEGRADED");
            });
    assertThat(deployedResourceTableExists(defaultTenantUrl)).isFalse();
    assertThat(deployedResourceTableExists(recoveringTenantUrl)).isFalse();

    // when - one tenant leaves recovery without a restore
    try (final var client = tenants.newClientBuilder(broker, DEFAULT_TENANT).build()) {
      InProcessRestoreTestUtil.changeMode(client, DEFAULT_TENANT, "PROCESSING", false);
    }

    // then - its schema is applied, but the node stays DEGRADED
    Awaitility.await("the tenant that left recovery has its schema applied")
        .atMost(TRANSITION_TIMEOUT)
        .untilAsserted(() -> assertThat(deployedResourceTableExists(defaultTenantUrl)).isTrue());
    assertThat(readinessStatus(readiness())).isEqualTo("DEGRADED");
    assertThat(deployedResourceTableExists(recoveringTenantUrl)).isFalse();

    // when - the other tenant leaves recovery as well
    try (final var client = tenants.newClientBuilder(broker, RECOVERING_TENANT).build()) {
      InProcessRestoreTestUtil.changeMode(client, RECOVERING_TENANT, "PROCESSING", false);
    }

    // then
    Awaitility.await("the node reports UP once every tenant's schema is initialized")
        .atMost(TRANSITION_TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(() -> assertThat(readinessStatus(readiness())).isEqualTo("UP"));
    assertThat(deployedResourceTableExists(recoveringTenantUrl)).isTrue();
  }

  private static String readinessStatus(final HttpResponse<String> readiness) throws Exception {
    return new ObjectMapper().readTree(readiness.body()).path("status").asText();
  }

  private HttpResponse<String> readiness() throws Exception {
    try (final var httpClient = HttpClient.newHttpClient()) {
      return httpClient.send(
          HttpRequest.newBuilder(broker.actuatorUri("health", "readiness")).GET().build(),
          HttpResponse.BodyHandlers.ofString());
    }
  }

  private void enterRecovery(final String physicalTenantId) {
    try (final var client = tenants.newClientBuilder(broker, physicalTenantId).build()) {
      InProcessRestoreTestUtil.changeMode(client, physicalTenantId, "RECOVERING", false);
    }
    awaitPartitionsOf(physicalTenantId, PartitionStateCode.RECOVERING);
  }

  private void awaitPartitionsOf(final String physicalTenantId, final PartitionStateCode state) {
    final var actuator = ClusterActuator.of(broker);
    Awaitility.await("partitions of " + physicalTenantId + " are " + state)
        .atMost(TRANSITION_TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(actuator.getTopology(physicalTenantId).getBrokers())
                    .flatExtracting(BrokerState::getPartitions)
                    .isNotEmpty()
                    .extracting(PartitionState::getState)
                    .containsOnly(state));
  }

  private static void awaitCommandsAccepted(final CamundaClient client) {
    final var processId = "recovery-startup-" + UUID.randomUUID();
    Awaitility.await("the tenant that is not recovering accepts commands")
        .atMost(TRANSITION_TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(
                        client
                            .newDeployResourceCommand()
                            .addProcessModel(
                                Bpmn.createExecutableProcess(processId)
                                    .startEvent()
                                    .endEvent()
                                    .done(),
                                processId + ".bpmn")
                            .send()
                            .join()
                            .getProcesses())
                    .isNotEmpty());
  }

  private static String h2Url(final String physicalTenantId) {
    return "jdbc:h2:mem:pt-recovery-startup-"
        + physicalTenantId
        + "-"
        + UUID.randomUUID()
        + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL";
  }

  private static void dropAllObjects(final String url) throws SQLException {
    try (final var connection = DriverManager.getConnection(url, "sa", "");
        final var statement = connection.createStatement()) {
      statement.execute("DROP ALL OBJECTS");
    }
  }

  private static boolean deployedResourceTableExists(final String url) throws SQLException {
    try (final Connection connection = DriverManager.getConnection(url, "sa", "");
        final var tables =
            connection
                .getMetaData()
                .getTables(null, "PUBLIC", "DEPLOYED_RESOURCE", new String[] {"TABLE"})) {
      return tables.next();
    }
  }
}
