/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report.interpreter.view.process.agent;

import static io.camunda.optimize.dto.optimize.ReportConstants.PAGINATION_DEFAULT_LIMIT;
import static io.camunda.optimize.dto.optimize.ReportConstants.PAGINATION_DEFAULT_OFFSET;
import static io.camunda.optimize.service.db.DatabaseConstants.MAX_RESPONSE_SIZE_LIMIT;

import io.camunda.optimize.dto.optimize.query.agent.AgentDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataAgentDto;
import io.camunda.optimize.dto.optimize.rest.pagination.PaginationDto;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.interpreter.util.ProcessDefinitionNameResolver;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;

/**
 * Builds the rows of the agent inventory report from the runs aggregated per agent, independent of
 * the database. The database interpreters aggregate the runs in agentInstances order and hand over
 * the buckets up to the end of the requested page.
 */
public final class AgentInventoryRows {

  public static final String AGENTS_AGGREGATION = "agentInventory";
  public static final String TOTAL_TOKENS_AGGREGATION = "totalTokens";
  public static final String LAST_END_DATE_AGGREGATION = "lastEndDate";
  public static final String UNSPECIFIED_AGENT_TYPE = "UNSPECIFIED";

  private static final Comparator<AgentDefinitionDto> NEWEST_DEFINITION_FIRST =
      Comparator.comparingInt(AgentInventoryRows::highestVersion)
          .reversed()
          .thenComparing(
              AgentDefinitionDto::getTenantId, Comparator.nullsFirst(Comparator.naturalOrder()));

  private AgentInventoryRows() {}

  /** How many agent buckets the aggregation returns: every row up to the end of the page. */
  public static int bucketCount(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return bucketCount(offset(context), limit(context));
  }

  static int bucketCount(final int offset, final int limit) {
    return (int) Math.min((long) offset + limit, MAX_RESPONSE_SIZE_LIMIT);
  }

  /** Turns the buckets into the rows of the page the report is evaluated for. */
  public static List<RawDataAgentDto> toPage(
      final List<AgentRunStats> buckets,
      final List<AgentDefinitionDto> agentDefinitions,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return toPage(
        buckets,
        agentDefinitions,
        context.getReportData().getDefinitions(),
        offset(context),
        limit(context));
  }

  /**
   * Turns the buckets into the rows from {@code offset}, at most {@code limit} of them. Name and
   * agent type come from the newest agent definition the user may see, the process name from the
   * report's process definitions.
   */
  static List<RawDataAgentDto> toPage(
      final List<AgentRunStats> buckets,
      final List<AgentDefinitionDto> agentDefinitions,
      final List<ReportDataDefinitionDto> processDefinitions,
      final int offset,
      final int limit) {
    final Map<String, AgentDefinitionDto> definitionByAgentId = newestDefinitions(agentDefinitions);
    final Map<String, String> processNames =
        ProcessDefinitionNameResolver.labelsByKey(processDefinitions);
    return buckets.stream()
        .skip(offset)
        .limit(limit)
        .map(bucket -> toRow(bucket, definitionByAgentId.get(bucket.agentId()), processNames))
        .toList();
  }

  private static RawDataAgentDto toRow(
      final AgentRunStats bucket,
      final AgentDefinitionDto definition,
      final Map<String, String> processNames) {
    // Runs only count with an imported definition, so the fallbacks only apply if the definition
    // is deleted between the gate and this lookup.
    final String processDefinitionKey =
        Optional.ofNullable(definition)
            .map(AgentDefinitionDto::getProcessDefinitionKey)
            .orElse(StringUtils.substringBefore(bucket.agentId(), ":"));
    final String flowNodeId =
        Optional.ofNullable(definition)
            .map(AgentDefinitionDto::getFlowNodeId)
            .orElse(StringUtils.substringAfter(bucket.agentId(), ":"));

    final RawDataAgentDto row = new RawDataAgentDto();
    row.setAgentId(bucket.agentId());
    row.setAgentName(
        Optional.ofNullable(definition)
            .map(AgentDefinitionDto::getName)
            .filter(StringUtils::isNotBlank)
            .orElse(flowNodeId));
    row.setAgentType(
        Optional.ofNullable(definition)
            .map(AgentDefinitionDto::getAgentType)
            .orElse(UNSPECIFIED_AGENT_TYPE));
    row.setProcessDefinitionKey(processDefinitionKey);
    row.setProcessName(processNames.getOrDefault(processDefinitionKey, processDefinitionKey));
    row.setAgentInstances(bucket.agentInstances());
    row.setTotalTokens(Math.round(bucket.totalTokens()));
    row.setAverageTokensPerAgentInstance(
        bucket.agentInstances() == 0 ? null : bucket.totalTokens() / bucket.agentInstances());
    row.setLastAgentInstanceEndDate(
        Optional.ofNullable(bucket.lastEndDateMillis())
            .filter(Double::isFinite)
            .map(
                millis ->
                    OffsetDateTime.ofInstant(
                        Instant.ofEpochMilli(millis.longValue()), ZoneOffset.UTC))
            .orElse(null));
    return row;
  }

  private static Map<String, AgentDefinitionDto> newestDefinitions(
      final List<AgentDefinitionDto> agentDefinitions) {
    final Map<String, AgentDefinitionDto> definitionByAgentId = new HashMap<>();
    agentDefinitions.forEach(
        definition ->
            definitionByAgentId.merge(
                definition.getAgentId(),
                definition,
                (current, candidate) ->
                    NEWEST_DEFINITION_FIRST.compare(candidate, current) < 0 ? candidate : current));
    return definitionByAgentId;
  }

  private static int highestVersion(final AgentDefinitionDto definition) {
    return definition.getProcessDefinitionVersions().stream()
        .mapToInt(AgentInventoryRows::parseVersion)
        .max()
        .orElse(-1);
  }

  private static int parseVersion(final String version) {
    try {
      return Integer.parseInt(version);
    } catch (final NumberFormatException e) {
      return -1;
    }
  }

  private static int offset(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return context.getPagination().map(PaginationDto::getOffset).orElse(PAGINATION_DEFAULT_OFFSET);
  }

  private static int limit(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return context
        .getPagination()
        .map(PaginationDto::getLimit)
        .map(limit -> Math.min(limit, MAX_RESPONSE_SIZE_LIMIT))
        .orElse(PAGINATION_DEFAULT_LIMIT);
  }

  /** The runs of one agent in the report's range. */
  public record AgentRunStats(
      String agentId, long agentInstances, double totalTokens, Double lastEndDateMillis) {}
}
