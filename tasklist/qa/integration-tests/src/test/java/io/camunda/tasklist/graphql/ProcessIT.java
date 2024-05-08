/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.graphql;

import static io.camunda.tasklist.util.TestCheck.PROCESS_IS_DEPLOYED_CHECK;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.graphql.spring.boot.test.GraphQLResponse;
import io.camunda.tasklist.util.TasklistZeebeIntegrationTest;
import io.camunda.tasklist.util.TestCheck;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

public class ProcessIT extends TasklistZeebeIntegrationTest {

  @Autowired
  @Qualifier(PROCESS_IS_DEPLOYED_CHECK)
  private TestCheck processIsDeployedCheck;

  @Test
  public void shouldStartProcess() {
    tester.deployProcess("simple_process.bpmn").waitUntil().processIsDeployed();

    final GraphQLResponse response = tester.startProcess("Process_1g4wt4m");
    assertTrue(response.isOk());
    final String processInstanceId = response.get("$.data.startProcess.id");
    assertNotNull(processInstanceId);
  }
}
