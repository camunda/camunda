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

  @Test
  public void canModifyElementsInMultiInstanceAdHocSubprocessWithAiAgentPattern() {
    // given
    final String processId = "ai-agent-process";
    final String mergeGateway = "merge_gateway";
    final String aiAgentTask = "ai_agent_task";
    final String shouldRunTools = "should_run_tools";
    final String toolsSubprocess = "tools_subprocess";
    final String toolA = "tool_a";
    final String toolB = "tool_b";
    final String toolC = "tool_c";
    final var process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .exclusiveGateway(mergeGateway)
            .serviceTask(aiAgentTask)
            .zeebeJobType("ai-agent")
            .exclusiveGateway(shouldRunTools)
            .defaultFlow()
            .endEvent()
            .moveToLastExclusiveGateway()
            .sequenceFlowId("run_tools")
            .condition("=is defined(agent.toolCalls)")
            .adHocSubProcess(
                toolsSubprocess,
                adHocSubProcess -> {
                  adHocSubProcess
                      .multiInstance()
                      .zeebeInputCollectionExpression("agent.iterations")
                      .zeebeInputElement("iteration");
                  adHocSubProcess.zeebeActiveElementsCollectionExpression("agent.toolCalls");
                  adHocSubProcess.serviceTask(toolA).zeebeJobType("tool-a");
                  adHocSubProcess.serviceTask(toolB).zeebeJobType("tool-b");
                  adHocSubProcess.serviceTask(toolC).zeebeJobType("tool-c");
                })
            .connectTo(mergeGateway)
            .done();
    final var deployment =
        deployProcessAndWaitForIt(
            camundaClient, process, String.format("%s-ai-agent.bpmn", testScopeId));
    final var processInstance =
        startScopedProcessInstance(camundaClient, deployment.getBpmnProcessId(), testScopeId);
    waitForProcessInstances(
        camundaClient, p -> p.processDefinitionKey(deployment.getProcessDefinitionKey()), 1);

    final long processInstanceKey = processInstance.getProcessInstanceKey();

    // Complete the AI agent task to route to the tools subprocess
    // The AI agent outputs toolCalls and iterations which activate the multi-instance ad-hoc
    // subprocess
    final var activateJobsResponse =
        camundaClient
            .newActivateJobsCommand()
            .jobType("ai-agent")
            .maxJobsToActivate(1)
            .send()
            .join();
    final var aiAgentJob = activateJobsResponse.getJobs().getFirst();
    camundaClient
        .newCompleteCommand(aiAgentJob.getKey())
        .variables(
            Map.of("agent", Map.of("toolCalls", List.of("tool_a"), "iterations", List.of(1, 2))))
        .send()
        .join();

    // Wait for the multi-instance ad-hoc subprocess body to be activated
    final var toolsSubprocessBodyInstance =
        waitForElementInstances(
                camundaClient,
                f ->
                    f.processInstanceKey(processInstanceKey)
                        .elementId(toolsSubprocess)
                        .type(ElementInstanceType.MULTI_INSTANCE_BODY))
            .getFirst();

    // Select one of the multi-instance ad-hoc subprocess instances
    final List<ElementInstance> miInstances =
        waitForElementInstances(
            camundaClient,
            f ->
                f.elementInstanceScopeKey(toolsSubprocessBodyInstance.getElementInstanceKey())
                    .elementId(toolsSubprocess));
    final var miInstance = miInstances.getFirst();

    final var toolAElementInstance =
        waitForElementInstances(
                camundaClient, f -> f.processInstanceKey(processInstanceKey).elementId(toolA))
            .getFirst();

    // when
    // terminate tool A and activate tools B and C in a specific instance of the multi-instance
    // ad-hoc subprocess
    camundaClient
        .newModifyProcessInstanceCommand(processInstanceKey)
        .terminateElement(toolAElementInstance.getElementInstanceKey())
        .and()
        .activateElement(toolB, miInstance.getElementInstanceKey())
        .and()
        .activateElement(toolC, miInstance.getElementInstanceKey())
        .send()
        .join();

    // then
    // verify both tool B and tool C are now active in the specified instance
    waitForElementInstances(
        camundaClient,
        f ->
            f.processInstanceKey(processInstanceKey)
                .elementId(toolB)
                .state(ElementInstanceState.ACTIVE)
                .type(ElementInstanceType.SERVICE_TASK));
    waitForElementInstances(
        camundaClient,
        f ->
            f.processInstanceKey(processInstanceKey)
                .elementId(toolC)
                .state(ElementInstanceState.ACTIVE)
                .type(ElementInstanceType.SERVICE_TASK));
  }
}
