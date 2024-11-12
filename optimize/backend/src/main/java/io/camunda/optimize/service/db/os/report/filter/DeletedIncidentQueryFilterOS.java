/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.filter;

import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.nested;
import static io.camunda.optimize.service.db.os.report.filter.util.IncidentFilterQueryUtilOS.createDeletedIncidentTermQuery;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.INCIDENTS;

import io.camunda.optimize.dto.optimize.query.report.single.process.filter.data.DeletedIncidentFilterDataDto;
import io.camunda.optimize.service.db.filter.FilterContext;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.List;
import org.apache.commons.collections4.CollectionUtils;
import org.opensearch.client.opensearch._types.query_dsl.ChildScoreMode;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class DeletedIncidentQueryFilterOS implements QueryFilterOS<DeletedIncidentFilterDataDto> {

  @Override
  public List<Query> filterQueries(
      final List<DeletedIncidentFilterDataDto> filter, final FilterContext filterContext) {
    return !CollectionUtils.isEmpty(filter)
        ? List.of(nested(INCIDENTS, createDeletedIncidentTermQuery(), ChildScoreMode.None))
        : List.of();
  }
}
