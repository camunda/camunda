/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.it.util.TestHelper.DEFAULT_TENANT_ID;
import static io.camunda.it.util.TestHelper.cancelInstance;
import static io.camunda.it.util.TestHelper.completeJob;
import static io.camunda.it.util.TestHelper.createTenant;
import static io.camunda.it.util.TestHelper.deployServiceTaskProcess;
import static io.camunda.it.util.TestHelper.startProcessInstance;
import static io.camunda.it.util.TestHelper.waitForJobs;
import static io.camunda.it.util.TestHelper.waitForProcessInstanceToBeTerminated;
import static io.camunda.it.util.TestHelper.waitForProcessInstances;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.client.api.statistics.response.ProcessDefinitionInstanceStatistics;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.security.configuration.InitializationConfiguration;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@MultiDbTest()
public class ProcessDefinitionInstanceStatisticsIT {

  private static final String TENANT_ID_1 = "tenant1";
  private static final String USERNAME_1 = "user1";

  private static CamundaClient camundaClient;

  @MultiDbTestApplication
  private static final TestCamundaApplication TEST_INSTANCE =
      new TestCamundaApplication().withBasicAuth().withMultiTenancyEnabled();

  @UserDefinition
  private static final TestUser USER_1 = new TestUser(USERNAME_1, "password", List.of());

  @BeforeAll
  public static void beforeAll(@Authenticated final CamundaClient adminClient)
      throws InterruptedException {

    createTenant(
        adminClient,
        TENANT_ID_1,
        TENANT_ID_1,
        InitializationConfiguration.DEFAULT_USER_USERNAME,
        USERNAME_1);
    adminClient.newAssignRoleToUserCommand().roleId("admin").username(USERNAME_1).execute();
  }

  @AfterEach
  void cleanupInstances() {

    // 1. Query all active instances (all tenants)
    final var activeInstances =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(f -> f.state(ProcessInstanceState.ACTIVE))
            .send()
            .join()
            .items();

    // 2. Cancel all active instances
    activeInstances.forEach(
        inst -> camundaClient.newCancelInstanceCommand(inst.getProcessInstanceKey()).send().join());

    // 3. Wait for all instances to be TERMINATED
    activeInstances.forEach(
        inst -> waitForProcessInstanceToBeTerminated(camundaClient, inst.getProcessInstanceKey()));

    // 4. Ensure no active instances remain in the index
    final var stillActive =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(f -> f.state(ProcessInstanceState.ACTIVE))
            .send()
            .join()
            .items();

    if (!stillActive.isEmpty()) {
      throw new IllegalStateException(
          "Cleanup failed – still ACTIVE instances after test: " + stillActive);
    }
  }

  private static ProcessInstanceEvent startInstance(final long processDefinitionKey) {
    return startProcessInstance(camundaClient, processDefinitionKey);
  }

  private static void startInstance(final long processDefinitionKey, final String tenantId) {
    startProcessInstance(camundaClient, processDefinitionKey, tenantId);
  }

  @Test
  void shouldReturnEmptyStatisticsWhenNoActiveInstances() {
    // given
    deployServiceTaskProcess(camundaClient, "no_instances_proc", "No Instances", "3");

    // when
    final var result = camundaClient.newProcessDefinitionInstanceStatisticsRequest().send().join();

    // then
    final long countForProcId =
        result.items().stream()
            .filter(s -> Objects.equals(s.getProcessDefinitionId(), "no_instances_proc"))
            .count();
    assertThat(countForProcId).isEqualTo(0);
  }

