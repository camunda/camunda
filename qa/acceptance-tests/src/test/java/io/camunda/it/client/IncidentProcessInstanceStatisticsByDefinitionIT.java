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
import static io.camunda.security.configuration.InitializationConfiguration.DEFAULT_USER_USERNAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.response.Job;
import io.camunda.client.api.statistics.response.IncidentProcessInstanceStatisticsByDefinition;
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
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms.*$")
public class IncidentProcessInstanceStatisticsByDefinitionIT {

  private static final String PASSWORD = "password";

  private static final Permissions READ_PROCESS_DEFINITION_INSTANCES =
      new Permissions(PROCESS_DEFINITION, READ_PROCESS_INSTANCE, List.of("*"));

  private static final String USER_EMPTY = "user-empty-definition";
  private static final String USER_SINGLE_DEFINITION = "user-single-definition";
  private static final String USER_MULTI_DEFINITION = "user-multi-definition";
  private static final String USER_NO_READ_PROCESS_INSTANCE = "user-no-read-process-instance-def";
  private static final String USER_HASH_COLLISION = "user-definition-hash-collision";
  private static final String USER_MULTI_TENANT = "user-definition-multi-tenant";
  private static final String USER_MULTI_VERSION = "user-definition-multi-version";

  private static final String TENANT_SINGLE_DEFINITION = "tenant-single-definition";
  private static final String TENANT_MULTI_DEFINITION = "tenant-multi-definition";
  private static final String TENANT_NO_READ_PROCESS_INSTANCE = "tenant-no-read-pi-definition";
  private static final String TENANT_HASH_COLLISION = "tenant-hash-collision";
  private static final String TENANT_MULTI_TENANT_1 = "tenant-multi-tenant-1";
  private static final String TENANT_MULTI_TENANT_2 = "tenant-multi-tenant-2";
  private static final String TENANT_MULTI_VERSION = "tenant-multi-version";

  // Java hash collision pair (same as used in the ByError IT)
  private static final String ERROR_HASH_COLLISION_1 = "error-hash-collision-Ea";
  private static final String ERROR_HASH_COLLISION_2 = "error-hash-collision-FB";

  private static final String ERROR_FAIL_1 = "error-fail-1";
  private static final String ERROR_FAIL_2 = "error-fail-2";

  private static final String JOB_TYPE_1 = "jobType1";
  private static final String JOB_TYPE_2 = "jobType2";

  private static final String PROCESS_NAME_1 = "Simple Process 1";
  private static final String PROCESS_NAME_2 = "Simple Process 2";

  private static final String SIMPLE_PROCESS_1 = "simple-process-1";
  private static final String SIMPLE_PROCESS_2 = "simple-process-2";

  @MultiDbTestApplication
  private static final TestCamundaApplication TEST_INSTANCE =
      new TestCamundaApplication().withBasicAuth().withMultiTenancyEnabled();

  @UserDefinition private static final TestUser U_EMPTY = testUser(USER_EMPTY);

  @UserDefinition
  private static final TestUser U_SINGLE_DEFINITION = testUser(USER_SINGLE_DEFINITION);

  @UserDefinition
  private static final TestUser U_MULTI_DEFINITION = testUser(USER_MULTI_DEFINITION);

  @UserDefinition
  private static final TestUser U_NO_READ_PROCESS_INSTANCE =
      new TestUser(USER_NO_READ_PROCESS_INSTANCE, PASSWORD, List.of());

  @UserDefinition private static final TestUser U_HASH_COLLISION = testUser(USER_HASH_COLLISION);

  @UserDefinition private static final TestUser U_MULTI_TENANT = testUser(USER_MULTI_TENANT);

  @UserDefinition private static final TestUser U_MULTI_VERSION = testUser(USER_MULTI_VERSION);

  private static CamundaClient adminClient;

  private static TestUser testUser(final String userId) {
    return new TestUser(userId, PASSWORD, List.of(READ_PROCESS_DEFINITION_INSTANCES));
  }

