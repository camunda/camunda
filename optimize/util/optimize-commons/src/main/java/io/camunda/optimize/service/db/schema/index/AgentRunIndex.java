/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.schema.index;

import static io.camunda.optimize.service.db.DatabaseConstants.OPTIMIZE_DATE_FORMAT;

import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import io.camunda.optimize.dto.optimize.query.agent.AgentRunDto;
import io.camunda.optimize.service.db.DatabaseConstants;
import io.camunda.optimize.service.db.schema.DefaultIndexMappingCreator;

/**
 * Shared flat index with one document per agent run. Definition, date and tenant fields reuse the
 * process instance index field names so the existing report filters apply to it unchanged.
 */
public abstract class AgentRunIndex<TBuilder> extends DefaultIndexMappingCreator<TBuilder> {

  public static final int VERSION = 1;

  public static final String AGENT_INSTANCE_ID = AgentRunDto.Fields.agentInstanceId;
  public static final String AGENT_DEFINITION_ID = AgentRunDto.Fields.agentDefinitionId;
  public static final String AGENT_ID = AgentRunDto.Fields.agentId;
  public static final String FLOW_NODE_ID = AgentRunDto.Fields.flowNodeId;
  public static final String FLOW_NODE_INSTANCE_IDS = AgentRunDto.Fields.flowNodeInstanceIds;
  public static final String PROCESS_DEFINITION_KEY = AgentRunDto.Fields.processDefinitionKey;
  public static final String PROCESS_DEFINITION_ID = AgentRunDto.Fields.processDefinitionId;
  public static final String PROCESS_DEFINITION_VERSION =
      AgentRunDto.Fields.processDefinitionVersion;
  public static final String PROCESS_INSTANCE_ID = AgentRunDto.Fields.processInstanceId;
  public static final String ROOT_PROCESS_INSTANCE_ID = AgentRunDto.Fields.rootProcessInstanceId;
  public static final String TENANT_ID = AgentRunDto.Fields.tenantId;
  public static final String MODEL = AgentRunDto.Fields.model;
  public static final String PROVIDER = AgentRunDto.Fields.provider;
  public static final String MAX_MODEL_CALLS = AgentRunDto.Fields.maxModelCalls;
  public static final String MAX_TOOL_CALLS = AgentRunDto.Fields.maxToolCalls;
  public static final String MAX_TOKENS = AgentRunDto.Fields.maxTokens;
  public static final String START_DATE = AgentRunDto.Fields.startDate;
  public static final String LAST_ACTIVITY_DATE = AgentRunDto.Fields.lastActivityDate;
  public static final String END_DATE = AgentRunDto.Fields.endDate;
  public static final String STATUS = AgentRunDto.Fields.status;
  public static final String DURATION_MS = AgentRunDto.Fields.durationMs;
  public static final String INPUT_TOKENS = AgentRunDto.Fields.inputTokens;
  public static final String OUTPUT_TOKENS = AgentRunDto.Fields.outputTokens;
  public static final String REASONING_TOKENS = AgentRunDto.Fields.reasoningTokens;
  public static final String CACHE_CREATION_TOKENS = AgentRunDto.Fields.cacheCreationTokens;
  public static final String CACHE_READ_TOKENS = AgentRunDto.Fields.cacheReadTokens;
  public static final String TOTAL_TOKENS = AgentRunDto.Fields.totalTokens;
  public static final String MODEL_CALLS = AgentRunDto.Fields.modelCalls;
  public static final String TOOL_CALLS = AgentRunDto.Fields.toolCalls;
  public static final String HIT_LIMIT = AgentRunDto.Fields.hitLimit;
  public static final String LIMIT_HIT = AgentRunDto.Fields.limitHit;
  public static final String TOOL_FLOW_NODE_IDS = AgentRunDto.Fields.toolFlowNodeIds;
  public static final String TOOL_CALLS_BY_TOOL = AgentRunDto.Fields.toolCallsByTool;
  public static final String TOOL_CALLS_TOOL_FLOW_NODE_ID =
      AgentRunDto.ToolCallsDto.Fields.toolFlowNodeId;
  public static final String TOOL_CALLS_TOOL_NAME = AgentRunDto.ToolCallsDto.Fields.toolName;
  public static final String TOOL_CALLS_COUNT = AgentRunDto.ToolCallsDto.Fields.count;
  public static final String TOKENS_BY_MODEL = AgentRunDto.Fields.tokensByModel;
  public static final String PROCESS_INSTANCE_STATE = AgentRunDto.Fields.processInstanceState;
  public static final String PROCESS_INSTANCE_END_DATE = AgentRunDto.Fields.processInstanceEndDate;
  public static final String HAS_INCIDENT = AgentRunDto.Fields.hasIncident;
  public static final String INCIDENT_COUNT = AgentRunDto.Fields.incidentCount;
  public static final String INCIDENT_FLOW_NODE_IDS = AgentRunDto.Fields.incidentFlowNodeIds;
  public static final String PROCESS_USER_TASK_COUNT = AgentRunDto.Fields.processUserTaskCount;
  public static final String LAST_RECORD_POSITION = AgentRunDto.Fields.lastRecordPosition;
  public static final String LAST_METRIC_TOTALS = AgentRunDto.Fields.lastMetricTotals;
  public static final String ENRICHED_AT = AgentRunDto.Fields.enrichedAt;

