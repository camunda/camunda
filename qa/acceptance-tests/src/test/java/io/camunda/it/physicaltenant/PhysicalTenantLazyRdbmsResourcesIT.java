/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.physicaltenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.application.commons.rdbms.PhysicalTenantMyBatisResources;
import io.camunda.application.commons.rdbms.RdbmsDataSources;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestHealthProbe;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Verifies that a node creates a physical tenant's MyBatis and connection pool only once it uses
 * the tenant: the broker that exports one of the tenant's partitions, or the gateway that serves
 * one of its queries.
 */
@Timeout(300)
@ZeebeIntegration
final class PhysicalTenantLazyRdbmsResourcesIT {

  private static final String DEFAULT_TENANT = PhysicalTenantsITHelper.DEFAULT_TENANT_ID;

  private final String tenantA = "lazya" + suffix();
  private final String tenantB = "lazyb" + suffix();
  private final List<String> tenantIds = List.of(DEFAULT_TENANT, tenantA, tenantB);

  private final PhysicalTenantsITHelper tenants =
      PhysicalTenantsITHelper.builder()
          .withTenant(DEFAULT_TENANT, h2("lazy-default"), 1)
          .withTenant(tenantA, h2(tenantA), 1)
          .withTenant(tenantB, h2(tenantB), 1)
          .build();

  @TestZeebe(autoStart = false)
  private final TestCluster cluster =
      TestCluster.builder()
          .withName("pt-lazy-rdbms")
          .withBrokersCount(2)
          .withEmbeddedGateway(true)
          .withPartitionsCount(1)
          .withReplicationFactor(1)
          .useRecordingExporter(false)
          .withBrokerConfig(
              broker ->
                  tenants.configure(
                      broker
                          .withUnauthenticatedAccess()
                          .withUnifiedConfig(
                              config -> {
                                config.getSystem().setCpuThreadCount(1);
                                config.getSystem().setIoThreadCount(1);
                              })))
          .build();

  @Test
  void shouldCreateATenantsResourcesOnlyOnTheBrokerThatExportsIt() {
    // given
    cluster.start().await(TestHealthProbe.READY);

    // when
    awaitEveryTenantCreatedOnExactlyOneBroker();

    // then
    for (final var tenant : tenantIds) {
      assertThat(brokersThatCreated(tenant)).hasSize(1);
      assertThat(brokersWithStartedPool(tenant)).hasSize(1);
    }
  }

  @Test
  void shouldCreateATenantsResourcesOnTheGatewayThatServesItsFirstQuery() {
    // given
    cluster.start().await(TestHealthProbe.READY);
    awaitEveryTenantCreatedOnExactlyOneBroker();
    final var gateway = brokerThatDidNotCreate(tenantA);

    // when
    try (final var client = restClient(gateway, tenantA)) {
      Awaitility.await("a query of " + tenantA + " succeeds on the other gateway")
          .atMost(Duration.ofSeconds(60))
          .ignoreExceptions()
          .untilAsserted(
              () -> assertThat(client.newProcessInstanceSearchRequest().send().join()).isNotNull());
    }

    // then
    assertThat(myBatisOf(gateway).isCreated(tenantA)).isTrue();
    assertThat(brokersThatCreated(tenantA)).hasSize(2);
    assertThat(brokersThatCreated(tenantB)).hasSize(1);
  }

  @Test
  void shouldExportAndQueryEveryTenantThroughAGatewayThatDoesNotExportIt() {
    // given
    cluster.start().await(TestHealthProbe.READY);
    awaitEveryTenantCreatedOnExactlyOneBroker();

    for (final var tenant : tenantIds) {
      final var processId = "lazy-process-" + suffix();
      final var gateway = brokerThatDidNotCreate(tenant);
      try (final var client = restClient(gateway, tenant)) {
        tenants.deploy(
            client,
            Bpmn.createExecutableProcess(processId).startEvent().endEvent().done(),
            processId);

        // when
        final long processInstanceKey =
            client
                .newCreateInstanceCommand()
                .bpmnProcessId(processId)
                .latestVersion()
                .send()
                .join()
                .getProcessInstanceKey();

        // then
        Awaitility.await("the instance of " + tenant + " is exported and found")
            .atMost(Duration.ofSeconds(60))
            .ignoreExceptions()
            .untilAsserted(
                () ->
                    assertThat(
                            client
                                .newProcessInstanceSearchRequest()
                                .filter(f -> f.processInstanceKey(processInstanceKey))
                                .send()
                                .join()
                                .items())
                        .hasSize(1));
      }
    }
  }

