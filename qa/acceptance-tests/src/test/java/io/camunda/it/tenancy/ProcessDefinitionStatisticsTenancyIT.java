/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.tenancy;

import static io.camunda.it.util.TestHelper.createTenant;
import static io.camunda.it.util.TestHelper.waitForMessageSubscriptions;
import static io.camunda.it.util.TestHelper.waitForProcessInstances;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.enums.MessageSubscriptionState;
import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.security.configuration.InitializationConfiguration;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

/**
 * Multi-tenant tests for process definition message subscription statistics. Verifies that users
 * only see statistics for tenants they have access to.
 */
@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class ProcessDefinitionStatisticsTenancyIT {

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withBasicAuth()
          .withMultiTenancyEnabled()
          .withAuthenticatedAccess();

  private static final String TENANT_1 = "tenant1";
  private static final String TENANT_2 = "tenant2";
  private static final String DEFAULT_TENANT = "<default>";

  private static final String USER_TENANT_1 = "user_tenant1";
  private static final String USER_TENANT_2 = "user_tenant2";
  private static final String USER_ALL_TENANTS = "user_all";

  private static final String PROCESS_WITH_SUBSCRIPTIONS = "process_with_subscriptions";

  // Process definition keys for each tenant
  private static long processDefKeyDefaultTenant;
  private static long processDefKeyTenant1Version1;
  private static long processDefKeyTenant1Version2;
  private static long processDefKeyTenant2;

  @UserDefinition
  private static final TestUser USER_1 = new TestUser(USER_TENANT_1, "password", List.of());

  @UserDefinition
  private static final TestUser USER_2 = new TestUser(USER_TENANT_2, "password", List.of());

  @UserDefinition
  private static final TestUser USER_ALL = new TestUser(USER_ALL_TENANTS, "password", List.of());

  @BeforeAll
  public static void beforeAll(@Authenticated final CamundaClient adminClient) {
    // Create tenants and assign users
    createTenant(
        adminClient,
        TENANT_1,
        "Tenant 1",
        InitializationConfiguration.DEFAULT_USER_USERNAME,
        USER_TENANT_1,
        USER_ALL_TENANTS);
    createTenant(
        adminClient,
        TENANT_2,
        "Tenant 2",
        InitializationConfiguration.DEFAULT_USER_USERNAME,
        USER_TENANT_2,
        USER_ALL_TENANTS);

    // Deploy process with message subscriptions to each tenant
    processDefKeyDefaultTenant = deployProcessWithSubscriptions(adminClient, DEFAULT_TENANT);
    processDefKeyTenant1Version1 = deployProcessWithSubscriptions(adminClient, TENANT_1);
    processDefKeyTenant1Version2 =
        deployProcessWithSubscriptionsWithVersion(adminClient, TENANT_1, "v2");
    processDefKeyTenant2 = deployProcessWithSubscriptions(adminClient, TENANT_2);

    // Create 3 instances in default tenant = 6 subscriptions (2 per instance)
    createInstance(adminClient, processDefKeyDefaultTenant, null, Map.of("key1", "A", "key2", "B"));
    createInstance(adminClient, processDefKeyDefaultTenant, null, Map.of("key1", "C", "key2", "D"));
    createInstance(adminClient, processDefKeyDefaultTenant, null, Map.of("key1", "E", "key2", "F"));

    // Create 2 instances in tenant1 [version 1] = 4 subscriptions
    createInstance(
        adminClient, processDefKeyTenant1Version1, TENANT_1, Map.of("key1", "G", "key2", "H"));
    createInstance(
        adminClient, processDefKeyTenant1Version1, TENANT_1, Map.of("key1", "I", "key2", "J"));
    // Create 2 instances in tenant1 [version 2] = 2 subscriptions
    createInstance(
        adminClient, processDefKeyTenant1Version2, TENANT_1, Map.of("key1", "X", "key2", "Y"));

    // Create 1 instance in tenant2 = 2 subscriptions
    createInstance(adminClient, processDefKeyTenant2, TENANT_2, Map.of("key1", "K", "key2", "L"));

    // Wait for all process instances
    waitForProcessInstances(
        adminClient,
        f ->
            f.processDefinitionKey(
                    f2 ->
                        f2.in(
                            processDefKeyDefaultTenant,
                            processDefKeyTenant1Version1,
                            processDefKeyTenant1Version2,
                            processDefKeyTenant2))
                .state(ProcessInstanceState.ACTIVE),
        7);

    // Wait for message subscriptions to be created
    waitForMessageSubscriptions(
        adminClient, f -> f.messageSubscriptionState(MessageSubscriptionState.CREATED), 14);
  }

  @Test
  void shouldGetSubscriptionStatisticsOnlyForTenant1WhenUserHasAccessToTenant1(
      @Authenticated(USER_TENANT_1) final CamundaClient client) {
    // when - user with access only to tenant1 requests subscription statistics
    final var actual =
        client.newProcessDefinitionMessageSubscriptionStatisticsRequest().send().join();

    // then - should see subscription statistics for tenant1
    assertThat(actual.items()).hasSize(2);
    final var statsVersion1 = actual.items().getFirst();
    assertThat(statsVersion1.getProcessDefinitionKey())
        .isEqualTo(String.valueOf(processDefKeyTenant1Version1));
    assertThat(statsVersion1.getActiveSubscriptions())
        .isEqualTo(4L); // 2 instances * 2 subscriptions each
    assertThat(statsVersion1.getProcessInstancesWithActiveSubscriptions()).isEqualTo(2L);
    final var statsVersion2 = actual.items().getLast();
    assertThat(statsVersion2.getProcessDefinitionKey())
        .isEqualTo(String.valueOf(processDefKeyTenant1Version2));
    assertThat(statsVersion2.getActiveSubscriptions())
        .isEqualTo(2L); // 1 instance * 2 subscriptions
    assertThat(statsVersion2.getProcessInstancesWithActiveSubscriptions()).isEqualTo(1L);
  }

  @Test
  void shouldGetEmptyResultWhenUserRequestsOtherTenantProcess(
      @Authenticated(USER_TENANT_1) final CamundaClient client) {
    // when - user with access only to tenant1 requests statistics for tenant2 process
    final var response =
        client
            .newProcessDefinitionMessageSubscriptionStatisticsRequest()
            .filter(f -> f.processDefinitionKey(processDefKeyTenant2))
            .send()
            .join();

    // then - should get empty result as user cannot access tenant2's process definition
    assertThat(response.items()).isEmpty();
  }

  @Test
  void shouldGetSubscriptionStatisticsForBothTenantsWhenUserHasAccessToAll(
      @Authenticated(USER_ALL_TENANTS) final CamundaClient client) {
    // when - user with access to all tenants requests subscription statistics
    final var actual =
        client.newProcessDefinitionMessageSubscriptionStatisticsRequest().send().join();

    // then - should see all subscription statistics
    assertThat(actual.items()).hasSize(3);
    final var statsTenant1Version1 = actual.items().getFirst();
    assertThat(statsTenant1Version1.getActiveSubscriptions()).isEqualTo(4L);
    assertThat(statsTenant1Version1.getProcessInstancesWithActiveSubscriptions()).isEqualTo(2L);
    assertThat(statsTenant1Version1.getProcessDefinitionKey())
        .isEqualTo(String.valueOf(processDefKeyTenant1Version1));
    final var statsTenant1Version2 = actual.items().get(1);
    assertThat(statsTenant1Version2.getActiveSubscriptions()).isEqualTo(2L);
    assertThat(statsTenant1Version2.getProcessInstancesWithActiveSubscriptions()).isEqualTo(1L);
    assertThat(statsTenant1Version2.getProcessDefinitionKey())
        .isEqualTo(String.valueOf(processDefKeyTenant1Version2));
    final var statsTenant2 = actual.items().getLast();
    assertThat(statsTenant2.getActiveSubscriptions()).isEqualTo(2L);
    assertThat(statsTenant2.getProcessInstancesWithActiveSubscriptions()).isEqualTo(1L);
  }

  @Test
  void shouldFilterByTenantIdAndGetCorrectSubscriptionStatistics(
      @Authenticated(USER_ALL_TENANTS) final CamundaClient client) {
    // when - user with all tenant access filters by specific tenant
    final var actual =
        client
            .newProcessDefinitionMessageSubscriptionStatisticsRequest()
            .filter(f -> f.tenantId(TENANT_1))
            .send()
            .join();

    // then - should see subscription statistics for tenant1 only
    assertThat(actual.items()).hasSize(2);
    final var statsVersion1 = actual.items().getFirst();
    assertThat(statsVersion1.getProcessDefinitionKey())
        .isEqualTo(String.valueOf(processDefKeyTenant1Version1));
    assertThat(statsVersion1.getActiveSubscriptions()).isEqualTo(4L);
    assertThat(statsVersion1.getProcessInstancesWithActiveSubscriptions()).isEqualTo(2L);
    final var statsVersion2 = actual.items().getLast();
    assertThat(statsVersion2.getProcessDefinitionKey())
        .isEqualTo(String.valueOf(processDefKeyTenant1Version2));
    assertThat(statsVersion2.getActiveSubscriptions()).isEqualTo(2L);
    assertThat(statsVersion2.getProcessInstancesWithActiveSubscriptions()).isEqualTo(1L);
  }

  @Test
  void shouldGetDefaultTenantSubscriptionStatistics(
      @Authenticated final CamundaClient adminClient) {
    // Admin user has access to default tenant
    // when
    final var actual =
        adminClient
            .newProcessDefinitionMessageSubscriptionStatisticsRequest()
            .filter(f -> f.processDefinitionKey(processDefKeyDefaultTenant))
            .send()
            .join();

    // then - should see subscription statistics for default tenant
    assertThat(actual.items()).hasSize(1);
    final var stats = actual.items().getFirst();
    assertThat(stats.getProcessDefinitionKey())
        .isEqualTo(String.valueOf(processDefKeyDefaultTenant));
    assertThat(stats.getActiveSubscriptions()).isEqualTo(6L); // 3 instances * 2 subscriptions each
    assertThat(stats.getProcessInstancesWithActiveSubscriptions()).isEqualTo(3L);
  }

  // Helper methods
  private static long deployProcessWithSubscriptionsWithVersion(
      final CamundaClient client, final String tenantId, final String version) {
    final var processModel = createBpmnModelWithSubscriptions(tenantId, version);
    return deployResource(
            client,
            processModel,
            "process_with_subscriptions_" + tenantId.replace("<", "").replace(">", "") + ".bpmn",
            tenantId)
        .getProcesses()
        .getFirst()
        .getProcessDefinitionKey();
  }

  private static long deployProcessWithSubscriptions(
      final CamundaClient client, final String tenantId) {
    return deployProcessWithSubscriptionsWithVersion(client, tenantId, "v1");
  }

  private static BpmnModelInstance createBpmnModelWithSubscriptions(
      final String tenantId, final String version) {
    final var processId =
        PROCESS_WITH_SUBSCRIPTIONS + "_" + tenantId.replace("<", "").replace(">", "");
    return Bpmn.createExecutableProcess(processId)
        .startEvent()
        .parallelGateway("fork_" + version)
        .intermediateCatchEvent(
            "catch1", e -> e.message(m -> m.name("msg1").zeebeCorrelationKeyExpression("key1")))
        .endEvent("end1")
        .moveToNode("fork_" + version)
        .intermediateCatchEvent(
            "catch2", e -> e.message(m -> m.name("msg2").zeebeCorrelationKeyExpression("key2")))
        .endEvent("end2")
        .done();
  }

  private static DeploymentEvent deployResource(
      final CamundaClient client,
      final BpmnModelInstance processModel,
      final String resourceName,
      final String tenantId) {
    return client
        .newDeployResourceCommand()
        .addProcessModel(processModel, resourceName)
        .tenantId(tenantId)
        .send()
        .join();
  }

  private static ProcessInstanceEvent createInstance(
      final CamundaClient client,
      final long processDefinitionKey,
      final String tenantId,
      final Map<String, Object> variables) {
    final var request =
        client
            .newCreateInstanceCommand()
            .processDefinitionKey(processDefinitionKey)
            .variables(variables);

    Optional.ofNullable(tenantId).ifPresent(request::tenantId);
    return request.send().join();
  }
}
