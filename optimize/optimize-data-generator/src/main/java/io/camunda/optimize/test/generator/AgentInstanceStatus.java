/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.generator;

/**
 * Fixture enum representing the lifecycle status of an AI agent instance.
 *
 * <p>This is a temporary fixture that mirrors the future {@code AgentInstanceStatus} from the Zeebe
 * protocol. Replace with the real protocol enum once {@code ValueType.AGENT_INSTANCE} is
 * introduced.
 */
enum AgentInstanceStatus {
  /** Reading BPMN tool schemas. No LLM call yet. Set on agent instance creation. */
  INITIALIZING,

  /**
   * Performing MCP/A2A tool discovery against external tool servers. Follows initialization when
   * remote tool discovery begins.
   */
  TOOL_DISCOVERY,

  /**
   * Calling the LLM. Typically the most expensive wait state (latency and cost). Central state of
   * the execution loop.
   */
  THINKING,

  /**
   * LLM requested tool calls; tools dispatched. Tool elements executing in BPMN. May be
   * long-running.
   */
  TOOL_CALLING,

  /**
   * Initialized and ready but not actively processing. Between iterations, waiting for next job
   * activation, or AHSP completion condition met but process still running.
   */
  IDLE,

  /**
   * Terminal. The agent is permanently finished. The owning process instance has completed or been
   * cancelled.
   */
  COMPLETED
}
