/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.modules.distributed_by;

import lombok.Setter;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.DistributedByResult;
import org.camunda.optimize.service.es.report.command.modules.view.ViewPart;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;

import java.util.List;

public abstract class DistributedByPart<Data extends SingleReportDataDto> {

  @Setter
  protected ViewPart<Data> viewPart;

  public void adjustBaseQuery(final BoolQueryBuilder baseQuery, final Data definitionData) {
    viewPart.adjustBaseQuery(baseQuery, definitionData);
  }

  public abstract AggregationBuilder createAggregation(final Data definitionData);

  public abstract List<DistributedByResult> retrieveResult(final Aggregations aggregations,
                                                           final Data reportData);

  public void addDistributedByAdjustmentsForCommandKeyGeneration(final Data dataForCommandKey) {
    addAdjustmentsForCommandKeyGeneration(dataForCommandKey);
    viewPart.addViewAdjustmentsForCommandKeyGeneration(dataForCommandKey);
  }

  protected abstract void addAdjustmentsForCommandKeyGeneration(final Data dataForCommandKey);
}
