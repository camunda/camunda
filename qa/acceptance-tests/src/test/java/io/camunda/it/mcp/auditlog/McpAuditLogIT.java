/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.mcp.auditlog;

import static io.camunda.it.mcp.McpServerTest.createBasicAuthCustomizer;
import static io.camunda.it.mcp.McpServerTest.createMcpClient;
import static io.camunda.it.util.TestHelper.deployProcessAndWaitForIt;
import static io.camunda.it.util.TestHelper.startProcessInstanceWithMessage;
import static io.camunda.it.util.TestHelper.waitForAuditLogEntries;
import static io.camunda.it.util.TestHelper.waitForMessageSubscriptions;
import static io.camunda.security.api.model.config.initialization.InitializationConfiguration.DEFAULT_USER_PASSWORD;
import static io.camunda.security.api.model.config.initialization.InitializationConfiguration.DEFAULT_USER_USERNAME;
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
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

/**
 * Acceptance tests verifying that when a message start event is correlated via the MCP processes
 * gateway, the resulting process instance's audit log entries carry the MCP tool name as
 * agentToolName.
 */
@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class McpAuditLogIT {

  @MultiDbTestApplication
  static final TestCamundaApplication TEST_INSTANCE =
      new TestCamundaApplication()
          .withBasicAuth()
          .withAuthorizationsEnabled()
          .withProperty("camunda.mcp.enabled", true);

  private static final String PROCESS_ID = "mcpAuditLogProcess";
  private static final String MSG_START_EVENT_ID = "msgStartEvent";
  private static final String MSG_NAME = "mcpAuditLogMessage";
  private static final String MCP_TOOL_NAME = "my-mcp-audit-tool";

  @BeforeAll
  static void setup(@Authenticated(DEFAULT_USER_USERNAME) final CamundaClient client) {
    final var processModel =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent(MSG_START_EVENT_ID)
            .zeebeProperty("io.camunda.tool:name", MCP_TOOL_NAME)
            .message(m -> m.name(MSG_NAME))
            .endEvent()
            .done();
    deployProcessAndWaitForIt(client, processModel, "mcp_audit_log_process.bpmn");
    waitForMessageSubscriptions(client, f -> f.toolName(MCP_TOOL_NAME), 1);

    try (final var mcpClient =
        createMcpClient(
            "processes",
            TEST_INSTANCE,
            createBasicAuthCustomizer(DEFAULT_USER_USERNAME, DEFAULT_USER_PASSWORD))) {
      final var fullToolName =
          mcpClient.listTools().tools().stream()
              .map(Tool::name)
              .filter(name -> name.startsWith(MCP_TOOL_NAME + "_"))
              .findFirst()
              .orElseThrow(
                  () -> new AssertionError("MCP tool not found for name: " + MCP_TOOL_NAME));
      mcpClient.callTool(CallToolRequest.builder().name(fullToolName).arguments(Map.of()).build());
    }

    // start a control instance via direct message correlation (no agentToolName)
    startProcessInstanceWithMessage(client, MSG_NAME);

    // wait until both process instance creation audit log entries are indexed
    waitForAuditLogEntries(
        client,
        f ->
            f.operationType(AuditLogOperationTypeEnum.CREATE)
                .entityType(AuditLogEntityTypeEnum.PROCESS_INSTANCE)
                .processDefinitionId(PROCESS_ID),
        2);
  }

  @Test
  void shouldAddAgentToolNameToAuditLogsFromMcpMessageCorrelation(
      @Authenticated(DEFAULT_USER_USERNAME) final CamundaClient client) {
    // when
    final var result =
        client.newAuditLogSearchRequest().filter(f -> f.agentToolName(MCP_TOOL_NAME)).send().join();

    // then
    assertThat(result.items())
        .hasSize(1)
        .allSatisfy(auditLog -> assertThat(auditLog.getAgentToolName()).isEqualTo(MCP_TOOL_NAME));
  }
}
