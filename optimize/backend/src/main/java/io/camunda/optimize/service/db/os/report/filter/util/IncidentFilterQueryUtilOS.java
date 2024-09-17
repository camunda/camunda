/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.filter.util;

import static io.camunda.optimize.service.db.os.externalcode.client.dsl.QueryDSL.and;
import static io.camunda.optimize.service.db.os.externalcode.client.dsl.QueryDSL.nested;
import static io.camunda.optimize.service.db.os.externalcode.client.dsl.QueryDSL.term;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.INCIDENTS;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.INCIDENT_STATUS;

import com.google.common.collect.ImmutableMap;
import io.camunda.optimize.dto.optimize.persistence.incident.IncidentStatus;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.OpenIncidentFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ResolvedIncidentFilterDto;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.ChildScoreMode;
import org.opensearch.client.opensearch._types.query_dsl.Query;

public class IncidentFilterQueryUtilOS {

  private static final Map<Class<? extends ProcessFilterDto<?>>, Query>
      incidentViewFilterInstanceQueries =
          ImmutableMap.of(
              OpenIncidentFilterDto.class,
              IncidentFilterQueryUtilOS.createDeletedIncidentTermQuery(),
              ResolvedIncidentFilterDto.class,
              IncidentFilterQueryUtilOS.createResolvedIncidentTermQuery());

  private IncidentFilterQueryUtilOS() {}

  public static Optional<Query> instanceFilterForRelevantViewLevelFiltersQuery(
      final List<ProcessFilterDto<?>> filters) {
    final List<Query> filterQueries =
        filters.stream()
            .filter(filter -> FilterApplicationLevel.VIEW.equals(filter.getFilterLevel()))
            .filter(filter -> incidentViewFilterInstanceQueries.containsKey(filter.getClass()))
            .map(filter -> incidentViewFilterInstanceQueries.get(filter.getClass()))
            .toList();
    return filterQueries.isEmpty()
        ? Optional.empty()
        : Optional.of(nested(INCIDENTS, and(filterQueries), ChildScoreMode.None));
  }

  public static Query createResolvedIncidentTermQuery() {
    return createResolvedIncidentTermQuery(new BoolQuery.Builder());
  }

  private static Query createResolvedIncidentTermQuery(final BoolQuery.Builder boolQueryBuilder) {
    return boolQueryBuilder
        .must(term(INCIDENTS + "." + INCIDENT_STATUS, IncidentStatus.RESOLVED.getId()))
        .build()
        .toQuery();
  }

  public static Query createOpenIncidentTermQuery() {
    return createOpenIncidentTermQuery(new BoolQuery.Builder());
  }

  public static Query createDeletedIncidentTermQuery() {
    return createDeletedIncidentTermQuery(new BoolQuery.Builder());
  }

  private static Query createDeletedIncidentTermQuery(final BoolQuery.Builder boolQueryBuilder) {
    return boolQueryBuilder
        .must(term(INCIDENTS + "." + INCIDENT_STATUS, IncidentStatus.DELETED.getId()))
        .build()
        .toQuery();
  }

  private static Query createOpenIncidentTermQuery(final BoolQuery.Builder boolQueryBuilder) {
    return boolQueryBuilder
        .must(term(INCIDENTS + "." + INCIDENT_STATUS, IncidentStatus.OPEN.getId()))
        .build()
        .toQuery();
  }
}
