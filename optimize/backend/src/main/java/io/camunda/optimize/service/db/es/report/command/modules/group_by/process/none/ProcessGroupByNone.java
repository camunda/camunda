/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.command.modules.group_by.process.none;

import static io.camunda.optimize.service.db.es.report.command.modules.result.CompositeCommandResult.DistributedByResult;

import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.NoneGroupByDto;
import io.camunda.optimize.service.db.es.report.command.exec.ExecutionContext;
import io.camunda.optimize.service.db.es.report.command.modules.group_by.process.ProcessGroupByPart;
import io.camunda.optimize.service.db.es.report.command.modules.result.CompositeCommandResult;
import io.camunda.optimize.service.db.es.report.command.modules.result.CompositeCommandResult.GroupByResult;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProcessGroupByNone extends ProcessGroupByPart {

  @Override
  public List<AggregationBuilder> createAggregation(
      final SearchSourceBuilder searchSourceBuilder,
      final ExecutionContext<ProcessReportDataDto> context) {
    // nothing to do here, since we don't group so just pass the view part on
    return distributedByPart.createAggregations(context).stream()
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  @Override
  public void addQueryResult(
      final CompositeCommandResult compositeCommandResult,
      final SearchResponse response,
      final ExecutionContext<ProcessReportDataDto> context) {
    final List<DistributedByResult> distributions =
        distributedByPart.retrieveResult(response, response.getAggregations(), context);
    GroupByResult groupByResult = GroupByResult.createGroupByNone(distributions);
    compositeCommandResult.setGroup(groupByResult);
  }

  @Override
  protected void addGroupByAdjustmentsForCommandKeyGeneration(
      final ProcessReportDataDto reportData) {
    reportData.setGroupBy(new NoneGroupByDto());
  }
}