  @Test
  void shouldStartAndServeOtherTenantsWhileATenantsDatabaseIsUnreachable() {
    // given
    final var unreachableTenant = "unreachable" + suffix();
    final var withUnreachable =
        PhysicalTenantsITHelper.builder()
            .withTenant(DEFAULT_TENANT, h2("lazy-default"))
            .withTenant(
                unreachableTenant,
                Storage.rdbms("jdbc:postgresql://localhost:1/camunda", "camunda", "camunda"))
            .build();
    try (final var broker =
        withUnreachable
            .configure(new TestStandaloneBroker().withUnauthenticatedAccess())
            .withPtConfig(
                unreachableTenant,
                camunda ->
                    camunda
                        .getData()
                        .getSecondaryStorage()
                        .getRdbms()
                        .getConnectionPool()
                        .setConnectionTimeout(Duration.ofSeconds(1)))) {

      // when
      broker.start().await(TestHealthProbe.READY);

      // then
      try (final var client = restClient(broker, withUnreachable, DEFAULT_TENANT)) {
        assertThat(client.newProcessInstanceSearchRequest().send().join().items()).isEmpty();
      }
      try (final var client = restClient(broker, withUnreachable, unreachableTenant)) {
        assertThatThrownBy(() -> client.newProcessInstanceSearchRequest().send().join())
            .isInstanceOfSatisfying(
                ProblemException.class, problem -> assertThat(problem.code()).isEqualTo(503))
            .hasMessageContaining(unreachableTenant);
      }
    }
  }

  private void awaitEveryTenantCreatedOnExactlyOneBroker() {
    Awaitility.await("every tenant's exporter has created its resources on one broker")
        .atMost(Duration.ofSeconds(60))
        .untilAsserted(
            () -> {
              for (final var tenant : tenantIds) {
                assertThat(brokersThatCreated(tenant)).as(tenant).hasSize(1);
              }
            });
  }

  private List<TestStandaloneBroker> brokersThatCreated(final String tenant) {
    return cluster.brokers().values().stream()
        .filter(broker -> myBatisOf(broker).isCreated(tenant))
        .toList();
  }

  private List<TestStandaloneBroker> brokersWithStartedPool(final String tenant) {
    return cluster.brokers().values().stream()
        .filter(broker -> broker.bean(RdbmsDataSources.class).isPoolStarted(tenant))
        .toList();
  }

  private TestStandaloneBroker brokerThatDidNotCreate(final String tenant) {
    return cluster.brokers().values().stream()
        .filter(broker -> !myBatisOf(broker).isCreated(tenant))
        .findFirst()
        .orElseThrow();
  }

  private CamundaClient restClient(final TestStandaloneBroker gateway, final String tenant) {
    return restClient(gateway, tenants, tenant);
  }

  private static CamundaClient restClient(
      final TestStandaloneBroker gateway,
      final PhysicalTenantsITHelper helper,
      final String tenant) {
    return helper.newClientBuilder(gateway, tenant).preferRestOverGrpc(true).build();
  }

  private static PhysicalTenantMyBatisResources myBatisOf(final TestStandaloneBroker broker) {
    return broker.bean(PhysicalTenantMyBatisResources.class);
  }

  private static Storage h2(final String name) {
    return Storage.rdbms(
        "jdbc:h2:mem:" + name + "-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "sa",
        "");
  }

  private static String suffix() {
    return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
  }
}