  @Test
  void shouldReturnSingleProcessDefinitionWithOnlyNonIncidentInstances() {
    // given
    final var deployment =
        deployServiceTaskProcess(camundaClient, "stats_proc_1", "Stats Proc 1", "3");
    final var processDefinitionKey = deployment.getProcesses().getFirst().getProcessDefinitionKey();

    startInstance(processDefinitionKey);
    startInstance(processDefinitionKey);

    waitForProcessInstances(
        camundaClient,
        f -> f.processDefinitionKey(processDefinitionKey).state(ProcessInstanceState.ACTIVE),
        2);

    // when
    final var result = camundaClient.newProcessDefinitionInstanceStatisticsRequest().send().join();

    // then
    final var stats =
        result.items().stream()
            .filter(
                s ->
                    Objects.equals(s.getProcessDefinitionId(), "stats_proc_1")
                        && DEFAULT_TENANT_ID.equals(s.getTenantId()))
            .findFirst()
            .orElseThrow();

    assertThat(stats.getLatestProcessDefinitionName()).isEqualTo("Stats Proc 1");
    assertThat(stats.getHasMultipleVersions()).isFalse();
    assertThat(stats.getActiveInstancesWithoutIncidentCount()).isEqualTo(2);
    assertThat(stats.getActiveInstancesWithIncidentCount()).isEqualTo(0);
    assertThat(result.page().totalItems()).isEqualTo(1);
    assertThat(result.page().hasMoreTotalItems()).isFalse();

    final long countForProcId =
        result.items().stream()
            .filter(s -> Objects.equals(s.getProcessDefinitionId(), "stats_proc_1"))
            .count();
    assertThat(countForProcId).isEqualTo(1);
  }

  @Test
  void shouldReturnStatisticsIgnoringCompletedAndTerminatedInstances() {
    // given
    final var deployment =
        deployServiceTaskProcess(camundaClient, "mixed_proc", "Mixed Process", "3");
    final var processDefinitionKey = deployment.getProcesses().getFirst().getProcessDefinitionKey();
    // 1. Start ACTIVE instances
    startInstance(processDefinitionKey);
    startInstance(processDefinitionKey);

    // 2. Start instances that we immediately complete (become COMPLETED)
    final var completed1 = startInstance(processDefinitionKey);
    final var completed2 = startInstance(processDefinitionKey);

    // 3. Start a terminated instance (optional)
    final var terminated = startInstance(processDefinitionKey);

    // wait for 5 instances to be created
    waitForProcessInstances(camundaClient, f -> f.processDefinitionKey(processDefinitionKey), 5);
    waitForJobs(
        camundaClient,
        List.of(completed1.getProcessInstanceKey(), completed2.getProcessInstanceKey()));

    final var job1 =
        camundaClient
            .newJobSearchRequest()
            .filter(f -> f.processInstanceKey(completed1.getProcessInstanceKey()))
            .send()
            .join()
            .singleItem()
            .getJobKey();

    final var job2 =
        camundaClient
            .newJobSearchRequest()
            .filter(f -> f.processInstanceKey(completed2.getProcessInstanceKey()))
            .send()
            .join()
            .singleItem()
            .getJobKey();

    completeJob(camundaClient, job1);
    completeJob(camundaClient, job2);
    cancelInstance(camundaClient, terminated);

    // Wait until exactly 2 ACTIVE instances exist
    waitForProcessInstances(
        camundaClient,
        f -> f.processDefinitionKey(processDefinitionKey).state(ProcessInstanceState.ACTIVE),
        2);

    // when
    final var result = camundaClient.newProcessDefinitionInstanceStatisticsRequest().send().join();

    // then
    final var stats =
        result.items().stream()
            .filter(
                s ->
                    s.getProcessDefinitionId().equals("mixed_proc")
                        && DEFAULT_TENANT_ID.equals(s.getTenantId()))
            .findFirst()
            .orElseThrow();

    assertThat(stats.getLatestProcessDefinitionName()).isEqualTo("Mixed Process");
    assertThat(stats.getHasMultipleVersions()).isFalse();
    assertThat(stats.getActiveInstancesWithoutIncidentCount()).isEqualTo(2);
    assertThat(stats.getActiveInstancesWithIncidentCount()).isEqualTo(0);
    assertThat(result.page().totalItems()).isEqualTo(1);
    assertThat(result.page().hasMoreTotalItems()).isFalse();

    final long countForProcId =
        result.items().stream()
            .filter(s -> Objects.equals(s.getProcessDefinitionId(), "mixed_proc"))
            .count();
    assertThat(countForProcId).isEqualTo(1);
  }

