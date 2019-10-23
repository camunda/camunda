/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.modules.view;

import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.ViewResult;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;

import java.util.function.Supplier;

public abstract class ViewPart {

  public void adjustBaseQuery(BoolQueryBuilder baseQuery, ProcessReportDataDto definitionData) {
    // by default don't do anything
  }

  public abstract AggregationBuilder createAggregation(ProcessReportDataDto definitionData);

  public abstract ViewResult retrieveResult(Aggregations aggs, ProcessReportDataDto reportData);

  public abstract void addViewAdjustmentsForCommandKeyGeneration(ProcessReportDataDto dataForCommandKey);
}
