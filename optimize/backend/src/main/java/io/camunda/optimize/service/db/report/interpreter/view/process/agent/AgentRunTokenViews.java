/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report.interpreter.view.process.agent;

import static io.camunda.optimize.service.db.report.plan.process.ProcessView.PROCESS_VIEW_AGENT_RUN_CACHE_READ_TOKENS;
import static io.camunda.optimize.service.db.report.plan.process.ProcessView.PROCESS_VIEW_AGENT_RUN_CACHE_WRITE_TOKENS;
import static io.camunda.optimize.service.db.report.plan.process.ProcessView.PROCESS_VIEW_AGENT_RUN_INPUT_TOKENS;
import static io.camunda.optimize.service.db.report.plan.process.ProcessView.PROCESS_VIEW_AGENT_RUN_OUTPUT_TOKENS;
import static io.camunda.optimize.service.db.report.plan.process.ProcessView.PROCESS_VIEW_AGENT_RUN_REASONING_TOKENS;
import static io.camunda.optimize.service.db.report.plan.process.ProcessView.PROCESS_VIEW_AGENT_RUN_TOTAL_TOKENS;

import io.camunda.optimize.service.db.report.plan.process.ProcessView;
import io.camunda.optimize.service.db.schema.index.AgentRunIndex;
import java.util.Map;

/** The agent run index field each agentRun token view aggregates. */
public final class AgentRunTokenViews {

  public static final Map<ProcessView, String> FIELD_BY_VIEW =
      Map.of(
          PROCESS_VIEW_AGENT_RUN_TOTAL_TOKENS, AgentRunIndex.TOTAL_TOKENS,
          PROCESS_VIEW_AGENT_RUN_INPUT_TOKENS, AgentRunIndex.INPUT_TOKENS,
          PROCESS_VIEW_AGENT_RUN_OUTPUT_TOKENS, AgentRunIndex.OUTPUT_TOKENS,
          PROCESS_VIEW_AGENT_RUN_CACHE_READ_TOKENS, AgentRunIndex.CACHE_READ_TOKENS,
          PROCESS_VIEW_AGENT_RUN_CACHE_WRITE_TOKENS, AgentRunIndex.CACHE_WRITE_TOKENS,
          PROCESS_VIEW_AGENT_RUN_REASONING_TOKENS, AgentRunIndex.REASONING_TOKENS);

  private AgentRunTokenViews() {}
}
