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
import static io.camunda.it.util.TestHelper.startProcessInstanceForTenant;
import static io.camunda.it.util.TestHelper.waitForJobs;
import static io.camunda.it.util.TestHelper.waitForProcessInstanceToBeTerminated;
import static io.camunda.it.util.TestHelper.waitForProcessInstances;
import static io.camunda.it.util.TestHelper.waitUntilIncidentsAreActive;
import static org.assertj.core.api.AssertionsForClassTypes.tuple;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.client.api.statistics.response.ProcessDefinitionInstanceVersionStatistics;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.security.configuration.InitializationConfiguration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@MultiDbTest()
public class ProcessDefinitionInstanceVersionStatisticsIT {

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

  @Test
  void shouldReturnEmptyVersionStatisticsWhenNoActiveInstances() {
    // given
    final var processDefinitionId = "versions_no_instances_proc";
    deployServiceTaskProcess(camundaClient, processDefinitionId, "Versions No Instances", "3");

    // when
    final var result =
        camundaClient
            .newProcessDefinitionInstanceVersionStatisticsRequest(processDefinitionId)
            .send()
            .join();

    // then
    assertThat(result.items()).isEmpty();
  }

  @Test
  void shouldReturnSingleVersionWithOnlyNonIncidentInstances() {
    // given
    final var processDefinitionId = "versions_single_non_incident";
    final var processDefinitionName = "Versions Single Non Incident";
    final var deployment =
        deployServiceTaskProcess(camundaClient, processDefinitionId, processDefinitionName, "3");
    final long v1Key = deployment.getProcesses().getFirst().getProcessDefinitionKey();

    startProcessInstance(camundaClient, v1Key);
    startProcessInstance(camundaClient, v1Key);

    // wait for two ACTIVE instances of this definition
    waitForProcessInstances(
        camundaClient, f -> f.processDefinitionKey(v1Key).state(ProcessInstanceState.ACTIVE), 2);

    // when
    final var result =
        camundaClient
            .newProcessDefinitionInstanceVersionStatisticsRequest(processDefinitionId)
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(1);
    final var stats = result.items().getFirst();
    assertThat(stats.getProcessDefinitionId()).isEqualTo(processDefinitionId);
    assertThat(stats.getProcessDefinitionKey()).isEqualTo(v1Key);
    assertThat(stats.getProcessDefinitionName()).isEqualTo(processDefinitionName);
    assertThat(stats.getProcessDefinitionVersion()).isEqualTo(1);
    assertThat(stats.getTenantId()).isEqualTo(DEFAULT_TENANT_ID);
    assertThat(stats.getActiveInstancesWithoutIncidentCount()).isEqualTo(2);
    assertThat(stats.getActiveInstancesWithIncidentCount()).isEqualTo(0);
    assertThat(result.page().totalItems()).isEqualTo(1);
    assertThat(result.page().hasMoreTotalItems()).isFalse();
  }