  @Test
  void shouldReturnStatisticsWithIncidentInstances() {
    // given
    final var deployment =
        deployServiceTaskProcess(
            camundaClient, "failing_service_proc", "Failing Service Process", "error");
    final var processDefinitionKey = deployment.getProcesses().getFirst().getProcessDefinitionKey();

    startInstance(processDefinitionKey);
    startInstance(processDefinitionKey);

    waitForProcessInstances(
        camundaClient,
        f ->
            f.processDefinitionKey(processDefinitionKey)
                .state(ProcessInstanceState.ACTIVE)
                .hasIncident(true),
        2);

    // when
    final var result = camundaClient.newProcessDefinitionInstanceStatisticsRequest().send().join();
    // then
    final var stats =
        result.items().stream()
            .filter(
                s ->
                    s.getProcessDefinitionId().equals("failing_service_proc")
                        && DEFAULT_TENANT_ID.equals(s.getTenantId()))
            .findFirst()
            .orElseThrow();

    assertThat(stats.getLatestProcessDefinitionName()).isEqualTo("Failing Service Process");
    assertThat(stats.getHasMultipleVersions()).isFalse();
    assertThat(stats.getActiveInstancesWithoutIncidentCount()).isEqualTo(0);
    assertThat(stats.getActiveInstancesWithIncidentCount()).isEqualTo(2);
    assertThat(result.page().totalItems()).isEqualTo(1);
    assertThat(result.page().hasMoreTotalItems()).isFalse();

    final long countForProcId =
        result.items().stream()
            .filter(s -> Objects.equals(s.getProcessDefinitionId(), "failing_service_proc"))
            .count();
    assertThat(countForProcId).isEqualTo(1);
  }

  @Test
  void shouldReturnStatisticsWithIncidentAndNonIncidentInstances() {
    // given
    final var deployment =
        deployServiceTaskProcess(
            camundaClient, "mixed_incident_proc", "Mixed Incident Process", "error");
    final var processDefinitionKey = deployment.getProcesses().getFirst().getProcessDefinitionKey();

    // Start instances that will fail and have incidents
    startInstance(processDefinitionKey);
    startInstance(processDefinitionKey);

    // Start instances that will succeed and not have incidents
    final var successfulDeployment =
        deployServiceTaskProcess(
            camundaClient, "mixed_incident_proc", "Mixed Incident Process", "3");
    final var successfulProcessDefinitionKey =
        successfulDeployment.getProcesses().getFirst().getProcessDefinitionKey();

    startInstance(successfulProcessDefinitionKey);
    startInstance(successfulProcessDefinitionKey);

    waitForProcessInstances(
        camundaClient,
        f ->
            f.processDefinitionKey(processDefinitionKey)
                .state(ProcessInstanceState.ACTIVE)
                .hasIncident(true),
        2);

    waitForProcessInstances(
        camundaClient,
        f ->
            f.processDefinitionKey(successfulProcessDefinitionKey)
                .state(ProcessInstanceState.ACTIVE)
                .hasIncident(false),
        2);

    // when
    final var result = camundaClient.newProcessDefinitionInstanceStatisticsRequest().send().join();
    // then
    final var stats =
        result.items().stream()
            .filter(
                s ->
                    s.getProcessDefinitionId().equals("mixed_incident_proc")
                        && DEFAULT_TENANT_ID.equals(s.getTenantId()))
            .findFirst()
            .orElseThrow();

    assertThat(stats.getLatestProcessDefinitionName()).isEqualTo("Mixed Incident Process");
    assertThat(stats.getHasMultipleVersions()).isTrue();
    assertThat(stats.getActiveInstancesWithoutIncidentCount()).isEqualTo(2);
    assertThat(stats.getActiveInstancesWithIncidentCount()).isEqualTo(2);
    assertThat(result.page().totalItems()).isEqualTo(1);
    assertThat(result.page().hasMoreTotalItems()).isFalse();

    final long countForProcId =
        result.items().stream()
            .filter(s -> Objects.equals(s.getProcessDefinitionId(), "mixed_incident_proc"))
            .count();
    assertThat(countForProcId).isEqualTo(1);
  }

