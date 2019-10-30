/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.modules.view;

import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.ViewResult;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;

public abstract class ViewPart<Data extends SingleReportDataDto> {

  public void adjustBaseQuery(BoolQueryBuilder baseQuery, Data definitionData) {
    // by default don't do anything
  }

  public abstract AggregationBuilder createAggregation(final ExecutionContext<Data> context);

  public abstract ViewResult retrieveResult(Aggregations aggs, Data reportData);

  public abstract void addViewAdjustmentsForCommandKeyGeneration(Data dataForCommandKey);
}
