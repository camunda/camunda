/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.generator;

/**
 * Fixture record representing a single tool available to an AI agent instance.
 *
 * <p>This is a temporary fixture that mirrors the future {@code AgentTool} structure from the Zeebe
 * protocol.
 *
 * @param name tool name as visible to the LLM (e.g. {@code MCP_slack___post_message})
 * @param description optional human-readable description of the tool; {@code null} if absent
 * @param elementId optional BPMN element ID that models this tool; {@code null} for MCP/A2A tools
 */
record AgentTool(String name, String description, String elementId) {

  /** Constructs an MCP/external tool with a description but no BPMN element. */
  AgentTool(final String name, final String description) {
    this(name, description, null);
  }
}
