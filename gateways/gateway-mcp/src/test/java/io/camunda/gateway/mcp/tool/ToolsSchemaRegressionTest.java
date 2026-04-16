/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.tool;

import io.camunda.gateway.mcp.OperationalToolsTest;
import io.camunda.gateway.mcp.tool.cluster.ClusterTools;
import io.camunda.gateway.mcp.tool.incident.IncidentTools;
import io.camunda.gateway.mcp.tool.process.definition.ProcessDefinitionTools;
import io.camunda.gateway.mcp.tool.process.instance.ProcessInstanceTools;
import io.camunda.gateway.mcp.tool.usertask.UserTaskTools;
import io.camunda.gateway.mcp.tool.variable.VariableTools;
import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.service.IncidentServices;
import io.camunda.service.JobServices;
import io.camunda.service.ProcessDefinitionServices;
import io.camunda.service.ProcessInstanceServices;
import io.camunda.service.TopologyServices;
import io.camunda.service.UserTaskServices;
import io.camunda.service.VariableServices;
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

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
class ToolsSchemaRegressionTest extends OperationalToolsTest {

  private static final String SNAPSHOT_PATH = "schema/tools-schema-snapshot.json";

  private static final JsonMapper OBJECT_MAPPER =
      JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();

  // Mock all services that tools depend on
  @MockitoBean private TopologyServices topologyServices;
  @MockitoBean private IncidentServices incidentServices;
  @MockitoBean private JobServices<?> jobServices;
  @MockitoBean private ProcessDefinitionServices processDefinitionServices;
  @MockitoBean private ProcessInstanceServices processInstanceServices;
  @MockitoBean private UserTaskServices userTaskServices;
  @MockitoBean private VariableServices variableServices;
  @MockitoBean private MultiTenancyConfiguration multiTenancyConfiguration;

  @TestFactory
  Stream<DynamicTest> eachToolSchemaShouldMatchSnapshot() throws Exception {
    final Map<String, ObjectNode> expectedTools = loadSnapshotByName();

    final ListToolsResult toolsResult = mcpClient.listTools();
    final Map<String, ObjectNode> actualTools =
        toolsResult.tools().stream()
            .collect(
                Collectors.toMap(Tool::name, tool -> (ObjectNode) OBJECT_MAPPER.valueToTree(tool)));

    return Stream.concat(expectedTools.keySet().stream(), actualTools.keySet().stream())
        .distinct()
        .sorted()
        .map(
            toolName ->
                DynamicTest.dynamicTest(
                    toolName,
                    () -> {
                      final ObjectNode expected = expectedTools.get(toolName);
                      final ObjectNode actual = actualTools.get(toolName);

                      if (expected == null) {
                        throw new AssertionError(
                            "Tool '"
                                + toolName
                                + "' is present but not in snapshot. "
                                + "If intentional, add it to: src/test/resources/"
                                + SNAPSHOT_PATH);
                      }
                      if (actual == null) {
                        throw new AssertionError(
                            "Tool '"
                                + toolName
                                + "' is in snapshot but missing from current tools. "
                                + "If intentional, remove it from: src/test/resources/"
                                + SNAPSHOT_PATH);
                      }

                      JSONAssert.assertEquals(
                          "Schema mismatch for tool '"
                              + toolName
                              + "'. If intentional, update: src/test/resources/"
                              + SNAPSHOT_PATH,
                          OBJECT_MAPPER.writeValueAsString(expected),
                          OBJECT_MAPPER.writeValueAsString(actual),
                          JSONCompareMode.STRICT);
                    }));
  }

  private Map<String, ObjectNode> loadSnapshotByName() throws IOException {
    try (final InputStream is = getClass().getClassLoader().getResourceAsStream(SNAPSHOT_PATH)) {
      if (is == null) {
        throw new IllegalStateException(
            "Snapshot file not found: "
                + SNAPSHOT_PATH
                + ". Create it at src/test/resources/"
                + SNAPSHOT_PATH);
      }
      final ObjectNode snapshotNode = (ObjectNode) OBJECT_MAPPER.readTree(is);
      return StreamSupport.stream(snapshotNode.get("tools").spliterator(), false)
          .map(ObjectNode.class::cast)
          .collect(Collectors.toMap(n -> n.get("name").asText(), n -> n));
    }
  }
}
