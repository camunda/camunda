/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.mcp.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.PermissionType;
import io.camunda.client.api.search.enums.ResourceType;
import io.camunda.client.api.search.response.ProcessDefinition;
import io.camunda.gateway.protocol.model.ProcessDefinitionResult;
import io.camunda.gateway.protocol.model.ProcessDefinitionSearchQueryResult;
import io.camunda.it.util.TestHelper;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.customizer.McpSyncHttpClientRequestCustomizer;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

abstract class AuthenticatedMcpServerTest extends McpServerAuthenticationTest {

  protected static final String RESTRICTED_PRINCIPAL_NAME = "test_restricted";
  protected static final String UNRESTRICTED_PRINCIPAL_NAME = "test_unrestricted";

  protected static final String PROCESS_DEFINITION_ID_1 = "process_1";
  protected static final String PROCESS_DEFINITION_ID_2 = "process_2";
  protected static final String PROCESS_DEFINITION_ID_3 = "process_3";

  protected static final Set<String> EXPECTED_UNRESTRICTED_PROCESS_DEFINITION_IDS =
      Set.of(PROCESS_DEFINITION_ID_1, PROCESS_DEFINITION_ID_2, PROCESS_DEFINITION_ID_3);
  protected static final Set<String> EXPECTED_RESTRICTED_PROCESS_DEFINITION_IDS =
      Set.of(PROCESS_DEFINITION_ID_1, PROCESS_DEFINITION_ID_2);

  protected static final List<Permissions> RESTRICTED_PERMISSIONS =
      List.of(
          new Permissions(
              ResourceType.PROCESS_DEFINITION,
              PermissionType.READ_PROCESS_DEFINITION,
              EXPECTED_RESTRICTED_PROCESS_DEFINITION_IDS.stream().toList()));

  protected static final List<Permissions> UNRESTRICTED_PERMISSIONS =
      List.of(
          new Permissions(ResourceType.RESOURCE, PermissionType.CREATE, List.of("*")),
          new Permissions(
              ResourceType.PROCESS_DEFINITION,
              PermissionType.READ_PROCESS_DEFINITION,
              List.of("*")));

  @BeforeAll
  static void deployTestProcesses(
      @Authenticated(UNRESTRICTED_PRINCIPAL_NAME) final CamundaClient unrestrictedClient) {
    deployTestProcessesAndAwaitSearchable(unrestrictedClient);
  }

  protected abstract McpSyncHttpClientRequestCustomizer
      createRestrictedMcpClientRequestCustomizer();

  @Test
  void failsOnUnauthenticatedRequest() {
    assertThatThrownBy(
            () -> {
              try (final var client = createMcpClient(testInstance(), null)) {
                client.listTools();
              }
            })
        .isNotNull()
        .hasMessage("Client failed to initialize listing tools");
  }

  @Test
  void searchProcessDefinitionsReturnsAllProcessDefinitionsForUnrestrictedClient() {
    assertThat(searchProcessDefinitionIds(mcpClient))
        .containsExactlyInAnyOrderElementsOf(EXPECTED_UNRESTRICTED_PROCESS_DEFINITION_IDS);
  }

  @Test
  void searchProcessDefinitionsReturnsLimitedProcessDefinitionsForRestrictedClient() {
    try (final var restrictedClient =
        createMcpClient(testInstance(), createRestrictedMcpClientRequestCustomizer())) {
      assertThat(searchProcessDefinitionIds(restrictedClient))
          .containsExactlyInAnyOrderElementsOf(EXPECTED_RESTRICTED_PROCESS_DEFINITION_IDS);
    }
  }

  private Set<String> searchProcessDefinitionIds(final McpSyncClient client) {
    final CallToolResult result =
        client.callTool(
            CallToolRequest.builder().name("searchProcessDefinitions").arguments(Map.of()).build());
    assertThat(result.isError())
        .withFailMessage(
            "Expected successful searchProcessDefinitions response, but got error result: %s",
            result)
        .isFalse();
    assertThat(result.structuredContent()).isNotNull();

    final var searchQueryResult =
        objectMapper.convertValue(
            result.structuredContent(), ProcessDefinitionSearchQueryResult.class);

    return searchQueryResult.getItems().stream()
        .map(ProcessDefinitionResult::getProcessDefinitionId)
        .collect(Collectors.toSet());
  }

  private static void deployTestProcessesAndAwaitSearchable(final CamundaClient camundaClient) {
    EXPECTED_UNRESTRICTED_PROCESS_DEFINITION_IDS.forEach(
        processDefinitionId ->
            TestHelper.deployResource(
                camundaClient,
                Bpmn.createExecutableProcess(processDefinitionId).startEvent().endEvent().done(),
                processDefinitionId + ".bpmn"));

    await("should make deployed processes searchable")
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              final var visibleProcessDefinitionIds =
                  camundaClient.newProcessDefinitionSearchRequest().send().join().items().stream()
                      .map(ProcessDefinition::getProcessDefinitionId)
                      .collect(Collectors.toSet());
              assertThat(visibleProcessDefinitionIds)
                  .containsAll(EXPECTED_UNRESTRICTED_PROCESS_DEFINITION_IDS);
            });
  }
}