  @Test
  void shouldIgnoreCompletedAndTerminatedInstancesInVersionStatistics() {
    // given
    final var processDefinitionId = "versions_ignore_completed_terminated";
    final var processDefinitionName = "Versions Mixed States";
    final var deployment =
        deployServiceTaskProcess(camundaClient, processDefinitionId, processDefinitionName, "3");
    final long v1Key = deployment.getProcesses().getFirst().getProcessDefinitionKey();

    // two instances that stay ACTIVE (we do nothing with their jobs)
    startProcessInstance(camundaClient, v1Key);
    startProcessInstance(camundaClient, v1Key);

    // two instances that we complete
    final ProcessInstanceEvent completed1 = startProcessInstance(camundaClient, v1Key);
    final ProcessInstanceEvent completed2 = startProcessInstance(camundaClient, v1Key);

    // one instance that we cancel
    final ProcessInstanceEvent terminated = startProcessInstance(camundaClient, v1Key);

    // wait until all 5 are visible
    waitForProcessInstances(camundaClient, f -> f.processDefinitionKey(v1Key), 5);

    // wait until jobs are created for the instances we want to complete
    waitForJobs(
        camundaClient,
        List.of(completed1.getProcessInstanceKey(), completed2.getProcessInstanceKey()));

    // complete jobs for the two instances we want COMPLETED
    final long jobKey1 =
        camundaClient
            .newJobSearchRequest()
            .filter(f -> f.processInstanceKey(completed1.getProcessInstanceKey()))
            .send()
            .join()
            .singleItem()
            .getJobKey();

    final long jobKey2 =
        camundaClient
            .newJobSearchRequest()
            .filter(f -> f.processInstanceKey(completed2.getProcessInstanceKey()))
            .send()
            .join()
            .singleItem()
            .getJobKey();

    completeJob(camundaClient, jobKey1);
    completeJob(camundaClient, jobKey2);

    // cancel one instance so it becomes TERMINATED
    cancelInstance(camundaClient, terminated);

    // wait until exactly the two "active" ones remain ACTIVE
    waitForProcessInstances(
        camundaClient, f -> f.processDefinitionKey(v1Key).state(ProcessInstanceState.ACTIVE), 2);

    // when
    final var result =
        camundaClient
            .newProcessDefinitionInstanceVersionStatisticsRequest(processDefinitionId)
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(1);
    final var stats = result.items().getFirst();
    assertThat(stats.getProcessDefinitionId()).isEqualTo(processDefinitionId);
    assertThat(stats.getProcessDefinitionKey()).isEqualTo(v1Key);
    assertThat(stats.getProcessDefinitionName()).isEqualTo(processDefinitionName);
    assertThat(stats.getProcessDefinitionVersion()).isEqualTo(1);
    assertThat(stats.getTenantId()).isEqualTo(DEFAULT_TENANT_ID);
    assertThat(stats.getActiveInstancesWithoutIncidentCount()).isEqualTo(2);
    assertThat(stats.getActiveInstancesWithIncidentCount()).isEqualTo(0);
    assertThat(result.page().totalItems()).isEqualTo(1);
    assertThat(result.page().hasMoreTotalItems()).isFalse();
  }

  @Test
  void shouldReturnSeparateStatisticsPerVersionForSameProcessDefinition() {
    // given
    final var processDefinitionId = "versions_multi_non_incident";
    final var processDefinitionNameV1 = "Versions Multi Non Incident v1";
    final var processDefinitionNameV2 = "Versions Multi Non Incident v2";

    final var v1Deployment =
        deployServiceTaskProcess(camundaClient, processDefinitionId, processDefinitionNameV1, "3");
    final long v1Key = v1Deployment.getProcesses().getFirst().getProcessDefinitionKey();

    // create a second version with the same BPMN process id
    final var v2Deployment =
        deployServiceTaskProcess(camundaClient, processDefinitionId, processDefinitionNameV2, "3");
    final long v2Key = v2Deployment.getProcesses().getFirst().getProcessDefinitionKey();

    // v1: 1 active instance
    startProcessInstance(camundaClient, v1Key);

    // v2: 2 active instances
    startProcessInstance(camundaClient, v2Key);
    startProcessInstance(camundaClient, v2Key);

    // wait until all 3 instances are ACTIVE
    waitForProcessInstances(
        camundaClient,
        f -> f.processDefinitionId(processDefinitionId).state(ProcessInstanceState.ACTIVE),
        3);

    // when
    final var result =
        camundaClient
            .newProcessDefinitionInstanceVersionStatisticsRequest(processDefinitionId)
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(2);
    assertThat(result.items())
        .extracting(
            ProcessDefinitionInstanceVersionStatistics::getProcessDefinitionId,
            ProcessDefinitionInstanceVersionStatistics::getProcessDefinitionKey,
            ProcessDefinitionInstanceVersionStatistics::getProcessDefinitionName,
            ProcessDefinitionInstanceVersionStatistics::getProcessDefinitionVersion,
            ProcessDefinitionInstanceVersionStatistics::getTenantId,
            ProcessDefinitionInstanceVersionStatistics::getActiveInstancesWithoutIncidentCount,
            ProcessDefinitionInstanceVersionStatistics::getActiveInstancesWithIncidentCount)
        .containsExactlyInAnyOrder(
            tuple(
                processDefinitionId, v1Key, processDefinitionNameV1, 1, DEFAULT_TENANT_ID, 1L, 0L),
            tuple(
                processDefinitionId, v2Key, processDefinitionNameV2, 2, DEFAULT_TENANT_ID, 2L, 0L));

    assertThat(result.page().totalItems()).isEqualTo(2);
    assertThat(result.page().hasMoreTotalItems()).isFalse();
  }

