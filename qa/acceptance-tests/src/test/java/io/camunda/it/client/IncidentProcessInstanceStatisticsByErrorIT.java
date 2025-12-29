/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.client.api.search.enums.PermissionType.READ_PROCESS_INSTANCE;
import static io.camunda.client.api.search.enums.ResourceType.PROCESS_DEFINITION;
import static io.camunda.it.util.TestHelper.createTenant;
import static io.camunda.it.util.TestHelper.deployResourceForTenant;
import static io.camunda.it.util.TestHelper.waitForJobs;
import static io.camunda.it.util.TestHelper.waitForProcessInstancesToStart;
import static io.camunda.it.util.TestHelper.waitForProcessesToBeDeployed;
import static io.camunda.it.util.TestHelper.waitUntilIncidentsAreActive;
import static io.camunda.it.util.TestHelper.waitUntilIncidentsAreResolved;
import static io.camunda.security.configuration.InitializationConfiguration.DEFAULT_USER_USERNAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.response.Incident;
import io.camunda.client.api.search.response.Job;
import io.camunda.client.api.statistics.response.IncidentProcessInstanceStatisticsByError;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@MultiDbTest
public class IncidentProcessInstanceStatisticsByErrorIT {

  private static final String PASSWORD = "password";

  private static final Permissions READ_PROCESS_DEFINITION_INSTANCES =
      new Permissions(PROCESS_DEFINITION, READ_PROCESS_INSTANCE, List.of("*"));

  private static final String USER_EMPTY = "user-empty";
  private static final String USER_SINGLE_ERROR = "user-single-error";
  private static final String USER_MULTI_ERROR = "user-multi-error";
  private static final String USER_ACTIVE_ONLY = "user-active-only";
  private static final String USER_ERROR_HASH_COLLISION = "user-error-hash-collision";
  private static final String USER_NO_READ_PROCESS_INSTANCE = "user-no-read-process-instance";

  private static final String TENANT_SINGLE_ERROR = "tenant-single-error";
  private static final String TENANT_MULTI_ERROR = "tenant-multi-error";
  private static final String TENANT_ACTIVE_ONLY = "tenant-active-only";
  private static final String TENANT_ERROR_HASH_COLLISION = "tenant-error-hash-collision";
  private static final String TENANT_NO_READ_PROCESS_INSTANCE = "tenant-no-read-process-instance";

  private static final String ERROR_FAIL_1 = "error-fail-1";
  private static final String ERROR_FAIL_2 = "error-fail-2";
  private static final String ERROR_HASH_COLLISION_1 = "Ea";
  private static final String ERROR_HASH_COLLISION_2 = "FB";

  private static final String JOB_TYPE_1 = "jobType1";
  private static final String JOB_TYPE_2 = "jobType2";

  private static final String SIMPLE_PROCESS_1 = "simple-process-1";
  private static final String SIMPLE_PROCESS_2 = "simple-process-2";

  @MultiDbTestApplication
  private static final TestCamundaApplication TEST_INSTANCE =
      new TestCamundaApplication().withBasicAuth().withMultiTenancyEnabled();

  @UserDefinition private static final TestUser U_EMPTY = testUser(USER_EMPTY);
  @UserDefinition private static final TestUser U_SINGLE_ERROR = testUser(USER_SINGLE_ERROR);
  @UserDefinition private static final TestUser U_MULTI_ERROR = testUser(USER_MULTI_ERROR);
  @UserDefinition private static final TestUser U_ACTIVE_ONLY = testUser(USER_ACTIVE_ONLY);

  @UserDefinition
  private static final TestUser U_ERROR_HASH_COLLISION = testUser(USER_ERROR_HASH_COLLISION);

  @UserDefinition
  private static final TestUser U_NO_READ_PROCESS_INSTANCE =
      new TestUser(USER_NO_READ_PROCESS_INSTANCE, PASSWORD, List.of());

