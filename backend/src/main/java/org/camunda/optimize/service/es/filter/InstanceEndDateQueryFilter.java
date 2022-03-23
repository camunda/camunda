/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.filter;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterDataDto;
import org.camunda.optimize.service.es.filter.util.DateFilterQueryUtil;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.springframework.stereotype.Component;

import java.util.List;

import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.END_DATE;

@RequiredArgsConstructor
@Component
public class InstanceEndDateQueryFilter implements QueryFilter<DateFilterDataDto<?>> {

  @Override
  public void addFilters(final BoolQueryBuilder query,
                         final List<DateFilterDataDto<?>> filters,
                         final FilterContext filterContext) {
    DateFilterQueryUtil.addFilters(query, filters, END_DATE, filterContext.getTimezone());
  }
}
