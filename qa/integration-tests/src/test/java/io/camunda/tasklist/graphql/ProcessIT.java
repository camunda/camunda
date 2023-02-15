/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.graphql;

import static org.junit.Assert.*;

import com.graphql.spring.boot.test.GraphQLResponse;
import io.camunda.tasklist.util.ElasticsearchChecks;
import io.camunda.tasklist.util.TasklistZeebeIntegrationTest;
import io.camunda.tasklist.util.ZeebeTestUtil;
import io.camunda.tasklist.webapp.graphql.mutation.ProcessMutationResolver;
import java.io.IOException;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

public class ProcessIT extends TasklistZeebeIntegrationTest {

  @Autowired private ProcessMutationResolver processMutationResolver;

  @Autowired
  @Qualifier("processIsDeployedCheck")
  private ElasticsearchChecks.TestCheck processIsDeployedCheck;

  @Override
  public void before() {
    super.before();
    processMutationResolver.setZeebeClient(super.getClient());
  }

  @Test
  public void shouldReturnAllProcessesBasedOnEmptySearchQuery() throws IOException {
    final String searchEmpty = "";
    final String processId1 = ZeebeTestUtil.deployProcess(zeebeClient, "simple_process.bpmn");
    final String processId2 = ZeebeTestUtil.deployProcess(zeebeClient, "simple_process_2.bpmn");
    final String processId3 = ZeebeTestUtil.deployProcess(zeebeClient, "userTaskForm.bpmn");

    elasticsearchTestRule.processAllRecordsAndWait(processIsDeployedCheck, processId1);
    elasticsearchTestRule.processAllRecordsAndWait(processIsDeployedCheck, processId2);
    elasticsearchTestRule.processAllRecordsAndWait(processIsDeployedCheck, processId3);

    final GraphQLResponse response = tester.getAllProcesses(searchEmpty);
    assertTrue(response.isOk());
    assertEquals("3", response.get("$.data.processes.length()"));
  }

  @Test
  public void shouldReturnProcessBasedOnProcessIdQuery() throws IOException {
    final String queryProcessId = "2251799813685249";
    final String processId1 = ZeebeTestUtil.deployProcess(zeebeClient, "simple_process.bpmn");
    final String processId2 = ZeebeTestUtil.deployProcess(zeebeClient, "simple_process_2.bpmn");
    final String processId3 = ZeebeTestUtil.deployProcess(zeebeClient, "userTaskForm.bpmn");

    elasticsearchTestRule.processAllRecordsAndWait(processIsDeployedCheck, processId1);
    elasticsearchTestRule.processAllRecordsAndWait(processIsDeployedCheck, processId2);
    elasticsearchTestRule.processAllRecordsAndWait(processIsDeployedCheck, processId3);

    final GraphQLResponse response = tester.getAllProcesses(queryProcessId);
    assertTrue(response.isOk());
    assertEquals("1", response.get("$.data.processes.length()"));
  }

  @Test
  public void shouldNotReturnProcessBasedOnPartialProcessIdQuery() throws IOException {
    final String queryProcessId = "799813685";
    final String processId1 = ZeebeTestUtil.deployProcess(zeebeClient, "simple_process.bpmn");
    final String processId2 = ZeebeTestUtil.deployProcess(zeebeClient, "simple_process_2.bpmn");
    final String processId3 = ZeebeTestUtil.deployProcess(zeebeClient, "userTaskForm.bpmn");

    elasticsearchTestRule.processAllRecordsAndWait(processIsDeployedCheck, processId1);
    elasticsearchTestRule.processAllRecordsAndWait(processIsDeployedCheck, processId2);
    elasticsearchTestRule.processAllRecordsAndWait(processIsDeployedCheck, processId3);

    final GraphQLResponse response = tester.getAllProcesses(queryProcessId);
    assertTrue(response.isOk());
    assertEquals("0", response.get("$.data.processes.length()"));
  }

