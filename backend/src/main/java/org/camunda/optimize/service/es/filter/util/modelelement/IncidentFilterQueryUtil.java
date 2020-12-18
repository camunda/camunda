/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter.util.modelelement;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.persistence.incident.IncidentStatus;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.OpenIncidentFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ResolvedIncidentFilterDto;
import org.elasticsearch.index.query.BoolQueryBuilder;

import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.INCIDENTS;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.INCIDENT_STATUS;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class IncidentFilterQueryUtil {

  public static BoolQueryBuilder createIncidentAggregationFilter(final ProcessReportDataDto reportDataDto) {
    final BoolQueryBuilder filterBoolQuery = boolQuery();
    addOpenIncidentFilter(filterBoolQuery, reportDataDto);
    addResolvedIncidentFilter(filterBoolQuery, reportDataDto);
    return filterBoolQuery;
  }

  public static BoolQueryBuilder createOpenIncidentFilterQuery() {
    return boolQuery().must(termQuery(INCIDENTS + "." + INCIDENT_STATUS, IncidentStatus.OPEN.getId()));
  }

  public static BoolQueryBuilder createResolvedIncidentFilterQuery() {
    return boolQuery().must(termQuery(INCIDENTS + "." + INCIDENT_STATUS, IncidentStatus.RESOLVED.getId()));
  }

  private static void addOpenIncidentFilter(final BoolQueryBuilder boolQuery,
                                            final ProcessReportDataDto reportDataDto) {
    if (containsViewLevelFilterOfType(reportDataDto, OpenIncidentFilterDto.class)) {
      boolQuery.filter(createOpenIncidentFilterQuery());
    }
  }

  private static void addResolvedIncidentFilter(final BoolQueryBuilder boolQuery,
                                                final ProcessReportDataDto reportDataDto) {
    if (containsViewLevelFilterOfType(reportDataDto, ResolvedIncidentFilterDto.class)) {
      boolQuery.filter(createResolvedIncidentFilterQuery());
    }
  }

  private static boolean containsViewLevelFilterOfType(final ProcessReportDataDto reportDataDto,
                                                       final Class<? extends ProcessFilterDto<?>> filterClass) {
    return reportDataDto.getFilter().stream()
      .filter(filter -> FilterApplicationLevel.VIEW.equals(filter.getFilterLevel()))
      .anyMatch(filterClass::isInstance);
  }

}