  @Test
  void shouldReturnSeparateStatisticsPerTenantForSameProcessDefinition() {
    // given
    final String bpmnProcessId = "multi_tenant_proc";
    final String processName = "Multi-Tenant Process";

    final DeploymentEvent defaultDeployment =
        deployServiceTaskProcess(camundaClient, bpmnProcessId, processName, "3", DEFAULT_TENANT_ID);

    final DeploymentEvent guestDeployment =
        deployServiceTaskProcess(camundaClient, bpmnProcessId, processName, "3", TENANT_ID_1);

    final long defaultProcDefKey =
        defaultDeployment.getProcesses().getFirst().getProcessDefinitionKey();
    final long guestProcDefKey =
        guestDeployment.getProcesses().getFirst().getProcessDefinitionKey();

    // Start 2 active, non-incident instances in <default> tenant
    startInstance(defaultProcDefKey, DEFAULT_TENANT_ID);
    startInstance(defaultProcDefKey, DEFAULT_TENANT_ID);

    // Start 3 active, non-incident instances in "non-default" tenant
    startInstance(guestProcDefKey, TENANT_ID_1);
    startInstance(guestProcDefKey, TENANT_ID_1);
    startInstance(guestProcDefKey, TENANT_ID_1);

    // Wait for instances to be ACTIVE in both tenants
    waitForProcessInstances(
        camundaClient,
        f -> f.processDefinitionKey(defaultProcDefKey).state(ProcessInstanceState.ACTIVE),
        2);

    waitForProcessInstances(
        camundaClient,
        f -> f.processDefinitionKey(guestProcDefKey).state(ProcessInstanceState.ACTIVE),
        3);

    // when
    final var result = camundaClient.newProcessDefinitionInstanceStatisticsRequest().send().join();

    // then
    // We expect two stats entries for the same bpmnProcessId, one per tenant
    final var defaultStats =
        result.items().stream()
            .filter(
                s ->
                    Objects.equals(s.getProcessDefinitionId(), bpmnProcessId)
                        && DEFAULT_TENANT_ID.equals(s.getTenantId()))
            .findFirst()
            .orElseThrow();

    final var guestStats =
        result.items().stream()
            .filter(
                s ->
                    Objects.equals(s.getProcessDefinitionId(), bpmnProcessId)
                        && TENANT_ID_1.equals(s.getTenantId()))
            .findFirst()
            .orElseThrow();

    // Verify basic metadata
    assertThat(defaultStats.getLatestProcessDefinitionName()).isEqualTo(processName);
    assertThat(guestStats.getLatestProcessDefinitionName()).isEqualTo(processName);

    // Only one version per tenant in this test
    assertThat(defaultStats.getHasMultipleVersions()).isFalse();
    assertThat(guestStats.getHasMultipleVersions()).isFalse();

    // Verify counts are separated per tenant
    assertThat(defaultStats.getActiveInstancesWithoutIncidentCount()).isEqualTo(2);
    assertThat(defaultStats.getActiveInstancesWithIncidentCount()).isEqualTo(0);

    assertThat(guestStats.getActiveInstancesWithoutIncidentCount()).isEqualTo(3);
    assertThat(guestStats.getActiveInstancesWithIncidentCount()).isEqualTo(0);

    assertThat(result.page().totalItems()).isEqualTo(2);
    assertThat(result.page().hasMoreTotalItems()).isFalse();

    // And we should have exactly 2 items for this process id (one per tenant)
    final long countForProcId =
        result.items().stream()
            .filter(s -> Objects.equals(s.getProcessDefinitionId(), bpmnProcessId))
            .count();
    assertThat(countForProcId).isEqualTo(2);
  }

