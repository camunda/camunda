/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.mcp;

import static io.camunda.it.mcp.McpServerTest.DEFAULT_REQUEST_CUSTOMIZER;
import static io.camunda.it.mcp.McpServerTest.createMcpClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.modelcontextprotocol.client.McpSyncClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.SAME_THREAD)
public class McpServerConfigurationIT {

  @Nested
  @MultiDbTest
  class McpServerEnabledIT {

    @MultiDbTestApplication
    static final TestCamundaApplication TEST_INSTANCE =
        new TestCamundaApplication().withProperty("camunda.mcp.enabled", true);

    @Test
    public void mcpInitializeRequestReturnsInitializationResponse() {
      try (final McpSyncClient mcpClient =
          createMcpClient(TEST_INSTANCE, DEFAULT_REQUEST_CUSTOMIZER)) {

        final var initializeResult = mcpClient.initialize();
        assertThat(initializeResult).isNotNull();
      }
    }
  }

  @Nested
  @MultiDbTest
  class McpServerEnabledWithRestGatewayDisabledIT {

    @MultiDbTestApplication(managedLifecycle = false)
    static final TestCamundaApplication TEST_INSTANCE =
        new TestCamundaApplication()
            .withProperty("camunda.mcp.enabled", true)
            .withProperty("camunda.rest.enabled", false);

    @BeforeEach
    void setUp() {
      if (!TEST_INSTANCE.isStarted()) {
        TEST_INSTANCE.start();
        final var grpcClient = TEST_INSTANCE.newClientBuilder().preferRestOverGrpc(false).build();
        TEST_INSTANCE.awaitCompleteTopology(TEST_INSTANCE.unifiedConfig(), grpcClient);
      }
    }

    @AfterEach
    void tearDown() {
      TEST_INSTANCE.stop();
    }

    @Test
    public void mcpInitializeRequestReturnsInitializationResponse() {
      try (final McpSyncClient mcpClient =
          createMcpClient(TEST_INSTANCE, DEFAULT_REQUEST_CUSTOMIZER)) {

        final var initializeResult = mcpClient.initialize();
        assertThat(initializeResult).isNotNull();
      }
    }
  }

  @Nested
  @MultiDbTest
  class McpServerDisabledIT {

    @MultiDbTestApplication
    static final TestCamundaApplication TEST_INSTANCE = new TestCamundaApplication();

    @Test
    public void mcpInitializeRequestReturnsNotFoundResponse() {
      try (final McpSyncClient mcpClient =
          createMcpClient(TEST_INSTANCE, DEFAULT_REQUEST_CUSTOMIZER)) {

        final Throwable throwable = catchThrowable(mcpClient::initialize);
        assertThat(throwable.getCause())
            .hasMessageContaining("Server Not Found")
            .hasMessageContaining("404");
      }
    }
  }
}
