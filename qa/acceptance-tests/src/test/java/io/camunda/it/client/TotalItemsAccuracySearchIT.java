/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.it.util.TestHelper.deployProcessAndWaitForIt;
import static io.camunda.it.util.TestHelper.sendRawSearchRequest;
import static io.camunda.it.util.TestHelper.startProcessInstance;
import static io.camunda.it.util.TestHelper.waitForProcessInstancesToStart;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

@MultiDbTest
class TotalItemsAccuracySearchIT {

  private static final int INSTANCE_COUNT = 5;
  private static final int REQUESTED_ACCURACY = 3;

  private static CamundaClient camundaClient;

  @Test
  void shouldCountTotalItemsOnlyUpToRequestedAccuracy() {
    final var processId = "accuracy-" + UUID.randomUUID();
    deployProcessAndWaitForIt(
        camundaClient,
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType("accuracy-job"))
            .endEvent()
            .done(),
        processId + ".bpmn");
    IntStream.range(0, INSTANCE_COUNT).forEach(i -> startProcessInstance(camundaClient, processId));
    waitForProcessInstancesToStart(
        camundaClient, f -> f.processDefinitionId(processId), INSTANCE_COUNT);

    final var page =
        sendRawSearchRequest(
                camundaClient,
                "v2/process-instances/search",
                Map.of(
                    "filter", Map.of("processDefinitionId", processId),
                    "page", Map.of("limit", 0, "totalItemsAccuracy", REQUESTED_ACCURACY)))
            .get("page");

    final var totalItems = page.get("totalItems").asLong();
    assertThat(totalItems).isIn((long) REQUESTED_ACCURACY, (long) INSTANCE_COUNT);
    assertThat(page.get("hasMoreTotalItems").asBoolean()).isEqualTo(totalItems < INSTANCE_COUNT);
  }
}