  @Test
  void shouldAggregateStatisticsAcrossMultipleVersionsOfSameProcessDefinition() {
    // given
    final String bpmnProcessId = "multi_version_proc";
    final String v1Name = "Multi-Version Process v1";
    final String v2Name = "Multi-Version Process v2";

    // Deploy V1
    final var deploymentV1 = deployServiceTaskProcess(camundaClient, bpmnProcessId, v1Name, "3");
    final long processDefinitionKeyV1 =
        deploymentV1.getProcesses().getFirst().getProcessDefinitionKey();

    // Deploy V2 (same BPMN process id, different name → higher version)
    final var deploymentV2 = deployServiceTaskProcess(camundaClient, bpmnProcessId, v2Name, "3");
    final long processDefinitionKeyV2 =
        deploymentV2.getProcesses().getFirst().getProcessDefinitionKey();

    // Start instances on both versions (all non-incident)
    startInstance(processDefinitionKeyV1);
    startInstance(processDefinitionKeyV1);
    startInstance(processDefinitionKeyV2);

    // Wait until the expected ACTIVE instances exist for each version
    waitForProcessInstances(
        camundaClient,
        f -> f.processDefinitionKey(processDefinitionKeyV1).state(ProcessInstanceState.ACTIVE),
        2);

    waitForProcessInstances(
        camundaClient,
        f -> f.processDefinitionKey(processDefinitionKeyV2).state(ProcessInstanceState.ACTIVE),
        1);

    // when
    final var result = camundaClient.newProcessDefinitionInstanceStatisticsRequest().send().join();

    // then
    final var stats =
        result.items().stream()
            .filter(
                s ->
                    Objects.equals(s.getProcessDefinitionId(), bpmnProcessId)
                        && DEFAULT_TENANT_ID.equals(s.getTenantId()))
            .findFirst()
            .orElseThrow();

    // Latest name should come from the latest version (V2)
    assertThat(stats.getLatestProcessDefinitionName()).isEqualTo(v2Name);

    // Multiple versions of the same process definition id exist
    assertThat(stats.getHasMultipleVersions()).isTrue();

    // Counts should be aggregated across both versions
    assertThat(stats.getActiveInstancesWithoutIncidentCount()).isEqualTo(3);
    assertThat(stats.getActiveInstancesWithIncidentCount()).isEqualTo(0);

    assertThat(result.page().totalItems()).isEqualTo(1);
    assertThat(result.page().hasMoreTotalItems()).isFalse();

    // Ensure we only have a single entry for this BPMN id in this tenant
    final long countForProcId =
        result.items().stream()
            .filter(s -> Objects.equals(s.getProcessDefinitionId(), bpmnProcessId))
            .count();
    assertThat(countForProcId).isEqualTo(1);
  }

