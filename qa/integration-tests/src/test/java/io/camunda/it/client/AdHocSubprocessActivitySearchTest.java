/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.it.client.QueryTest.deployResource;
import static io.camunda.it.client.QueryTest.waitForProcessesToBeDeployed;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.Process;
import io.camunda.client.api.search.response.AdHocSubprocessActivityResponse.AdHocSubprocessActivity;
import io.camunda.client.api.search.response.AdHocSubprocessActivityResponse.AdHocSubprocessActivity.AdHocSubprocessActivityType;
import io.camunda.qa.util.multidb.MultiDbTest;
import org.junit.jupiter.api.Test;

@MultiDbTest
public class AdHocSubprocessActivitySearchTest {

  private static CamundaClient camundaClient;

  @Test
  void findsAdHocSubprocessActivities() {
    final var process = deployAdHocSubprocessProcess();
    final var response =
        camundaClient
            .newAdHocSubprocessActivitySearchRequest(
                process.getProcessDefinitionKey(), "TestAdHocSubprocess")
            .send()
            .join();

    assertThat(response.getItems())
        .hasSize(2)
        // TestServiceTask is no root node (has incoming sequence flow)
        .noneMatch(activity -> activity.getElementId().equals("TestServiceTask"))
        .extracting(
            AdHocSubprocessActivity::getProcessDefinitionKey,
            AdHocSubprocessActivity::getProcessDefinitionId,
            AdHocSubprocessActivity::getAdHocSubprocessId,
            AdHocSubprocessActivity::getElementId,
            AdHocSubprocessActivity::getElementName,
            AdHocSubprocessActivity::getType,
            AdHocSubprocessActivity::getDocumentation,
            AdHocSubprocessActivity::getTenantId)
        .containsExactlyInAnyOrder(
            tuple(
                process.getProcessDefinitionKey(),
                process.getBpmnProcessId(),
                "TestAdHocSubprocess",
                "TestScriptTask",
                "test script task",
                AdHocSubprocessActivityType.SCRIPT_TASK,
                "This is a test script task",
                "<default>"),
            tuple(
                process.getProcessDefinitionKey(),
                process.getBpmnProcessId(),
                "TestAdHocSubprocess",
                "TestUserTask",
                "test user task",
                AdHocSubprocessActivityType.USER_TASK,
                null,
                "<default>"));
  }

  private Process deployAdHocSubprocessProcess() {
    final var deployedProcesses =
        deployResource(camundaClient, "process/ad_hoc_subprocess_activities.bpmn").getProcesses();
    assertThat(deployedProcesses).hasSize(1);

    waitForProcessesToBeDeployed(camundaClient, 1);

    return deployedProcesses.getFirst();
  }
}
