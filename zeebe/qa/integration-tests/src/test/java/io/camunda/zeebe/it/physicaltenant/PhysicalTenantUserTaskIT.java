/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.physicaltenant;

import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.UserTaskState;
import io.camunda.client.api.search.response.UserTask;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.AbstractUserTaskBuilder;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Covers the full user task lifecycle (deploy, create, search, assign, complete) per physical
 * tenant over REST, on an <em>asymmetric</em> cluster: the default tenant runs a single partition
 * while tenant A runs two. The asymmetry is what makes the routing assertion strong — a user task
 * command misrouted to the default tenant's partition group cannot find a partition 2 at all and
 * fails loudly, whereas on a symmetric cluster it could silently land on an identical-looking
 * partition of the wrong tenant and pass. Tenant A is driven with enough instances that at least
 * one user task provably lives on its partition 2 (checked via {@link
 * Protocol#decodePartitionId(long)}).
 *
 * <p>Both tenants complete user tasks, so the isolation assertion is also strong: each tenant's
 * user task search must see exactly its own tasks — not merely "the other tenant sees nothing".
 */
@ZeebeIntegration
final class PhysicalTenantUserTaskIT {

  private static final String DEFAULT = PhysicalTenantsITHelper.DEFAULT_TENANT_ID;
  private static final String TENANT_A = "tenanta";
  private static final int TENANT_A_PARTITION_COUNT = 2;
  private static final int MAX_INSTANCES_PER_TENANT = 20;

  // both tenants use an isolated in-memory RDBMS so the REST user task search (which reads from
  // secondary storage) can serve results per tenant
  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(DEFAULT, Storage.rdbmsH2("pt-ut-default"))
          .withTenant(TENANT_A, Storage.rdbmsH2("pt-ut-tenanta"), TENANT_A_PARTITION_COUNT)
          .build();

  @TestZeebe
  private final TestStandaloneBroker broker =
      TENANTS.configure(new TestStandaloneBroker().withUnauthenticatedAccess());

  @Test
  void shouldCompleteUserTasksPerPhysicalTenantWithoutCrossTenantVisibility() {
    try (final CamundaClient defaultClient = TENANTS.newClientBuilder(broker, DEFAULT).build();
        final CamundaClient tenantAClient = TENANTS.newClientBuilder(broker, TENANT_A).build()) {
      // given - a user task process deployed on each tenant, with instances spanning tenant A's
      // second partition so at least one user task key decodes to a partition the default tenant
      // does not have
      deployUserTaskProcess(defaultClient, DEFAULT);
      deployUserTaskProcess(tenantAClient, TENANT_A);

      final List<Long> defaultInstances =
          createInstancesCoveringPartitions(defaultClient, DEFAULT, Set.of(1));
      final List<Long> tenantAInstances =
          createInstancesCoveringPartitions(tenantAClient, TENANT_A, Set.of(1, 2));

      // when - each tenant assigns and completes all of its user tasks through its own
      // tenant-scoped REST client
      final Set<Long> defaultTaskKeys =
          assignAndCompleteUserTasks(defaultClient, DEFAULT, defaultInstances.size());
      final Set<Long> tenantATaskKeys =
          assignAndCompleteUserTasks(tenantAClient, TENANT_A, tenantAInstances.size());

      // then - a task completed on tenant A's partition 2 proves commands routed into the right
      // partition group, not just any partition group with a matching partition id
      assertThat(tenantATaskKeys.stream().map(Protocol::decodePartitionId).collect(toSet()))
          .as("tenant A's completed user tasks span both of its partitions")
          .containsExactlyInAnyOrder(Protocol.START_PARTITION_ID, Protocol.START_PARTITION_ID + 1);

      // and - every completion is visible in the owning tenant's search
      awaitCompletedUserTasks(defaultClient, DEFAULT, defaultTaskKeys);
      awaitCompletedUserTasks(tenantAClient, TENANT_A, tenantATaskKeys);

      // and - each tenant's search sees exactly its own tasks and none of the other tenant's
      assertThat(searchUserTaskKeys(defaultClient))
          .as("the default tenant's user task search sees only its own tasks")
          .containsExactlyInAnyOrderElementsOf(defaultTaskKeys)
          .doesNotContainAnyElementsOf(tenantATaskKeys);
      assertThat(searchUserTaskKeys(tenantAClient))
          .as("tenant A's user task search sees only its own tasks")
          .containsExactlyInAnyOrderElementsOf(tenantATaskKeys)
          .doesNotContainAnyElementsOf(defaultTaskKeys);
    }
  }

  private void deployUserTaskProcess(final CamundaClient client, final String tenantId) {
    final String processId = processId(tenantId);
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .userTask("user-task-" + tenantId, AbstractUserTaskBuilder::zeebeUserTask)
            .endEvent()
            .done();

    // the tenant's partition group may need a moment to elect a leader after startup; retry the
    // deployment until it goes through
    await("deployment to tenant '%s' succeeds".formatted(tenantId))
        .atMost(Duration.ofSeconds(30))
        .ignoreExceptions()
        .until(
            () ->
                client
                    .newDeployResourceCommand()
                    .addProcessModel(process, processId + ".bpmn")
                    .send()
                    .join()
                    .getProcesses()
                    .get(0)
                    .getProcessDefinitionKey(),
            key -> key > 0);
  }

  /**
   * Creates process instances one by one until their keys cover all of the tenant's expected
   * partitions. Instance creation is round-robined across the tenant's healthy partitions, but a
   * freshly started partition group may briefly route everything to partition 1 until the second
   * partition elects a leader and receives the distributed deployment — hence the retry loop
   * instead of a fixed instance count.
   */
  private List<Long> createInstancesCoveringPartitions(
      final CamundaClient client, final String tenantId, final Set<Integer> expectedPartitions) {
    final List<Long> instanceKeys = new ArrayList<>();
    final Set<Integer> coveredPartitions = new HashSet<>();
    await("instances on tenant '%s' cover partitions %s".formatted(tenantId, expectedPartitions))
        .atMost(Duration.ofSeconds(60))
        .pollInterval(Duration.ofMillis(250))
        .until(
            () -> {
              // fail fast (not ignored, unlike the transient creation failures below) if coverage
              // is not reached within a sane number of instances — round-robin should get there in
              // a handful
              assertThat(instanceKeys)
                  .as(
                      "partition coverage should be reached within %d instances",
                      MAX_INSTANCES_PER_TENANT)
                  .hasSizeLessThan(MAX_INSTANCES_PER_TENANT);
              try {
                final long key =
                    client
                        .newCreateInstanceCommand()
                        .bpmnProcessId(processId(tenantId))
                        .latestVersion()
                        .send()
                        .join()
                        .getProcessInstanceKey();
                instanceKeys.add(key);
                coveredPartitions.add(Protocol.decodePartitionId(key));
              } catch (final Exception e) {
                // a partition may transiently reject creation until it elects a leader and
                // receives the distributed deployment; retry on the next poll
                return false;
              }
              return coveredPartitions.containsAll(expectedPartitions);
            });
    return instanceKeys;
  }

  /**
   * Waits until the tenant's user task search shows all created tasks, then assigns and completes
   * each of them via the tenant-scoped REST client, returning the completed task keys.
   */
  private Set<Long> assignAndCompleteUserTasks(
      final CamundaClient client, final String tenantId, final int expectedTaskCount) {
    final List<UserTask> tasks =
        await(
                "all %d user tasks of tenant '%s' appear in its search"
                    .formatted(expectedTaskCount, tenantId))
            .atMost(Duration.ofSeconds(60))
            .pollInterval(Duration.ofMillis(500))
            .until(
                () ->
                    client
                        .newUserTaskSearchRequest()
                        .filter(f -> f.state(UserTaskState.CREATED))
                        .send()
                        .join()
                        .items(),
                items -> items.size() == expectedTaskCount);

    final Set<Long> taskKeys = new HashSet<>();
    for (final UserTask task : tasks) {
      final long taskKey = task.getUserTaskKey();
      client.newAssignUserTaskCommand(taskKey).assignee(tenantId + "-user").send().join();
      client.newCompleteUserTaskCommand(taskKey).send().join();
      taskKeys.add(taskKey);
    }
    return taskKeys;
  }

  private void awaitCompletedUserTasks(
      final CamundaClient client, final String tenantId, final Set<Long> taskKeys) {
    await("all user tasks of tenant '%s' show as completed in its search".formatted(tenantId))
        .atMost(Duration.ofSeconds(60))
        .pollInterval(Duration.ofMillis(500))
        .untilAsserted(
            () ->
                assertThat(
                        client
                            .newUserTaskSearchRequest()
                            .filter(f -> f.state(UserTaskState.COMPLETED))
                            .send()
                            .join()
                            .items())
                    .extracting(UserTask::getUserTaskKey)
                    .containsExactlyInAnyOrderElementsOf(taskKeys));
  }

  private Set<Long> searchUserTaskKeys(final CamundaClient client) {
    final Set<Long> keys = new HashSet<>();
    client
        .newUserTaskSearchRequest()
        .send()
        .join()
        .items()
        .forEach(task -> keys.add(task.getUserTaskKey()));
    return keys;
  }

  private String processId(final String tenantId) {
    return "user-task-process-" + tenantId;
  }
}