  @Test
  void shouldSupportSortingAndPagination() {
    //
    // given: three process definitions with predictable counts of INCIDENT instances
    //
    final var depA = deployServiceTaskProcess(camundaClient, "sort_proc_a", "Sort A", "3");
    final var keyA = depA.getProcesses().getFirst().getProcessDefinitionKey();

    final var depB = deployServiceTaskProcess(camundaClient, "sort_proc_b", "Sort B", "3");
    final var keyB = depB.getProcesses().getFirst().getProcessDefinitionKey();

    final var depC = deployServiceTaskProcess(camundaClient, "sort_proc_c", "Sort C", "error");
    final var keyC = depC.getProcesses().getFirst().getProcessDefinitionKey();

    // A: 3 non-incident
    startInstance(keyA);
    startInstance(keyA);
    startInstance(keyA);

    // B: 1 non-incident + 2 incident
    startInstance(keyB);

    final var depBFail = deployServiceTaskProcess(camundaClient, "sort_proc_b", "Sort B", "error");
    final var keyBFail = depBFail.getProcesses().getFirst().getProcessDefinitionKey();
    startInstance(keyBFail);
    startInstance(keyBFail);

    // C: 4 incident
    startInstance(keyC);
    startInstance(keyC);
    startInstance(keyC);
    startInstance(keyC);

    // Wait for all required instance states
    waitForProcessInstances(
        camundaClient,
        f ->
            f.processDefinitionId("sort_proc_c")
                .state(ProcessInstanceState.ACTIVE)
                .hasIncident(true),
        4);

    final var page1 =
        camundaClient
            .newProcessDefinitionInstanceStatisticsRequest()
            .sort(s -> s.activeInstancesWithIncidentCount().desc())
            .page(p -> p.from(0).limit(2))
            .send()
            .join();

    final var page1Ids =
        page1.items().stream()
            .map(ProcessDefinitionInstanceStatistics::getProcessDefinitionId)
            .toList();

    assertThat(page1Ids).containsExactly("sort_proc_c", "sort_proc_b");
    assertThat(page1.page().totalItems()).isEqualTo(3);
    assertThat(page1.page().hasMoreTotalItems()).isFalse();

    final var page2 =
        camundaClient
            .newProcessDefinitionInstanceStatisticsRequest()
            .sort(s -> s.activeInstancesWithIncidentCount().desc())
            .page(p -> p.from(2).limit(2))
            .send()
            .join();

    final var page2Ids =
        page2.items().stream()
            .map(ProcessDefinitionInstanceStatistics::getProcessDefinitionId)
            .toList();

    assertThat(page2Ids).containsExactly("sort_proc_a");
    assertThat(page2.page().totalItems()).isEqualTo(3);
    assertThat(page2.page().hasMoreTotalItems()).isFalse();
  }

