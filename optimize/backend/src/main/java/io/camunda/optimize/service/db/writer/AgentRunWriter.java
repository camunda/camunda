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
import static io.camunda.optimize.service.db.DatabaseConstants.OPTIMIZE_DATE_FORMAT;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.ImportRequestDto;
import io.camunda.optimize.dto.optimize.RequestType;
import io.camunda.optimize.dto.optimize.query.agent.AgentRunDto;
import io.camunda.optimize.service.importing.engine.service.zeebe.AgentRunRecordFolder.FoldResult;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Upserts agent run documents. A first insert stores the folded document as is; a run that spans
 * several import pages is merged by a painless script that applies the same rules as {@code
 * AgentRunRecordFolder} and {@code AgentRunDerivedFields} on top of the stored document.
 */
@Component
public class AgentRunWriter {

  private static final String IMPORT_NAME = "agent runs";

  // Config, metrics and status: a later page carries later records, so its non-null values win.
  private static final List<String> LATEST_FIELDS =
      List.of(
          AgentRunDto.Fields.agentInstanceId,
          AgentRunDto.Fields.agentDefinitionId,
          AgentRunDto.Fields.agentId,
          AgentRunDto.Fields.flowNodeId,
          AgentRunDto.Fields.processDefinitionKey,
          AgentRunDto.Fields.processDefinitionId,
          AgentRunDto.Fields.processDefinitionVersion,
          AgentRunDto.Fields.processInstanceId,
          AgentRunDto.Fields.rootProcessInstanceId,
          AgentRunDto.Fields.tenantId,
          AgentRunDto.Fields.status,
          AgentRunDto.Fields.model,
          AgentRunDto.Fields.provider,
          AgentRunDto.Fields.maxModelCalls,
          AgentRunDto.Fields.maxToolCalls,
          AgentRunDto.Fields.maxTokens,
          AgentRunDto.Fields.inputTokens,
          AgentRunDto.Fields.outputTokens,
          AgentRunDto.Fields.reasoningTokens,
          AgentRunDto.Fields.cacheCreationTokens,
          AgentRunDto.Fields.cacheReadTokens,
          AgentRunDto.Fields.modelCalls,
          AgentRunDto.Fields.toolCalls,
          AgentRunDto.Fields.lastActivityDate,
          AgentRunDto.Fields.endDate,
          AgentRunDto.Fields.lastRecordPosition);

  private static final List<String> TOKEN_FIELDS =
      List.of(
          AgentRunDto.MetricTotalsDto.Fields.inputTokens,
          AgentRunDto.MetricTotalsDto.Fields.outputTokens,
          AgentRunDto.MetricTotalsDto.Fields.reasoningTokens,
          AgentRunDto.MetricTotalsDto.Fields.cacheCreationTokens,
          AgentRunDto.MetricTotalsDto.Fields.cacheReadTokens);