  @BeforeAll
  public static void beforeAll(@Authenticated final CamundaClient adminClient) {
    IncidentProcessInstanceStatisticsByDefinitionIT.adminClient = adminClient;
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
        userClient
            .newIncidentProcessInstanceStatisticsByDefinitionRequest(ERROR_FAIL_1.hashCode())
            .send()
            .join();

    // then
    assertThat(result.items()).isEmpty();
    assertThat(result.page().totalItems()).isEqualTo(0);
    assertThat(result.page().hasMoreTotalItems()).isFalse();
  }

  @Test
  void shouldReturnSingleDefinitionStatisticForMultipleFailingInstances(
      @Authenticated(USER_SINGLE_DEFINITION) final CamundaClient userClient) {
    // given
    ensureTenantExistsForUser(adminClient, TENANT_SINGLE_DEFINITION, USER_SINGLE_DEFINITION);

    final String processId = SIMPLE_PROCESS_1;
    final BpmnModelInstance model = singleServiceTaskProcess(processId, PROCESS_NAME_1, JOB_TYPE_1);

    final long processDefinitionKey =
        deployAndWait(userClient, model, resourceName(processId), TENANT_SINGLE_DEFINITION);

    final int numberOfInstances = 5;
    final List<Long> processInstanceKeys =
        createInstances(
            userClient, processDefinitionKey, TENANT_SINGLE_DEFINITION, numberOfInstances);

    waitForProcessInstancesToStart(userClient, numberOfInstances);
    waitForJobs(userClient, processInstanceKeys);

    final List<Long> jobKeys = findJobKeysForInstances(userClient, processInstanceKeys, JOB_TYPE_1);
    failJobs(userClient, jobKeys, ERROR_FAIL_1);
    waitUntilIncidentsAreActive(userClient, numberOfInstances);

    // when
    final var result =
        userClient
            .newIncidentProcessInstanceStatisticsByDefinitionRequest(ERROR_FAIL_1.hashCode())
            .send()
            .join();

    // then
    assertThat(result.page().totalItems()).isEqualTo(1);
    assertThat(result.page().hasMoreTotalItems()).isFalse();
    assertThat(result.items()).hasSize(1);

    assertThat(result.items())
        .extracting(
            IncidentProcessInstanceStatisticsByDefinition::getProcessDefinitionKey,
            IncidentProcessInstanceStatisticsByDefinition::getProcessDefinitionId,
            IncidentProcessInstanceStatisticsByDefinition::getProcessDefinitionName,
            IncidentProcessInstanceStatisticsByDefinition::getProcessDefinitionVersion,
            IncidentProcessInstanceStatisticsByDefinition::getTenantId,
            IncidentProcessInstanceStatisticsByDefinition::getActiveInstancesWithErrorCount)
        .containsExactly(
            tuple(
                processDefinitionKey,
                SIMPLE_PROCESS_1,
                PROCESS_NAME_1,
                1,
                TENANT_SINGLE_DEFINITION,
                (long) numberOfInstances));
  }

