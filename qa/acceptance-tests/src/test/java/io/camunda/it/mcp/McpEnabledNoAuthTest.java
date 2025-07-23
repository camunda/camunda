/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult;
import org.junit.jupiter.api.Test;

@MultiDbTest
public class McpEnabledNoAuthTest {

  private static McpSyncClient mcpClient;

  @MultiDbTestApplication
  private static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker().withProperty("camunda.gateway.mcp.enabled", true);

  @Test
  void shouldReturnSuccessOnMcpEndpoints() throws Exception {
    mcpClient.initialize();
    final ListToolsResult listToolsResult = mcpClient.listTools();
    assertThat(listToolsResult.tools()).isNotEmpty();
  }
}
