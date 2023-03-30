/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.graphql;

import static org.junit.Assert.*;

import com.graphql.spring.boot.test.GraphQLResponse;
import io.camunda.tasklist.util.TasklistZeebeIntegrationTest;
import java.io.IOException;
import org.junit.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

public class ProcessSaasIT extends TasklistZeebeIntegrationTest {

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add("camunda.tasklist.cloud.clusterId", () -> "449ac2ad-d3c6-4c73-9c68-7752e39ae616");
    registry.add("camunda.tasklist.client.clusterId", () -> "449ac2ad-d3c6-4c73-9c68-7752e39ae616");
  }

  @Test
  public void shouldReturnOnlyMostRecentVersionForAlphaBasedOnQuery() throws IOException {
    tasklistProperties.setVersion("8.2.0-alpha");
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
  public void shouldReturnOnlyMostRecentVersionForAlphaEmptyQuery() throws IOException {
    tasklistProperties.setVersion("8.2.0-alpha");
    final String emptyQuery = "";
    tester.deployProcess("multipleVersions.bpmn").waitUntil().processIsDeployed();
    tester.deployProcess("multipleVersions-v2.bpmn").waitUntil().processIsDeployed();

    final GraphQLResponse response = tester.getAllProcesses(emptyQuery);
    assertTrue(response.isOk());
    assertEquals("1", response.get("$.data.processes.length()"));
    assertEquals("2", response.get("$.data.processes[0].version"));
  }

  @Test
  public void shouldNotReturnProcess() throws IOException {
    tasklistProperties.setVersion("8.2.0");
    final String emptyQuery = "";
    tester.deployProcess("multipleVersions.bpmn").waitUntil().processIsDeployed();
    tester.deployProcess("multipleVersions-v2.bpmn").waitUntil().processIsDeployed();

    final GraphQLResponse response = tester.getAllProcesses(emptyQuery);
    assertTrue(response.isOk());
    assertEquals("0", response.get("$.data.processes.length()"));
  }

  @Test
  public void shouldReturnAllProcessesBasedOnEmptySearchQuery() throws IOException {
    tasklistProperties.setVersion("8.2.0-alpha");
    final String searchEmpty = "";
    tester.deployProcess("simple_process.bpmn").waitUntil().processIsDeployed();
    tester.deployProcess("simple_process_2.bpmn").waitUntil().processIsDeployed();
    tester.deployProcess("userTaskForm.bpmn").waitUntil().processIsDeployed();

    final GraphQLResponse response = tester.getAllProcesses(searchEmpty);
    assertTrue(response.isOk());
    assertEquals("3", response.get("$.data.processes.length()"));
  }

  @Test
  public void shouldReturnProcessBasedOnProcessNameQuery() throws IOException {
    final String queryProcessId = "iMpLe";
    tester.deployProcess("simple_process.bpmn").waitUntil().processIsDeployed();
    tester.deployProcess("simple_process_2.bpmn").waitUntil().processIsDeployed();
    tester.deployProcess("userTaskForm.bpmn").waitUntil().processIsDeployed();

    final GraphQLResponse response = tester.getAllProcesses(queryProcessId);
    assertTrue(response.isOk());
    assertEquals("1", response.get("$.data.processes.length()"));
    assertEquals("Simple process", response.get("$.data.processes[0].name"));
  }

  @Test
  public void shouldReturnProcessBasedOnProcessDefinitionIdQuery() throws IOException {
    tasklistProperties.setVersion("8.2.0-alpha");
    final String queryProcessId = "FoRm";
    tester.deployProcess("simple_process.bpmn").waitUntil().processIsDeployed();
    tester.deployProcess("simple_process_2.bpmn").waitUntil().processIsDeployed();
    tester.deployProcess("userTaskForm.bpmn").waitUntil().processIsDeployed();

    final GraphQLResponse response = tester.getAllProcesses(queryProcessId);
    assertTrue(response.isOk());
    assertEquals("1", response.get("$.data.processes.length()"));
    assertEquals("userTaskFormProcess", response.get("$.data.processes[0].processDefinitionId"));
  }

  @Test
  public void shouldReturnProcessBasedOnProcessIdQuery() throws IOException {
    tasklistProperties.setVersion("8.2.0-alpha");
    final String queryProcessId = "2251799813685249";
    tester.deployProcess("simple_process.bpmn").waitUntil().processIsDeployed();
    tester.deployProcess("simple_process_2.bpmn").waitUntil().processIsDeployed();
    tester.deployProcess("userTaskForm.bpmn").waitUntil().processIsDeployed();

    final GraphQLResponse response = tester.getAllProcesses(queryProcessId);
    assertTrue(response.isOk());
    assertEquals("1", response.get("$.data.processes.length()"));
  }

  @Test
  public void shouldNotReturnProcessBasedOnPartialProcessIdQuery() throws IOException {
    tasklistProperties.setVersion("8.2.0-alpha");
    final String queryProcessId = "799813685";
    tester.deployProcess("simple_process.bpmn").waitUntil().processIsDeployed();
    tester.deployProcess("simple_process_2.bpmn").waitUntil().processIsDeployed();
    tester.deployProcess("userTaskForm.bpmn").waitUntil().processIsDeployed();

    final GraphQLResponse response = tester.getAllProcesses(queryProcessId);
    assertTrue(response.isOk());
    assertEquals("0", response.get("$.data.processes.length()"));
  }

  @Test
  public void shouldNotReturnProcessBasedOnSearchQuery() throws IOException {
    tasklistProperties.setVersion("8.2.0-alpha");
    final String queryProcessId = "shouldNotReturn";
    tester.deployProcess("simple_process.bpmn").waitUntil().processIsDeployed();
    tester.deployProcess("simple_process_2.bpmn").waitUntil().processIsDeployed();
    tester.deployProcess("userTaskForm.bpmn").waitUntil().processIsDeployed();

    final GraphQLResponse response = tester.getAllProcesses(queryProcessId);
    assertTrue(response.isOk());
    assertEquals("0", response.get("$.data.processes.length()"));
  }
}
