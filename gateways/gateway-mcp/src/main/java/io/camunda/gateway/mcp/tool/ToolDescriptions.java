/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.tool;

/**
 * Common description fragments for MCP tool annotations.
 *
 * <p>Use these constants to ensure consistent messaging across all tools.
 */
public final class ToolDescriptions {

  /**
   * Append this to descriptions of tools that query the search index (Elasticsearch/OpenSearch).
   * These operations are eventually consistent - data may not immediately reflect recent writes.
   *
   * <p>Usage:
   *
   * <pre>{@code
   * @McpTool(description = "Search for incidents. " + EVENTUAL_CONSISTENCY_NOTE)
   * }</pre>
   */
  public static final String EVENTUAL_CONSISTENCY_NOTE =
      "Note: Results are eventually consistent and may not immediately reflect recent changes.";

  public static final String DATE_TIME_FORMAT =
      "RFC 3339 format (e.g., '2024-12-17T10:30:00Z' or '2024-12-17T10:30:00+01:00').";

  private ToolDescriptions() {
    // Utility class
  }
}
