/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.generator;

import java.util.List;

/**
 * Parameter object carrying all data required to build one {@code AGENT_INSTANCE} record.
 *
 * <p>Following the spec contract, all fields are present on every event (CREATED, UPDATED,
 * COMPLETED). The definition and limits fields are constant after CREATED; the metrics carry
 * engine-aggregated running totals; tools are replaced wholesale on each UPDATE.
 *
 * <p>This is a fixture parameter object — not a public API. Used only inside {@link
 * ZeebeRecordFactory} and {@link FlowNodeEmitter}.
 *
 * @param agentInstanceKey engine-assigned key (= record key)
 * @param elementInstanceKey key of the AHSP or AI Agent Task element instance
 * @param elementId BPMN element ID of the hosting element
 * @param intent event intent as a string: {@code "CREATED"}, {@code "UPDATED"}, {@code "COMPLETED"}
 * @param status current lifecycle status of the agent instance
 * @param model LLM model identifier (e.g. {@code gpt-4o})
 * @param provider LLM provider identifier (e.g. {@code openai})
 * @param systemPrompt system prompt configured for this agent
 * @param maxTokens maximum total tokens allowed; {@code -1} means no limit
 * @param maxModelCalls maximum LLM calls allowed; {@code -1} means no limit
 * @param maxToolCalls maximum tool calls allowed; {@code -1} means no limit
 * @param inputTokens running total of input tokens consumed across all model calls
 * @param outputTokens running total of output tokens produced across all model calls
 * @param modelCalls running total number of LLM calls made
 * @param toolCalls running total number of tool calls made
 * @param tools current tool list (replace semantics on each UPDATE)
 * @param timestamp epoch-millisecond timestamp of this event
 */
record AgentInstanceEvent(
    long agentInstanceKey,
    long elementInstanceKey,
    String elementId,
    String intent,
    AgentInstanceStatus status,
    String model,
    String provider,
    String systemPrompt,
    long maxTokens,
    int maxModelCalls,
    int maxToolCalls,
    long inputTokens,
    long outputTokens,
    int modelCalls,
    int toolCalls,
    List<AgentTool> tools,
    long timestamp) {}
