/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.graphql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.graphql.spring.boot.test.GraphQLResponse;
import io.camunda.tasklist.util.IdentityTester;
import java.io.IOException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

public class ProcessIdentityIT extends IdentityTester {

  @BeforeAll
  public static void beforeClass() {
    IdentityTester.beforeClass(false);
  }

  @DynamicPropertySource
  protected static void registerProperties(final DynamicPropertyRegistry registry) {
    IdentityTester.registerProperties(registry, false);
  }

  @Test
  public void shouldReturnProcessAfterAssigningAuthorizations() throws IOException {
    tester.deployProcess("simple_process.bpmn").waitUntil().processIsDeployed();

    final String querySimpleProcess = "simple";
    GraphQLResponse response;
    response = tester.getAllProcessesWithBearerAuth(querySimpleProcess, generateTasklistToken());
    assertTrue(response.isOk());
    assertEquals("0", response.get("$.data.processes.length()"));

    final String demoUserId = getDemoUserId();
    createAuthorization(
        demoUserId, "USER", "Process_1g4wt4m", "process-definition", "START_PROCESS_INSTANCE");

    response = tester.getAllProcessesWithBearerAuth(querySimpleProcess, generateTasklistToken());
    assertTrue(response.isOk());
    assertEquals("1", response.get("$.data.processes.length()"));
    assertEquals("Simple process", response.get("$.data.processes[0].name"));
  }

  @Test
  public void shouldReturnAllProcessesWithWildCard() throws IOException {
    tester.deployProcess("simple_process.bpmn").waitUntil().processIsDeployed();
    tester.deployProcess("simple_process_2.bpmn").waitUntil().processIsDeployed();
    tester.deployProcess("userTaskForm.bpmn").waitUntil().processIsDeployed();

    final String query = "";

    final String demoUserId = getDemoUserId();
    createAuthorization(demoUserId, "USER", "*", "process-definition", "START_PROCESS_INSTANCE");

    final GraphQLResponse response =
        tester.getAllProcessesWithBearerAuth(query, generateTasklistToken());
    assertTrue(response.isOk());
    assertEquals("3", response.get("$.data.processes.length()"));
    assertFalse(
        response
            .getList("$.data.processes[?(@.name=='Simple process')].name", String.class)
            .isEmpty());
  }
}