  @Test
  void shouldReturnIncidentAndNonIncidentCountsPerVersion() {
    // given
    final var processDefinitionId = "versions_incident_and_non_incident";
    final var processDefinitionNameV1 = "Versions Mixed Incidents v1";
    final var processDefinitionNameV2 = "Versions Mixed Incidents v2";

    // v1: successful service task (no incident)
    final var v1Deployment =
        deployServiceTaskProcess(camundaClient, processDefinitionId, processDefinitionNameV1, "3");
    final long v1Key = v1Deployment.getProcesses().getFirst().getProcessDefinitionKey();

    // v2: failing service task (incident)
    final var v2Deployment =
        deployServiceTaskProcess(
            camundaClient, processDefinitionId, processDefinitionNameV2, "error");
    final long v2Key = v2Deployment.getProcesses().getFirst().getProcessDefinitionKey();

    // v1: 2 non-incident ACTIVE instances
    startProcessInstance(camundaClient, v1Key);
    startProcessInstance(camundaClient, v1Key);

    // v2: 2 ACTIVE instances with incidents
    startProcessInstance(camundaClient, v2Key);
    startProcessInstance(camundaClient, v2Key);

    // wait until the non-incident instances are ACTIVE
    waitForProcessInstances(
        camundaClient,
        f -> f.processDefinitionKey(v1Key).state(ProcessInstanceState.ACTIVE).hasIncident(false),
        2);

    // wait until the incident instances are ACTIVE with incidents
    waitForProcessInstances(
        camundaClient,
        f -> f.processDefinitionKey(v2Key).state(ProcessInstanceState.ACTIVE).hasIncident(true),
        2);

    waitUntilIncidentsAreActive(camundaClient, 2);

    // when
    final var result =
        camundaClient
            .newProcessDefinitionInstanceVersionStatisticsRequest(processDefinitionId)
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(2);
    assertThat(result.items())
        .extracting(
            ProcessDefinitionInstanceVersionStatistics::getProcessDefinitionId,
            ProcessDefinitionInstanceVersionStatistics::getProcessDefinitionKey,
            ProcessDefinitionInstanceVersionStatistics::getProcessDefinitionName,
            ProcessDefinitionInstanceVersionStatistics::getProcessDefinitionVersion,
            ProcessDefinitionInstanceVersionStatistics::getTenantId,
            ProcessDefinitionInstanceVersionStatistics::getActiveInstancesWithoutIncidentCount,
            ProcessDefinitionInstanceVersionStatistics::getActiveInstancesWithIncidentCount)
        .containsExactlyInAnyOrder(
            tuple(
                processDefinitionId, v1Key, processDefinitionNameV1, 1, DEFAULT_TENANT_ID, 2L, 0L),
            tuple(
                processDefinitionId, v2Key, processDefinitionNameV2, 2, DEFAULT_TENANT_ID, 0L, 2L));

    assertThat(result.page().totalItems()).isEqualTo(2);
    assertThat(result.page().hasMoreTotalItems()).isFalse();
  }

