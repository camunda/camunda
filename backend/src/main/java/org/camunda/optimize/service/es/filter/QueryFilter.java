package org.camunda.optimize.service.es.filter;

import org.camunda.optimize.dto.optimize.query.report.filter.data.FilterDataDto;
import org.elasticsearch.index.query.BoolQueryBuilder;

import java.util.List;

public interface QueryFilter<FILTER extends FilterDataDto> {

  void addFilters(BoolQueryBuilder query, List<FILTER> filter);
}