  @Test
  void shouldReturnMultipleDefinitionStatisticsForMultipleFailingDefinitions(
      @Authenticated(USER_MULTI_DEFINITION) final CamundaClient userClient) {

    // given
    ensureTenantExistsForUser(adminClient, TENANT_MULTI_DEFINITION, USER_MULTI_DEFINITION);

    final String process1Id = SIMPLE_PROCESS_1;
    final String process2Id = SIMPLE_PROCESS_2;

    final BpmnModelInstance model1 =
        singleServiceTaskProcess(process1Id, PROCESS_NAME_1, JOB_TYPE_1);
    final BpmnModelInstance model2 =
        singleServiceTaskProcess(process2Id, PROCESS_NAME_2, JOB_TYPE_2);

    final long processDefinitionKey1 =
        deployAndWait(userClient, model1, resourceName(process1Id), TENANT_MULTI_DEFINITION);
    final long processDefinitionKey2 =
        deployAndWait(userClient, model2, resourceName(process2Id), TENANT_MULTI_DEFINITION);

    final int numberOfInstancesPerDefinition = 3;

    final List<Long> process1InstanceKeys =
        createInstances(
            userClient,
            processDefinitionKey1,
            TENANT_MULTI_DEFINITION,
            numberOfInstancesPerDefinition);
    final List<Long> process2InstanceKeys =
        createInstances(
            userClient,
            processDefinitionKey2,
            TENANT_MULTI_DEFINITION,
            numberOfInstancesPerDefinition);

    waitForProcessInstancesToStart(userClient, numberOfInstancesPerDefinition * 2);
    waitForJobs(
        userClient,
        Stream.concat(process1InstanceKeys.stream(), process2InstanceKeys.stream()).toList());

    final List<Long> jobKeys1 =
        findJobKeysForInstances(userClient, process1InstanceKeys, JOB_TYPE_1);
    final List<Long> jobKeys2 =
        findJobKeysForInstances(userClient, process2InstanceKeys, JOB_TYPE_2);

    // Cause incidents with the SAME error hash code across both definitions so the endpoint returns
    // 2 items.
    failJobs(userClient, jobKeys1, ERROR_FAIL_1);
    failJobs(userClient, jobKeys2, ERROR_FAIL_1);

    waitUntilIncidentsAreActive(userClient, numberOfInstancesPerDefinition * 2);

    // when
    final var result =
        userClient
            .newIncidentProcessInstanceStatisticsByDefinitionRequest(ERROR_FAIL_1.hashCode())
            .send()
            .join();

    // then
    assertThat(result.page().totalItems()).isEqualTo(2);
    assertThat(result.page().hasMoreTotalItems()).isFalse();
    assertThat(result.items()).hasSize(2);

    assertThat(result.items())
        .extracting(
            IncidentProcessInstanceStatisticsByDefinition::getProcessDefinitionKey,
            IncidentProcessInstanceStatisticsByDefinition::getTenantId,
            IncidentProcessInstanceStatisticsByDefinition::getActiveInstancesWithErrorCount)
        .containsExactlyInAnyOrder(
            tuple(
                processDefinitionKey1,
                TENANT_MULTI_DEFINITION,
                (long) numberOfInstancesPerDefinition),
            tuple(
                processDefinitionKey2,
                TENANT_MULTI_DEFINITION,
                (long) numberOfInstancesPerDefinition));
  }

  @Test
  void shouldReturnNoStatisticsForUserWithoutReadProcessInstance(
      @Authenticated(USER_NO_READ_PROCESS_INSTANCE) final CamundaClient userClient) {
    // given
    ensureTenantExistsForUser(adminClient, TENANT_NO_READ_PROCESS_INSTANCE, DEFAULT_USER_USERNAME);

    final BpmnModelInstance process =
        singleServiceTaskProcess(SIMPLE_PROCESS_1, PROCESS_NAME_1, JOB_TYPE_1);

    final long processDefinitionKey =
        deployAndWait(
            adminClient, process, resourceName(SIMPLE_PROCESS_1), TENANT_NO_READ_PROCESS_INSTANCE);

    final List<Long> instanceKeys =
        createInstances(adminClient, processDefinitionKey, TENANT_NO_READ_PROCESS_INSTANCE, 1);

    waitForProcessInstancesToStart(adminClient, 1);
    waitForJobs(adminClient, instanceKeys);

    final List<Long> jobKeys = findJobKeysForInstances(adminClient, instanceKeys, JOB_TYPE_1);
    failJobs(adminClient, jobKeys, ERROR_FAIL_2);
    waitUntilIncidentsAreActive(adminClient, 1);

    final var adminResult =
        adminClient
            .newIncidentProcessInstanceStatisticsByDefinitionRequest(ERROR_FAIL_2.hashCode())
            .send()
            .join();
    assertThat(adminResult.items()).isNotEmpty();

    // when
    final var result =
        userClient
            .newIncidentProcessInstanceStatisticsByDefinitionRequest(ERROR_FAIL_2.hashCode())
            .send()
            .join();

    // then
    assertThat(result.items()).isEmpty();
  }