  @Test
  void shouldReturnSeparateStatisticsPerTenantForSameVersion() {
    // given
    final var processDefinitionId = "versions_multi_tenant";
    final var processDefinitionName = "Versions Multi Tenant";

    final var defaultDeployment =
        deployServiceTaskProcess(camundaClient, processDefinitionId, processDefinitionName, "3");
    final long defaultKey = defaultDeployment.getProcesses().getFirst().getProcessDefinitionKey();

    final var tenant1Deployment =
        deployServiceTaskProcess(
            camundaClient, processDefinitionId, processDefinitionName, "3", TENANT_ID_1);
    final long tenant1Key = tenant1Deployment.getProcesses().getFirst().getProcessDefinitionKey();

    // default tenant: 2 ACTIVE instances
    startProcessInstance(camundaClient, defaultKey);
    startProcessInstance(camundaClient, defaultKey);

    // tenant1 tenant: 1 ACTIVE instance
    startProcessInstanceForTenant(camundaClient, processDefinitionId, TENANT_ID_1);

    // wait for all 3 instances to be ACTIVE
    waitForProcessInstances(
        camundaClient,
        f -> f.processDefinitionId(processDefinitionId).state(ProcessInstanceState.ACTIVE),
        3);

    // when
    final var result =
        camundaClient
            .newProcessDefinitionInstanceVersionStatisticsRequest(processDefinitionId)
            .send()
            .join();

    // then
    // We expect two entries: version 1 per <default> and tenant1 tenant
    assertThat(result.items()).hasSize(2);
    assertThat(result.items())
        .extracting(
            ProcessDefinitionInstanceVersionStatistics::getProcessDefinitionId,
            ProcessDefinitionInstanceVersionStatistics::getProcessDefinitionKey,
            ProcessDefinitionInstanceVersionStatistics::getProcessDefinitionName,
            ProcessDefinitionInstanceVersionStatistics::getProcessDefinitionVersion,
            ProcessDefinitionInstanceVersionStatistics::getTenantId,
            ProcessDefinitionInstanceVersionStatistics::getActiveInstancesWithoutIncidentCount,
            ProcessDefinitionInstanceVersionStatistics::getActiveInstancesWithIncidentCount)
        .containsExactlyInAnyOrder(
            tuple(
                processDefinitionId,
                defaultKey,
                processDefinitionName,
                1,
                DEFAULT_TENANT_ID,
                2L,
                0L),
            tuple(processDefinitionId, tenant1Key, processDefinitionName, 1, TENANT_ID_1, 1L, 0L));

    assertThat(result.page().totalItems()).isEqualTo(2);
    assertThat(result.page().hasMoreTotalItems()).isFalse();
  }

  @Test
  void shouldFilterByTenantId() {
    // given
    final var processDefinitionId = "versions_filter_by_tenant";
    final var processDefinitionName = "Versions Filter By Tenant";

    final var defaultDeployment =
        deployServiceTaskProcess(camundaClient, processDefinitionId, processDefinitionName, "3");
    final long defaultKey = defaultDeployment.getProcesses().getFirst().getProcessDefinitionKey();

    final var tenant1Deployment =
        deployServiceTaskProcess(
            camundaClient, processDefinitionId, processDefinitionName, "3", TENANT_ID_1);
    final long tenant1Key = tenant1Deployment.getProcesses().getFirst().getProcessDefinitionKey();

    // default tenant: 1 ACTIVE instance
    startProcessInstance(camundaClient, defaultKey);

    // tenant1 tenant: 2 ACTIVE instances
    startProcessInstanceForTenant(camundaClient, processDefinitionId, TENANT_ID_1);
    startProcessInstanceForTenant(camundaClient, processDefinitionId, TENANT_ID_1);

    // wait for all 3 instances to be ACTIVE
    waitForProcessInstances(
        camundaClient,
        f -> f.processDefinitionId(processDefinitionId).state(ProcessInstanceState.ACTIVE),
        3);

    // when
    final var result =
        camundaClient
            .newProcessDefinitionInstanceVersionStatisticsRequest(processDefinitionId)
            .filter(f -> f.tenantId(TENANT_ID_1))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(1);
    final var stats = result.items().getFirst();
    assertThat(stats.getProcessDefinitionId()).isEqualTo(processDefinitionId);
    assertThat(stats.getProcessDefinitionKey()).isEqualTo(tenant1Key);
    assertThat(stats.getProcessDefinitionName()).isEqualTo(processDefinitionName);
    assertThat(stats.getProcessDefinitionVersion()).isEqualTo(1);
    assertThat(stats.getTenantId()).isEqualTo(TENANT_ID_1);
    assertThat(stats.getActiveInstancesWithoutIncidentCount()).isEqualTo(2);
    assertThat(stats.getActiveInstancesWithIncidentCount()).isEqualTo(0);
    assertThat(result.page().totalItems()).isEqualTo(1);
    assertThat(result.page().hasMoreTotalItems()).isFalse();
  }

