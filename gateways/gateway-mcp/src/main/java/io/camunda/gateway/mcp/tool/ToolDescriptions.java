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

  /** Paging/sorting descriptions */
  public static final String FILTER_DESCRIPTION = "Filter search by the given fields";

  public static final String SORT_DESCRIPTION = "Sort criteria";
  public static final String PAGE_DESCRIPTION = "Pagination criteria";

  /** Common property descriptions */
  public static final String PROCESS_DEFINITION_KEY_DESCRIPTION =
      "The assigned key of the process definition, which acts as a unique identifier for this process definition.";

  public static final String USER_TASK_KEY_DESCRIPTION = "The user task key.";

  /* Variable descriptions */
  public static final String VARIABLE_FILTER_FORMAT_NOTE =
      """
      Variable values in filters need to be in serialized JSON format.
      Example string value: \\"myValue\\"
      Example nested JSON value: "{\\"myVar\\":\\"myValue\\"}"
      """;

  public static final String VARIABLE_VALUE_RETURN_FORMAT =
      "The variable value is returned in serialized JSON format.";

  public static final String TRUNCATE_VARIABLES_DESCRIPTION =
      "When true (default), long variable values in the response are truncated. When false, full variable values are returned.";

  /** Validation constraint messages */
  public static final String POSITIVE_NUMBER_MESSAGE = "must be a positive number.";

  public static final String INCIDENT_KEY_POSITIVE_MESSAGE =
      "Incident key " + POSITIVE_NUMBER_MESSAGE;
  public static final String PROCESS_INSTANCE_KEY_POSITIVE_MESSAGE =
      "Process instance key " + POSITIVE_NUMBER_MESSAGE;
  public static final String VARIABLE_KEY_POSITIVE_MESSAGE =
      "Variable key " + POSITIVE_NUMBER_MESSAGE;
  public static final String PROCESS_DEFINITION_KEY_POSITIVE_MESSAGE =
      "Process definition key " + POSITIVE_NUMBER_MESSAGE;
  public static final String USER_TASK_KEY_POSITIVE_MESSAGE =
      "User task key " + POSITIVE_NUMBER_MESSAGE;

  private ToolDescriptions() {
    // Utility class
  }
}
