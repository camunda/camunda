/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.tool.testgen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.gateway.mcp.tool.ToolsTest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ContextConfiguration(classes = {TestCaseGeneratorTools.class})
class TestCaseGeneratorToolsTest extends ToolsTest {

  @MockitoBean private GitHubProductHubService gitHubService;
  @MockitoBean private TestRailIntegrationService testRailService;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setUpMocks() {
    // Default mock setup
  }

  @Nested
  class FetchProductHubEpics {

    @Test
    void shouldFetchEpicsSuccessfully() throws Exception {
      // Given
      final var epic1 = createMockEpic(123, "Epic 1", "Description 1", "open");
      final var epic2 = createMockEpic(456, "Epic 2", "Description 2", "open");
      final var mockEpics = List.of(epic1, epic2);

      when(gitHubService.fetchEpics("epic", "open"))
          .thenReturn(CompletableFuture.completedFuture(mockEpics));

      // When
      final var result =
          mcpClient
              .callTool("fetchProductHubEpics", Map.of("label", "epic", "state", "open"))
              .get();

      // Then
      assertThat(result.isError()).isFalse();
      final var content = result.content().getFirst();
      assertThat(content.text()).isNotEmpty();

      // Verify the response contains expected data
      assertThat(content.text()).contains("Epic 1", "Epic 2");
    }

    @Test
    void shouldUseDefaultParametersWhenNotProvided() throws Exception {
      // Given
      final var mockEpics = List.of(createMockEpic(123, "Default Epic", "Description", "open"));

      when(gitHubService.fetchEpics("epic", "open"))
          .thenReturn(CompletableFuture.completedFuture(mockEpics));

      // When
      final var result = mcpClient.callTool("fetchProductHubEpics", Map.of()).get();

      // Then
      assertThat(result.isError()).isFalse();
    }

    @Test
    void shouldHandleGitHubServiceError() throws Exception {
      // Given
      when(gitHubService.fetchEpics(anyString(), anyString()))
          .thenReturn(
              CompletableFuture.failedFuture(
                  new RuntimeException("GitHub API rate limit exceeded")));

      // When
      final var result =
          mcpClient
              .callTool("fetchProductHubEpics", Map.of("label", "epic", "state", "open"))
              .get();

      // Then
      assertThat(result.isError()).isTrue();
    }
  }

  @Nested
  class GenerateTestCasesFromEpic {

    @Test
    void shouldGenerateTestCasesSuccessfully() throws Exception {
      // Given
      final var mockEpic = createMockEpic(123, "Test Epic", "Epic description", "open");
      final var mockTestCase = new HashMap<String, Object>();
      mockTestCase.put("id", "TC-001");
      mockTestCase.put("title", "Test case from epic");

      final var mockResult = new HashMap<String, Object>();
      mockResult.put("created_count", 1);
      mockResult.put("test_cases", List.of(mockTestCase));

      when(gitHubService.fetchEpicByIssueNumber("123"))
          .thenReturn(CompletableFuture.completedFuture(mockEpic));
      when(testRailService.createTestCases(eq("1"), eq("5"), eq(null), eq(List.of())))
          .thenReturn(CompletableFuture.completedFuture(mockResult));

      // When
      final var result =
          mcpClient
              .callTool(
                  "generateTestCasesFromEpic",
                  Map.of(
                      "epicIssueNumber", "123",
                      "testRailProjectId", "1",
                      "testRailSuiteId", "5"))
              .get();

      // Then
      assertThat(result.isError()).isFalse();
    }

    @Test
    void shouldValidateRequiredParameters() throws Exception {
      // When - missing required parameters
      final var result =
          mcpClient.callTool("generateTestCasesFromEpic", Map.of("epicIssueNumber", "123")).get();

      // Then
      assertThat(result.isError()).isTrue();
    }
  }

  @Nested
  class BulkGenerateTestCases {

    @Test
    void shouldBulkGenerateTestCases() throws Exception {
      // Given
      final var epic1 = createMockEpic(123, "Epic 1", "Description 1", "open");
      final var epic2 = createMockEpic(456, "Epic 2", "Description 2", "open");
      final var mockEpics = List.of(epic1, epic2);

      final var mockBulkResult = new HashMap<String, Object>();
      mockBulkResult.put("epics_processed", 2);

      when(gitHubService.fetchEpics("epic", "open"))
          .thenReturn(CompletableFuture.completedFuture(mockEpics));
      when(testRailService.bulkCreateFromEpics(
              CompletableFuture.completedFuture(mockEpics), "1", "5"))
          .thenReturn(CompletableFuture.completedFuture(mockBulkResult));

      // When
      final var result =
          mcpClient
              .callTool(
                  "bulkGenerateTestCases",
                  Map.of("testRailProjectId", "1", "testRailSuiteId", "5"))
              .get();

      // Then
      assertThat(result.isError()).isFalse();
    }
  }

  @Nested
  class ValidateTestRailConnection {

    @Test
    void shouldValidateConnectionSuccessfully() throws Exception {
      // Given
      final var mockValidation = new HashMap<String, Object>();
      mockValidation.put("connected", true);
      mockValidation.put("project_id", "1");
      mockValidation.put("project_name", "Test Project");

      when(testRailService.validateConnection("1"))
          .thenReturn(CompletableFuture.completedFuture(mockValidation));

      // When
      final var result =
          mcpClient
              .callTool("validateTestRailConnection", Map.of("testRailProjectId", "1"))
              .get();

      // Then
      assertThat(result.isError()).isFalse();
      final var content = result.content().getFirst();
      assertThat(content.text()).contains("connected", "true");
    }

    @Test
    void shouldHandleConnectionFailure() throws Exception {
      // Given
      final var mockValidation = new HashMap<String, Object>();
      mockValidation.put("connected", false);
      mockValidation.put("error", "Authentication failed");

      when(testRailService.validateConnection("1"))
          .thenReturn(CompletableFuture.completedFuture(mockValidation));

      // When
      final var result =
          mcpClient
              .callTool("validateTestRailConnection", Map.of("testRailProjectId", "1"))
              .get();

      // Then
      assertThat(result.isError()).isFalse();
      final var content = result.content().getFirst();
      assertThat(content.text()).contains("connected", "false");
    }

    @Test
    void shouldRequireProjectId() throws Exception {
      // When - missing required parameter
      final var result = mcpClient.callTool("validateTestRailConnection", Map.of()).get();

      // Then
      assertThat(result.isError()).isTrue();
    }
  }

  // Helper methods
  private Map<String, Object> createMockEpic(
      final int number, final String title, final String description, final String state) {
    final var epic = new HashMap<String, Object>();
    epic.put("number", number);
    epic.put("title", title);
    epic.put("body", description);
    epic.put("state", state);
    epic.put("url", "https://github.com/camunda/product-hub/issues/" + number);
    epic.put("labels", List.of("epic"));
    epic.put("author", "test-user");
    return epic;
  }
}
