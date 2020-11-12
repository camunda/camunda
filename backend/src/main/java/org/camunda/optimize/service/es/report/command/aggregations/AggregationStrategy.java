/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.aggregations;

import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.support.ValuesSourceAggregationBuilder;

public interface AggregationStrategy {
  Double getValue(Aggregations aggs);

  ValuesSourceAggregationBuilder<?> getAggregationBuilder();

  AggregationType getAggregationType();
}
