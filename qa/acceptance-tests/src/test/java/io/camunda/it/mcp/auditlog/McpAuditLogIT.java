/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.mcp.auditlog;

import static io.camunda.it.auditlog.AuditLogUtils.DEFAULT_USERNAME;
import static io.camunda.it.mcp.McpServerTest.createBasicAuthCustomizer;
import static io.camunda.it.mcp.McpServerTest.createMcpClient;
import static io.camunda.it.util.TestHelper.startProcessInstanceWithMessage;
import static io.camunda.it.util.TestHelper.waitForAuditLogEntries;
import static io.camunda.it.util.TestHelper.waitForMessageSubscriptions;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.AuditLogEntityTypeEnum;
import io.camunda.client.api.search.enums.AuditLogOperationTypeEnum;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@MultiDbTest
public class McpAuditLogIT {

  @MultiDbTestApplication
  static final TestCamundaApplication TEST_INSTANCE =
      new TestCamundaApplication()
          .withBasicAuth()
          .withAuthorizationsEnabled()
          .withProperty("camunda.mcp.enabled", true);

  private static final String TOOL_NAME = "mcp_audit_log_tool";
  private static final String MCP_MESSAGE_NAME = "McpAuditLogIT_start";
  private static final String MCP_PROCESS_ID = "McpAuditLogIT";
  private static final String DEFAULT_PASSWORD = "demo";

  @BeforeAll
  static void setup(@Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // create a process with a message start event bound to an MCP tool
    final var processModel =
        Bpmn.createExecutableProcess(MCP_PROCESS_ID)
            .startEvent()
            .zeebeProperty("io.camunda.tool:name", TOOL_NAME)
            .message(MCP_MESSAGE_NAME)
            .endEvent()
            .done();

    client
        .newDeployResourceCommand()
        .addProcessModel(processModel, MCP_PROCESS_ID + ".bpmn")
        .send()
        .join();

    waitForMessageSubscriptions(client, f -> f.toolName(TOOL_NAME), 1);

    // start one instance via MCP tool call (requestSource=MCP)
    try (final var mcpClient =
        createMcpClient(
            "processes",
            TEST_INSTANCE,
            createBasicAuthCustomizer(DEFAULT_USERNAME, DEFAULT_PASSWORD))) {
      final var fullToolName =
          mcpClient.listTools().tools().stream()
              .map(Tool::name)
              .filter(name -> name.startsWith(TOOL_NAME + "_"))
              .findFirst()
              .orElseThrow(() -> new AssertionError("Tool not found for: " + TOOL_NAME));

      final var result =
          mcpClient.callTool(
              CallToolRequest.builder().name(fullToolName).arguments(Map.of()).build());
      assertThat(result.isError()).withFailMessage("MCP tool call failed: %s", result).isFalse();
    }

    // start a control instance via direct message correlation (no requestSource)
    startProcessInstanceWithMessage(client, MCP_MESSAGE_NAME);

    // wait until both process instance creation audit log entries are indexed
    waitForAuditLogEntries(
        client,
        f ->
            f.operationType(AuditLogOperationTypeEnum.CREATE)
                .entityType(AuditLogEntityTypeEnum.PROCESS_INSTANCE)
                .processDefinitionId(MCP_PROCESS_ID),
        2);
  }

  @Test
  void shouldSetRequestSourceToMcpInAuditLogForMcpProcessStart(
      @Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // when
    final var auditLogs =
        client
            .newAuditLogSearchRequest()
            .filter(
                f ->
                    f.operationType(AuditLogOperationTypeEnum.CREATE)
                        .entityType(AuditLogEntityTypeEnum.PROCESS_INSTANCE)
                        .processDefinitionId(MCP_PROCESS_ID))
            .send()
            .join();

    // then
    assertThat(auditLogs.items())
        .anySatisfy(log -> assertThat(log.getRequestSource()).isEqualTo("MCP"));
  }

  @Test
  void shouldFilterAuditLogsByMcpRequestSource(
      @Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // when
    final var auditLogs =
        client
            .newAuditLogSearchRequest()
            .filter(f -> f.requestSource("MCP").processDefinitionId(MCP_PROCESS_ID))
            .send()
            .join();

    // then — only the MCP-initiated entry is returned
    assertThat(auditLogs.items()).isNotEmpty();
    assertThat(auditLogs.items())
        .allSatisfy(log -> assertThat(log.getRequestSource()).isEqualTo("MCP"));
  }
}
