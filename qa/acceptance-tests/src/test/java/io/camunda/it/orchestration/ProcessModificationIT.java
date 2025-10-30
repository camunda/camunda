/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.orchestration;

import static io.camunda.it.util.TestHelper.deployProcessAndWaitForIt;
import static io.camunda.it.util.TestHelper.startScopedProcessInstance;
import static io.camunda.it.util.TestHelper.waitForElementInstances;
import static io.camunda.it.util.TestHelper.waitForProcessInstances;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.ElementInstanceState;
import io.camunda.client.api.search.enums.ElementInstanceType;
import io.camunda.client.api.search.response.ElementInstance;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

@MultiDbTest
public class ProcessModificationIT {
  private static CamundaClient camundaClient;
  private String testScopeId;

  @BeforeEach
  public void beforeEach(final TestInfo testInfo) {
    Objects.requireNonNull(camundaClient);
    testScopeId =
        testInfo.getTestMethod().map(Method::toString).orElse(UUID.randomUUID().toString());
  }

  @Test
  public void canActivateElementInSpecificMultiInstanceBodyInstance() {
    // given
    // deploy process with multi-instance subprocess and start process instance
    final String processId = "mi-process";
    final String multiInstanceBody = "multi_instance_subprocess";
    final String userTaskA = "A";
    final String userTaskB = "B";
    final var process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .subProcess(
                multiInstanceBody,
                sub ->
                    sub.multiInstance(
                        m ->
                            m.zeebeInputCollectionExpression("[1,2,3]")
                                .zeebeInputElement("index")
                                .parallel()))
            .embeddedSubProcess()
            .startEvent()
            .userTask(userTaskA)
            .userTask(userTaskB)
            .endEvent()
            .subProcessDone()
            .endEvent()
            .done();
    final var deployment =
        deployProcessAndWaitForIt(camundaClient, process, String.format("%s-mi.bpmn", testScopeId));
    final var processInstanceEvent =
        startScopedProcessInstance(camundaClient, deployment.getBpmnProcessId(), testScopeId);
    waitForProcessInstances(
        camundaClient, p -> p.processDefinitionKey(deployment.getProcessDefinitionKey()), 1);
    final long processInstanceKey = processInstanceEvent.getProcessInstanceKey();

    // now select the multi-instance body instance
    final var miBodyInstance =
        waitForElementInstances(
                camundaClient,
                f ->
                    f.processInstanceKey(processInstanceKey)
                        .elementId(multiInstanceBody)
                        .type(ElementInstanceType.MULTI_INSTANCE_BODY))
            .getFirst();

    // select one of the multi-instance body instances
    final List<ElementInstance> miInstances =
        waitForElementInstances(
            camundaClient,
            f ->
                f.elementInstanceScopeKey(miBodyInstance.getElementInstanceKey())
                    .elementId(multiInstanceBody));
    final var miInstance = miInstances.getFirst();

    final var taskAElementInstance =
        waitForElementInstances(
                camundaClient, f -> f.processInstanceKey(processInstanceKey).elementId(userTaskA))
            .getFirst();

    // when
    // terminate task A in the selected multi-instance instance and activate task B there
    camundaClient
        .newModifyProcessInstanceCommand(processInstanceKey)
        .terminateElement(taskAElementInstance.getElementInstanceKey())
        .and()
        .activateElement(userTaskB, miInstance.getElementInstanceKey())
        .send()
        .join();

    // then
    waitForElementInstances(
        camundaClient,
        f ->
            f.processInstanceKey(processInstanceKey)
                .elementId(userTaskB)
                .state(ElementInstanceState.ACTIVE)
                .type(ElementInstanceType.USER_TASK));
  }

  @Test
  public void canActivateElementInSpecificMultiInstanceAdhocSubprocessInstance() {
    // given
    // deploy process with multi-instance subprocess and start process instance
    final String processId = "adhoc-process";
    final String adhocBody = "adhoc_subprocess";
    final String taskA = "A";
    final String taskB = "B";
    final var process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .adHocSubProcess(
                adhocBody,
                adHocSubProcess -> {
                  adHocSubProcess
                      .multiInstance()
                      .zeebeInputCollectionExpression("activateElements")
                      .zeebeInputElement("element");
                  adHocSubProcess.zeebeActiveElementsCollectionExpression("[element]");
                  adHocSubProcess.userTask(taskA);
                  adHocSubProcess.userTask(taskB);
                })
            .endEvent()
            .done();
    final var deployment =
        deployProcessAndWaitForIt(
            camundaClient, process, String.format("%s-adh.bpmn", testScopeId));
    final var processInstance =
        startScopedProcessInstance(
            camundaClient,
            deployment.getBpmnProcessId(),
            testScopeId,
            Map.of("activateElements", List.of("A", "B")));
    waitForProcessInstances(
        camundaClient, p -> p.processDefinitionKey(deployment.getProcessDefinitionKey()), 1);

    final long processInstanceKey = processInstance.getProcessInstanceKey();

    // now select the multi-instance body instance
    final var adhocBodyInstance =
        waitForElementInstances(
                camundaClient,
                f ->
                    f.processInstanceKey(processInstanceKey)
                        .elementId(adhocBody)
                        .type(ElementInstanceType.MULTI_INSTANCE_BODY))
            .getFirst();

    // select one of the multi-instance body instances
    final List<ElementInstance> miInstances =
        waitForElementInstances(
            camundaClient,
            f ->
                f.elementInstanceScopeKey(adhocBodyInstance.getElementInstanceKey())
                    .elementId(adhocBody));
    final var miInstance = miInstances.getFirst();

    final var taskAElementInstance =
        waitForElementInstances(
                camundaClient, f -> f.processInstanceKey(processInstanceKey).elementId(taskA))
            .getFirst();

    // when
    // terminate task A in the selected multi-instance instance and activate task B there
    camundaClient
        .newModifyProcessInstanceCommand(processInstanceKey)
        .terminateElement(taskAElementInstance.getElementInstanceKey())
        .and()
        .activateElement(taskB, miInstance.getElementInstanceKey())
        .send()
        .join();

    // then
    waitForElementInstances(
        camundaClient,
        f ->
            f.processInstanceKey(processInstanceKey)
                .elementId(taskB)
                .state(ElementInstanceState.ACTIVE)
                .type(ElementInstanceType.USER_TASK));
  }
}
