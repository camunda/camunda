/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.client.api.search.enums.ProcessInstanceState.SUSPENDED;
import static io.camunda.it.util.TestHelper.deployResource;
import static io.camunda.it.util.TestHelper.startProcessInstance;
import static io.camunda.it.util.TestHelper.waitForProcessInstancesToStart;
import static io.camunda.it.util.TestHelper.waitForProcessesToBeDeployed;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.qa.util.multidb.CamundaMultiDBExtension;
import io.camunda.qa.util.multidb.MultiDbTest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@MultiDbTest
public class ProcessInstanceSuspendedSearchIT {

  private static final String PROCESS_ID = "service_tasks_v1";

  private static CamundaClient camundaClient;
  private static long suspendedInstanceKey;
  private static long activeInstanceKey;

  @BeforeAll
  static void beforeAll() {
    Objects.requireNonNull(camundaClient);
    deployResource(camundaClient, "process/service_tasks_v1.bpmn");
    waitForProcessesToBeDeployed(camundaClient, 1);

    suspendedInstanceKey = startProcessInstance(camundaClient, PROCESS_ID).getProcessInstanceKey();
    activeInstanceKey = startProcessInstance(camundaClient, PROCESS_ID).getProcessInstanceKey();
    waitForProcessInstancesToStart(camundaClient, 2);

    camundaClient.newSuspendProcessInstanceCommand(suspendedInstanceKey).send().join();

    // wait until the suspension is visible in secondary storage
    await()
        .atMost(CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () -> {
              final ProcessInstance instance =
                  camundaClient.newProcessInstanceGetRequest(suspendedInstanceKey).send().join();
              assertThat(instance.getState()).isEqualTo(SUSPENDED);
              assertThat(instance.getSuspendedDate()).isNotNull();
            });
  }

  @Test
  void shouldFilterProcessInstancesByExistingSuspendedDate() {
    // when
    final List<ProcessInstance> result =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(f -> f.processDefinitionId(PROCESS_ID).suspendedDate(d -> d.exists(true)))
            .send()
            .join()
            .items();

    // then
    assertThat(result)
        .extracting(ProcessInstance::getProcessInstanceKey)
        .containsExactly(suspendedInstanceKey);
  }

  @Test
  void shouldFindOnlyActiveProcessInstanceByAbsentSuspendedDate() {
    // when
    final List<ProcessInstance> result =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(f -> f.processDefinitionId(PROCESS_ID).suspendedDate(d -> d.exists(false)))
            .send()
            .join()
            .items();

    // then
    assertThat(result)
        .extracting(ProcessInstance::getProcessInstanceKey)
        .containsExactly(activeInstanceKey);
  }

  @Test
  void shouldFilterProcessInstancesBySuspendedDateRange() {
    // given
    final OffsetDateTime suspendedDate =
        camundaClient
            .newProcessInstanceGetRequest(suspendedInstanceKey)
            .send()
            .join()
            .getSuspendedDate();

    // when
    final List<ProcessInstance> result =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(
                f ->
                    f.processDefinitionId(PROCESS_ID)
                        .suspendedDate(
                            d ->
                                d.gte(suspendedDate.minusMinutes(1))
                                    .lte(suspendedDate.plusMinutes(1))))
            .send()
            .join()
            .items();

    // then
    assertThat(result)
        .extracting(ProcessInstance::getProcessInstanceKey)
        .containsExactly(suspendedInstanceKey);
  }

  @Test
  void shouldSortProcessInstancesBySuspendedDate() {
    // when
    final List<ProcessInstance> result =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(f -> f.processDefinitionId(PROCESS_ID))
            .sort(s -> s.suspendedDate().desc())
            .send()
            .join()
            .items();

    // then
    assertThat(result).hasSize(2);
    assertThat(result)
        .extracting(ProcessInstance::getProcessInstanceKey)
        .containsExactlyInAnyOrder(suspendedInstanceKey, activeInstanceKey);
    assertThat(result.get(0).getProcessInstanceKey()).isEqualTo(suspendedInstanceKey);
    assertThat(result.get(0).getSuspendedDate()).isNotNull();
  }
}
