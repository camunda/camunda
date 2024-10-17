/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.filter;

import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.END_DATE;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterDataDto;
import io.camunda.optimize.service.db.es.filter.util.DateFilterQueryUtilES;
import io.camunda.optimize.service.db.filter.FilterContext;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.List;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class InstanceEndDateQueryFilterES implements QueryFilterES<DateFilterDataDto<?>> {

  public InstanceEndDateQueryFilterES() {}

  @Override
  public void addFilters(
      final BoolQuery.Builder query,
      final List<DateFilterDataDto<?>> filters,
      final FilterContext filterContext) {
    DateFilterQueryUtilES.addFilters(query, filters, END_DATE, filterContext.getTimezone());
  }
}
