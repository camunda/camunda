package org.camunda.optimize.service.es.filter;

import org.elasticsearch.index.query.BoolQueryBuilder;

import java.util.List;

public interface QueryFilterEnhancer<T> {
  void addFilterToQuery(BoolQueryBuilder query, List<T> filter);
}
