/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.plan.process;

import static io.camunda.optimize.service.db.DatabaseConstants.AGENT_DEFINITION_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.AGENT_INSTANCE_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.PROCESS_INSTANCE_MULTI_ALIAS;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.END_DATE;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import io.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.AgentRunFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.builders.OptimizeSearchRequestBuilderES;
import io.camunda.optimize.service.db.es.filter.ProcessQueryFilterEnhancerES;
import io.camunda.optimize.service.db.es.report.interpreter.groupby.process.ProcessGroupByInterpreterFacadeES;
import io.camunda.optimize.service.db.es.report.interpreter.view.process.ProcessViewInterpreterFacadeES;
import io.camunda.optimize.service.db.es.report.interpreter.view.process.agentrun.ProcessViewAgentRunAdoptionRateInterpreterES;
import io.camunda.optimize.service.db.reader.ProcessDefinitionReader;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.plan.process.ProcessView;
import io.camunda.optimize.service.db.schema.index.AgentDefinitionIndex;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/**
 * Evaluates agent run reports against the flat agent-instance index. The agent index reuses the
 * process instance field names for definition, version, tenant and dates, so the inherited base
 * query and filters apply unchanged; only the index differs.
 */
@Component
@Conditional(ElasticSearchCondition.class)
public class AgentRunExecutionPlanInterpreterES extends AbstractProcessExecutionPlanInterpreterES {

  private static final String AGENTIC_PROCESS_KEYS_AGGREGATION = "agenticProcessKeys";
  private static final int MAX_AGENTIC_PROCESSES = 10_000;

  private final ProcessDefinitionReader processDefinitionReader;
  private final OptimizeElasticsearchClient esClient;
  private final ProcessQueryFilterEnhancerES queryFilterEnhancer;
  private final ProcessGroupByInterpreterFacadeES groupByInterpreter;
  private final ProcessViewInterpreterFacadeES viewInterpreter;

  public AgentRunExecutionPlanInterpreterES(
      final ProcessDefinitionReader processDefinitionReader,
      final OptimizeElasticsearchClient esClient,
      final ProcessQueryFilterEnhancerES queryFilterEnhancer,
      final ProcessGroupByInterpreterFacadeES groupByInterpreter,
      final ProcessViewInterpreterFacadeES viewInterpreter) {
    this.processDefinitionReader = processDefinitionReader;
    this.esClient = esClient;
    this.queryFilterEnhancer = queryFilterEnhancer;
    this.groupByInterpreter = groupByInterpreter;
    this.viewInterpreter = viewInterpreter;
  }

  @Override
  public Set<ProcessExecutionPlan> getSupportedExecutionPlans() {
    return Arrays.stream(ProcessExecutionPlan.values())
        .filter(
            plan -> plan.getView().getProcessViewDto().getEntity() == ProcessViewEntity.AGENT_RUN)
        .collect(Collectors.toSet());
  }

  @Override
  protected String[] getIndexNames(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return new String[] {AGENT_INSTANCE_INDEX_NAME};
  }

  @Override
  protected String[] getMultiIndexAlias() {
    return new String[] {AGENT_INSTANCE_INDEX_NAME};
  }

  /**
   * For the adoption rate, counts the denominator: completed process instances of agentic process
   * definitions, with the report's definitions, versions, tenants and date filters but without the
   * agent and model selection, which only applies to runs.
   */
  @Override
  protected Map<String, Long> retrievePerGroupBaselineCounts(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context,
      final String[] indices) {
    if (context.getPlan().getView() != ProcessView.PROCESS_VIEW_AGENT_RUN_ADOPTION_RATE) {
      return Map.of();
    }
    final ProcessReportDataDto reportData = context.getReportData();
    final List<ReportDataDefinitionDto> definitions = reportData.getDefinitions();
    final List<ProcessFilterDto<?>> filters = reportData.getFilter();
    final Set<String> agenticProcessKeys = fetchAgenticProcessKeys();
    final List<ReportDataDefinitionDto> agenticDefinitions =
        definitions.stream().filter(d -> agenticProcessKeys.contains(d.getKey())).toList();
    if (agenticDefinitions.isEmpty()) {
      return Map.of(ProcessViewAgentRunAdoptionRateInterpreterES.DENOMINATOR_KEY, 0L);
    }
    // The context is per request and single threaded, so the report data is narrowed for building
    // the denominator query and restored right after.
    try {
      reportData.setDefinitions(agenticDefinitions);
      reportData.setFilter(
          filters.stream().filter(f -> !(f instanceof AgentRunFilterDto)).toList());
      final BoolQuery.Builder query = getBaseQueryBuilder(context);
      query.filter(f -> f.exists(e -> e.field(END_DATE)));
      return Map.of(
          ProcessViewAgentRunAdoptionRateInterpreterES.DENOMINATOR_KEY,
          esClient.count(new String[] {PROCESS_INSTANCE_MULTI_ALIAS}, query));
    } catch (final IOException e) {
      throw new OptimizeRuntimeException("Could not count process instances for adoption rate", e);
    } finally {
      reportData.setDefinitions(definitions);
      reportData.setFilter(filters);
    }
  }

  private Set<String> fetchAgenticProcessKeys() {
    try {
      final var response =
          esClient.search(
              OptimizeSearchRequestBuilderES.of(
                  b ->
                      b.optimizeIndex(esClient, AGENT_DEFINITION_INDEX_NAME)
                          .size(0)
                          .aggregations(
                              AGENTIC_PROCESS_KEYS_AGGREGATION,
                              a ->
                                  a.terms(
                                      t ->
                                          t.field(AgentDefinitionIndex.PROCESS_DEFINITION_KEY)
                                              .size(MAX_AGENTIC_PROCESSES)))),
              Object.class);
      return response
          .aggregations()
          .get(AGENTIC_PROCESS_KEYS_AGGREGATION)
          .sterms()
          .buckets()
          .array()
          .stream()
          .map(bucket -> bucket.key().stringValue())
          .collect(Collectors.toSet());
    } catch (final IOException e) {
      throw new OptimizeRuntimeException("Could not read agentic process definitions", e);
    }
  }

  @Override
  protected ProcessDefinitionReader getProcessDefinitionReader() {
    return processDefinitionReader;
  }

  @Override
  protected OptimizeElasticsearchClient getEsClient() {
    return esClient;
  }

  @Override
  protected ProcessQueryFilterEnhancerES getQueryFilterEnhancer() {
    return queryFilterEnhancer;
  }

  @Override
  public ProcessGroupByInterpreterFacadeES getGroupByInterpreter() {
    return groupByInterpreter;
  }

  @Override
  public ProcessViewInterpreterFacadeES getViewInterpreter() {
    return viewInterpreter;
  }
}