  private static CamundaClient adminClient;

  private static TestUser testUser(final String userId) {
    return new TestUser(userId, PASSWORD, List.of(READ_PROCESS_DEFINITION_INSTANCES));
  }

  @BeforeAll
  public static void beforeAll(@Authenticated final CamundaClient adminClient) {
    IncidentProcessInstanceStatisticsByErrorIT.adminClient = adminClient;
  }

  private static void ensureTenantExistsForUser(
      final CamundaClient adminClient, final String tenantId, final String userId) {
    createTenant(adminClient, tenantId, tenantId, userId);
  }

  @Test
  void shouldReturnEmptyStatisticsWhenNoActiveIncidentsExist(
      @Authenticated(USER_EMPTY) final CamundaClient userClient) {
    // when
    final var result =
        userClient.newIncidentProcessInstanceStatisticsByErrorRequest().send().join();

    // then
    assertThat(result.items()).isEmpty();
    assertThat(result.page().totalItems()).isEqualTo(0);
    assertThat(result.page().hasMoreTotalItems()).isFalse();
  }

  @Test
  void shouldReturnSingleErrorStatisticForMultipleFailingInstances(
      @Authenticated(USER_SINGLE_ERROR) final CamundaClient userClient) {
    // given
    ensureTenantExistsForUser(adminClient, TENANT_SINGLE_ERROR, USER_SINGLE_ERROR);

    final String processId = SIMPLE_PROCESS_1;
    final BpmnModelInstance model = singleServiceTaskProcess(processId, JOB_TYPE_1);

    final long processDefinitionKey =
        deployAndWait(userClient, model, resourceName(processId), TENANT_SINGLE_ERROR);

    final int numberOfInstances = 5;
    final List<Long> processInstanceKeys =
        createInstances(userClient, processDefinitionKey, TENANT_SINGLE_ERROR, numberOfInstances);

    waitForProcessInstancesToStart(userClient, numberOfInstances);

    waitForJobs(userClient, processInstanceKeys);

    final List<Long> jobKeys = findJobKeysForInstances(userClient, processInstanceKeys, JOB_TYPE_1);

    failJobs(userClient, jobKeys, ERROR_FAIL_1);

    waitUntilIncidentsAreActive(userClient, numberOfInstances);

    // when
    final var result =
        userClient.newIncidentProcessInstanceStatisticsByErrorRequest().send().join();

    // then
    assertThat(result.page().totalItems()).isEqualTo(1);
    assertThat(result.page().hasMoreTotalItems()).isFalse();
    assertThat(result.items()).hasSize(1);

    assertThat(result.items())
        .extracting(
            IncidentProcessInstanceStatisticsByError::getErrorMessage,
            IncidentProcessInstanceStatisticsByError::getActiveInstancesWithErrorCount)
        .containsExactly(tuple(ERROR_FAIL_1, (long) numberOfInstances));
  }

