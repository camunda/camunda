/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.command.exec;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.report.CommandEvaluationResult;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.filter.DecisionQueryFilterEnhancer;
import org.camunda.optimize.service.es.reader.DecisionDefinitionReader;
import org.camunda.optimize.service.es.report.command.modules.distributed_by.DistributedByPart;
import org.camunda.optimize.service.es.report.command.modules.group_by.GroupByPart;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult;
import org.camunda.optimize.service.es.report.command.modules.view.ViewPart;
import org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex;
import org.camunda.optimize.service.util.DefinitionQueryUtil;
import org.camunda.optimize.service.util.InstanceIndexUtil;
import org.elasticsearch.index.query.BoolQueryBuilder;

import java.util.function.Function;
import java.util.function.Supplier;

import static org.elasticsearch.index.query.QueryBuilders.boolQuery;

@Slf4j
public class DecisionReportCmdExecutionPlan<T> extends ReportCmdExecutionPlan<T, DecisionReportDataDto> {

  private final DecisionDefinitionReader decisionDefinitionReader;
  private final DecisionQueryFilterEnhancer queryFilterEnhancer;

  public DecisionReportCmdExecutionPlan(final ViewPart<DecisionReportDataDto> viewPart,
                                        final GroupByPart<DecisionReportDataDto> groupByPart,
                                        final DistributedByPart<DecisionReportDataDto> distributedByPart,
                                        final Function<CompositeCommandResult, CommandEvaluationResult<T>> mapToReportResult,
                                        final OptimizeElasticsearchClient esClient,
                                        final DecisionDefinitionReader decisionDefinitionReader,
                                        final DecisionQueryFilterEnhancer queryFilterEnhancer) {
    super(viewPart, groupByPart, distributedByPart, mapToReportResult, esClient);
    this.decisionDefinitionReader = decisionDefinitionReader;
    this.queryFilterEnhancer = queryFilterEnhancer;
  }

  @Override
  public BoolQueryBuilder setupBaseQuery(final ExecutionContext<DecisionReportDataDto> context) {
    final BoolQueryBuilder boolQueryBuilder = setupUnfilteredBaseQuery(context);
    queryFilterEnhancer.addFilterToQuery(
      boolQueryBuilder, context.getReportData().getFilter(), context.getFilterContext()
    );
    return boolQueryBuilder;
  }

  @Override
  protected BoolQueryBuilder setupUnfilteredBaseQuery(final ExecutionContext<DecisionReportDataDto> context) {
    final BoolQueryBuilder definitionFilterQuery = boolQuery().minimumShouldMatch(1);
    // for decision reports only one (the first) definition is supported
    context.getReportData()
      .getDefinitions()
      .stream()
      .findFirst()
      .ifPresent(definitionDto -> definitionFilterQuery.should(
        DefinitionQueryUtil.createDefinitionQuery(
          definitionDto.getKey(),
          definitionDto.getVersions(),
          definitionDto.getTenantIds(),
          new DecisionInstanceIndex(definitionDto.getKey()),
          decisionDefinitionReader::getLatestVersionToKey
        )
      ));
    return definitionFilterQuery;
  }

  @Override
  protected String[] getIndexNames(final ExecutionContext<DecisionReportDataDto> context) {
    return InstanceIndexUtil.getDecisionInstanceIndexAliasName(context.getReportData());
  }

  @Override
  protected Supplier<DecisionReportDataDto> getDataDtoSupplier() {
    return DecisionReportDataDto::new;
  }
}
