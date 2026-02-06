/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.camunda.gateway.mcp.tool.cluster.ClusterTools;
import io.camunda.gateway.mcp.tool.incident.IncidentTools;
import io.camunda.gateway.mcp.tool.process.definition.ProcessDefinitionTools;
import io.camunda.gateway.mcp.tool.process.instance.ProcessInstanceTools;
import io.camunda.gateway.mcp.tool.usertask.UserTaskTools;
import io.camunda.gateway.mcp.tool.variable.VariableTools;
import io.camunda.gateway.protocol.model.JobActivationResult;
import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.service.IncidentServices;
import io.camunda.service.JobServices;
import io.camunda.service.ProcessDefinitionServices;
import io.camunda.service.ProcessInstanceServices;
import io.camunda.service.TopologyServices;
import io.camunda.service.UserTaskServices;
import io.camunda.service.VariableServices;
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult;
import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Regression test to ensure tool schemas remain stable across refactoring.
 *
 * <p>This test compares the current tool schema output against a stored snapshot to catch any
 * unintended schema changes during migration (e.g., from @McpTool to @CamundaMcpTool).
 *
 * <p>To update the snapshot after intentional changes, manually update the file at:
 * src/test/resources/schema/tools-schema-snapshot.json
 */
@ContextConfiguration(
    classes = {
      ClusterTools.class,
      IncidentTools.class,
      ProcessDefinitionTools.class,
      ProcessInstanceTools.class,
      UserTaskTools.class,
      VariableTools.class
    })
class ToolsSchemaRegressionTest extends ToolsTest {

  private static final String SNAPSHOT_PATH = "schema/tools-schema-snapshot.json";

  private static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

  // Mock all services that tools depend on
  @MockitoBean private TopologyServices topologyServices;
  @MockitoBean private IncidentServices incidentServices;
  @MockitoBean private JobServices<JobActivationResult> jobServices;
  @MockitoBean private ProcessDefinitionServices processDefinitionServices;
  @MockitoBean private ProcessInstanceServices processInstanceServices;
  @MockitoBean private UserTaskServices userTaskServices;
  @MockitoBean private VariableServices variableServices;
  @MockitoBean private MultiTenancyConfiguration multiTenancyConfiguration;

  @Test
  void toolSchemasShouldMatchSnapshot() throws Exception {
    // Get current tool schemas
    final ListToolsResult toolsResult = mcpClient.listTools();

    // Sort tools alphabetically for consistent comparison
    final ObjectNode currentSchemas = OBJECT_MAPPER.createObjectNode();
    final ArrayNode toolsArray = OBJECT_MAPPER.createArrayNode();

    StreamSupport.stream(OBJECT_MAPPER.valueToTree(toolsResult).get("tools").spliterator(), false)
        .map(ObjectNode.class::cast)
        .sorted(Comparator.comparing(n -> n.get("name").asText()))
        .forEach(toolsArray::add);

    currentSchemas.set("tools", toolsArray);
    final String currentJson = OBJECT_MAPPER.writeValueAsString(currentSchemas);

    // Load expected snapshot
    final String expectedJson = loadSnapshot();

    // Compare with stored snapshot
    JSONAssert.assertEquals(
        "MCP tool schemas should match snapshot. "
            + "If this is an intentional change, update the snapshot file at: "
            + "src/test/resources/"
            + SNAPSHOT_PATH,
        expectedJson,
        currentJson,
        JSONCompareMode.STRICT);
  }

  private String loadSnapshot() throws IOException {
    try (final InputStream is = getClass().getClassLoader().getResourceAsStream(SNAPSHOT_PATH)) {
      if (is == null) {
        throw new IllegalStateException(
            "Snapshot file not found: "
                + SNAPSHOT_PATH
                + ". Create the snapshot file at src/test/resources/"
                + SNAPSHOT_PATH);
      }
      // Sort the snapshot as well for consistent comparison
      final ObjectNode snapshotNode = (ObjectNode) OBJECT_MAPPER.readTree(is);
      final ObjectNode sortedSnapshot = OBJECT_MAPPER.createObjectNode();
      final ArrayNode sortedTools = OBJECT_MAPPER.createArrayNode();

      StreamSupport.stream(snapshotNode.get("tools").spliterator(), false)
          .map(node -> (ObjectNode) node)
          .sorted(Comparator.comparing(n -> n.get("name").asText()))
          .forEach(sortedTools::add);

      sortedSnapshot.set("tools", sortedTools);
      return OBJECT_MAPPER.writeValueAsString(sortedSnapshot);
    }
  }
}
