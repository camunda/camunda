/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.plan.process;

import static io.camunda.optimize.service.db.DatabaseConstants.AGENT_INSTANCE_INDEX_NAME;

import io.camunda.optimize.dto.optimize.query.report.CommandEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.os.client.dsl.QueryDSL;
import io.camunda.optimize.service.db.os.report.filter.AgentRunQueryFilterEnhancerOS;
import io.camunda.optimize.service.db.os.report.interpreter.groupby.process.ProcessGroupByInterpreterFacadeOS;
import io.camunda.optimize.service.db.os.report.interpreter.view.process.ProcessViewInterpreterFacadeOS;
import io.camunda.optimize.service.db.os.util.DefinitionQueryUtilOS;
import io.camunda.optimize.service.db.reader.AgentDefinitionReader;
import io.camunda.optimize.service.db.reader.ProcessDefinitionReader;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.interpreter.plan.process.AgentRunExecutionPlanInterpreter;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.schema.index.AgentRunIndex;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class AgentRunExecutionPlanInterpreterOS extends AbstractProcessExecutionPlanInterpreterOS
    implements AgentRunExecutionPlanInterpreter {

  private final ProcessDefinitionReader processDefinitionReader;
  private final OptimizeOpenSearchClient osClient;
  private final AgentRunQueryFilterEnhancerOS queryFilterEnhancer;
  private final ProcessGroupByInterpreterFacadeOS groupByInterpreter;
  private final ProcessViewInterpreterFacadeOS viewInterpreter;
  private final AgentDefinitionReader agentDefinitionReader;

  public AgentRunExecutionPlanInterpreterOS(
      final ProcessDefinitionReader processDefinitionReader,
      final OptimizeOpenSearchClient osClient,
      final AgentRunQueryFilterEnhancerOS queryFilterEnhancer,
      final ProcessGroupByInterpreterFacadeOS groupByInterpreter,
      final ProcessViewInterpreterFacadeOS viewInterpreter,
      final AgentDefinitionReader agentDefinitionReader) {
    this.processDefinitionReader = processDefinitionReader;
    this.osClient = osClient;
    this.queryFilterEnhancer = queryFilterEnhancer;
    this.groupByInterpreter = groupByInterpreter;
    this.viewInterpreter = viewInterpreter;
    this.agentDefinitionReader = agentDefinitionReader;
  }

  @Override
  public BoolQuery.Builder baseQueryBuilder(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return super.baseQueryBuilder(context).filter(definedAgentsQuery(context));
  }

  @Override
  protected BoolQuery.Builder unfilteredBaseQueryBuilder(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return super.unfilteredBaseQueryBuilder(context).filter(definedAgentsQuery(context));
  }

  private Query definedAgentsQuery(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final Map<String, Set<String>> agentIdsByTenant =
        AgentRunExecutionPlanInterpreter.definedAgentIdsByTenant(
            agentDefinitionReader, context.getReportData());
    if (agentIdsByTenant.isEmpty()) {
      return QueryDSL.not(QueryDSL.matchAll());
    }
    final List<Query> tenantQueries = new ArrayList<>();
    agentIdsByTenant.forEach(
        (tenantId, agentIds) ->
            tenantQueries.add(
                QueryDSL.and(
                    DefinitionQueryUtilOS.createTenantIdQuery(
                        AgentRunIndex.TENANT_ID, Collections.singletonList(tenantId)),
                    QueryDSL.stringTerms(AgentRunIndex.AGENT_ID, agentIds))));
    return QueryDSL.or("1", tenantQueries.toArray(Query[]::new));
  }

  @Override
  public CommandEvaluationResult<Object> interpret(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    AgentRunExecutionPlanInterpreter.validateFilters(context.getReportData());
    return super.interpret(context);
  }

  @Override
  protected BoolQuery.Builder createDefinitionQuery(final ReportDataDefinitionDto definitionDto) {
    return DefinitionQueryUtilOS.createDefinitionQuery(
        definitionDto.getKey(),
        definitionDto.getVersions(),
        definitionDto.getTenantIds(),
        AgentRunIndex.PROCESS_DEFINITION_KEY,
        AgentRunIndex.PROCESS_DEFINITION_VERSION,
        AgentRunIndex.TENANT_ID,
        processDefinitionReader::getLatestVersionToKey);
  }

  @Override
  protected String[] getIndexNames(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return getMultiIndexAlias();
  }

  @Override
  protected String[] getMultiIndexAlias() {
    return new String[] {AGENT_INSTANCE_INDEX_NAME};
  }

  @Override
  protected ProcessDefinitionReader getProcessDefinitionReader() {
    return processDefinitionReader;
  }

  @Override
  protected AgentRunQueryFilterEnhancerOS getQueryFilterEnhancer() {
    return queryFilterEnhancer;
  }

  @Override
  protected OptimizeOpenSearchClient getOsClient() {
    return osClient;
  }

  @Override
  public ProcessGroupByInterpreterFacadeOS getGroupByInterpreter() {
    return groupByInterpreter;
  }

  @Override
  public ProcessViewInterpreterFacadeOS getViewInterpreter() {
    return viewInterpreter;
  }
}
