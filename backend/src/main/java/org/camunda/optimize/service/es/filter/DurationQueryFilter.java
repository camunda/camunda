/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter;

import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.DurationFilterDataDto;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.List;

import static org.camunda.optimize.service.es.filter.FilterOperatorConstants.GREATER_THAN;
import static org.camunda.optimize.service.es.filter.FilterOperatorConstants.GREATER_THAN_EQUALS;
import static org.camunda.optimize.service.es.filter.FilterOperatorConstants.LESS_THAN;
import static org.camunda.optimize.service.es.filter.FilterOperatorConstants.LESS_THAN_EQUALS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;


@Component
public class DurationQueryFilter implements QueryFilter<DurationFilterDataDto> {

  public void addFilters(BoolQueryBuilder query, List<DurationFilterDataDto> durations) {
    if (durations != null && !durations.isEmpty()) {
      List<QueryBuilder> filters = query.filter();
      for (DurationFilterDataDto durationDto : durations) {
        RangeQueryBuilder queryDate = QueryBuilders.rangeQuery(DurationFilterDataDto.DURATION);
        queryDate = addBoundaries(queryDate, durationDto);
        filters.add(queryDate);
      }
    }
  }

  private static TemporalUnit unitOf(String unit) {
    return ChronoUnit.valueOf(unit.toUpperCase());
  }

  private RangeQueryBuilder addBoundaries(RangeQueryBuilder queryDate, DurationFilterDataDto dto) {
    OffsetDateTime now = OffsetDateTime.now();
    OffsetDateTime plus = now.plus(dto.getValue(), unitOf(dto.getUnit()));
    if (LESS_THAN.equalsIgnoreCase(dto.getOperator())) {
      queryDate.lt(
        now.until(plus, ChronoUnit.MILLIS)
      );
    } else if (LESS_THAN_EQUALS.equalsIgnoreCase(dto.getOperator())) {
      queryDate.lte(
        now.until(plus, ChronoUnit.MILLIS)
      );
    } else {
      OffsetDateTime minus = now.minus(dto.getValue(), unitOf(dto.getUnit()));
      if (GREATER_THAN.equalsIgnoreCase(dto.getOperator())) {
        queryDate.gt(
            minus.until(now, ChronoUnit.MILLIS)
        );
      } else if (GREATER_THAN_EQUALS.equalsIgnoreCase(dto.getOperator())) {
        queryDate.gte(
            minus.until(now, ChronoUnit.MILLIS)
        );
      }
    }

    queryDate.format(OPTIMIZE_DATE_FORMAT);
    return queryDate;
  }

}
