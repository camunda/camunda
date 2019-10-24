/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.exec;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.DecisionReportResultDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.filter.DecisionQueryFilterEnhancer;
import org.camunda.optimize.service.es.reader.DecisionDefinitionReader;
import org.camunda.optimize.service.es.report.command.modules.distributed_by.DistributedByPart;
import org.camunda.optimize.service.es.report.command.modules.group_by.GroupByPart;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult;
import org.camunda.optimize.service.es.report.command.modules.view.ViewPart;
import org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex;
import org.elasticsearch.index.query.BoolQueryBuilder;

import java.util.function.Function;
import java.util.function.Supplier;

import static org.camunda.optimize.service.util.DefinitionQueryUtil.createDefinitionQuery;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_INSTANCE_INDEX_NAME;

@Slf4j
public class DecisionReportCmdExecutionPlan<R extends DecisionReportResultDto>
  extends ReportCmdExecutionPlan<R, DecisionReportDataDto> {

  private final DecisionDefinitionReader decisionDefinitionReader;
  private final DecisionQueryFilterEnhancer queryFilterEnhancer;

  public DecisionReportCmdExecutionPlan(final ViewPart<DecisionReportDataDto> viewPart,
                                        final GroupByPart<DecisionReportDataDto> groupByPart,
                                        final DistributedByPart<DecisionReportDataDto> distributedByPart,
                                        final Function<CompositeCommandResult, R> mapToReportResult,
                                        final OptimizeElasticsearchClient esClient,
                                        final DecisionDefinitionReader decisionDefinitionReader,
                                        final DecisionQueryFilterEnhancer queryFilterEnhancer) {
    super(viewPart, groupByPart, distributedByPart, mapToReportResult, esClient);
    this.decisionDefinitionReader = decisionDefinitionReader;
    this.queryFilterEnhancer = queryFilterEnhancer;
  }

  @Override
  protected BoolQueryBuilder setupBaseQuery(final DecisionReportDataDto reportData) {
    BoolQueryBuilder boolQueryBuilder = createDefinitionQuery(
      reportData.getDefinitionKey(),
      reportData.getDefinitionVersions(),
      reportData.getTenantIds(),
      new DecisionInstanceIndex(),
      decisionDefinitionReader::getLatestVersionToKey
    );
    queryFilterEnhancer.addFilterToQuery(boolQueryBuilder, reportData.getFilter());
    return boolQueryBuilder;
  }

  @Override
  protected String getIndexName() {
    return DECISION_INSTANCE_INDEX_NAME;
  }

  @Override
  protected Supplier<DecisionReportDataDto> getDataDtoSupplier() {
    return DecisionReportDataDto::new;
  }
}