  @Test
  void shouldAggregateStatisticsAcrossMultipleVersionsAndTenants() {
    // given
    final String bpmnProcessId = "multi_version_multi_tenant_proc";

    final String defaultV1Name = "Multi-Version Multi-Tenant v1 (default)";
    final String defaultV2Name = "Multi-Version Multi-Tenant v2 (default)";

    final String tenantV1Name = "Multi-Version Multi-Tenant v1 (tenant1)";
    final String tenantV2Name = "Multi-Version Multi-Tenant v2 (tenant1)";

    // --- Default tenant: deploy V1 and V2 ---
    final var defaultDepV1 =
        deployServiceTaskProcess(
            camundaClient, bpmnProcessId, defaultV1Name, "3", DEFAULT_TENANT_ID);
    final long defaultKeyV1 = defaultDepV1.getProcesses().getFirst().getProcessDefinitionKey();

    final var defaultDepV2 =
        deployServiceTaskProcess(
            camundaClient, bpmnProcessId, defaultV2Name, "3", DEFAULT_TENANT_ID);
    final long defaultKeyV2 = defaultDepV2.getProcesses().getFirst().getProcessDefinitionKey();

    // --- Tenant1: deploy V1 and V2 ---
    final var tenantDepV1 =
        deployServiceTaskProcess(camundaClient, bpmnProcessId, tenantV1Name, "3", TENANT_ID_1);
    final long tenantKeyV1 = tenantDepV1.getProcesses().getFirst().getProcessDefinitionKey();

    final var tenantDepV2 =
        deployServiceTaskProcess(camundaClient, bpmnProcessId, tenantV2Name, "3", TENANT_ID_1);
    final long tenantKeyV2 = tenantDepV2.getProcesses().getFirst().getProcessDefinitionKey();

    // --- Start instances in default tenant (2 total: 1 on each version) ---
    startInstance(defaultKeyV1, DEFAULT_TENANT_ID);
    startInstance(defaultKeyV2, DEFAULT_TENANT_ID);

    // --- Start instances in tenant1 (4 total: 2 on each version) ---
    startInstance(tenantKeyV1, TENANT_ID_1);
    startInstance(tenantKeyV1, TENANT_ID_1);
    startInstance(tenantKeyV2, TENANT_ID_1);
    startInstance(tenantKeyV2, TENANT_ID_1);

    // Wait for instances to be ACTIVE for each version/key
    waitForProcessInstances(
        camundaClient,
        f -> f.processDefinitionKey(defaultKeyV1).state(ProcessInstanceState.ACTIVE),
        1);

    waitForProcessInstances(
        camundaClient,
        f -> f.processDefinitionKey(defaultKeyV2).state(ProcessInstanceState.ACTIVE),
        1);

    waitForProcessInstances(
        camundaClient,
        f -> f.processDefinitionKey(tenantKeyV1).state(ProcessInstanceState.ACTIVE),
        2);

    waitForProcessInstances(
        camundaClient,
        f -> f.processDefinitionKey(tenantKeyV2).state(ProcessInstanceState.ACTIVE),
        2);

    // when
    final var result = camundaClient.newProcessDefinitionInstanceStatisticsRequest().send().join();

    // then
    final var defaultStats =
        result.items().stream()
            .filter(
                s ->
                    Objects.equals(s.getProcessDefinitionId(), bpmnProcessId)
                        && DEFAULT_TENANT_ID.equals(s.getTenantId()))
            .findFirst()
            .orElseThrow();

    final var tenantStats =
        result.items().stream()
            .filter(
                s ->
                    Objects.equals(s.getProcessDefinitionId(), bpmnProcessId)
                        && TENANT_ID_1.equals(s.getTenantId()))
            .findFirst()
            .orElseThrow();

    // Latest name should come from the latest version per tenant
    assertThat(defaultStats.getLatestProcessDefinitionName()).isEqualTo(defaultV2Name);
    assertThat(tenantStats.getLatestProcessDefinitionName()).isEqualTo(tenantV2Name);

    // Both tenants should report multiple versions
    assertThat(defaultStats.getHasMultipleVersions()).isTrue();
    assertThat(tenantStats.getHasMultipleVersions()).isTrue();

    // Counts aggregated across versions but separated per tenant
    assertThat(defaultStats.getActiveInstancesWithoutIncidentCount()).isEqualTo(2);
    assertThat(defaultStats.getActiveInstancesWithIncidentCount()).isEqualTo(0);

    assertThat(tenantStats.getActiveInstancesWithoutIncidentCount()).isEqualTo(4);
    assertThat(tenantStats.getActiveInstancesWithIncidentCount()).isEqualTo(0);

    assertThat(result.page().totalItems()).isEqualTo(2);
    assertThat(result.page().hasMoreTotalItems()).isFalse();

    // Optional: ensure we only have one entry per (bpmnProcessId, tenant)
    final long countForDefault =
        result.items().stream()
            .filter(
                s ->
                    Objects.equals(s.getProcessDefinitionId(), bpmnProcessId)
                        && DEFAULT_TENANT_ID.equals(s.getTenantId()))
            .count();
    assertThat(countForDefault).isEqualTo(1);

    final long countForTenant1 =
        result.items().stream()
            .filter(
                s ->
                    Objects.equals(s.getProcessDefinitionId(), bpmnProcessId)
                        && TENANT_ID_1.equals(s.getTenantId()))
            .count();
    assertThat(countForTenant1).isEqualTo(1);
  }

