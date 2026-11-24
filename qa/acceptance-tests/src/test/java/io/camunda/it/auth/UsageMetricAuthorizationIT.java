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
import static io.camunda.client.api.search.enums.PermissionType.READ_JOB_METRIC;
import static io.camunda.client.api.search.enums.PermissionType.READ_USAGE_METRIC;
import static io.camunda.client.api.search.enums.PermissionType.UPDATE;
import static io.camunda.client.api.search.enums.ResourceType.PROCESS_DEFINITION;
import static io.camunda.client.api.search.enums.ResourceType.RESOURCE;
import static io.camunda.client.api.search.enums.ResourceType.SYSTEM;
import static io.camunda.client.api.search.enums.ResourceType.TENANT;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.client.api.search.response.TenantUser;
import io.camunda.client.api.statistics.response.UsageMetricsStatistics;
import io.camunda.client.impl.statistics.response.UsageMetricsStatisticsImpl;
import io.camunda.client.impl.statistics.response.UsageMetricsStatisticsItemImpl;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.TenantDefinition;
import io.camunda.qa.util.auth.TestTenant;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.multidb.CamundaMultiDBExtension;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@MultiDbTest
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
          .withUnifiedConfig(
              cfg ->
                  cfg.getMonitoring()
                      .getMetrics()
                      .getUsageMetrics()
                      .setExportInterval(EXPORT_INTERVAL));

  private static final String TENANT_A = "tenantA";
  private static final String TENANT_B = "tenantB";
  private static final String PROCESS_ID = "service_tasks_v1";
  private static final String PROCESS_ID_WITH_USER_TASK = "PROCESS_WITH_USER_TASK_PRE_ASSIGNED";
  private static final String ADMIN = "admin";
  private static final String RESTRICTED = "restrictedUser";
  private static final String UNAUTHORIZED = "unauthorizedUser";
  private static final String TENANT_MEMBER_WITHOUT_PERMISSION = "tenantMemberWithoutPermission";

  @UserDefinition
  private static final TestUser ADMIN_USER =
      new TestUser(
          ADMIN,
          PASSWORD,
          List.of(
              new Permissions(RESOURCE, CREATE, List.of("*")),
              new Permissions(PROCESS_DEFINITION, CREATE_PROCESS_INSTANCE, List.of("*")),
              new Permissions(SYSTEM, READ_USAGE_METRIC, List.of("*")),
              new Permissions(SYSTEM, READ_JOB_METRIC, List.of("*")),
              new Permissions(TENANT, CREATE, List.of("*")),
              new Permissions(TENANT, UPDATE, List.of("*")),
              new Permissions(TENANT, READ, List.of("*"))));

  @UserDefinition
  private static final TestUser RESTRICTED_USER =
      new TestUser(
          RESTRICTED,
          PASSWORD,
          List.of(
              new Permissions(SYSTEM, READ_USAGE_METRIC, List.of("*")),
              new Permissions(SYSTEM, READ_JOB_METRIC, List.of("*"))));

  @UserDefinition
  private static final TestUser UNAUTHORIZED_USER = new TestUser(UNAUTHORIZED, PASSWORD, List.of());

  @UserDefinition
  private static final TestUser TENANT_MEMBER_WITHOUT_PERMISSION_USER =
      new TestUser(TENANT_MEMBER_WITHOUT_PERMISSION, PASSWORD, List.of());

  @TenantDefinition
  private static final TestTenant A_TENANT =
      new TestTenant(TENANT_A)
          .setName(TENANT_A)
          .addUsers(ADMIN, RESTRICTED, TENANT_MEMBER_WITHOUT_PERMISSION);

  @TenantDefinition
  private static final TestTenant B_TENANT =
      new TestTenant(TENANT_B).setName(TENANT_B).addUsers(ADMIN, TENANT_MEMBER_WITHOUT_PERMISSION);

  @Authenticated(ADMIN)
  private static CamundaClient adminClient;

  @Authenticated(RESTRICTED)
  private static CamundaClient restrictedClient;

  @Authenticated(UNAUTHORIZED)
  private static CamundaClient unauthorizedClient;

  @Authenticated(TENANT_MEMBER_WITHOUT_PERMISSION)
  private static CamundaClient tenantMemberWithoutPermissionClient;

  private static void waitForUsageMetrics(
      final CamundaClient camundaClient, final Consumer<UsageMetricsStatistics> fnRequirements) {
    Awaitility.await("should export metrics to secondary storage")
        .atMost(CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () ->
                assertThat(
                        camundaClient
                            .newUsageMetricsRequest(NOW_MINUS_1D, NOW_PLUS_1D)
                            .withTenants(true)
                            .send()
                            .join())
                    .satisfies(fnRequirements));
  }

  // Tenant assignments are exported asynchronously to secondary storage and used at request time
  // to resolve a user's authenticated tenants. Without this wait, basic-auth requests can race
  // and see an empty tenant list, causing tenant-scoped queries to return 0 results.
  private static void waitForUsersAssignedToTenant(
      final CamundaClient camundaClient, final String tenant, final String... usernames) {
    Awaitility.await("users " + Arrays.asList(usernames) + " assigned to tenant " + tenant)
        .atMost(CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(
                        camundaClient
                            .newUsersByTenantSearchRequest(tenant)
                            .send()
                            .join()
                            .items()
                            .stream()
                            .map(TenantUser::getUsername))
                    .contains(usernames));
  }

  @BeforeAll
  static void setUp(@Authenticated(ADMIN) final CamundaClient adminClient) {
    waitForUsersAssignedToTenant(
        adminClient, TENANT_A, ADMIN, RESTRICTED, TENANT_MEMBER_WITHOUT_PERMISSION);
    waitForUsersAssignedToTenant(adminClient, TENANT_B, ADMIN, TENANT_MEMBER_WITHOUT_PERMISSION);

    // Create PIs & wait for metrics to be exported
    deployResource(adminClient, "process/service_tasks_v1.bpmn", TENANT_A);
    deployResource(adminClient, "process/service_tasks_v1.bpmn", TENANT_B);
    deployResource(adminClient, "process/process_with_assigned_user_task.bpmn", TENANT_A);
    deployResource(adminClient, "process/process_with_assigned_user_task.bpmn", TENANT_B);
    startProcessInstance(adminClient, PROCESS_ID, TENANT_A);
    startProcessInstance(adminClient, PROCESS_ID, TENANT_B);
    startProcessInstance(adminClient, PROCESS_ID_WITH_USER_TASK, TENANT_A);
    startProcessInstance(adminClient, PROCESS_ID_WITH_USER_TASK, TENANT_B);
    waitForUsageMetrics(
        adminClient,
        res -> {
          // both tenants share the assignee, so the overall assignee count reaches 1 as soon as
          // the first tenant's metric is exported; wait for each tenant instead
          assertThat(res.getProcessInstances()).isEqualTo(4);
          assertThat(res.getTenants()).containsOnlyKeys(TENANT_A, TENANT_B);
          assertThat(res.getTenants().values())
              .allSatisfy(
                  tenant -> {
                    assertThat(tenant.getProcessInstances()).isEqualTo(2);
                    assertThat(tenant.getAssignees()).isEqualTo(1);
                  });
        });
  }

  @ParameterizedTest
  @MethodSource("provideParameters")
  void searchShouldReturnAssignedAndAuthorizedTenantMetricsOnly(final TestCase testCase) {
    // when
    final var actual =
        testCase
            .client()
            .newUsageMetricsRequest(NOW_MINUS_1D, NOW_PLUS_1D)
            .withTenants(true)
            .send()
            .join();

    // then
    final UsageMetricsStatistics expected = testCase.expected();
    assertThat(actual.getProcessInstances()).isEqualTo(expected.getProcessInstances());
    assertThat(actual.getDecisionInstances()).isEqualTo(expected.getDecisionInstances());
    assertThat(actual.getAssignees()).isEqualTo(expected.getAssignees());
    assertThat(actual.getActiveTenants()).isEqualTo(expected.getActiveTenants());
    assertThat(actual.getTenants()).containsExactlyInAnyOrderEntriesOf(expected.getTenants());
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

  public static Stream<TestCase> provideParameters() {
    return Stream.of(
        new TestCase(
            adminClient,
            new UsageMetricsStatisticsImpl(
                4,
                0,
                1,
                2,
                Map.of(
                    TENANT_A,
                    new UsageMetricsStatisticsItemImpl(2, 0, 1),
                    TENANT_B,
                    new UsageMetricsStatisticsItemImpl(2, 0, 1)))),
        new TestCase(
            restrictedClient,
            new UsageMetricsStatisticsImpl(
                2, 0, 1, 1, Map.of(TENANT_A, new UsageMetricsStatisticsItemImpl(2, 0, 1)))),
        new TestCase(unauthorizedClient, new UsageMetricsStatisticsImpl(0, 0, 0, 0, Map.of())),
        new TestCase(
            tenantMemberWithoutPermissionClient,
            new UsageMetricsStatisticsImpl(0, 0, 0, 0, Map.of())));
  }

  private record TestCase(CamundaClient client, UsageMetricsStatistics expected) {}
}
