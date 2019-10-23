/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.modules.distributed_by;

import lombok.Setter;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.DistributedByResult;
import org.camunda.optimize.service.es.report.command.modules.view.ViewPart;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;

import java.util.Collections;
import java.util.List;

public abstract class DistributedByPart {

  @Setter
  protected ViewPart viewPart;

  public void adjustBaseQuery(final BoolQueryBuilder baseQuery, final ProcessReportDataDto definitionData) {
    viewPart.adjustBaseQuery(baseQuery, definitionData);
  }

  public abstract AggregationBuilder createAggregation(final ProcessReportDataDto definitionData);

  public abstract List<DistributedByResult> retrieveResult(final Aggregations aggregations,
                                                           final ProcessReportDataDto reportData);

  public void addDistributedByAdjustmentsForCommandKeyGeneration(final ProcessReportDataDto dataForCommandKey) {
    addAdjustmentsForCommandKeyGeneration(dataForCommandKey);
    viewPart.addViewAdjustmentsForCommandKeyGeneration(dataForCommandKey);
  }

  protected abstract void addAdjustmentsForCommandKeyGeneration(final ProcessReportDataDto dataForCommandKey);
}
