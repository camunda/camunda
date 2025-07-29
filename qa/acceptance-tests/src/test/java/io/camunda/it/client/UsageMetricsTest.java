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
import io.camunda.client.api.response.EvaluateDecisionResponse;
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

@MultiDbTest
public class UsageMetricsTest {

  public static final OffsetDateTime NOW = OffsetDateTime.now();

  public static final Duration EXPORT_INTERVAL = Duration.ofSeconds(2);

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
                      .setExportInterval(EXPORT_INTERVAL));

  private static final String ADMIN = "admin";
  private static final String TENANT_A = "tenantA";
  private static final String TENANT_B = "tenantB";
  private static final String PROCESS_ID = "service_tasks_v1";

  @UserDefinition
  private static final TestUser ADMIN_USER = new TestUser(ADMIN, "password", List.of());

  private static OffsetDateTime exportedTime;

  private static void waitForUsageMetrics(
      final CamundaClient camundaClient,
      final OffsetDateTime startTime,
      final OffsetDateTime endTime,
      final Consumer<UsageMetricsStatistics> fnRequirements) {
    Awaitility.await("should export metrics to secondary storage")
        .atMost(EXPORT_INTERVAL.multipliedBy(2))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () ->
                assertThat(camundaClient.newUsageMetricsRequest(startTime, endTime).send().join())
                    .satisfies(fnRequirements));
  }

  @BeforeAll
  static void setup(@Authenticated(ADMIN) final CamundaClient adminClient)
      throws InterruptedException {
    createTenant(adminClient, TENANT_A);
    createTenant(adminClient, TENANT_B);
    assignUserToTenant(adminClient, ADMIN, TENANT_A);
    assignUserToTenant(adminClient, ADMIN, TENANT_B);

    // Create PI & wait for metrics to be exported
    deployResource(adminClient, "process/service_tasks_v1.bpmn", TENANT_A);
    startProcessInstance(adminClient, PROCESS_ID, TENANT_A);
    waitForUsageMetrics(
        adminClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        res -> assertThat(res.getProcessInstances()).isEqualTo(1));

    // Store first export time & wait 2 export intervals
    exportedTime = OffsetDateTime.now();
    Thread.sleep(2 * EXPORT_INTERVAL.toMillis());

    // Create PI for TENANT_B
    deployResource(adminClient, "process/service_tasks_v1.bpmn", TENANT_B);
    startProcessInstance(adminClient, PROCESS_ID, TENANT_B);

    // Deploy a decision model for TENANT_A and evaluate it 2 times
    deployResource(adminClient, "decisions/decision_model.dmn", TENANT_A);
    evaluateDecision(adminClient, "decision_1", Map.of("age", 20, "income", 25000), TENANT_A);
    evaluateDecision(adminClient, "decision_1", Map.of("age", 40, "income", 3000), TENANT_A);

    // Deploy another decision model for TENANT_B and evaluate it
    deployResource(adminClient, "decisions/decision_model_1.dmn", TENANT_B);
    evaluateDecision(adminClient, "test_qa", Map.of("input1", "B"), TENANT_B);

    // Wait for rPI and eDI metrics to be exported
    waitForUsageMetrics(
        adminClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        res -> {
          assertThat(res.getProcessInstances()).isEqualTo(2);
          assertThat(res.getDecisionInstances()).isEqualTo(3);
        });
  }

  @Test
  void shouldReturnMetrics(@Authenticated(ADMIN) final CamundaClient adminClient) {
    // given
    final var now = OffsetDateTime.now();

    // when
    final var actual =
        adminClient.newUsageMetricsRequest(now.minusDays(1), now.plusDays(1)).send().join();

    // then
    assertThat(actual).isEqualTo(new UsageMetricsStatisticsImpl(2, 3, 0, 2, Map.of()));
  }

  @Test
  void shouldReturnMetricsWithTenants(@Authenticated(ADMIN) final CamundaClient adminClient) {
    // given
    final var now = OffsetDateTime.now();

    // when
    final var actual =
        adminClient
            .newUsageMetricsRequest(now.minusDays(1), now.plusDays(1))
            .withTenants(true)
            .send()
            .join();

    // then
    assertThat(actual)
        .isEqualTo(
            new UsageMetricsStatisticsImpl(
                2,
                3,
                0,
                2,
                Map.of(
                    TENANT_A,
                    new UsageMetricsStatisticsItemImpl(1, 2, 0),
                    TENANT_B,
                    new UsageMetricsStatisticsItemImpl(1, 1, 0))));
  }

  @Test
  void shouldReturnMetricsWithinInterval(@Authenticated(ADMIN) final CamundaClient adminClient) {
    // given
    final var now = OffsetDateTime.now();

    // when
    final var actual =
        adminClient
            .newUsageMetricsRequest(now.minusDays(1), exportedTime)
            .withTenants(true)
            .send()
            .join();

    // then
    assertThat(actual)
        .isEqualTo(
            new UsageMetricsStatisticsImpl(
                1, 0, 0, 1, Map.of(TENANT_A, new UsageMetricsStatisticsItemImpl(1, 0, 0))));
  }

  @Test
  void shouldReturnMetricsByTenantId(@Authenticated(ADMIN) final CamundaClient adminClient) {
    // given
    final var now = OffsetDateTime.now();

    // when
    final var actual =
        adminClient
            .newUsageMetricsRequest(now.minusDays(1), now.plusDays(1))
            .tenantId(TENANT_B)
            .withTenants(true)
            .send()
            .join();

    // then
    assertThat(actual)
        .isEqualTo(
            new UsageMetricsStatisticsImpl(
                1, 1, 0, 1, Map.of(TENANT_B, new UsageMetricsStatisticsItemImpl(1, 1, 0))));
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

  private static EvaluateDecisionResponse evaluateDecision(
      final CamundaClient camundaClient,
      final String decisionId,
      final Map<String, Object> variables,
      final String tenantId) {
    return camundaClient
        .newEvaluateDecisionCommand()
        .decisionId(decisionId)
        .tenantId(tenantId)
        .variables(variables)
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