  @Test
  void shouldReturnSeparateItemsWhenErrorHashCodeCollides(
      @Authenticated(USER_HASH_COLLISION) final CamundaClient userClient) {

    // given
    ensureTenantExistsForUser(adminClient, TENANT_HASH_COLLISION, USER_HASH_COLLISION);

    final String processId = SIMPLE_PROCESS_1;
    final BpmnModelInstance model1 =
        singleServiceTaskProcess(processId, PROCESS_NAME_1, JOB_TYPE_1);
    final BpmnModelInstance model2 =
        singleServiceTaskProcess(processId, PROCESS_NAME_1, JOB_TYPE_2);

    final long processDefinitionKey1 =
        deployAndWait(userClient, model1, resourceName(processId), TENANT_HASH_COLLISION);
    final long processDefinitionKey2 =
        deployAndWait(userClient, model2, resourceName(processId), TENANT_HASH_COLLISION);

    final int numberOfInstancesPerDefinition = 4;

    final List<Long> process1InstanceKeys =
        createInstances(
            userClient,
            processDefinitionKey1,
            TENANT_HASH_COLLISION,
            numberOfInstancesPerDefinition);
    final List<Long> process2InstanceKeys =
        createInstances(
            userClient,
            processDefinitionKey2,
            TENANT_HASH_COLLISION,
            numberOfInstancesPerDefinition);

    waitForProcessInstancesToStart(userClient, numberOfInstancesPerDefinition * 2);
    waitForJobs(
        userClient,
        Stream.concat(process1InstanceKeys.stream(), process2InstanceKeys.stream()).toList());

    final List<Long> jobKeys1 =
        findJobKeysForInstances(userClient, process1InstanceKeys, JOB_TYPE_1);
    final List<Long> jobKeys2 =
        findJobKeysForInstances(userClient, process2InstanceKeys, JOB_TYPE_2);

    // Different messages, same Java hash code (colliding). Endpoint is scoped by hash code.
    failJobs(userClient, jobKeys1, ERROR_HASH_COLLISION_1);
    failJobs(userClient, jobKeys2, ERROR_HASH_COLLISION_2);

    waitUntilIncidentsAreActive(userClient, numberOfInstancesPerDefinition * 2);

    final int sameHashCode = ERROR_HASH_COLLISION_1.hashCode();

    // when
    final var result =
        userClient
            .newIncidentProcessInstanceStatisticsByDefinitionRequest(sameHashCode)
            .send()
            .join();

    // then
    // We expect two entries because the grouping is by definition (key+tenant), not by error
    // message.
    assertThat(result.page().totalItems()).isEqualTo(2);
    assertThat(result.page().hasMoreTotalItems()).isFalse();

    assertThat(result.items())
        .extracting(
            IncidentProcessInstanceStatisticsByDefinition::getProcessDefinitionKey,
            IncidentProcessInstanceStatisticsByDefinition::getProcessDefinitionId,
            IncidentProcessInstanceStatisticsByDefinition::getProcessDefinitionName,
            IncidentProcessInstanceStatisticsByDefinition::getProcessDefinitionVersion,
            IncidentProcessInstanceStatisticsByDefinition::getTenantId,
            IncidentProcessInstanceStatisticsByDefinition::getActiveInstancesWithErrorCount)
        .containsExactlyInAnyOrder(
            tuple(
                processDefinitionKey1,
                SIMPLE_PROCESS_1,
                PROCESS_NAME_1,
                1,
                TENANT_HASH_COLLISION,
                (long) numberOfInstancesPerDefinition),
            tuple(
                processDefinitionKey2,
                SIMPLE_PROCESS_1,
                PROCESS_NAME_1,
                2,
                TENANT_HASH_COLLISION,
                (long) numberOfInstancesPerDefinition));
  }

