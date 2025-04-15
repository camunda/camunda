/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.it.util.TestHelper.deployResource;
import static io.camunda.it.util.TestHelper.startProcessInstance;
import static io.camunda.it.util.TestHelper.waitForProcessInstancesToStart;
import static io.camunda.it.util.TestHelper.waitForProcessesToBeDeployed;
import static io.camunda.it.util.TestHelper.waitUntilIncidentsAreActive;
import static io.camunda.it.util.TestHelper.waitUntilIncidentsAreResolved;
import static io.camunda.it.util.TestHelper.waitUntilProcessInstanceHasIncidents;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.Process;
import io.camunda.client.api.search.enums.BatchOperationItemState;
import io.camunda.client.api.search.enums.BatchOperationState;
import io.camunda.client.api.search.response.BatchOperationItems.BatchOperationItem;
import io.camunda.client.api.search.response.Incident;
import io.camunda.client.impl.search.filter.ProcessInstanceFilterImpl;
import io.camunda.qa.util.multidb.MultiDbTest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "es")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "os")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class BatchResolveIncidentTest {

  static final List<Process> DEPLOYED_PROCESSES = new ArrayList<>();
  static final List<Long> ACTIVE_INCIDENTS = new ArrayList<>();
  private static final int AMOUNT_OF_INCIDENTS = 3;

  private static CamundaClient camundaClient;

  private static Incident incident;

  @BeforeAll
  public static void beforeAll() {

    final var processes =
        List.of("service_tasks_v1.bpmn", "service_tasks_v2.bpmn", "incident_process_v1.bpmn");
    processes.forEach(
        process ->
            DEPLOYED_PROCESSES.addAll(
                deployResource(camundaClient, String.format("process/%s", process))
                    .getProcesses()));

    waitForProcessesToBeDeployed(camundaClient, 3);

    startProcessInstance(camundaClient, "service_tasks_v1");
    startProcessInstance(camundaClient, "service_tasks_v2", "{\"path\":222}");
    startProcessInstance(camundaClient, "incident_process_v1");
    startProcessInstance(camundaClient, "incident_process_v1");
    startProcessInstance(camundaClient, "incident_process_v1");

    waitForProcessInstancesToStart(camundaClient, 5);
    waitUntilProcessInstanceHasIncidents(camundaClient, AMOUNT_OF_INCIDENTS);
    waitUntilIncidentsAreActive(camundaClient, AMOUNT_OF_INCIDENTS);

    ACTIVE_INCIDENTS.addAll(
        camundaClient.newIncidentSearchRequest().send().join().items().stream()
            .map(Incident::getIncidentKey)
            .toList());
  }

  @AfterAll
  static void afterAll() {
    DEPLOYED_PROCESSES.clear();
    ACTIVE_INCIDENTS.clear();
  }

  @Test
  void shouldResolveIncidentsWithBatch() throws InterruptedException {
    // when
    final var result =
        camundaClient
            .newCreateBatchOperationCommand()
            .resolveIncident()
            .filter(new ProcessInstanceFilterImpl())
            .send()
            .join();
    final var batchOperationKey = result.getBatchOperationKey();

    // then
    assertThat(result).isNotNull();
    //    assertThat(result.getBatchOperationKey()).isNotNull();

    // and
    Awaitility.await("should complete batch operation")
        .atMost(Duration.ofSeconds(15))
        .pollInterval(Duration.ofMillis(100))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              // and
              final var batch =
                  camundaClient
                      .newBatchOperationGetRequest(result.getBatchOperationKey())
                      .send()
                      .join();
              assertThat(batch).isNotNull();
              assertThat(batch.getEndDate()).isNotNull();
              assertThat(batch.getStatus()).isEqualTo(BatchOperationState.COMPLETED);
            });

    waitUntilIncidentsAreResolved(camundaClient, ACTIVE_INCIDENTS.size());

    // and
    final var itemsObj =
        camundaClient.newBatchOperationItemsGetRequest(batchOperationKey).send().join();
    final var itemKeys = itemsObj.items().stream().map(BatchOperationItem::getKey).toList();

    assertThat(itemsObj.items()).hasSize(3);
    assertThat(itemsObj.items().stream().map(BatchOperationItem::getStatus).distinct().toList())
        .containsExactly(BatchOperationItemState.COMPLETED);
    assertThat(itemKeys).containsExactlyInAnyOrder(ACTIVE_INCIDENTS.toArray(Long[]::new));
  }
}
