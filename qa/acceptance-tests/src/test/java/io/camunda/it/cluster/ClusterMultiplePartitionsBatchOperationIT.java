/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.cluster;

import static io.camunda.it.util.TestHelper.deployResource;
import static io.camunda.it.util.TestHelper.getScopedVariables;
import static io.camunda.it.util.TestHelper.startScopedProcessInstance;
import static io.camunda.it.util.TestHelper.waitForBatchOperationCompleted;
import static io.camunda.it.util.TestHelper.waitForBatchOperationWithCorrectTotalCount;
import static io.camunda.it.util.TestHelper.waitForProcessInstanceToBeTerminated;
import static io.camunda.it.util.TestHelper.waitForProcessesToBeDeployed;
import static io.camunda.it.util.TestHelper.waitForScopedProcessInstancesToStart;
import static io.camunda.qa.util.multidb.CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.enums.BatchOperationItemState;
import io.camunda.client.api.search.response.BatchOperationItems.BatchOperationItem;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneApplication;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.IntStream;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class ClusterMultiplePartitionsBatchOperationIT {

  @MultiDbTestApplication
  private static final TestStandaloneApplication<?> APPLICATION =
      new TestStandaloneBroker()
          .withUnauthenticatedAccess()
          .withUnifiedConfig(
              b -> {
                final var cluster = b.getCluster();
                cluster.setPartitionCount(3);
                b.setCluster(cluster);
              });

  private static CamundaClient camundaClient;

  private String testScopeId;
  private final List<ProcessInstanceEvent> activeProcessInstances = new ArrayList<>();

  @BeforeAll
  static void beforeAll() {
    Objects.requireNonNull(camundaClient);

    // Deploy process definitions
    final var deployedProcess =
        deployResource(camundaClient, "process/service_tasks_v1.bpmn").getProcesses().getFirst();
    waitForProcessesToBeDeployed(
        camundaClient, f -> f.processDefinitionKey(deployedProcess.getProcessDefinitionKey()), 1);
  }

  @BeforeEach
  void beforeEach(final TestInfo testInfo) {
    testScopeId =
        testInfo.getTestMethod().map(Method::toString).orElse(UUID.randomUUID().toString());

    // Start scoped process instances
    IntStream.range(0, 10)
        .forEach(
            i ->
                activeProcessInstances.add(
                    startScopedProcessInstance(
                        camundaClient, "service_tasks_v1", testScopeId, Map.of("xyz", "bar"))));

    // given we really have processes on more than one partitions
    final long countPartitionsDeployedTo =
        activeProcessInstances.stream()
            .map(ProcessInstanceEvent::getProcessInstanceKey)
            .map(Protocol::decodePartitionId)
            .distinct()
            .count();
    assumeTrue(
        countPartitionsDeployedTo > 1,
        "Test requires process instances to be deployed to multiple partitions.");

    // Wait for scoped process instances to start
    waitForScopedProcessInstancesToStart(camundaClient, testScopeId, activeProcessInstances.size());
  }

  @AfterEach
  void afterEach() {
    activeProcessInstances.clear();
  }

  @Test
  void shouldCancelProcessInstancesOnSeveralPartitionsWithBatch() {
    // when
    final var result =
        camundaClient
            .newCreateBatchOperationCommand()
            .processInstanceCancel()
            .filter(b -> b.variables(getScopedVariables(testScopeId)))
            .send()
            .join();
    final var batchOperationKey = result.getBatchOperationKey();

    // then
    assertThat(result).isNotNull();

    // and
    waitForBatchOperationWithCorrectTotalCount(
        camundaClient, batchOperationKey, activeProcessInstances.size());
    waitForBatchOperationCompleted(
        camundaClient, batchOperationKey, activeProcessInstances.size(), 0);

    // Now wait until all process instances are terminated
    final var activeKeys =
        activeProcessInstances.stream().map(ProcessInstanceEvent::getProcessInstanceKey).toList();
    for (final Long key : activeKeys) {
      waitForProcessInstanceToBeTerminated(camundaClient, key);
    }

    // and
    Awaitility.await("should find all batch operation items")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var itemsObj =
                  camundaClient
                      .newBatchOperationItemsSearchRequest()
                      .filter(f -> f.batchOperationKey(batchOperationKey))
                      .send()
                      .join();
              final var itemKeys =
                  itemsObj.items().stream().map(BatchOperationItem::getItemKey).toList();

              assertThat(itemsObj.items()).hasSize(activeProcessInstances.size());
              assertThat(
                      itemsObj.items().stream()
                          .map(BatchOperationItem::getStatus)
                          .distinct()
                          .toList())
                  .containsExactly(BatchOperationItemState.COMPLETED);
              assertThat(itemKeys).containsExactlyInAnyOrder(activeKeys.toArray(Long[]::new));
            });
  }
}