  @Test
  void shouldSupportVersionPagination() {
    // given
    final var processDefinitionId = "versions_pagination";

    // create three versions of the same process id, each with one ACTIVE instance
    final var v1Deployment =
        deployServiceTaskProcess(camundaClient, processDefinitionId, "Versions Pagination v1", "3");
    final long v1Key = v1Deployment.getProcesses().getFirst().getProcessDefinitionKey();

    final var v2Deployment =
        deployServiceTaskProcess(camundaClient, processDefinitionId, "Versions Pagination v2", "3");
    final long v2Key = v2Deployment.getProcesses().getFirst().getProcessDefinitionKey();

    final var v3Deployment =
        deployServiceTaskProcess(camundaClient, processDefinitionId, "Versions Pagination v3", "3");
    final long v3Key = v3Deployment.getProcesses().getFirst().getProcessDefinitionKey();

    startProcessInstance(camundaClient, v1Key);
    startProcessInstance(camundaClient, v2Key);
    startProcessInstance(camundaClient, v3Key);

    // wait for all three ACTIVE instances
    waitForProcessInstances(
        camundaClient,
        f -> f.processDefinitionId(processDefinitionId).state(ProcessInstanceState.ACTIVE),
        3);

    // when - page 1 (first 2 versions)
    final var page1 =
        camundaClient
            .newProcessDefinitionInstanceVersionStatisticsRequest(processDefinitionId)
            .page(p -> p.from(0).limit(2))
            .send()
            .join();

    // when - page 2 (remaining versions)
    final var page2 =
        camundaClient
            .newProcessDefinitionInstanceVersionStatisticsRequest(processDefinitionId)
            .page(p -> p.from(2).limit(2))
            .send()
            .join();

    // then
    assertThat(page1.items()).hasSize(2);
    assertThat(page1.items())
        .extracting(
            ProcessDefinitionInstanceVersionStatistics::getProcessDefinitionId,
            ProcessDefinitionInstanceVersionStatistics::getProcessDefinitionKey,
            ProcessDefinitionInstanceVersionStatistics::getProcessDefinitionName,
            ProcessDefinitionInstanceVersionStatistics::getProcessDefinitionVersion,
            ProcessDefinitionInstanceVersionStatistics::getTenantId,
            ProcessDefinitionInstanceVersionStatistics::getActiveInstancesWithoutIncidentCount,
            ProcessDefinitionInstanceVersionStatistics::getActiveInstancesWithIncidentCount)
        .containsExactly(
            tuple(
                processDefinitionId, v1Key, "Versions Pagination v1", 1, DEFAULT_TENANT_ID, 1L, 0L),
            tuple(
                processDefinitionId,
                v2Key,
                "Versions Pagination v2",
                2,
                DEFAULT_TENANT_ID,
                1L,
                0L));

    assertThat(page1.page().totalItems()).isEqualTo(3);
    assertThat(page1.page().hasMoreTotalItems()).isFalse();

    assertThat(page2.items()).hasSize(1);
    assertThat(page2.items())
        .extracting(
            ProcessDefinitionInstanceVersionStatistics::getProcessDefinitionId,
            ProcessDefinitionInstanceVersionStatistics::getProcessDefinitionKey,
            ProcessDefinitionInstanceVersionStatistics::getProcessDefinitionName,
            ProcessDefinitionInstanceVersionStatistics::getProcessDefinitionVersion,
            ProcessDefinitionInstanceVersionStatistics::getTenantId,
            ProcessDefinitionInstanceVersionStatistics::getActiveInstancesWithoutIncidentCount,
            ProcessDefinitionInstanceVersionStatistics::getActiveInstancesWithIncidentCount)
        .containsExactly(
            tuple(
                processDefinitionId,
                v3Key,
                "Versions Pagination v3",
                3,
                DEFAULT_TENANT_ID,
                1L,
                0L));

    assertThat(page2.page().totalItems()).isEqualTo(3);
    assertThat(page2.page().hasMoreTotalItems()).isFalse();
  }

