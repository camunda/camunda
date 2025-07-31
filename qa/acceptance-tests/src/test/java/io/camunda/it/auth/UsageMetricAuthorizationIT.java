/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static io.camunda.client.api.search.enums.PermissionType.CREATE;
import static io.camunda.client.api.search.enums.PermissionType.CREATE_PROCESS_INSTANCE;
import static io.camunda.client.api.search.enums.PermissionType.READ;
import static io.camunda.client.api.search.enums.PermissionType.UPDATE;
import static io.camunda.client.api.search.enums.ResourceType.PROCESS_DEFINITION;
import static io.camunda.client.api.search.enums.ResourceType.RESOURCE;
import static io.camunda.client.api.search.enums.ResourceType.TENANT;
import static io.camunda.client.api.search.enums.ResourceType.USAGE_METRIC;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.client.api.statistics.response.UsageMetricsStatistics;
import io.camunda.client.impl.basicauth.BasicAuthCredentialsProviderBuilder;
import io.camunda.client.impl.statistics.response.UsageMetricsStatisticsImpl;
import io.camunda.client.impl.statistics.response.UsageMetricsStatisticsItemImpl;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.Permissions;
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
import java.util.stream.Stream;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class UsageMetricAuthorizationIT {

  public static final OffsetDateTime NOW_PLUS_1D = OffsetDateTime.now().plusDays(1);
  public static final OffsetDateTime NOW_MINUS_1D = OffsetDateTime.now().minusDays(1);
  public static final String PASSWORD = "password";
  public static final Duration EXPORT_INTERVAL = Duration.ofSeconds(2);

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withBasicAuth()
          .withMultiTenancyEnabled()
          .withAuthorizationsEnabled()
          .withBrokerConfig(
              brokerBasedProperties ->
                  brokerBasedProperties
                      .getExperimental()
                      .getEngine()
                      .getUsageMetrics()
                      .setExportInterval(EXPORT_INTERVAL));

  private static final String TENANT_A = "tenantA";
  private static final String TENANT_B = "tenantB";
  private static final String PROCESS_ID = "service_tasks_v1";
  private static final String ADMIN = "admin";
  private static final String RESTRICTED = "restrictedUser";
  private static final String UNAUTHORIZED = "unauthorizedUser";

  @UserDefinition
  private static final TestUser ADMIN_USER =
      new TestUser(
          ADMIN,
          PASSWORD,
          List.of(
              new Permissions(RESOURCE, CREATE, List.of("*")),
              new Permissions(PROCESS_DEFINITION, CREATE_PROCESS_INSTANCE, List.of("*")),
              new Permissions(USAGE_METRIC, READ, List.of("*")),
              new Permissions(TENANT, CREATE, List.of("*")),
              new Permissions(TENANT, UPDATE, List.of("*")),
              new Permissions(TENANT, READ, List.of("*"))));

  @UserDefinition
  private static final TestUser RESTRICTED_USER =
      new TestUser(
          RESTRICTED, PASSWORD, List.of(new Permissions(USAGE_METRIC, READ, List.of("*"))));

  @UserDefinition
  private static final TestUser UNAUTHORIZED_USER = new TestUser(UNAUTHORIZED, PASSWORD, List.of());

  private static void waitForUsageMetrics(
      final CamundaClient camundaClient, final Consumer<UsageMetricsStatistics> fnRequirements) {
    Awaitility.await("should export metrics to secondary storage")
        .atMost(EXPORT_INTERVAL.multipliedBy(2))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () ->
                assertThat(
                        camundaClient
                            .newUsageMetricsRequest(NOW_MINUS_1D, NOW_PLUS_1D)
                            .send()
                            .join())
                    .satisfies(fnRequirements));
  }

  @BeforeAll
  static void setUp(@Authenticated(ADMIN) final CamundaClient adminClient) {
    createTenant(adminClient, TENANT_A);
    createTenant(adminClient, TENANT_B);
    assignUserToTenant(adminClient, ADMIN, TENANT_A);
    assignUserToTenant(adminClient, ADMIN, TENANT_B);
    assignUserToTenant(adminClient, RESTRICTED, TENANT_A);

    // Create PIs & wait for metrics to be exported
    deployResource(adminClient, "process/service_tasks_v1.bpmn", TENANT_A);
    deployResource(adminClient, "process/service_tasks_v1.bpmn", TENANT_B);
    startProcessInstance(adminClient, PROCESS_ID, TENANT_A);
    startProcessInstance(adminClient, PROCESS_ID, TENANT_B);
    waitForUsageMetrics(adminClient, res -> assertThat(res.getProcessInstances()).isEqualTo(2));
  }

  @ParameterizedTest
  @MethodSource("provideParameters")
  void searchShouldReturnAssignedAndAuthorizedTenantMetricsOnly(
      final TestUser testUser, final UsageMetricsStatisticsImpl expected) {
    // given

    try (final CamundaClient client = createClient(testUser)) {
      // when
      final var actual =
          client.newUsageMetricsRequest(NOW_MINUS_1D, NOW_PLUS_1D).withTenants(true).send().join();

      // then
      assertThat(actual.getProcessInstances()).isEqualTo(expected.getProcessInstances());
      assertThat(actual.getDecisionInstances()).isEqualTo(expected.getDecisionInstances());
      assertThat(actual.getAssignees()).isEqualTo(expected.getAssignees());
      assertThat(actual.getActiveTenants()).isEqualTo(expected.getActiveTenants());
      assertThat(actual.getTenants()).containsExactlyInAnyOrderEntriesOf(expected.getTenants());
    }
  }

  private static CamundaClient createClient(final TestUser user) {
    return BROKER
        .newClientBuilder()
        .preferRestOverGrpc(true)
        .credentialsProvider(
            new BasicAuthCredentialsProviderBuilder()
                .username(user.username())
                .password(user.password())
                .build())
        .build();
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

  public static Stream<Arguments> provideParameters() {
    return Stream.of(
        Arguments.of(
            ADMIN_USER,
            new UsageMetricsStatisticsImpl(
                2,
                0,
                0,
                2,
                Map.of(
                    TENANT_A,
                    new UsageMetricsStatisticsItemImpl(1, 0, 0),
                    TENANT_B,
                    new UsageMetricsStatisticsItemImpl(1, 0, 0)))),
        Arguments.of(
            RESTRICTED_USER,
            new UsageMetricsStatisticsImpl(
                1, 0, 0, 1, Map.of(TENANT_A, new UsageMetricsStatisticsItemImpl(1, 0, 0)))),
        Arguments.of(UNAUTHORIZED_USER, new UsageMetricsStatisticsImpl(0, 0, 0, 0, Map.of())));
  }
}
