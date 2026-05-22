/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.filter;

import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.matchAll;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.AGENT_INSTANCES;

import io.camunda.optimize.dto.optimize.query.report.single.process.filter.data.HasAgentInstancesFilterDataDto;
import io.camunda.optimize.service.db.filter.FilterContext;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.List;
import org.opensearch.client.opensearch._types.query_dsl.ChildScoreMode;
import org.opensearch.client.opensearch._types.query_dsl.NestedQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class HasAgentInstancesQueryFilterOS
    implements QueryFilterOS<HasAgentInstancesFilterDataDto> {

  @Override
  public List<Query> filterQueries(
      final List<HasAgentInstancesFilterDataDto> filterData, final FilterContext filterContext) {
    if (filterData == null || filterData.isEmpty()) {
      return List.of();
    }
    return List.of(
        Query.of(
            q ->
                q.nested(
                    NestedQuery.of(
                        n ->
                            n.path(AGENT_INSTANCES)
                                .query(matchAll())
                                .scoreMode(ChildScoreMode.None)))));
  }
}
