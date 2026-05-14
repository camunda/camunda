/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.copilot.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgentEvent(
    String conversationId,
    String type,
    String status,
    String content,
    String toolName,
    String toolCallId,
    String toolArguments,
    String toolResult) {

  public static AgentEvent thinking(String conversationId, String delta, boolean completed) {
    return new AgentEvent(
        conversationId,
        "THINKING",
        completed ? "COMPLETED" : "IN_PROGRESS",
        delta,
        null,
        null,
        null,
        null);
  }

  public static AgentEvent toolInvoke(
      String conversationId, String toolName, String toolCallId, String args) {
    return new AgentEvent(
        conversationId, "TOOL_INVOKE", "IN_PROGRESS", null, toolName, toolCallId, args, null);
  }

  public static AgentEvent toolResult(
      String conversationId, String toolName, String toolCallId, String result, boolean success) {
    return new AgentEvent(
        conversationId,
        "TOOL_RESULT",
        success ? "COMPLETED" : "ERROR",
        null,
        toolName,
        toolCallId,
        null,
        result);
  }

  public static AgentEvent executionComplete(String conversationId, String content) {
    return new AgentEvent(
        conversationId, "EXECUTION_COMPLETE", "COMPLETED", content, null, null, null, null);
  }

  public static AgentEvent error(String conversationId, String message) {
    return new AgentEvent(conversationId, "ERROR", "ERROR", message, null, null, null, null);
  }
}
