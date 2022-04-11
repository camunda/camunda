/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.filter;

import org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterDataDto;
import org.elasticsearch.index.query.BoolQueryBuilder;

import java.util.List;

public interface QueryFilter<FILTER extends FilterDataDto> {
  void addFilters(BoolQueryBuilder query, List<FILTER> filter, FilterContext filterContext);
}