  @Test
  public void shouldReturnProcessBasedOnProcessNameQuery() throws IOException {
    final String queryProcessId = "iMpLe";
    final String processId1 = ZeebeTestUtil.deployProcess(zeebeClient, "simple_process.bpmn");
    final String processId2 = ZeebeTestUtil.deployProcess(zeebeClient, "simple_process_2.bpmn");
    final String processId3 = ZeebeTestUtil.deployProcess(zeebeClient, "userTaskForm.bpmn");

    elasticsearchTestRule.processAllRecordsAndWait(processIsDeployedCheck, processId1);
    elasticsearchTestRule.processAllRecordsAndWait(processIsDeployedCheck, processId2);
    elasticsearchTestRule.processAllRecordsAndWait(processIsDeployedCheck, processId3);

    final GraphQLResponse response = tester.getAllProcesses(queryProcessId);
    assertTrue(response.isOk());
    assertEquals("1", response.get("$.data.processes.length()"));
    assertEquals("Simple process", response.get("$.data.processes[0].name"));
  }

  @Test
  public void shouldNotReturnProcessBasedOnSearchQuery() throws IOException {
    final String queryProcessId = "shouldNotReturn";
    final String processId1 = ZeebeTestUtil.deployProcess(zeebeClient, "simple_process.bpmn");
    final String processId2 = ZeebeTestUtil.deployProcess(zeebeClient, "simple_process_2.bpmn");
    final String processId3 = ZeebeTestUtil.deployProcess(zeebeClient, "userTaskForm.bpmn");

    elasticsearchTestRule.processAllRecordsAndWait(processIsDeployedCheck, processId1);
    elasticsearchTestRule.processAllRecordsAndWait(processIsDeployedCheck, processId2);
    elasticsearchTestRule.processAllRecordsAndWait(processIsDeployedCheck, processId3);

    final GraphQLResponse response = tester.getAllProcesses(queryProcessId);
    assertTrue(response.isOk());
    assertEquals("0", response.get("$.data.processes.length()"));
  }

  @Test
  public void shouldReturnProcessBasedOnProcessDefinitionIdQuery() throws IOException {
    final String queryProcessId = "FoRm";
    final String processId1 = ZeebeTestUtil.deployProcess(zeebeClient, "simple_process.bpmn");
    final String processId2 = ZeebeTestUtil.deployProcess(zeebeClient, "simple_process_2.bpmn");
    final String processId3 = ZeebeTestUtil.deployProcess(zeebeClient, "userTaskForm.bpmn");

    elasticsearchTestRule.processAllRecordsAndWait(processIsDeployedCheck, processId1);
    elasticsearchTestRule.processAllRecordsAndWait(processIsDeployedCheck, processId2);
    elasticsearchTestRule.processAllRecordsAndWait(processIsDeployedCheck, processId3);

    final GraphQLResponse response = tester.getAllProcesses(queryProcessId);
    assertTrue(response.isOk());
    assertEquals("1", response.get("$.data.processes.length()"));
    assertEquals("userTaskFormProcess", response.get("$.data.processes[0].processDefinitionId"));
  }

  @Test
  public void shouldStartProcess() throws IOException {
    tester.deployProcess("simple_process.bpmn").waitUntil().processIsDeployed();

    final GraphQLResponse response = tester.startProcess("Process_1g4wt4m");
    assertTrue(response.isOk());
    final String processInstanceId = response.get("$.data.startProcess.id");
    assertNotNull(processInstanceId);
  }

  @Test
  public void shouldReturnOnlyMostRecentVersionBasedOnQuery() throws IOException {
    final String querySimpleProcess = "multipleVersions";
    tester.deployProcess("multipleVersions.bpmn").waitUntil().processIsDeployed();
    tester.deployProcess("multipleVersions-v2.bpmn").waitUntil().processIsDeployed();

    final GraphQLResponse response = tester.getAllProcesses(querySimpleProcess);
    assertTrue(response.isOk());
    assertEquals("1", response.get("$.data.processes.length()"));
    assertEquals(querySimpleProcess, response.get("$.data.processes[0].name"));
    assertEquals("2", response.get("$.data.processes[0].version"));
  }

  @Test
  public void shouldReturnOnlyMostRecentVersionForEmptyQuery() throws IOException {
    final String emptyQuery = "";
    tester.deployProcess("multipleVersions.bpmn").waitUntil().processIsDeployed();
    tester.deployProcess("multipleVersions-v2.bpmn").waitUntil().processIsDeployed();

    final GraphQLResponse response = tester.getAllProcesses(emptyQuery);
    assertTrue(response.isOk());
    assertEquals("1", response.get("$.data.processes.length()"));
    assertEquals("2", response.get("$.data.processes[0].version"));
  }
}