  @Test
  void shouldReturnSeparateItemsPerTenantForSameProcessAndError(
      @Authenticated(USER_MULTI_TENANT) final CamundaClient userClient) {

    // given
    ensureTenantExistsForUser(adminClient, TENANT_MULTI_TENANT_1, USER_MULTI_TENANT);
    ensureTenantExistsForUser(adminClient, TENANT_MULTI_TENANT_2, USER_MULTI_TENANT);

    final String processId = SIMPLE_PROCESS_1;
    final BpmnModelInstance model = singleServiceTaskProcess(processId, PROCESS_NAME_1, JOB_TYPE_1);

    final long processDefinitionKeyTenant1 =
        deployAndWait(userClient, model, resourceName(processId), TENANT_MULTI_TENANT_1);
    final long processDefinitionKeyTenant2 =
        deployAndWait(userClient, model, resourceName(processId), TENANT_MULTI_TENANT_2);

    final int instancesPerTenant = 2;

    final List<Long> instanceKeysTenant1 =
        createInstances(
            userClient, processDefinitionKeyTenant1, TENANT_MULTI_TENANT_1, instancesPerTenant);
    final List<Long> instanceKeysTenant2 =
        createInstances(
            userClient, processDefinitionKeyTenant2, TENANT_MULTI_TENANT_2, instancesPerTenant);

    waitForProcessInstancesToStart(userClient, instancesPerTenant * 2);

    waitForJobs(
        userClient,
        Stream.concat(instanceKeysTenant1.stream(), instanceKeysTenant2.stream()).toList());

    failJobs(
        userClient,
        findJobKeysForInstances(userClient, instanceKeysTenant1, JOB_TYPE_1),
        ERROR_FAIL_1);
    failJobs(
        userClient,
        findJobKeysForInstances(userClient, instanceKeysTenant2, JOB_TYPE_1),
        ERROR_FAIL_1);

    waitUntilIncidentsAreActive(userClient, instancesPerTenant * 2);

    // when
    final var result =
        userClient
            .newIncidentProcessInstanceStatisticsByDefinitionRequest(ERROR_FAIL_1.hashCode())
            .send()
            .join();

    // then
    assertThat(result.page().totalItems()).isEqualTo(2);
    assertThat(result.page().hasMoreTotalItems()).isFalse();

    assertThat(result.items())
        .extracting(
            IncidentProcessInstanceStatisticsByDefinition::getProcessDefinitionKey,
            IncidentProcessInstanceStatisticsByDefinition::getProcessDefinitionId,
            IncidentProcessInstanceStatisticsByDefinition::getProcessDefinitionName,
            IncidentProcessInstanceStatisticsByDefinition::getProcessDefinitionVersion,
            IncidentProcessInstanceStatisticsByDefinition::getTenantId,
            IncidentProcessInstanceStatisticsByDefinition::getActiveInstancesWithErrorCount)
        .containsExactlyInAnyOrder(
            tuple(
                processDefinitionKeyTenant1,
                SIMPLE_PROCESS_1,
                PROCESS_NAME_1,
                1,
                TENANT_MULTI_TENANT_1,
                (long) instancesPerTenant),
            tuple(
                processDefinitionKeyTenant2,
                SIMPLE_PROCESS_1,
                PROCESS_NAME_1,
                1,
                TENANT_MULTI_TENANT_2,
                (long) instancesPerTenant));
  }

  @Test
  void shouldReturnSeparateItemsForDifferentVersionsWithinSameTenant(
      @Authenticated(USER_MULTI_VERSION) final CamundaClient userClient) {

    // given
    ensureTenantExistsForUser(adminClient, TENANT_MULTI_VERSION, USER_MULTI_VERSION);

    final String processId = SIMPLE_PROCESS_1;

    // Deploy v1 (jobType1)
    final long processDefinitionKeyV1 =
        deployAndWait(
            userClient,
            singleServiceTaskProcess(processId, PROCESS_NAME_1, JOB_TYPE_1),
            resourceName(processId),
            TENANT_MULTI_VERSION);

    // Deploy v2 (same BPMN process id, different job type)
    final long processDefinitionKeyV2 =
        deployAndWait(
            userClient,
            singleServiceTaskProcess(processId, PROCESS_NAME_1, JOB_TYPE_2),
            resourceName(processId),
            TENANT_MULTI_VERSION);

    // Sanity: keys should differ
    assertThat(processDefinitionKeyV2).isNotEqualTo(processDefinitionKeyV1);

    final int instancesPerVersion = 2;

    final List<Long> v1InstanceKeys =
        createInstances(
            userClient, processDefinitionKeyV1, TENANT_MULTI_VERSION, instancesPerVersion);
    final List<Long> v2InstanceKeys =
        createInstances(
            userClient, processDefinitionKeyV2, TENANT_MULTI_VERSION, instancesPerVersion);

    waitForProcessInstancesToStart(userClient, instancesPerVersion * 2);

    waitForJobs(
        userClient, Stream.concat(v1InstanceKeys.stream(), v2InstanceKeys.stream()).toList());

    failJobs(
        userClient, findJobKeysForInstances(userClient, v1InstanceKeys, JOB_TYPE_1), ERROR_FAIL_1);
    failJobs(
        userClient, findJobKeysForInstances(userClient, v2InstanceKeys, JOB_TYPE_2), ERROR_FAIL_1);

    waitUntilIncidentsAreActive(userClient, instancesPerVersion * 2);

    // when
    final var result =
        userClient
            .newIncidentProcessInstanceStatisticsByDefinitionRequest(ERROR_FAIL_1.hashCode())
            .send()
            .join();

    // then
    // Each version is a different processDefinitionKey, so results should contain two items.
    assertThat(result.page().totalItems()).isEqualTo(2);
    assertThat(result.page().hasMoreTotalItems()).isFalse();

    assertThat(result.items())
        .extracting(
            IncidentProcessInstanceStatisticsByDefinition::getProcessDefinitionKey,
            IncidentProcessInstanceStatisticsByDefinition::getProcessDefinitionId,
            IncidentProcessInstanceStatisticsByDefinition::getProcessDefinitionName,
            IncidentProcessInstanceStatisticsByDefinition::getProcessDefinitionVersion,
            IncidentProcessInstanceStatisticsByDefinition::getTenantId,
            IncidentProcessInstanceStatisticsByDefinition::getActiveInstancesWithErrorCount)
        .containsExactlyInAnyOrder(
            tuple(
                processDefinitionKeyV1,
                SIMPLE_PROCESS_1,
                PROCESS_NAME_1,
                1,
                TENANT_MULTI_VERSION,
                (long) instancesPerVersion),
            tuple(
                processDefinitionKeyV2,
                SIMPLE_PROCESS_1,
                PROCESS_NAME_1,
                2,
                TENANT_MULTI_VERSION,
                (long) instancesPerVersion));
  }

