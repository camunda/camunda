/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.filter;

import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.and;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.exists;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.nested;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.not;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.or;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.term;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.END_DATE;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_ID;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;

import io.camunda.optimize.dto.optimize.query.report.single.process.filter.data.ExecutingFlowNodeFilterDataDto;
import io.camunda.optimize.service.db.filter.FilterContext;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.List;
import org.opensearch.client.opensearch._types.query_dsl.ChildScoreMode;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class ExecutingFlowNodeQueryFilterOS
    implements QueryFilterOS<ExecutingFlowNodeFilterDataDto> {

  @Override
  public List<Query> filterQueries(
      final List<ExecutingFlowNodeFilterDataDto> flowNodeFilter,
      final FilterContext filterContext) {
    return flowNodeFilter.stream().map(this::createFilterQueryBuilder).toList();
  }

  private Query createFilterQueryBuilder(final ExecutingFlowNodeFilterDataDto flowNodeFilter) {
    final List<Query> queries =
        flowNodeFilter.getValues().stream()
            .map(
                flowNodeId ->
                    nested(
                        FLOW_NODE_INSTANCES,
                        and(
                            term(nestedActivityIdFieldLabel(), flowNodeId),
                            not(exists(nestedEndDateFieldLabel()))),
                        ChildScoreMode.None))
            .toList();
    return or(queries);
  }

  private String nestedActivityIdFieldLabel() {
    return FLOW_NODE_INSTANCES + "." + FLOW_NODE_ID;
  }

  private String nestedEndDateFieldLabel() {
    return FLOW_NODE_INSTANCES + "." + END_DATE;
  }
}
