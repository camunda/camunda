/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.tool.testgen;

import io.camunda.gateway.mcp.mapper.CallToolResultMapper;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import jakarta.validation.constraints.NotBlank;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpTool.McpAnnotations;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * MCP tools for generating test cases from GitHub product-hub Epics.
 *
 * <p>This tool integrates with:
 *
 * <ul>
 *   <li>GitHub API to fetch Epics from https://github.com/camunda/product-hub/
 *   <li>TestRail MCP server (https://github.com/bun913/mcp-testrail) to create test cases
 * </ul>
 *
 * <p><strong>Prerequisites:</strong>
 *
 * <ul>
 *   <li>GitHub personal access token with repo read access
 *   <li>TestRail MCP server configured and accessible
 *   <li>TestRail API credentials
 * </ul>
 */
@Component
@Validated
public class TestCaseGeneratorTools {

  private final GitHubProductHubService gitHubService;
  private final TestRailIntegrationService testRailService;

  public TestCaseGeneratorTools(
      final GitHubProductHubService gitHubService,
      final TestRailIntegrationService testRailService) {
    this.gitHubService = gitHubService;
    this.testRailService = testRailService;
  }

  @McpTool(
      description =
          "Fetches Epics from the camunda/product-hub GitHub repository. "
              + "Returns a list of Epic issues with their titles, descriptions, labels, and metadata. "
              + "Requires GitHub API authentication configured.",
      annotations = @McpAnnotations(readOnlyHint = true))
  public CallToolResult fetchProductHubEpics(
      @McpToolParam(
              description =
                  "Optional label filter to fetch only Epics with specific labels (e.g., 'epic', 'feature')",
              required = false)
          final String label,
      @McpToolParam(
              description = "Optional state filter: 'open', 'closed', or 'all'. Default is 'open'.",
              required = false)
          final String state) {
    try {
      return CallToolResultMapper.from(
          gitHubService.fetchEpics(
              label != null ? label : "epic", state != null ? state : "open"));
    } catch (final Exception e) {
      return CallToolResultMapper.mapErrorToResult(e);
    }
  }

  @McpTool(
      description =
          "Generates test cases from a GitHub product-hub Epic and creates them in TestRail. "
              + "This tool analyzes the Epic content, extracts requirements, and automatically generates "
              + "structured test cases with steps, expected results, and proper categorization. "
              + "Requires both GitHub and TestRail API authentication configured.")
  public CallToolResult generateTestCasesFromEpic(
      @McpToolParam(
              description =
                  "The GitHub issue number of the Epic from camunda/product-hub repository")
          @NotBlank(message = "Epic issue number must not be blank")
          final String epicIssueNumber,
      @McpToolParam(
              description = "The TestRail project ID where test cases will be created")
          @NotBlank(message = "TestRail project ID must not be blank")
          final String testRailProjectId,
      @McpToolParam(
              description = "The TestRail suite ID where test cases will be added")
          @NotBlank(message = "TestRail suite ID must not be blank")
          final String testRailSuiteId,
      @McpToolParam(
              description = "Optional section ID within the suite. If not provided, uses the root.",
              required = false)
          final String testRailSectionId) {
    try {
      // Fetch the Epic from GitHub
      final var epic = gitHubService.fetchEpicByIssueNumber(epicIssueNumber);

      // Transform Epic into test case specifications
      final var testCaseSpecs = TestCaseTransformer.transformEpicToTestCases(epic);

      // Create test cases in TestRail
      final var createdTestCases =
          testRailService.createTestCases(
              testRailProjectId, testRailSuiteId, testRailSectionId, testCaseSpecs);

      return CallToolResultMapper.from(createdTestCases);
    } catch (final Exception e) {
      return CallToolResultMapper.mapErrorToResult(e);
    }
  }

  @McpTool(
      description =
          "Bulk generates test cases from multiple Epics in the product-hub repository. "
              + "Filters Epics by label and state, then creates corresponding test cases in TestRail. "
              + "Returns a summary of created test cases per Epic.",
      annotations = @McpAnnotations(readOnlyHint = false))
  public CallToolResult bulkGenerateTestCases(
      @McpToolParam(
              description = "Label filter for Epics (default: 'epic')",
              required = false)
          final String epicLabel,
      @McpToolParam(
              description = "State filter for Epics: 'open', 'closed', or 'all' (default: 'open')",
              required = false)
          final String state,
      @McpToolParam(description = "The TestRail project ID where test cases will be created")
          @NotBlank(message = "TestRail project ID must not be blank")
          final String testRailProjectId,
      @McpToolParam(description = "The TestRail suite ID where test cases will be added")
          @NotBlank(message = "TestRail suite ID must not be blank")
          final String testRailSuiteId) {
    try {
      final var epics = gitHubService.fetchEpics(
          epicLabel != null ? epicLabel : "epic",
          state != null ? state : "open");

      final var results = testRailService.bulkCreateFromEpics(
          epics, testRailProjectId, testRailSuiteId);

      return CallToolResultMapper.from(results);
    } catch (final Exception e) {
      return CallToolResultMapper.mapErrorToResult(e);
    }
  }

  @McpTool(
      description =
          "Validates TestRail connection and configuration. "
              + "Checks if TestRail API is accessible and returns project/suite information.",
      annotations = @McpAnnotations(readOnlyHint = true))
  public CallToolResult validateTestRailConnection(
      @McpToolParam(description = "The TestRail project ID to validate")
          @NotBlank(message = "TestRail project ID must not be blank")
          final String testRailProjectId) {
    try {
      return CallToolResultMapper.from(testRailService.validateConnection(testRailProjectId));
    } catch (final Exception e) {
      return CallToolResultMapper.mapErrorToResult(e);
    }
  }
}