  @Test
  void shouldReturnMultipleErrorStatisticsForMultipleFailingInstances(
      @Authenticated(USER_MULTI_ERROR) final CamundaClient userClient) {

    // given
    ensureTenantExistsForUser(adminClient, TENANT_MULTI_ERROR, USER_MULTI_ERROR);

    final String process1Id = SIMPLE_PROCESS_1;
    final String process2Id = SIMPLE_PROCESS_2;

    final BpmnModelInstance model1 = singleServiceTaskProcess(process1Id, JOB_TYPE_1);
    final BpmnModelInstance model2 = singleServiceTaskProcess(process2Id, JOB_TYPE_2);

    final long processDefinitionKey1 =
        deployAndWait(userClient, model1, resourceName(process1Id), TENANT_MULTI_ERROR);
    final long processDefinitionKey2 =
        deployAndWait(userClient, model2, resourceName(process2Id), TENANT_MULTI_ERROR);

    final int numberOfInstancesPerError = 3;

    final List<Long> process1InstanceKeys =
        createInstances(
            userClient, processDefinitionKey1, TENANT_MULTI_ERROR, numberOfInstancesPerError);
    final List<Long> process2InstanceKeys =
        createInstances(
            userClient, processDefinitionKey2, TENANT_MULTI_ERROR, numberOfInstancesPerError);

    waitForProcessInstancesToStart(userClient, numberOfInstancesPerError * 2);

    waitForJobs(
        userClient,
        Stream.concat(process1InstanceKeys.stream(), process2InstanceKeys.stream()).toList());

    final List<Long> jobKeys1 =
        findJobKeysForInstances(userClient, process1InstanceKeys, JOB_TYPE_1);
    final List<Long> jobKeys2 =
        findJobKeysForInstances(userClient, process2InstanceKeys, JOB_TYPE_2);

    failJobs(userClient, jobKeys1, ERROR_FAIL_1);
    failJobs(userClient, jobKeys2, ERROR_FAIL_2);

    waitUntilIncidentsAreActive(userClient, numberOfInstancesPerError * 2);

    // when
    final var result =
        userClient.newIncidentProcessInstanceStatisticsByErrorRequest().send().join();

    // then
    assertThat(result.page().totalItems()).isEqualTo(2);
    assertThat(result.page().hasMoreTotalItems()).isFalse();
    assertThat(result.items()).hasSize(2);

    assertThat(result.items())
        .extracting(
            IncidentProcessInstanceStatisticsByError::getErrorMessage,
            IncidentProcessInstanceStatisticsByError::getActiveInstancesWithErrorCount)
        .containsExactlyInAnyOrder(
            tuple(ERROR_FAIL_1, (long) numberOfInstancesPerError),
            tuple(ERROR_FAIL_2, (long) numberOfInstancesPerError));
  }

  @Test
  void shouldReturnOnlyActiveIncidentsInStatistics(
      @Authenticated(USER_ACTIVE_ONLY) final CamundaClient userClient) {

    // given
    ensureTenantExistsForUser(adminClient, TENANT_ACTIVE_ONLY, USER_ACTIVE_ONLY);

    final String processId = SIMPLE_PROCESS_1;
    final BpmnModelInstance model = singleServiceTaskProcess(processId, JOB_TYPE_1);

    final long processDefinitionKey =
        deployAndWait(userClient, model, resourceName(processId), TENANT_ACTIVE_ONLY);

    final int totalInstances = 6;
    final List<Long> processInstanceKeys =
        createInstances(userClient, processDefinitionKey, TENANT_ACTIVE_ONLY, totalInstances);

    waitForProcessInstancesToStart(userClient, totalInstances);

    waitForJobs(userClient, processInstanceKeys);

    final List<Long> instancesToFail = processInstanceKeys.subList(0, 4);
    final List<Long> jobKeysToFail =
        findJobKeysForInstances(userClient, instancesToFail, JOB_TYPE_1);

    failJobs(userClient, jobKeysToFail, ERROR_FAIL_1);

    waitUntilIncidentsAreActive(userClient, jobKeysToFail.size());

    final List<Long> instancesToResolve = instancesToFail.subList(0, 2);
    final List<Long> incidentKeysToResolve =
        findIncidentKeysForInstances(userClient, instancesToResolve);

    increaseRetries(userClient, jobKeysToFail);
    resolveIncidents(userClient, incidentKeysToResolve);

    waitUntilIncidentsAreResolved(userClient, 2);

    // when
    final var result =
        userClient.newIncidentProcessInstanceStatisticsByErrorRequest().send().join();

    // then
    assertThat(result.page().totalItems()).isEqualTo(1);
    assertThat(result.page().hasMoreTotalItems()).isFalse();
    assertThat(result.items()).hasSize(1);

    assertThat(result.items())
        .extracting(IncidentProcessInstanceStatisticsByError::getErrorMessage)
        .containsExactly(ERROR_FAIL_1);
    assertThat(result.items())
        .extracting(IncidentProcessInstanceStatisticsByError::getActiveInstancesWithErrorCount)
        .containsExactly(2L);
  }