  @Override
  public String getIndexName() {
    return DatabaseConstants.AGENT_INSTANCE_INDEX_NAME;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  public TypeMapping.Builder addProperties(final TypeMapping.Builder builder) {
    return builder
        .properties(AGENT_INSTANCE_ID, p -> p.keyword(k -> k))
        .properties(AGENT_DEFINITION_ID, p -> p.keyword(k -> k))
        .properties(AGENT_ID, p -> p.keyword(k -> k))
        .properties(FLOW_NODE_ID, p -> p.keyword(k -> k))
        .properties(FLOW_NODE_INSTANCE_IDS, p -> p.keyword(k -> k))
        .properties(PROCESS_DEFINITION_KEY, p -> p.keyword(k -> k))
        .properties(PROCESS_DEFINITION_ID, p -> p.keyword(k -> k))
        .properties(PROCESS_DEFINITION_VERSION, p -> p.keyword(k -> k))
        .properties(PROCESS_INSTANCE_ID, p -> p.keyword(k -> k))
        .properties(ROOT_PROCESS_INSTANCE_ID, p -> p.keyword(k -> k))
        .properties(TENANT_ID, p -> p.keyword(k -> k))
        .properties(MODEL, p -> p.keyword(k -> k))
        .properties(PROVIDER, p -> p.keyword(k -> k))
        .properties(MAX_MODEL_CALLS, p -> p.long_(k -> k))
        .properties(MAX_TOOL_CALLS, p -> p.long_(k -> k))
        .properties(MAX_TOKENS, p -> p.long_(k -> k))
        .properties(START_DATE, p -> p.date(k -> k.format(OPTIMIZE_DATE_FORMAT)))
        .properties(LAST_ACTIVITY_DATE, p -> p.date(k -> k.format(OPTIMIZE_DATE_FORMAT)))
        .properties(END_DATE, p -> p.date(k -> k.format(OPTIMIZE_DATE_FORMAT)))
        .properties(STATUS, p -> p.keyword(k -> k))
        .properties(DURATION_MS, p -> p.long_(k -> k))
        .properties(INPUT_TOKENS, p -> p.long_(k -> k))
        .properties(OUTPUT_TOKENS, p -> p.long_(k -> k))
        .properties(REASONING_TOKENS, p -> p.long_(k -> k))
        .properties(CACHE_CREATION_TOKENS, p -> p.long_(k -> k))
        .properties(CACHE_READ_TOKENS, p -> p.long_(k -> k))
        .properties(TOTAL_TOKENS, p -> p.long_(k -> k))
        .properties(MODEL_CALLS, p -> p.long_(k -> k))
        .properties(TOOL_CALLS, p -> p.long_(k -> k))
        .properties(HIT_LIMIT, p -> p.boolean_(k -> k))
        .properties(LIMIT_HIT, p -> p.keyword(k -> k))
        .properties(TOOL_FLOW_NODE_IDS, p -> p.keyword(k -> k))
        .properties(
            TOOL_CALLS_BY_TOOL,
            p ->
                p.nested(
                    n ->
                        n.properties(TOOL_CALLS_TOOL_FLOW_NODE_ID, np -> np.keyword(k -> k))
                            .properties(TOOL_CALLS_TOOL_NAME, np -> np.keyword(k -> k))
                            .properties(TOOL_CALLS_COUNT, np -> np.long_(k -> k))))
        .properties(
            TOKENS_BY_MODEL,
            p ->
                p.nested(
                    n ->
                        n.properties(
                                AgentRunDto.ModelTokensDto.Fields.provider,
                                np -> np.keyword(k -> k))
                            .properties(
                                AgentRunDto.ModelTokensDto.Fields.model, np -> np.keyword(k -> k))
                            .properties(
                                AgentRunDto.ModelTokensDto.Fields.inputTokens,
                                np -> np.long_(k -> k))
                            .properties(
                                AgentRunDto.ModelTokensDto.Fields.outputTokens,
                                np -> np.long_(k -> k))
                            .properties(
                                AgentRunDto.ModelTokensDto.Fields.reasoningTokens,
                                np -> np.long_(k -> k))
                            .properties(
                                AgentRunDto.ModelTokensDto.Fields.cacheCreationTokens,
                                np -> np.long_(k -> k))
                            .properties(
                                AgentRunDto.ModelTokensDto.Fields.cacheReadTokens,
                                np -> np.long_(k -> k))
                            .properties(
                                AgentRunDto.ModelTokensDto.Fields.totalTokens,
                                np -> np.long_(k -> k))))
        .properties(PROCESS_INSTANCE_STATE, p -> p.keyword(k -> k))
        .properties(PROCESS_INSTANCE_END_DATE, p -> p.date(k -> k.format(OPTIMIZE_DATE_FORMAT)))
        .properties(HAS_INCIDENT, p -> p.boolean_(k -> k))
        .properties(INCIDENT_COUNT, p -> p.long_(k -> k))
        .properties(INCIDENT_FLOW_NODE_IDS, p -> p.keyword(k -> k))
        .properties(PROCESS_USER_TASK_COUNT, p -> p.long_(k -> k))
        .properties(LAST_RECORD_POSITION, p -> p.long_(k -> k))
        // Import bookkeeping only, never queried.
        .properties(LAST_METRIC_TOTALS, p -> p.object(o -> o.enabled(false)))
        .properties(ENRICHED_AT, p -> p.date(k -> k.format(OPTIMIZE_DATE_FORMAT)));
  }
}
