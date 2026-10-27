/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.filter;

import static io.camunda.optimize.service.db.schema.index.DecisionInstanceIndex.EVALUATION_DATE_TIME;

import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterDataDto;
import io.camunda.optimize.service.db.filter.FilterContext;
import io.camunda.optimize.service.db.os.report.filter.util.DateFilterQueryUtilOS;
import java.util.List;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.springframework.stereotype.Component;

@Component
public class EvaluationDateQueryFilterOS implements QueryFilterOS<DateFilterDataDto<?>> {

  public EvaluationDateQueryFilterOS() {}

  @Override
  public List<Query> filterQueries(
      final List<DateFilterDataDto<?>> filter, final FilterContext filterContext) {
    return DateFilterQueryUtilOS.createRangeQueries(
        filter, EVALUATION_DATE_TIME, filterContext.getTimezone());
  }
}
