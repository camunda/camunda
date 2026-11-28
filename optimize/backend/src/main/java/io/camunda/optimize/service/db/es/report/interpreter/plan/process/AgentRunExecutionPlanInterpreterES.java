/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.plan.process;

import static io.camunda.optimize.service.db.DatabaseConstants.AGENT_INSTANCE_INDEX_NAME;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import io.camunda.optimize.dto.optimize.query.report.CommandEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.filter.ProcessQueryFilterEnhancerES;
import io.camunda.optimize.service.db.es.report.interpreter.groupby.process.ProcessGroupByInterpreterFacadeES;
import io.camunda.optimize.service.db.es.report.interpreter.util.DefinitionQueryUtilES;
import io.camunda.optimize.service.db.es.report.interpreter.view.process.ProcessViewInterpreterFacadeES;
import io.camunda.optimize.service.db.reader.AgentDefinitionReader;
import io.camunda.optimize.service.db.reader.ProcessDefinitionReader;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.interpreter.plan.process.AgentRunExecutionPlanInterpreter;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.schema.index.AgentRunIndex;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class AgentRunExecutionPlanInterpreterES extends AbstractProcessExecutionPlanInterpreterES
    implements AgentRunExecutionPlanInterpreter {

  private final ProcessDefinitionReader processDefinitionReader;
  private final OptimizeElasticsearchClient esClient;
  private final ProcessQueryFilterEnhancerES queryFilterEnhancer;
  private final ProcessGroupByInterpreterFacadeES groupByInterpreter;
  private final ProcessViewInterpreterFacadeES viewInterpreter;
  private final AgentDefinitionReader agentDefinitionReader;

  public AgentRunExecutionPlanInterpreterES(
      final ProcessDefinitionReader processDefinitionReader,
      final OptimizeElasticsearchClient esClient,
      final ProcessQueryFilterEnhancerES queryFilterEnhancer,
      final ProcessGroupByInterpreterFacadeES groupByInterpreter,
      final ProcessViewInterpreterFacadeES viewInterpreter,
      final AgentDefinitionReader agentDefinitionReader) {
    this.processDefinitionReader = processDefinitionReader;
    this.esClient = esClient;
    this.queryFilterEnhancer = queryFilterEnhancer;
    this.groupByInterpreter = groupByInterpreter;
    this.viewInterpreter = viewInterpreter;
    this.agentDefinitionReader = agentDefinitionReader;
  }

  @Override
  public CommandEvaluationResult<Object> interpret(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    AgentRunExecutionPlanInterpreter.validateFilters(context.getReportData());
    return super.interpret(context);
  }

  @Override
  public BoolQuery.Builder getBaseQueryBuilder(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return super.getBaseQueryBuilder(context).filter(definedAgentsQuery(context));
  }

  @Override
  protected BoolQuery.Builder setupUnfilteredBaseQueryBuilder(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return super.setupUnfilteredBaseQueryBuilder(context).filter(definedAgentsQuery(context));
  }

  private Query definedAgentsQuery(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final Map<String, Set<String>> agentIdsByTenant =
        AgentRunExecutionPlanInterpreter.definedAgentIdsByTenant(
            agentDefinitionReader, context.getReportData());
    if (agentIdsByTenant.isEmpty()) {
      return Query.of(q -> q.bool(b -> b.mustNot(m -> m.matchAll(a -> a))));
    }
    final BoolQuery.Builder query = new BoolQuery.Builder().minimumShouldMatch("1");
    agentIdsByTenant.forEach(
        (tenantId, agentIds) ->
            query.should(
                s ->
                    s.bool(
                        b ->
                            b.must(
                                    DefinitionQueryUtilES.createTenantIdQuery(
                                        AgentRunIndex.TENANT_ID,
                                        Collections.singletonList(tenantId)))
                                .must(
                                    m ->
                                        m.terms(
                                            t ->
                                                t.field(AgentRunIndex.AGENT_ID)
                                                    .terms(
                                                        tt ->
                                                            tt.value(
                                                                agentIds.stream()
                                                                    .map(FieldValue::of)
                                                                    .toList())))))));
    return Query.of(q -> q.bool(query.build()));
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
  protected ProcessQueryFilterEnhancerES getQueryFilterEnhancer() {
    return queryFilterEnhancer;
  }

  @Override
  protected OptimizeElasticsearchClient getEsClient() {
    return esClient;
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