  @Test
  void shouldReturnSeparateItemsWhenErrorHashCodeCollides(
      @Authenticated(USER_ERROR_HASH_COLLISION) final CamundaClient userClient) {

    // given
    ensureTenantExistsForUser(adminClient, TENANT_ERROR_HASH_COLLISION, USER_ERROR_HASH_COLLISION);

    final String processId = SIMPLE_PROCESS_1;
    final BpmnModelInstance model1 = singleServiceTaskProcess(processId, JOB_TYPE_1);
    final BpmnModelInstance model2 = singleServiceTaskProcess(processId, JOB_TYPE_2);

    final long processDefinitionKey1 =
        deployAndWait(userClient, model1, resourceName(processId), TENANT_ERROR_HASH_COLLISION);
    final long processDefinitionKey2 =
        deployAndWait(userClient, model2, resourceName(processId), TENANT_ERROR_HASH_COLLISION);

    final int numberOfInstancesPerError = 4;

    final List<Long> process1InstanceKeys =
        createInstances(
            userClient,
            processDefinitionKey1,
            TENANT_ERROR_HASH_COLLISION,
            numberOfInstancesPerError);
    final List<Long> process2InstanceKeys =
        createInstances(
            userClient,
            processDefinitionKey2,
            TENANT_ERROR_HASH_COLLISION,
            numberOfInstancesPerError);

    waitForProcessInstancesToStart(userClient, numberOfInstancesPerError * 2);
    waitForJobs(
        userClient,
        Stream.concat(process1InstanceKeys.stream(), process2InstanceKeys.stream()).toList());

    final List<Long> jobKeys1 =
        findJobKeysForInstances(userClient, process1InstanceKeys, JOB_TYPE_1);
    final List<Long> jobKeys2 =
        findJobKeysForInstances(userClient, process2InstanceKeys, JOB_TYPE_2);

    failJobs(userClient, jobKeys1, ERROR_HASH_COLLISION_1);
    failJobs(userClient, jobKeys2, ERROR_HASH_COLLISION_2);

    waitUntilIncidentsAreActive(userClient, numberOfInstancesPerError * 2);

    // when
    final var result =
        userClient.newIncidentProcessInstanceStatisticsByErrorRequest().send().join();

    // then
    assertThat(result.page().totalItems()).isEqualTo(2);
    assertThat(result.page().hasMoreTotalItems()).isFalse();
    assertThat(result.items()).hasSize(2);

    final int sameHashCode = ERROR_HASH_COLLISION_1.hashCode();

    assertThat(result.items())
        .extracting(
            IncidentProcessInstanceStatisticsByError::getErrorHashCode,
            IncidentProcessInstanceStatisticsByError::getErrorMessage,
            IncidentProcessInstanceStatisticsByError::getActiveInstancesWithErrorCount)
        .containsExactlyInAnyOrder(
            tuple(sameHashCode, ERROR_HASH_COLLISION_1, (long) numberOfInstancesPerError),
            tuple(sameHashCode, ERROR_HASH_COLLISION_2, (long) numberOfInstancesPerError));
  }