  @Test
  void shouldSortByActiveInstancesWithIncidentCountDesc() {
    // given — deploy multiple versions of the same process
    final var processDefinitionId = "sort_versions_proc";
    final var processDefinitionName = "Sort Versions Proc";

    final var v1 =
        deployServiceTaskProcess(
            camundaClient, processDefinitionId, processDefinitionName, "error");
    final var v1Key = v1.getProcesses().getFirst().getProcessDefinitionKey();

    final var v2 =
        deployServiceTaskProcess(
            camundaClient, processDefinitionId, processDefinitionName, "error");
    final var v2Key = v2.getProcesses().getFirst().getProcessDefinitionKey();

    final var v3 =
        deployServiceTaskProcess(
            camundaClient, processDefinitionId, processDefinitionName, "error");
    final var v3Key = v3.getProcesses().getFirst().getProcessDefinitionKey();

    // Create instances with different incident counts:
    startProcessInstance(camundaClient, v1Key);

    startProcessInstance(camundaClient, v2Key);
    startProcessInstance(camundaClient, v2Key);
    startProcessInstance(camundaClient, v2Key);

    startProcessInstance(camundaClient, v3Key);
    startProcessInstance(camundaClient, v3Key);

    // wait for all instances across all versions to be ACTIVE with incidents
    waitForProcessInstances(
        camundaClient,
        f ->
            f.processDefinitionId(processDefinitionId)
                .state(ProcessInstanceState.ACTIVE)
                .hasIncident(true),
        6);

    // when — request sorted DESC by "activeInstancesWithIncidentCount"
    final var result =
        camundaClient
            .newProcessDefinitionInstanceVersionStatisticsRequest(processDefinitionId)
            .sort(s -> s.activeInstancesWithIncidentCount().desc())
            .send()
            .join();

    // then — extract version statistics in returned order
    final var items = result.items();

    // They should appear in this order:
    // v2 → 3 incidents
    // v3 → 2 incidents
    // v1 → 1 incidents
    assertThat(items).hasSize(3);

    assertThat(items.get(0).getProcessDefinitionVersion())
        .isEqualTo(v2.getProcesses().getFirst().getVersion());
    assertThat(items.get(0).getActiveInstancesWithIncidentCount()).isEqualTo(3);

    assertThat(items.get(1).getProcessDefinitionVersion())
        .isEqualTo(v3.getProcesses().getFirst().getVersion());
    assertThat(items.get(1).getActiveInstancesWithIncidentCount()).isEqualTo(2);

    assertThat(items.get(2).getProcessDefinitionVersion())
        .isEqualTo(v1.getProcesses().getFirst().getVersion());
    assertThat(items.get(2).getActiveInstancesWithIncidentCount()).isEqualTo(1);

    assertThat(result.page().totalItems()).isEqualTo(3);
    assertThat(result.page().hasMoreTotalItems()).isFalse();
  }
}
