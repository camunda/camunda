/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.tool.testgen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service for integrating with TestRail API to create and manage test cases.
 *
 * <p>This service can also integrate with TestRail MCP server if configured.
 */
@Service
public class TestRailIntegrationService {

  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;
  private final String testRailUrl;
  private final String testRailUsername;
  private final String testRailApiKey;

  public TestRailIntegrationService(
      @Value("${camunda.mcp.testrail.url:#{null}}") final String testRailUrl,
      @Value("${camunda.mcp.testrail.username:#{null}}") final String testRailUsername,
      @Value("${camunda.mcp.testrail.api-key:#{null}}") final String testRailApiKey) {
    this.httpClient = HttpClient.newHttpClient();
    this.objectMapper = new ObjectMapper();
    this.testRailUrl = testRailUrl;
    this.testRailUsername = testRailUsername;
    this.testRailApiKey = testRailApiKey;
  }

  /**
   * Creates test cases in TestRail from test case specifications.
   *
   * @param projectId TestRail project ID
   * @param suiteId TestRail suite ID
   * @param sectionId Optional section ID
   * @param testCaseSpecs List of test case specifications
   * @return CompletableFuture containing the created test cases
   */
  public CompletableFuture<Map<String, Object>> createTestCases(
      final String projectId,
      final String suiteId,
      final String sectionId,
      final List<Map<String, Object>> testCaseSpecs) {

    return CompletableFuture.supplyAsync(
        () -> {
          final var results = new ArrayList<Map<String, Object>>();

          for (final Map<String, Object> spec : testCaseSpecs) {
            try {
              final var created = createSingleTestCase(projectId, suiteId, sectionId, spec);
              results.add(created);
            } catch (final Exception e) {
              final var error = new HashMap<String, Object>();
              error.put("error", e.getMessage());
              error.put("testCaseTitle", spec.get("title"));
              results.add(error);
            }
          }

          final var response = new HashMap<String, Object>();
          response.put("created_count", results.size());
          response.put("test_cases", results);
          return response;
        });
  }

  /**
   * Creates test cases in bulk from multiple Epics.
   *
   * @param epics List of Epic data from GitHub
   * @param projectId TestRail project ID
   * @param suiteId TestRail suite ID
   * @return CompletableFuture containing bulk creation results
   */
  public CompletableFuture<Map<String, Object>> bulkCreateFromEpics(
      final CompletableFuture<List<Map<String, Object>>> epics,
      final String projectId,
      final String suiteId) {

    return epics.thenCompose(
        epicsList -> {
          final var allResults = new ArrayList<CompletableFuture<Map<String, Object>>>();

          for (final Map<String, Object> epic : epicsList) {
            final var testCaseSpecs = TestCaseTransformer.transformEpicToTestCases(epic);
            final var result = createTestCases(projectId, suiteId, null, testCaseSpecs);
            allResults.add(result);
          }

          return CompletableFuture.allOf(allResults.toArray(new CompletableFuture[0]))
              .thenApply(
                  v -> {
                    final var summary = new HashMap<String, Object>();
                    final var epicResults = new ArrayList<Map<String, Object>>();

                    for (final CompletableFuture<Map<String, Object>> future : allResults) {
                      epicResults.add(future.join());
                    }

                    summary.put("epics_processed", epicsList.size());
                    summary.put("results", epicResults);
                    return summary;
                  });
        });
  }

  /**
   * Validates the TestRail connection and configuration.
   *
   * @param projectId TestRail project ID to validate
   * @return CompletableFuture containing validation results
   */
  public CompletableFuture<Map<String, Object>> validateConnection(final String projectId) {
    return CompletableFuture.supplyAsync(
        () -> {
          try {
            final var url = testRailUrl + "/index.php?/api/v2/get_project/" + projectId;
            final var response = makeTestRailRequest(url, "GET", null);

            final var result = new HashMap<String, Object>();
            result.put("connected", true);
            result.put("project_id", response.get("id"));
            result.put("project_name", response.get("name"));
            return result;
          } catch (final Exception e) {
            final var result = new HashMap<String, Object>();
            result.put("connected", false);
            result.put("error", e.getMessage());
            return result;
          }
        });
  }

  private Map<String, Object> createSingleTestCase(
      final String projectId,
      final String suiteId,
      final String sectionId,
      final Map<String, Object> spec) {
    try {
      final var url =
          testRailUrl + "/index.php?/api/v2/add_case/" + (sectionId != null ? sectionId : suiteId);

      final var payload = objectMapper.createObjectNode();
      payload.put("title", (String) spec.get("title"));
      payload.put("type_id", 1); // Default test case type
      payload.put("priority_id", 2); // Medium priority

      if (spec.containsKey("description")) {
        payload.put("custom_preconds", (String) spec.get("description"));
      }

      if (spec.containsKey("steps")) {
        payload.put("custom_steps", (String) spec.get("steps"));
      }

      if (spec.containsKey("expected")) {
        payload.put("custom_expected", (String) spec.get("expected"));
      }

      return makeTestRailRequest(url, "POST", payload);
    } catch (final Exception e) {
      throw new RuntimeException("Failed to create test case: " + e.getMessage(), e);
    }
  }

  private Map<String, Object> makeTestRailRequest(
      final String url, final String method, final ObjectNode payload) {
    try {
      if (testRailUrl == null || testRailUsername == null || testRailApiKey == null) {
        throw new IllegalStateException(
            "TestRail configuration is missing. Please configure: "
                + "camunda.mcp.testrail.url, camunda.mcp.testrail.username, camunda.mcp.testrail.api-key");
      }

      final var auth =
          Base64.getEncoder()
              .encodeToString((testRailUsername + ":" + testRailApiKey).getBytes(StandardCharsets.UTF_8));

      final var requestBuilder =
          HttpRequest.newBuilder()
              .uri(URI.create(url))
              .header("Content-Type", "application/json")
              .header("Authorization", "Basic " + auth);

      if ("POST".equals(method) && payload != null) {
        requestBuilder.POST(
            HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)));
      } else {
        requestBuilder.GET();
      }

      final var response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new RuntimeException(
            "TestRail API request failed with status: "
                + response.statusCode()
                + " - "
                + response.body());
      }

      final var jsonResponse = objectMapper.readTree(response.body());
      return objectMapper.convertValue(jsonResponse, Map.class);
    } catch (final Exception e) {
      throw new RuntimeException("Failed to communicate with TestRail: " + e.getMessage(), e);
    }
  }
}