  @Test
  void shouldReturnNoStatisticsForUserWithoutReadProcessInstance(
      @Authenticated(USER_NO_READ_PROCESS_INSTANCE) final CamundaClient userClient) {
    // given
    ensureTenantExistsForUser(adminClient, TENANT_NO_READ_PROCESS_INSTANCE, DEFAULT_USER_USERNAME);

    final BpmnModelInstance process = singleServiceTaskProcess(SIMPLE_PROCESS_1, JOB_TYPE_1);

    final long processDefinitionKey =
        deployAndWait(
            adminClient, process, resourceName(SIMPLE_PROCESS_1), TENANT_NO_READ_PROCESS_INSTANCE);

    final List<Long> instanceKeys =
        createInstances(adminClient, processDefinitionKey, TENANT_NO_READ_PROCESS_INSTANCE, 1);
    waitForProcessInstancesToStart(adminClient, 1);

    waitForJobs(adminClient, instanceKeys);

    final List<Long> jobKeys = findJobKeysForInstances(adminClient, instanceKeys, JOB_TYPE_1);
    failJobs(adminClient, jobKeys, ERROR_FAIL_1);
    waitUntilIncidentsAreActive(adminClient, 1);

    final var adminResult =
        adminClient.newIncidentProcessInstanceStatisticsByErrorRequest().send().join();
    assertThat(adminResult.items()).isNotEmpty();

    // when
    final var result =
        userClient.newIncidentProcessInstanceStatisticsByErrorRequest().send().join();

    // then
    assertThat(result.items()).isEmpty();
  }

  private static BpmnModelInstance singleServiceTaskProcess(
      final String bpmnProcessId, final String jobType) {
    return Bpmn.createExecutableProcess(bpmnProcessId)
        .startEvent()
        .serviceTask("serviceTask", t -> t.zeebeJobType(jobType))
        .endEvent()
        .done();
  }

  private static String resourceName(final String processId) {
    return processId + ".bpmn";
  }

  private static long deployAndWait(
      final CamundaClient client,
      final BpmnModelInstance model,
      final String resourceName,
      final String tenantId) {
    final long processDefinitionKey =
        deployResourceForTenant(client, model, resourceName, tenantId)
            .getProcesses()
            .getFirst()
            .getProcessDefinitionKey();
    waitForProcessesToBeDeployed(client, f -> f.processDefinitionKey(processDefinitionKey), 1);
    return processDefinitionKey;
  }

  private static List<Long> createInstances(
      final CamundaClient client,
      final long processDefinitionKey,
      final String tenantId,
      final int count) {
    final List<Long> keys = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      keys.add(
          client
              .newCreateInstanceCommand()
              .processDefinitionKey(processDefinitionKey)
              .tenantId(tenantId)
              .send()
              .join()
              .getProcessInstanceKey());
    }
    return keys;
  }

  private static List<Long> findJobKeysForInstances(
      final CamundaClient client, final List<Long> processInstanceKeys, final String jobType) {
    final List<Long> keys =
        client
            .newJobSearchRequest()
            .filter(f -> f.processInstanceKey(ops -> ops.in(processInstanceKeys)).type(jobType))
            .send()
            .join()
            .items()
            .stream()
            .map(Job::getJobKey)
            .sorted(Comparator.naturalOrder())
            .toList();
    assertThat(keys).hasSize(processInstanceKeys.size());
    return keys;
  }

  private static void failJobs(
      final CamundaClient client, final List<Long> jobKeys, final String errorMessage) {
    for (final Long jobKey : jobKeys) {
      client.newFailCommand(jobKey).retries(0).errorMessage(errorMessage).send().join();
    }
  }

  private static void increaseRetries(final CamundaClient client, final List<Long> jobKeys) {
    for (final Long jobKey : jobKeys) {
      client.newUpdateJobCommand(jobKey).updateRetries(1).send().join();
    }
  }

  private static List<Long> findIncidentKeysForInstances(
      final CamundaClient client, final List<Long> processInstanceKeys) {
    return client
        .newIncidentSearchRequest()
        .filter(f -> f.processInstanceKey(ops -> ops.in(processInstanceKeys)))
        .send()
        .join()
        .items()
        .stream()
        .map(Incident::getIncidentKey)
        .sorted(Comparator.naturalOrder())
        .toList();
  }

  private static void resolveIncidents(final CamundaClient client, final List<Long> incidentKeys) {
    for (final Long incidentKey : incidentKeys) {
      client.newResolveIncidentCommand(incidentKey).send().join();
    }
  }
}
