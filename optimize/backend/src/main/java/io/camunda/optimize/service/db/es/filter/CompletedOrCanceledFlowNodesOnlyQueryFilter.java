/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.filter;

import static io.camunda.optimize.service.db.es.filter.util.ModelElementFilterQueryUtil.createCompletedOrCanceledFlowNodesOnlyFilterQuery;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;

import io.camunda.optimize.dto.optimize.query.report.single.process.filter.data.CompletedOrCanceledFlowNodesOnlyFilterDataDto;
import java.util.List;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.springframework.stereotype.Component;

@Component
public class CompletedOrCanceledFlowNodesOnlyQueryFilter
    implements QueryFilter<CompletedOrCanceledFlowNodesOnlyFilterDataDto> {

  @Override
  public void addFilters(
      final BoolQueryBuilder query,
      final List<CompletedOrCanceledFlowNodesOnlyFilterDataDto> completedOrCanceledFilterData,
      final FilterContext filterContext) {
    if (!CollectionUtils.isEmpty(completedOrCanceledFilterData)) {
      List<QueryBuilder> filters = query.filter();
      filters.add(
          nestedQuery(
              FLOW_NODE_INSTANCES,
              createCompletedOrCanceledFlowNodesOnlyFilterQuery(boolQuery()),
              ScoreMode.None));
    }
  }
}