  @Test
  void shouldUseCachedProcessDefinitionDataForRepeatedRequests(
      @Authenticated(USER_SINGLE_DEFINITION) final CamundaClient userClient) {

    ensureTenantExistsForUser(adminClient, TENANT_SINGLE_DEFINITION, USER_SINGLE_DEFINITION);

    final BpmnModelInstance model =
        singleServiceTaskProcess(SIMPLE_PROCESS_1, PROCESS_NAME_1, JOB_TYPE_1);

    final long processDefinitionKey =
        deployAndWait(userClient, model, resourceName(SIMPLE_PROCESS_1), TENANT_SINGLE_DEFINITION);

    final List<Long> processInstanceKeys =
        createInstances(userClient, processDefinitionKey, TENANT_SINGLE_DEFINITION, 1);

    waitForProcessInstancesToStart(userClient, 1);
    waitForJobs(userClient, processInstanceKeys);

    final List<Long> jobKeys = findJobKeysForInstances(userClient, processInstanceKeys, JOB_TYPE_1);
    failJobs(userClient, jobKeys, ERROR_FAIL_1);
    waitUntilIncidentsAreActive(userClient, 1);

    // first call seeds cache
    final var firstResult =
        userClient
            .newIncidentProcessInstanceStatisticsByDefinitionRequest(ERROR_FAIL_1.hashCode())
            .send()
            .join();

    assertThat(firstResult.items()).hasSize(1);
    assertThat(firstResult.items().getFirst().getProcessDefinitionId()).isEqualTo(SIMPLE_PROCESS_1);
    assertThat(firstResult.items().getFirst().getProcessDefinitionName()).isEqualTo(PROCESS_NAME_1);
    assertThat(firstResult.items().getFirst().getProcessDefinitionVersion()).isEqualTo(1);

    // second call should reuse cached definition data; we assert identical payload
    final var secondResult =
        userClient
            .newIncidentProcessInstanceStatisticsByDefinitionRequest(ERROR_FAIL_1.hashCode())
            .send()
            .join();

    assertThat(secondResult.items()).hasSize(1);
    assertThat(secondResult.items().getFirst())
        .usingRecursiveComparison()
        .isEqualTo(firstResult.items().getFirst());
  }

  private static BpmnModelInstance singleServiceTaskProcess(
      final String bpmnProcessId, final String name, final String jobType) {
    return Bpmn.createExecutableProcess(bpmnProcessId)
        .name(name)
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
}
