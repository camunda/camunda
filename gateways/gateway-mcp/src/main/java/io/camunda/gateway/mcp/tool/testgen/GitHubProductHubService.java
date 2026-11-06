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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service for interacting with the GitHub API to fetch Epics from the camunda/product-hub
 * repository.
 */
@Service
public class GitHubProductHubService {

  private static final String GITHUB_API_BASE = "https://api.github.com";
  private static final String PRODUCT_HUB_REPO = "camunda/product-hub";

  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;
  private final String githubToken;

  public GitHubProductHubService(
      @Value("${camunda.mcp.github.token:#{null}}") final String githubToken) {
    this.httpClient = HttpClient.newHttpClient();
    this.objectMapper = new ObjectMapper();
    this.githubToken = githubToken;
  }

  /**
   * Fetches Epics from the product-hub repository.
   *
   * @param label Label to filter issues (default: "epic")
   * @param state State filter: "open", "closed", or "all"
   * @return CompletableFuture containing a list of Epic data
   */
  public CompletableFuture<List<Map<String, Object>>> fetchEpics(
      final String label, final String state) {
    final var url =
        String.format(
            "%s/repos/%s/issues?labels=%s&state=%s",
            GITHUB_API_BASE, PRODUCT_HUB_REPO, label, state);

    return makeGitHubRequest(url)
        .thenApply(
            json -> {
              final var epics = new ArrayList<Map<String, Object>>();
              if (json.isArray()) {
                for (final JsonNode issue : json) {
                  epics.add(parseIssueToMap(issue));
                }
              }
              return epics;
            });
  }

  /**
   * Fetches a specific Epic by issue number.
   *
   * @param issueNumber The GitHub issue number
   * @return CompletableFuture containing the Epic data
   */
  public CompletableFuture<Map<String, Object>> fetchEpicByIssueNumber(final String issueNumber) {
    final var url =
        String.format("%s/repos/%s/issues/%s", GITHUB_API_BASE, PRODUCT_HUB_REPO, issueNumber);

    return makeGitHubRequest(url).thenApply(this::parseIssueToMap);
  }

  private CompletableFuture<JsonNode> makeGitHubRequest(final String url) {
    return CompletableFuture.supplyAsync(
        () -> {
          try {
            final var requestBuilder =
                HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28");

            if (githubToken != null && !githubToken.isEmpty()) {
              requestBuilder.header("Authorization", "Bearer " + githubToken);
            }

            final var request = requestBuilder.GET().build();
            final var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
              throw new RuntimeException(
                  "GitHub API request failed with status: "
                      + response.statusCode()
                      + " - "
                      + response.body());
            }

            return objectMapper.readTree(response.body());
          } catch (final Exception e) {
            throw new RuntimeException("Failed to fetch data from GitHub: " + e.getMessage(), e);
          }
        });
  }

  private Map<String, Object> parseIssueToMap(final JsonNode issue) {
    final var epicMap = new HashMap<String, Object>();

    epicMap.put("number", issue.get("number").asInt());
    epicMap.put("title", issue.get("title").asText());
    epicMap.put("body", issue.has("body") ? issue.get("body").asText() : "");
    epicMap.put("state", issue.get("state").asText());
    epicMap.put("url", issue.get("html_url").asText());
    epicMap.put("created_at", issue.get("created_at").asText());
    epicMap.put("updated_at", issue.get("updated_at").asText());

    // Extract labels
    final var labels = new ArrayList<String>();
    if (issue.has("labels") && issue.get("labels").isArray()) {
      for (final JsonNode label : issue.get("labels")) {
        labels.add(label.get("name").asText());
      }
    }
    epicMap.put("labels", labels);

    // Extract user info
    if (issue.has("user")) {
      final var user = issue.get("user");
      epicMap.put("author", user.get("login").asText());
    }

    return epicMap;
  }
}
