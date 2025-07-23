/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.client.api.statistics.response.UsageMetricsStatistics;
import io.camunda.client.impl.statistics.response.UsageMetricsStatisticsImpl;
import io.camunda.client.impl.statistics.response.UsageMetricsStatisticsItemImpl;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@MultiDbTest
// TODO remove once ES/OS is complete
@EnabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
public class UsageMetricsTest {

  public static final OffsetDateTime NOW = OffsetDateTime.now();

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withBasicAuth()
          .withMultiTenancyEnabled()
          .withAuthenticatedAccess()
          .withBrokerConfig(
              brokerBasedProperties ->
                  brokerBasedProperties
                      .getExperimental()
                      .getEngine()
                      .getUsageMetrics()
                      .setExportInterval(Duration.ofSeconds(1)));

  private static final String ADMIN = "admin";
  private static final String USER1 = "user1";
  private static final String TENANT_A = "tenantA";
  private static final String TENANT_B = "tenantB";
  private static final String PROCESS_ID = "service_tasks_v1";

  @UserDefinition
  private static final TestUser ADMIN_USER = new TestUser(ADMIN, "password", List.of());

  @UserDefinition
  private static final TestUser USER1_USER = new TestUser(USER1, "password", List.of());

  private static CamundaClient camundaClient;

  private static void waitForUsageMetrics(
      final OffsetDateTime startTime,
      final OffsetDateTime endTime,
      final Consumer<UsageMetricsStatistics> fnRequirements) {
    Awaitility.await("should export metrics to secondary storage")
        .atMost(Duration.ofSeconds(10))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () ->
                assertThat(camundaClient.newUsageMetricsRequest(startTime, endTime).send().join())
                    .satisfies(fnRequirements));
  }

  @BeforeAll
  static void setup(@Authenticated(ADMIN) final CamundaClient adminClient) {
    createTenant(adminClient, TENANT_A);
    createTenant(adminClient, TENANT_B);
    assignUserToTenant(adminClient, ADMIN, TENANT_A);
    assignUserToTenant(adminClient, ADMIN, TENANT_B);
    assignUserToTenant(adminClient, USER1, TENANT_A);

    deployResource(adminClient, "process/service_tasks_v1.bpmn", TENANT_A);
    startProcessInstance(adminClient, PROCESS_ID, TENANT_A);

    deployResource(adminClient, "process/service_tasks_v1.bpmn", TENANT_B);
    startProcessInstance(adminClient, PROCESS_ID, TENANT_B);

    waitForUsageMetrics(
        NOW.minusDays(1),
        NOW.plusDays(1),
        res -> assertThat(res.getProcessInstances()).isEqualTo(2));
  }

  @Test
  void shouldReturnMetrics() {
    // given
    final var now = OffsetDateTime.now();

    // when
    final var actual =
        camundaClient.newUsageMetricsRequest(now.minusDays(1), now.plusDays(1)).send().join();

    // then
    assertThat(actual).isEqualTo(new UsageMetricsStatisticsImpl(2, 0, 0, 2, null));
  }

  @Test
  void shouldReturnMetricsWithTenants() {
    // given
    final var now = OffsetDateTime.now();

    // when
    final var actual =
        camundaClient
            .newUsageMetricsRequest(now.minusDays(1), now.plusDays(1))
            .withTenants(true)
            .send()
            .join();

    // then
    assertThat(actual)
        .isEqualTo(
            new UsageMetricsStatisticsImpl(
                2,
                0,
                0,
                2,
                Map.of(
                    TENANT_A,
                    new UsageMetricsStatisticsItemImpl(1, 0, 0),
                    TENANT_B,
                    new UsageMetricsStatisticsItemImpl(1, 0, 0))));
  }

  @Test
  void shouldReturnMetricsByTenantId() {
    // given
    final var now = OffsetDateTime.now();

    // when
    final var actual =
        camundaClient
            .newUsageMetricsRequest(now.minusDays(1), now.plusDays(1))
            .tenantId(TENANT_B)
            .withTenants(true)
            .send()
            .join();

    // then
    assertThat(actual)
        .isEqualTo(
            new UsageMetricsStatisticsImpl(
                1, 0, 0, 1, Map.of(TENANT_B, new UsageMetricsStatisticsItemImpl(1, 0, 0))));
  }

  private static DeploymentEvent deployResource(
      final CamundaClient camundaClient, final String resourceName, final String tenantId) {
    return camundaClient
        .newDeployResourceCommand()
        .addResourceFromClasspath(resourceName)
        .tenantId(tenantId)
        .send()
        .join();
  }

  private static void createTenant(final CamundaClient camundaClient, final String tenant) {
    camundaClient.newCreateTenantCommand().tenantId(tenant).name(tenant).send().join();
  }

  private static void assignUserToTenant(
      final CamundaClient camundaClient, final String username, final String tenant) {
    camundaClient.newAssignUserToTenantCommand().username(username).tenantId(tenant).send().join();
  }

  private static void startProcessInstance(
      final CamundaClient camundaClient, final String processId, final String tenant) {
    camundaClient
        .newCreateInstanceCommand()
        .bpmnProcessId(processId)
        .latestVersion()
        .tenantId(tenant)
        .send()
        .join();
  }
}
