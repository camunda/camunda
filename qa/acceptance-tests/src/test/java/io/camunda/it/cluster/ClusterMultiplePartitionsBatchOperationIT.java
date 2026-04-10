/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.cluster;

import static io.camunda.it.util.TestHelper.deployResource;
import static io.camunda.it.util.TestHelper.startProcessInstance;
import static io.camunda.it.util.TestHelper.waitForBatchOperationCompleted;
import static io.camunda.it.util.TestHelper.waitForBatchOperationWithCorrectTotalCount;
import static io.camunda.it.util.TestHelper.waitForElementInstances;
import static io.camunda.it.util.TestHelper.waitForProcessInstanceToBeTerminated;
import static io.camunda.it.util.TestHelper.waitForProcessInstancesToStart;
import static io.camunda.it.util.TestHelper.waitForProcessesToBeDeployed;
import static io.camunda.qa.util.multidb.CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.Process;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.enums.BatchOperationItemState;
import io.camunda.client.api.search.response.BatchOperationItems.BatchOperationItem;
import io.camunda.client.impl.search.filter.ProcessInstanceFilterImpl;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneApplication;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
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

  private static Process deployedProcess;
  private static final List<ProcessInstanceEvent> ACTIVE_PROCESS_INSTANCES = new ArrayList<>();

  @BeforeAll
  public static void beforeAll(@Authenticated final CamundaClient camundaClient) {
    Objects.requireNonNull(camundaClient);

    // Deploy process definitions
    deployedProcess =
        deployResource(camundaClient, "process/service_tasks_v1.bpmn").getProcesses().getFirst();
    waitForProcessesToBeDeployed(camundaClient, 1);

    // Start process instances
    IntStream.range(0, 10)
        .forEach(
            i ->
                ACTIVE_PROCESS_INSTANCES.add(
                    startProcessInstance(camundaClient, "service_tasks_v1", "{\"xyz\":\"bar\"}")));

    // given we really have processes on more than one partitions
    final long countPartitionsDeployedTo =
        ACTIVE_PROCESS_INSTANCES.stream()
            .map(ProcessInstanceEvent::getProcessInstanceKey)
            .map(Protocol::decodePartitionId)
            .distinct()
            .count();
    assumeTrue(
        countPartitionsDeployedTo > 1,
        "Test requires process instances to be deployed to multiple partitions.");

    // Wait for process instances to start
    waitForProcessInstancesToStart(camundaClient, ACTIVE_PROCESS_INSTANCES.size());
    waitForElementInstances(camundaClient, ACTIVE_PROCESS_INSTANCES.size() * 2);
  }

  @AfterEach
  void afterAll() {
    deployedProcess = null;
    ACTIVE_PROCESS_INSTANCES.clear();
  }

  @Test
  void shouldCancelProcessInstancesOnSeveralPartitionsWithBatch(
      @Authenticated final CamundaClient camundaClient) {
    // when
    final var result =
        camundaClient
            .newCreateBatchOperationCommand()
            .processInstanceCancel()
            .filter(new ProcessInstanceFilterImpl().processDefinitionId("service_tasks_v1"))
            .send()
            .join();
    final var batchOperationKey = result.getBatchOperationKey();

    // then
    assertThat(result).isNotNull();

    // and
    waitForBatchOperationWithCorrectTotalCount(
        camundaClient, batchOperationKey, ACTIVE_PROCESS_INSTANCES.size());
    waitForBatchOperationCompleted(
        camundaClient, batchOperationKey, ACTIVE_PROCESS_INSTANCES.size(), 0);

    // Now wait until all process instances are terminated
    final var activeKeys =
        ACTIVE_PROCESS_INSTANCES.stream().map(ProcessInstanceEvent::getProcessInstanceKey).toList();
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

              assertThat(itemsObj.items()).hasSize(ACTIVE_PROCESS_INSTANCES.size());
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
