/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.filter;

import static io.camunda.optimize.service.db.es.filter.util.ModelElementFilterQueryUtil.createCandidateGroupFilterQuery;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;

import io.camunda.optimize.dto.optimize.query.report.single.process.filter.data.IdentityLinkFilterDataDto;
import java.util.List;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.springframework.stereotype.Component;

@Component
public class CandidateGroupQueryFilter implements QueryFilter<IdentityLinkFilterDataDto> {

  @Override
  public void addFilters(
      final BoolQueryBuilder query,
      final List<IdentityLinkFilterDataDto> candidateGroupFilters,
      final FilterContext filterContext) {
    if (!CollectionUtils.isEmpty(candidateGroupFilters)) {
      final List<QueryBuilder> filters = query.filter();
      for (final IdentityLinkFilterDataDto candidateGroupFilter : candidateGroupFilters) {
        filters.add(
            nestedQuery(
                FLOW_NODE_INSTANCES,
                createCandidateGroupFilterQuery(candidateGroupFilter),
                ScoreMode.None));
      }
    }
  }
}
