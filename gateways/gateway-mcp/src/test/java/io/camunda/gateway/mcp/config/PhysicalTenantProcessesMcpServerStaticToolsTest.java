/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.gateway.mcp.PhysicalTenantProcessesToolsTest;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration(classes = ProcessesMcpServerStaticToolsTest.StaticToolsConfiguration.class)
class PhysicalTenantProcessesMcpServerStaticToolsTest extends PhysicalTenantProcessesToolsTest {

  @Test
  void shouldListToolsOnTenantProcessesRoute() {
    final var result = mcpClient.listTools();

    assertThat(result.tools()).extracting(Tool::name).containsExactly("staticProcess");
  }

  @Test
  void shouldCallToolOnTenantProcessesRoute() {
    final CallToolResult result =
        mcpClient.callTool(CallToolRequest.builder().name("staticProcess").build());

    assertThat(result.isError()).isFalse();
    assertThat(result.content())
        .singleElement()
        .isInstanceOfSatisfying(
            TextContent.class,
            textContent -> assertThat(textContent.text()).isEqualTo("started static process"));
  }
}
