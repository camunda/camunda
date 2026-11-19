/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.writer;

import static io.camunda.optimize.service.db.DatabaseConstants.AGENT_INSTANCE_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.ImportRequestDto;
import io.camunda.optimize.dto.optimize.RequestType;
import io.camunda.optimize.dto.optimize.query.agent.AgentRunDto;
import io.camunda.optimize.dto.optimize.query.agent.AgentRunDto.Fields;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

@Component
public class AgentRunWriter {

  private static final String IMPORT_NAME = "zeebe agent runs";
  private static final String PARAM_RUN = "run";
  private static final String PARAM_NEWEST_WINS_FIELDS = "newestWinsFields";
  private static final String PARAM_USAGE_FIELDS = "usageFields";

  /** Fields the newest record of a run sets as they are. */
  private static final List<String> NEWEST_WINS_FIELDS =
      List.of(
          Fields.agentId,
          Fields.flowNodeId,
          Fields.flowNodeInstanceIds,
          Fields.processDefinitionKey,
          Fields.processDefinitionId,
          Fields.processDefinitionVersion,
          Fields.processInstanceId,
          Fields.rootProcessInstanceId,
          Fields.tenantId,
          Fields.maxModelCalls,
          Fields.maxToolCalls,
          Fields.maxTokens,
          Fields.status,
          Fields.inputTokens,
          Fields.outputTokens,
          Fields.reasoningTokens,
          Fields.cacheWriteTokens,
          Fields.cacheReadTokens,
          Fields.totalTokens,
          Fields.modelCalls,
          Fields.toolCalls,
          Fields.limitHit,
          Fields.lastRecordPosition);

  /** Fields whose change counts as agent activity for {@code lastActivityDate}. */
  private static final List<String> USAGE_FIELDS =
      List.of(
          Fields.inputTokens,
          Fields.outputTokens,
          Fields.reasoningTokens,
          Fields.cacheWriteTokens,
          Fields.cacheReadTokens,
          Fields.modelCalls,
          Fields.toolCalls);

  // A page that holds only records at or below the stored position was already applied (re-import
  // after a restart), so it is a noop. Otherwise the page's newest state replaces the stored one,
  // except for the start date, which the oldest record set, and the end date, which only COMPLETED
  // sets. Fields this script does not touch, such as the enrichment fields, are kept.
  private static final String MERGE_SCRIPT =
      """
      def doc = ctx._source;
      def run = params.run;
      if (doc.lastRecordPosition != null
          && ((Number) doc.lastRecordPosition).longValue()
              >= ((Number) run.lastRecordPosition).longValue()) {
        ctx.op = 'noop';
      } else {
        boolean usageChanged = false;
        for (def field : params.usageFields) {
          def stored = doc[field];
          def incoming = run[field];
          if (stored == null || incoming == null) {
            if (stored != incoming) {
              usageChanged = true;
            }
          } else if (((Number) stored).longValue() != ((Number) incoming).longValue()) {
            usageChanged = true;
          }
        }
        if (usageChanged || doc.lastActivityDate == null) {
          doc.lastActivityDate = run.lastActivityDate;
        }
        for (def field : params.newestWinsFields) {
          doc[field] = run[field];
        }
        if (run.model != null) {
          doc.model = run.model;
        }
        if (run.provider != null) {
          doc.provider = run.provider;
        }
        if (doc.startDate == null) {
          doc.startDate = run.startDate;
        }
        if (run.endDate != null) {
          doc.endDate = run.endDate;
        }
      }
      """;

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(AgentRunWriter.class);

  private final ObjectMapper objectMapper;

  public AgentRunWriter(final ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public List<ImportRequestDto> generateAgentRunImports(final List<AgentRunDto> agentRuns) {
    LOG.debug("Creating imports for {} [{}].", agentRuns.size(), IMPORT_NAME);
    return agentRuns.stream()
        .map(
            run ->
                ImportRequestDto.builder()
                    .importName(IMPORT_NAME)
                    .type(RequestType.UPDATE)
                    .id(run.getAgentInstanceId())
                    .indexName(AGENT_INSTANCE_INDEX_NAME)
                    .source(run)
                    .retryNumberOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT)
                    .scriptData(
                        DatabaseWriterUtil.createScriptData(
                            MERGE_SCRIPT,
                            Map.of(
                                PARAM_RUN,
                                run,
                                PARAM_NEWEST_WINS_FIELDS,
                                NEWEST_WINS_FIELDS,
                                PARAM_USAGE_FIELDS,
                                USAGE_FIELDS),
                            objectMapper))
                    .build())
        .toList();
  }
}
