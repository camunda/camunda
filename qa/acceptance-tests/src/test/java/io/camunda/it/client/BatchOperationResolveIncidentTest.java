/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.it.task.UserTaskIT.startProcessInstance;
import static io.camunda.it.util.TestHelper.deployResource;
import static io.camunda.it.util.TestHelper.waitForBatchOperationCompleted;
import static io.camunda.it.util.TestHelper.waitForBatchOperationWithCorrectTotalCount;
import static io.camunda.it.util.TestHelper.waitForProcessInstancesToStart;
import static io.camunda.it.util.TestHelper.waitForProcessesToBeDeployed;
import static io.camunda.it.util.TestHelper.waitUntilIncidentsAreActive;
import static io.camunda.it.util.TestHelper.waitUntilIncidentsAreResolved;
import static io.camunda.it.util.TestHelper.waitUntilProcessInstanceHasIncidents;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.CreateBatchOperationResponse;
import io.camunda.client.api.search.enums.BatchOperationItemState;
import io.camunda.client.api.search.enums.IncidentState;
import io.camunda.client.api.search.response.BatchOperationItems.BatchOperationItem;
import io.camunda.client.api.search.response.Incident;
import io.camunda.qa.util.compatibility.CompatibilityTest;
import io.camunda.qa.util.multidb.CamundaMultiDBExtension;
import io.camunda.qa.util.multidb.MultiDbTest;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@CompatibilityTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class BatchOperationResolveIncidentTest {

  static final int AMOUNT_OF_MI_INCIDENTS = 5;
  static final int AMOUNT_OF_INCIDENTS = 3 + AMOUNT_OF_MI_INCIDENTS;

  private static CamundaClient camundaClient;

  @BeforeAll
  static void before() {
    Objects.requireNonNull(camundaClient);

    final var processes =
        List.of(
            "service_tasks_v1.bpmn",
            "service_tasks_v2.bpmn",
            "incident_process_v1.bpmn",
            "multi_instance_incident.bpmn");
    processes.forEach(
        process -> deployResource(camundaClient, String.format("process/%s", process)));

    waitForProcessesToBeDeployed(camundaClient, 4);

    startProcessInstance(camundaClient, "service_tasks_v1");
    startProcessInstance(camundaClient, "service_tasks_v2", Map.of("path", 222));
    startProcessInstance(camundaClient, "incident_process_v1");
    startProcessInstance(camundaClient, "incident_process_v1");
    startProcessInstance(camundaClient, "incident_process_v1");
    startProcessInstance(
        camundaClient, "multi-instance-incident", Map.of("items", AMOUNT_OF_MI_INCIDENTS));

    waitForProcessInstancesToStart(camundaClient, 6);
  }

  @BeforeEach
  void beforeEach() {
    waitUntilProcessInstanceHasIncidents(camundaClient, 4);
    waitUntilIncidentsAreActive(camundaClient, AMOUNT_OF_INCIDENTS);
  }

  @Test
  void shouldResolveIncidentsWithBatch() {
    // given
    final var activeIncidentKeys =
        camundaClient
            .newIncidentSearchRequest()
            .filter(f -> f.state(IncidentState.ACTIVE))
            .send()
            .join()
            .items()
            .stream()
            .map(Incident::getIncidentKey)
            .toList();

    // when
    final Future<CreateBatchOperationResponse> result =
        camundaClient
            .newCreateBatchOperationCommand()
            .resolveIncident()
            .filter(f -> f.hasIncident(true))
            .send();

    // then each incident should be resolved and in an item, with each item completed
    final var batchOperationKey =
        assertThat(result)
            .succeedsWithin(CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY)
            .actual()
            .getBatchOperationKey();
    waitForBatchOperationWithCorrectTotalCount(
        camundaClient, batchOperationKey, AMOUNT_OF_INCIDENTS);
    waitForBatchOperationCompleted(camundaClient, batchOperationKey, AMOUNT_OF_INCIDENTS, 0);
    waitUntilIncidentsAreResolved(camundaClient, activeIncidentKeys);

    // and
    final var itemsObj =
        camundaClient
            .newBatchOperationItemsSearchRequest()
            .filter(f -> f.batchOperationKey(batchOperationKey))
            .send()
            .join();
    final var itemKeys = itemsObj.items().stream().map(BatchOperationItem::getItemKey).toList();

    assertThat(itemKeys).hasSize(AMOUNT_OF_INCIDENTS);
    assertThat(itemKeys).containsExactlyInAnyOrderElementsOf(activeIncidentKeys);
    assertThat(itemsObj.items().stream().map(BatchOperationItem::getStatus).distinct().toList())
        .containsExactly(BatchOperationItemState.COMPLETED);
  }

  @Test
  void shouldResolveActiveIncidentsOnlyWithBatch() {
    // given we resolve all incidents once
    final var firstActiveIncidentKeys =
        camundaClient
            .newIncidentSearchRequest()
            .filter(f -> f.state(IncidentState.ACTIVE))
            .send()
            .join()
            .items()
            .stream()
            .map(Incident::getIncidentKey)
            .toList();
    final Future<CreateBatchOperationResponse> firstResult =
        camundaClient
            .newCreateBatchOperationCommand()
            .resolveIncident()
            .filter(f -> f.hasIncident(true))
            .send();
    final var firstBatchOperationKey =
        assertThat(firstResult)
            .succeedsWithin(CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY)
            .actual()
            .getBatchOperationKey();
    waitForBatchOperationWithCorrectTotalCount(
        camundaClient, firstBatchOperationKey, AMOUNT_OF_INCIDENTS);
    waitForBatchOperationCompleted(camundaClient, firstBatchOperationKey, AMOUNT_OF_INCIDENTS, 0);
    waitUntilIncidentsAreResolved(camundaClient, firstActiveIncidentKeys);

    // and we fetch the newly created incidents
    waitUntilIncidentsAreActive(camundaClient, AMOUNT_OF_INCIDENTS);
    final var activeIncidentKeys =
        camundaClient
            .newIncidentSearchRequest()
            .filter(f -> f.state(IncidentState.ACTIVE))
            .send()
            .join()
            .items()
            .stream()
            .map(Incident::getIncidentKey)
            .toList();
    assertThat(activeIncidentKeys)
        .hasSize(AMOUNT_OF_INCIDENTS)
        .doesNotContainAnyElementsOf(firstActiveIncidentKeys);

    // when
    final Future<CreateBatchOperationResponse> result =
        camundaClient
            .newCreateBatchOperationCommand()
            .resolveIncident()
            .filter(f -> f.hasIncident(true))
            .send();

    // then we should only have the original amount of items for the operation, not duplicated (or
    // more)
    final var batchOperationKey =
        assertThat(result)
            .succeedsWithin(CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY)
            .actual()
            .getBatchOperationKey();
    waitForBatchOperationWithCorrectTotalCount(
        camundaClient, batchOperationKey, AMOUNT_OF_INCIDENTS);
    waitForBatchOperationCompleted(camundaClient, batchOperationKey, AMOUNT_OF_INCIDENTS, 0);
    waitUntilIncidentsAreResolved(camundaClient, activeIncidentKeys);

    // and
    final var itemsObj =
        camundaClient
            .newBatchOperationItemsSearchRequest()
            .filter(f -> f.batchOperationKey(batchOperationKey))
            .send()
            .join();
    final var itemKeys = itemsObj.items().stream().map(BatchOperationItem::getItemKey).toList();

    assertThat(itemKeys).hasSize(AMOUNT_OF_INCIDENTS);
    assertThat(itemKeys).containsExactlyInAnyOrderElementsOf(activeIncidentKeys);
    assertThat(itemsObj.items().stream().map(BatchOperationItem::getStatus).distinct().toList())
        .containsExactly(BatchOperationItemState.COMPLETED);
  }
}
