/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.service.db.es.filter;

import io.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterDataDto;
import java.util.List;
import org.elasticsearch.index.query.BoolQueryBuilder;

public interface QueryFilter<FILTER extends FilterDataDto> {
  void addFilters(BoolQueryBuilder query, List<FILTER> filter, FilterContext filterContext);
}