  private static final String MERGE_SCRIPT =
      """
      boolean reached(def value, def limit) {
        return limit != null && ((Number) limit).longValue() > 0
            && value != null && ((Number) value).longValue() >= ((Number) limit).longValue();
      }

      def doc = ctx._source;
      def inc = params.run;
      long prevPos = doc.lastRecordPosition == null
          ? -1L : ((Number) doc.lastRecordPosition).longValue();
      if (((Number) inc.lastRecordPosition).longValue() <= prevPos) {
        ctx.op = 'none';
        return;
      }

      for (def field : params.latestFields) {
        if (inc[field] != null) { doc[field] = inc[field]; }
      }
      if (doc.startDate == null && inc.startDate != null) { doc.startDate = inc.startDate; }
      if (inc.flowNodeInstanceIds != null && !inc.flowNodeInstanceIds.isEmpty()) {
        doc.flowNodeInstanceIds = inc.flowNodeInstanceIds;
      }

      if (doc.toolFlowNodeIds == null) { doc.toolFlowNodeIds = new ArrayList(); }
      if (doc.toolCallsByTool == null) { doc.toolCallsByTool = new ArrayList(); }
      for (def tool : inc.toolCallsByTool) {
        if (!doc.toolFlowNodeIds.contains(tool.toolFlowNodeId)) {
          doc.toolFlowNodeIds.add(tool.toolFlowNodeId);
          doc.toolCallsByTool.add(tool);
        }
      }

      // Split the new tokens by model, continuing from the totals already stored.
      def prev = doc.lastMetricTotals;
      if (prev == null) {
        prev = new HashMap();
        for (def k : params.tokenFields) { prev[k] = 0L; }
      }
      if (doc.tokensByModel == null) { doc.tokensByModel = new ArrayList(); }
      for (def o : params.observations) {
        if (((Number) o.position).longValue() <= prevPos) { continue; }
        def cur = new HashMap();
        for (def k : params.tokenFields) { cur[k] = o[k] != null ? o[k] : prev[k]; }
        def entry = null;
        for (def e : doc.tokensByModel) {
          if (e.provider == o.provider && e.model == o.model) { entry = e; break; }
        }
        if (entry == null) {
          entry = new HashMap();
          entry.provider = o.provider;
          entry.model = o.model;
          for (def k : params.tokenFields) { entry[k] = 0L; }
          entry.totalTokens = 0L;
          doc.tokensByModel.add(entry);
        }
        long total = 0L;
        for (def k : params.tokenFields) {
          long d = ((Number) cur[k]).longValue() - ((Number) prev[k]).longValue();
          if (d < 0L) { d = 0L; }
          entry[k] = ((Number) entry[k]).longValue() + d;
          // reasoning is already part of output
          if (k != 'reasoningTokens') { total += d; }
        }
        entry.totalTokens = ((Number) entry.totalTokens).longValue() + total;
        prev = cur;
      }
      doc.lastMetricTotals = prev;

      boolean anyToken = false;
      long tokenSum = 0L;
      for (def c : [doc.inputTokens, doc.outputTokens, doc.cacheCreationTokens, doc.cacheReadTokens]) {
        if (c != null) { anyToken = true; tokenSum += ((Number) c).longValue(); }
      }
      if (anyToken) { doc.totalTokens = tokenSum; } else { doc.totalTokens = null; }

      if (doc.startDate != null && doc.lastActivityDate != null) {
        def formatter = new SimpleDateFormat(params.dateFormatPattern);
        doc.durationMs = formatter.parse(doc.lastActivityDate).getTime()
            - formatter.parse(doc.startDate).getTime();
      }

      def hit = new ArrayList();
      if (reached(doc.modelCalls, doc.maxModelCalls)) { hit.add('maxModelCalls'); }
      if (reached(doc.toolCalls, doc.maxToolCalls)) { hit.add('maxToolCalls'); }
      if (reached(doc.totalTokens, doc.maxTokens)) { hit.add('maxTokens'); }
      doc.limitHit = hit;
      doc.hitLimit = !hit.isEmpty();
      """;

  private static final Set<String> ENRICHMENT_FIELDS =
      Set.of(
          AgentRunDto.Fields.processInstanceState,
          AgentRunDto.Fields.processInstanceEndDate,
          AgentRunDto.Fields.hasIncident,
          AgentRunDto.Fields.incidentCount,
          AgentRunDto.Fields.incidentFlowNodeIds,
          AgentRunDto.Fields.processUserTaskCount,
          AgentRunDto.Fields.toolCallsByTool,
          AgentRunDto.Fields.enrichedAt);

  private final ObjectMapper objectMapper;

  public AgentRunWriter(final ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public List<ImportRequestDto> generateAgentRunImports(final List<FoldResult> foldedRuns) {
    return foldedRuns.stream()
        .map(
            folded ->
                ImportRequestDto.builder()
                    .importName(IMPORT_NAME)
                    .type(RequestType.UPDATE)
                    .id(folded.run().getAgentInstanceId())
                    .indexName(AGENT_INSTANCE_INDEX_NAME)
                    .source(folded.run())
                    .retryNumberOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT)
                    .scriptData(
                        DatabaseWriterUtil.createScriptData(
                            MERGE_SCRIPT,
                            Map.of(
                                "run",
                                folded.run(),
                                "observations",
                                folded.metricObservations(),
                                "latestFields",
                                LATEST_FIELDS,
                                "tokenFields",
                                TOKEN_FIELDS,
                                "dateFormatPattern",
                                OPTIMIZE_DATE_FORMAT),
                            objectMapper))
                    .build())
        .toList();
  }

  /** Overwrites only the enrichment fields of existing runs. */
  public List<ImportRequestDto> generateEnrichmentImports(final List<AgentRunDto> enrichedRuns) {
    return enrichedRuns.stream()
        .map(
            enriched ->
                ImportRequestDto.builder()
                    .importName(IMPORT_NAME)
                    .type(RequestType.UPDATE)
                    .id(enriched.getAgentInstanceId())
                    .indexName(AGENT_INSTANCE_INDEX_NAME)
                    .source(enriched)
                    .retryNumberOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT)
                    .scriptData(
                        DatabaseWriterUtil.createScriptData(
                            ENRICHMENT_FIELDS, enriched, objectMapper))
                    .build())
        .toList();
  }
}