  @Test
  void shouldHandleMixedVersionCountsAcrossTenants() {
    // given
    final String bpmnProcessId = "mixed_version_tenants_proc";

    final String defaultV1Name = "Mixed Versions v1 (default)";
    final String defaultV2Name = "Mixed Versions v2 (default)";

    final String tenantV1Name = "Mixed Versions v1 (tenant1)"; // tenant1 only gets ONE version

    // --- Default tenant: deploy V1 and V2 ---
    final var defaultDepV1 =
        deployServiceTaskProcess(
            camundaClient, bpmnProcessId, defaultV1Name, "3", DEFAULT_TENANT_ID);
    final long defaultKeyV1 = defaultDepV1.getProcesses().getFirst().getProcessDefinitionKey();

    final var defaultDepV2 =
        deployServiceTaskProcess(
            camundaClient, bpmnProcessId, defaultV2Name, "3", DEFAULT_TENANT_ID);
    final long defaultKeyV2 = defaultDepV2.getProcesses().getFirst().getProcessDefinitionKey();

    // --- Tenant1: deploy ONLY V1 ---
    final var tenantDepV1 =
        deployServiceTaskProcess(camundaClient, bpmnProcessId, tenantV1Name, "3", TENANT_ID_1);
    final long tenantKeyV1 = tenantDepV1.getProcesses().getFirst().getProcessDefinitionKey();

    // --- Start instances in default tenant ---
    startInstance(defaultKeyV1, DEFAULT_TENANT_ID);
    startInstance(defaultKeyV2, DEFAULT_TENANT_ID);
    startInstance(defaultKeyV2, DEFAULT_TENANT_ID); // 3 total: V1 has 1, V2 has 2

    // --- Start instances in tenant1 (single version) ---
    startInstance(tenantKeyV1, TENANT_ID_1);
    startInstance(tenantKeyV1, TENANT_ID_1);

    // Wait for instances
    waitForProcessInstances(
        camundaClient,
        f -> f.processDefinitionKey(defaultKeyV1).state(ProcessInstanceState.ACTIVE),
        1);

    waitForProcessInstances(
        camundaClient,
        f -> f.processDefinitionKey(defaultKeyV2).state(ProcessInstanceState.ACTIVE),
        2);

    waitForProcessInstances(
        camundaClient,
        f -> f.processDefinitionKey(tenantKeyV1).state(ProcessInstanceState.ACTIVE),
        2);

    // when
    final var result = camundaClient.newProcessDefinitionInstanceStatisticsRequest().send().join();

    // then
    final var defaultStats =
        result.items().stream()
            .filter(
                s ->
                    Objects.equals(s.getProcessDefinitionId(), bpmnProcessId)
                        && DEFAULT_TENANT_ID.equals(s.getTenantId()))
            .findFirst()
            .orElseThrow();

    final var tenantStats =
        result.items().stream()
            .filter(
                s ->
                    Objects.equals(s.getProcessDefinitionId(), bpmnProcessId)
                        && TENANT_ID_1.equals(s.getTenantId()))
            .findFirst()
            .orElseThrow();

    // Default tenant should use V2 name as the latest
    assertThat(defaultStats.getLatestProcessDefinitionName()).isEqualTo(defaultV2Name);
    assertThat(defaultStats.getHasMultipleVersions()).isTrue();
    assertThat(defaultStats.getActiveInstancesWithoutIncidentCount()).isEqualTo(3);

    // Tenant1 should have only one version → no multiple version flag
    assertThat(tenantStats.getLatestProcessDefinitionName()).isEqualTo(tenantV1Name);
    assertThat(tenantStats.getHasMultipleVersions()).isFalse();
    assertThat(tenantStats.getActiveInstancesWithoutIncidentCount()).isEqualTo(2);

    assertThat(result.page().totalItems()).isEqualTo(2);
    assertThat(result.page().hasMoreTotalItems()).isFalse();

    // Ensure exactly one entry per tenant
    assertThat(
            result.items().stream()
                .filter(
                    s ->
                        Objects.equals(s.getProcessDefinitionId(), bpmnProcessId)
                            && DEFAULT_TENANT_ID.equals(s.getTenantId()))
                .count())
        .isEqualTo(1);

    assertThat(
            result.items().stream()
                .filter(
                    s ->
                        Objects.equals(s.getProcessDefinitionId(), bpmnProcessId)
                            && TENANT_ID_1.equals(s.getTenantId()))
                .count())
        .isEqualTo(1);
  }
}
