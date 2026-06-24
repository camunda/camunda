/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.filter;

import static io.camunda.optimize.service.db.es.filter.util.IncidentFilterQueryUtilES.createDeletedIncidentTermQuery;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.INCIDENTS;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.ChildScoreMode;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.data.DeletedIncidentFilterDataDto;
import io.camunda.optimize.service.db.filter.FilterContext;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.List;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class DeletedIncidentQueryFilterES implements QueryFilterES<DeletedIncidentFilterDataDto> {

  @Override
  public void addFilters(
      final BoolQuery.Builder query,
      final List<DeletedIncidentFilterDataDto> filter,
      final FilterContext filterContext) {
    if (!CollectionUtils.isEmpty(filter)) {
      query.filter(
          f ->
              f.nested(
                  n ->
                      n.path(INCIDENTS)
                          .query(q -> q.bool(createDeletedIncidentTermQuery().build()))
                          .scoreMode(ChildScoreMode.None)));
    }
  }
}
