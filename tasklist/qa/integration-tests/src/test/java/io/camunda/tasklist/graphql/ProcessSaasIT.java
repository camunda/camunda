/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.graphql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.graphql.spring.boot.test.GraphQLResponse;
import io.camunda.tasklist.util.TasklistZeebeIntegrationTest;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

public class ProcessSaasIT extends TasklistZeebeIntegrationTest {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProcessSaasIT.class);

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add("camunda.tasklist.cloud.clusterId", () -> "449ac2ad-d3c6-4c73-9c68-7752e39ae616");
    registry.add("camunda.tasklist.client.clusterId", () -> "449ac2ad-d3c6-4c73-9c68-7752e39ae616");
  }

  @Test
  public void shouldReturnOnlyMostRecentVersion() throws IOException {
    final String querySimpleProcess = "multipleVersions";
    tester.deployProcess("multipleVersions.bpmn").waitUntil().processIsDeployed();
    tester.deployProcess("multipleVersions-v2.bpmn").waitUntil().processIsDeployed();

    LOGGER.info("Test query with a simple process");
    final GraphQLResponse responseQuery = tester.getAllProcesses(querySimpleProcess);
    assertTrue(responseQuery.isOk());
    assertEquals("1", responseQuery.get("$.data.processes.length()"));
    assertEquals(querySimpleProcess, responseQuery.get("$.data.processes[0].name"));
    assertEquals("2", responseQuery.get("$.data.processes[0].version"));

    LOGGER.info("Test with empty query");
    final String emptyQuery = "";
    final GraphQLResponse responseEmptyQuery = tester.getAllProcesses(emptyQuery);
    assertTrue(responseEmptyQuery.isOk());
    assertEquals("1", responseEmptyQuery.get("$.data.processes.length()"));
    assertEquals("2", responseEmptyQuery.get("$.data.processes[0].version"));
  }

  @Test
  public void shouldReturnAllProcessesBasedOnQueries() throws IOException {
    tester.deployProcess("simple_process.bpmn").waitUntil().processIsDeployed();
    tester.deployProcess("simple_process_2.bpmn").waitUntil().processIsDeployed();
    tester.deployProcess("userTaskForm.bpmn").waitUntil().processIsDeployed();

    LOGGER.info("Should return all processes based on empty search query");
    testProcessRetrieval("", 3);

    LOGGER.info("Should return all process based on Process named query");
    final GraphQLResponse responseProcessNamed = testProcessRetrieval("iMpLe", 1);
    assertEquals("Simple process", responseProcessNamed.get("$.data.processes[0].name"));

    LOGGER.info("Should return all process based on Process definition id query");
    final GraphQLResponse responseProcessDefinition = testProcessRetrieval("FoRm", 1);
    assertEquals(
        "userTaskFormProcess",
        responseProcessDefinition.get("$.data.processes[0].processDefinitionId"));

    LOGGER.info("Should return all process based on Process id query");
    testProcessRetrieval("2251799813685250", 1);

    LOGGER.info("Should return all process based on partial Process id query");
    testProcessRetrieval("799813685", 0);

    LOGGER.info("Should not return");
    testProcessRetrieval("shouldNotReturn", 0);
  }

  private GraphQLResponse testProcessRetrieval(String query, int expectedCount) throws IOException {
    final GraphQLResponse response = tester.getAllProcesses(query);
    assertTrue(response.isOk());
    assertEquals(String.valueOf(expectedCount), response.get("$.data.processes.length()"));
    return response;
  }
}
