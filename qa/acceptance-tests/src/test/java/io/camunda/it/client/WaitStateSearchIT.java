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
import static io.camunda.qa.util.multidb.CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.WaitStateElementType;
import io.camunda.client.api.search.enums.WaitStateType;
import io.camunda.client.api.search.response.ElementInstanceWaitStateResult;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Verifies the element instance wait-state search command filters across a call-activity hierarchy.
 * The root process forks into a service task (its own job wait state) and a call activity into a
 * child process whose service task produces a second job wait state. Both wait states share the
 * same {@code rootProcessInstanceKey} but live under different {@code processInstanceKey}s. Jobs
 * are never activated or completed so the wait states persist for the duration of the test.
 */
@MultiDbTest
public class WaitStateSearchIT {

  private static final String ROOT_PROCESS_ID = "waitStateRootProcess";
  private static final String CHILD_PROCESS_ID = "waitStateChildProcess";
  private static final String ROOT_SERVICE_TASK = "root-service-task";
  private static final String CHILD_SERVICE_TASK = "child-service-task";

  private static CamundaClient camundaClient;

  private static long rootProcessInstanceKey;
  private static long childProcessInstanceKey;
  private static long rootElementInstanceKey;
  private static long childElementInstanceKey;

  @BeforeAll
  static void beforeAll() {
    final BpmnModelInstance rootProcess =
        Bpmn.createExecutableProcess(ROOT_PROCESS_ID)
            .startEvent()
            .parallelGateway("fork")
            .serviceTask(ROOT_SERVICE_TASK)
            .zeebeJobType("root-svc")
            .moveToNode("fork")
            .callActivity("call-activity")
            .zeebeProcessId(CHILD_PROCESS_ID)
            .done();
    final BpmnModelInstance childProcess =
        Bpmn.createExecutableProcess(CHILD_PROCESS_ID)
            .startEvent()
            .serviceTask(CHILD_SERVICE_TASK)
            .zeebeJobType("child-svc")
            .endEvent()
            .done();

    deployResource(camundaClient, rootProcess, "waitStateRootProcess.bpmn");
    deployResource(camundaClient, childProcess, "waitStateChildProcess.bpmn");
    waitForProcessesToBeDeployed(camundaClient, 2);

    rootProcessInstanceKey =
        startProcessInstance(camundaClient, ROOT_PROCESS_ID).getProcessInstanceKey();
    // root + child instance (the call activity starts the child)
    waitForProcessInstancesToStart(camundaClient, 2);

    final var items = waitForWaitStates(2);

    final var rootItem = byElementId(items, ROOT_SERVICE_TASK);
    final var childItem = byElementId(items, CHILD_SERVICE_TASK);
    childProcessInstanceKey = Long.parseLong(childItem.getProcessInstanceKey());
    rootElementInstanceKey = Long.parseLong(rootItem.getElementInstanceKey());
    childElementInstanceKey = Long.parseLong(childItem.getElementInstanceKey());
  }

  @Test
  void shouldFilterByRootProcessInstanceKey() {
    // when
    final var result =
        camundaClient
            .newElementInstanceWaitStateSearchRequest()
            .filter(f -> f.rootProcessInstanceKey(rootProcessInstanceKey))
            .send()
            .join();

    // then — both the root and the child task wait states share the root process instance key
    assertThat(result.items()).hasSize(2);
    assertThat(result.items())
        .allSatisfy(
            item ->
                assertThat(item.getRootProcessInstanceKey())
                    .isEqualTo(String.valueOf(rootProcessInstanceKey)));
  }

  @Test
  void shouldFilterByProcessInstanceKeyRoot() {
    // when
    final var result =
        camundaClient
            .newElementInstanceWaitStateSearchRequest()
            .filter(f -> f.processInstanceKey(rootProcessInstanceKey))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().getElementId()).isEqualTo(ROOT_SERVICE_TASK);
  }

  @Test
  void shouldFilterByProcessInstanceKeyChild() {
    // when
    final var result =
        camundaClient
            .newElementInstanceWaitStateSearchRequest()
            .filter(f -> f.processInstanceKey(childProcessInstanceKey))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().getElementId()).isEqualTo(CHILD_SERVICE_TASK);
  }

  @Test
  void shouldFilterBySingleElementInstanceKey() {
    // when
    final var result =
        camundaClient
            .newElementInstanceWaitStateSearchRequest()
            .filter(f -> f.elementInstanceKey(childElementInstanceKey))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().getElementInstanceKey())
        .isEqualTo(String.valueOf(childElementInstanceKey));
  }

  @Test
  void shouldFilterByMultipleElementInstanceKeys() {
    // when
    final var result =
        camundaClient
            .newElementInstanceWaitStateSearchRequest()
            .filter(
                f ->
                    f.elementInstanceKey(
                        k ->
                            k.in(
                                String.valueOf(rootElementInstanceKey),
                                String.valueOf(childElementInstanceKey))))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(2);
  }

  @Test
  void shouldFilterByElementType() {
    // when
    final var result =
        camundaClient
            .newElementInstanceWaitStateSearchRequest()
            .filter(f -> f.elementType(WaitStateElementType.SERVICE_TASK))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(2);
  }

  @Test
  void shouldFilterByWaitStateType() {
    // when
    final var result =
        camundaClient
            .newElementInstanceWaitStateSearchRequest()
            .filter(f -> f.waitStateType(WaitStateType.JOB))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(2);
  }

  @Test
  void shouldFilterByElementId() {
    // when
    final var result =
        camundaClient
            .newElementInstanceWaitStateSearchRequest()
            .filter(f -> f.elementId(CHILD_SERVICE_TASK))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().getProcessInstanceKey())
        .isEqualTo(String.valueOf(childProcessInstanceKey));
  }

  private static ElementInstanceWaitStateResult byElementId(
      final List<ElementInstanceWaitStateResult> items, final String elementId) {
    return items.stream()
        .filter(item -> elementId.equals(item.getElementId()))
        .findFirst()
        .orElseThrow();
  }

  private static List<ElementInstanceWaitStateResult> waitForWaitStates(final int expectedCount) {
    return Awaitility.await("should export %d wait states".formatted(expectedCount))
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .until(
            () -> camundaClient.newElementInstanceWaitStateSearchRequest().send().join().items(),
            items -> items.size() == expectedCount);
  }
}
