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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

public class ProcessSaasIT extends TasklistZeebeIntegrationTest {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProcessSaasIT.class);

  @DynamicPropertySource
  static void registerProperties(final DynamicPropertyRegistry registry) {
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
    final var response = tester.getAllProcesses("");
    final List<Map<String, Object>> processes =
        (List<Map<String, Object>>) response.getRaw("$.data.processes");
    final List<String> processIds = processes.stream().map(p -> (String) p.get("id")).toList();

    assertEquals(3, processIds.size());
    LOGGER.info("Should return all processes based on empty search query");
    testProcessRetrieval("", 3);

    LOGGER.info("Should return process based on Process named query");
    final GraphQLResponse responseProcessNamed = testProcessRetrieval("iMpLe", 1);
    assertEquals("Simple process", responseProcessNamed.get("$.data.processes[0].name"));

    LOGGER.info("Should return process based on Process definition id query");
    final GraphQLResponse responseProcessDefinition = testProcessRetrieval("FoRm", 1);
    assertEquals(
        "userTaskFormProcess",
        responseProcessDefinition.get("$.data.processes[0].processDefinitionId"));

    LOGGER.info("Should return process based on Process id query");
    for (final String processId : processIds) {
      final GraphQLResponse processResponse = testProcessRetrieval(processId, 1);
      assertEquals(processId, processResponse.get("$.data.processes[0].id"));
    }

    LOGGER.info("Should not return all process based on partial Process id query");
    final String commonProcessSubstring =
        longestCommonSubstring(processIds.get(0), processIds.get(1), processIds.get(2));
    testProcessRetrieval(commonProcessSubstring, 0);

    LOGGER.info("Should not return");
    testProcessRetrieval("shouldNotReturn", 0);
  }

  private String longestCommonSubstring(final String... strings) {

    final int numStrings = strings.length;
    final int[] stringLengths = Arrays.stream(strings).mapToInt(String::length).toArray();
    int maxLength = 0;
    int endIndex = 0;

    final int[][] stringIndices = new int[stringLengths[0] + 1][numStrings];

    for (int i = 1; i <= stringLengths[0]; i++) {
      final boolean allMatch = stringsMatchAtChar(strings, numStrings, i, stringLengths);
      if (allMatch) {
        stringIndices[i][0] = stringIndices[i - 1][0] + 1;
        if (stringIndices[i][0] > maxLength) {
          maxLength = stringIndices[i][0];
          endIndex = i;
        }
      } else {
        stringIndices[i][0] = 0;
      }
    }

    return strings[0].substring(endIndex - maxLength, endIndex);
  }

  private static boolean stringsMatchAtChar(
      final String[] strings, final int numStrings, final int index, final int[] stringLengths) {
    for (int j = 1; j < numStrings; j++) {
      if (index > stringLengths[j]
          || strings[0].charAt(index - 1) != strings[j].charAt(index - 1)) {
        return false;
      }
    }
    return true;
  }

  private GraphQLResponse testProcessRetrieval(final String query, final int expectedCount)
      throws IOException {
    final GraphQLResponse response = tester.getAllProcesses(query);
    assertTrue(response.isOk());
    assertEquals(String.valueOf(expectedCount), response.get("$.data.processes.length()"));
    return response;
  }
}
