/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auditlog;

import static io.camunda.it.auditlog.AuditLogUtils.DEFAULT_USERNAME;
import static io.camunda.it.util.TestHelper.deployProcessAndWaitForIt;
import static io.camunda.it.util.TestHelper.waitForProcessInstancesToStart;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

/**
 * Acceptance tests verifying that when a message start event is correlated with a toolName (as the
 * MCP gateway does), the resulting process instance's audit log entries carry agentElementId (the
 * start event element ID) and agentToolName (the tool name).
 */
@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class McpAuditLogIT {

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withBasicAuth()
          .withMultiTenancyEnabled()
          .withAuthorizationsEnabled()
          .withAuthenticatedAccess();

  private static final String PROCESS_ID = "mcpAuditLogProcess";
  private static final String MSG_START_EVENT_ID = "msgStartEvent";
  private static final String MSG_NAME = "mcpAuditLogMessage";
  private static final String MCP_TOOL_NAME = "my-mcp-tool";

  @BeforeAll
  static void setup(@Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // Deploy a process with a message start event
    final var processModel =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent(MSG_START_EVENT_ID)
            .message(m -> m.name(MSG_NAME))
            .endEvent()
            .done();
    deployProcessAndWaitForIt(client, processModel, "mcp_audit_log_process.bpmn");

    // Correlate a message with toolName set — this simulates what the MCP gateway does
    client
        .newCorrelateMessageCommand()
        .messageName(MSG_NAME)
        .withoutCorrelationKey()
        .toolName(MCP_TOOL_NAME)
        .send()
        .join();

    waitForProcessInstancesToStart(client, 1);

    // Wait for the audit log entry with agentToolName to be indexed
    Awaitility.await("audit log entry with agentToolName to be available")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              final var result =
                  client
                      .newAuditLogSearchRequest()
                      .filter(f -> f.agentToolName(MCP_TOOL_NAME))
                      .send()
                      .join();
              assertThat(result.items()).isNotEmpty();
            });
  }

  @Test
  void shouldAddAgentToolNameToAuditLogsFromMcpMessageCorrelation(
      @Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // when - search for audit logs with the MCP tool name
    final var result =
        client.newAuditLogSearchRequest().filter(f -> f.agentToolName(MCP_TOOL_NAME)).send().join();

    // then - all entries should carry both agentToolName and agentElementId from the start event
    assertThat(result.items())
        .isNotEmpty()
        .allSatisfy(
            auditLog -> {
              assertThat(auditLog.getAgentToolName()).isEqualTo(MCP_TOOL_NAME);
              assertThat(auditLog.getAgentElementId()).isEqualTo(MSG_START_EVENT_ID);
            });
  }

  @Test
  void shouldFilterAuditLogsByAgentToolName(
      @Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // when - filter by agentToolName
    final var result =
        client.newAuditLogSearchRequest().filter(f -> f.agentToolName(MCP_TOOL_NAME)).send().join();

    // then - only entries with the matching agentToolName are returned
    assertThat(result.items()).isNotEmpty();
    assertThat(result.items())
        .allSatisfy(auditLog -> assertThat(auditLog.getAgentToolName()).isEqualTo(MCP_TOOL_NAME));
  }
}
