/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.command.modules.distributed_by.process;

import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.distributed.NoneDistributedByDto;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.DistributedByResult;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.ViewResult;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProcessDistributedByNone extends ProcessDistributedByPart {

  @Override
  public boolean isKeyOfNumericType(final ExecutionContext<ProcessReportDataDto> context) {
    return false;
  }

  @Override
  public List<AggregationBuilder> createAggregations(final ExecutionContext<ProcessReportDataDto> context) {
    return viewPart.createAggregations(context);
  }

  @Override
  public List<DistributedByResult> retrieveResult(final SearchResponse response,
                                                  final Aggregations aggregations,
                                                  final ExecutionContext<ProcessReportDataDto> context) {
    final ViewResult viewResult = viewPart.retrieveResult(response, aggregations, context);
    return Collections.singletonList(DistributedByResult.createDistributedByNoneResult(viewResult));
  }

  @Override
  public List<DistributedByResult> createEmptyResult(final ExecutionContext<ProcessReportDataDto> context) {
    return Collections.singletonList(DistributedByResult.createDistributedByNoneResult(viewPart.createEmptyResult(context)));
  }

  @Override
  protected void addAdjustmentsForCommandKeyGeneration(final ProcessReportDataDto dataForCommandKey) {
    dataForCommandKey.setDistributedBy(new NoneDistributedByDto());
  }
}
