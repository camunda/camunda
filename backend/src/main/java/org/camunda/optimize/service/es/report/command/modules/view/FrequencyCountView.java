/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.modules.view;

import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.nested.ReverseNested;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import static org.elasticsearch.search.aggregations.AggregationBuilders.reverseNested;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class FrequencyCountView extends ViewPart {
  private static final String VARIABLES_PROCESS_INSTANCE_COUNT_AGGREGATION = "proc_inst_count";

  @Override
  public AggregationBuilder createAggregation() {
    // the same process instance could have several same variable names -> do not count each but only the proc inst once
    return reverseNested(VARIABLES_PROCESS_INSTANCE_COUNT_AGGREGATION);
  }

  @Override
  public Long retrieveQueryResult(Aggregations aggs) {
    final ReverseNested variableProcInstCount = aggs.get(VARIABLES_PROCESS_INSTANCE_COUNT_AGGREGATION);
    return variableProcInstCount.getDocCount();
  }
}
