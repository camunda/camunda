package org.camunda.optimize.service.es.filter;

import org.camunda.optimize.dto.optimize.query.FilterMapDto;
import org.elasticsearch.index.query.BoolQueryBuilder;

public interface QueryFilter {

  void addFilters(BoolQueryBuilder query, FilterMapDto filter);
}
