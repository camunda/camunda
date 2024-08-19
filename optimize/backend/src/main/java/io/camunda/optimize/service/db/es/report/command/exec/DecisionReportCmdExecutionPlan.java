/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.command.exec;

import static io.camunda.optimize.service.db.DatabaseConstants.DECISION_INSTANCE_MULTI_ALIAS;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;

import io.camunda.optimize.dto.optimize.query.report.CommandEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.es.filter.DecisionQueryFilterEnhancer;
import io.camunda.optimize.service.db.es.report.command.modules.distributed_by.DistributedByPart;
import io.camunda.optimize.service.db.es.report.command.modules.group_by.GroupByPart;
import io.camunda.optimize.service.db.es.report.command.modules.result.CompositeCommandResult;
import io.camunda.optimize.service.db.es.report.command.modules.view.ViewPart;
import io.camunda.optimize.service.db.es.schema.index.DecisionInstanceIndexES;
import io.camunda.optimize.service.db.reader.DecisionDefinitionReader;
import io.camunda.optimize.service.util.DefinitionQueryUtilES;
import io.camunda.optimize.service.util.InstanceIndexUtil;
import java.util.function.Function;
import java.util.function.Supplier;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.slf4j.Logger;

public class DecisionReportCmdExecutionPlan<T>
    extends ReportCmdExecutionPlan<T, DecisionReportDataDto> {

  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(DecisionReportCmdExecutionPlan.class);
  private final DecisionDefinitionReader decisionDefinitionReader;
  private final DecisionQueryFilterEnhancer queryFilterEnhancer;

  public DecisionReportCmdExecutionPlan(
      final ViewPart<DecisionReportDataDto> viewPart,
      final GroupByPart<DecisionReportDataDto> groupByPart,
      final DistributedByPart<DecisionReportDataDto> distributedByPart,
      final Function<CompositeCommandResult, CommandEvaluationResult<T>> mapToReportResult,
      final DatabaseClient databaseClient,
      final DecisionDefinitionReader decisionDefinitionReader,
      final DecisionQueryFilterEnhancer queryFilterEnhancer) {
    super(viewPart, groupByPart, distributedByPart, mapToReportResult, databaseClient);
    this.decisionDefinitionReader = decisionDefinitionReader;
    this.queryFilterEnhancer = queryFilterEnhancer;
  }

  @Override
  public BoolQueryBuilder setupBaseQuery(final ExecutionContext<DecisionReportDataDto> context) {
    final BoolQueryBuilder boolQueryBuilder = setupUnfilteredBaseQuery(context);
    queryFilterEnhancer.addFilterToQuery(
        boolQueryBuilder, context.getReportData().getFilter(), context.getFilterContext());
    return boolQueryBuilder;
  }

  @Override
  protected BoolQueryBuilder setupUnfilteredBaseQuery(
      final ExecutionContext<DecisionReportDataDto> context) {
    final BoolQueryBuilder definitionFilterQuery = boolQuery().minimumShouldMatch(1);
    // for decision reports only one (the first) definition is supported
    context.getReportData().getDefinitions().stream()
        .findFirst()
        .ifPresent(
            definitionDto ->
                definitionFilterQuery.should(
                    DefinitionQueryUtilES.createDefinitionQuery(
                        definitionDto.getKey(),
                        definitionDto.getVersions(),
                        definitionDto.getTenantIds(),
                        new DecisionInstanceIndexES(definitionDto.getKey()),
                        decisionDefinitionReader::getLatestVersionToKey)));
    return definitionFilterQuery;
  }

  @Override
  protected String[] getIndexNames(final ExecutionContext<DecisionReportDataDto> context) {
    return InstanceIndexUtil.getDecisionInstanceIndexAliasName(context.getReportData());
  }

  @Override
  protected String[] getMultiIndexAlias() {
    return new String[] {DECISION_INSTANCE_MULTI_ALIAS};
  }

  @Override
  protected Supplier<DecisionReportDataDto> getDataDtoSupplier() {
    return DecisionReportDataDto::new;
  }
}
