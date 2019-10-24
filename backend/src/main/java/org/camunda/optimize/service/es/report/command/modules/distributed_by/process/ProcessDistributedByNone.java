/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.modules.distributed_by.process;

import org.camunda.optimize.dto.optimize.query.report.single.configuration.DistributedBy;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.DistributedByResult;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.ViewResult;
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
  public AggregationBuilder createAggregation(final ProcessReportDataDto definitionData) {
    return viewPart.createAggregation(definitionData);
  }

  @Override
  public List<DistributedByResult> retrieveResult(final Aggregations aggregations,
                                                  final ProcessReportDataDto reportData) {
    final ViewResult viewResult = viewPart.retrieveResult(aggregations, reportData);
    return Collections.singletonList(DistributedByResult.createEmptyDistributedBy(viewResult));
  }

  @Override
  protected void addAdjustmentsForCommandKeyGeneration(final ProcessReportDataDto dataForCommandKey) {
    dataForCommandKey.getConfiguration().setDistributedBy(DistributedBy.NONE);
  }
}
