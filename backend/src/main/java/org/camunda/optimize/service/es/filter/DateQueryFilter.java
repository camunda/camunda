/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter;

import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterType;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.FixedDateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RelativeDateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RelativeDateFilterStartDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RollingDateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RollingDateFilterStartDto;
import org.camunda.optimize.service.exceptions.OptimizeValidationException;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.DateFilterUtil;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterUnit.QUARTERS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;

public abstract class DateQueryFilter implements QueryFilter<DateFilterDataDto> {
  protected final Logger logger = LoggerFactory.getLogger(getClass());

  private final DateTimeFormatter formatter;

  protected DateQueryFilter(final DateTimeFormatter formatter) {
    this.formatter = formatter;
  }

  public void addFilters(BoolQueryBuilder query, List<DateFilterDataDto> dates, String dateFieldType) {
    if (dates != null) {
      List<QueryBuilder> filters = query.filter();
      for (DateFilterDataDto dateDto : dates) {
        RangeQueryBuilder queryDate = null;
        if (DateFilterType.FIXED.equals(dateDto.getType())) {
          FixedDateFilterDataDto fixedDateFilterDataDto = (FixedDateFilterDataDto) dateDto;
          queryDate = createFixedDateFilter(fixedDateFilterDataDto, dateFieldType);
        } else if (DateFilterType.RELATIVE.equals(dateDto.getType())) {
          RelativeDateFilterDataDto relativeDateFilterDataDto = (RelativeDateFilterDataDto) dateDto;
          queryDate = createRelativeDateFilter(relativeDateFilterDataDto, dateFieldType);
        } else if (DateFilterType.ROLLING.equals(dateDto.getType())) {
          RollingDateFilterDataDto rollingDateFilterDataDto = (RollingDateFilterDataDto) dateDto;
          queryDate = createRollingDateFilter(rollingDateFilterDataDto, dateFieldType);
        } else {
          logger.warn("Cannot execute date filter. Unknown type [{}]", dateDto.getType());
        }

        if (queryDate != null) {
          queryDate.format(OPTIMIZE_DATE_FORMAT);
          filters.add(queryDate);
        }
      }
    }
  }

  private RangeQueryBuilder createFixedDateFilter(FixedDateFilterDataDto dateDto, String type) {
    RangeQueryBuilder queryDate = QueryBuilders.rangeQuery(type);
    if (dateDto.getEnd() != null) {
      queryDate.lte(formatter.format(dateDto.getEnd()));
    }
    if (dateDto.getStart() != null) {
      queryDate.gte(formatter.format(dateDto.getStart()));
    }
    return queryDate;
  }

  private RangeQueryBuilder createRelativeDateFilter(RelativeDateFilterDataDto dateDto, String type) {
    RelativeDateFilterStartDto startDto = dateDto.getStart();
    RangeQueryBuilder queryDate = QueryBuilders.rangeQuery(type);
    OffsetDateTime now = LocalDateUtil.getCurrentDateTime();
    queryDate.lte(formatter.format(now));

    if (startDto.getUnit().equals(QUARTERS)) {
      logger.warn("Cannot create date filter: {} is not supported for {} filters", startDto.getUnit(), dateDto.getType());
      throw new OptimizeValidationException(String.format("%s is not supported for %s filters", startDto.getUnit(), dateDto.getType()));
    }

    OffsetDateTime dateBeforeGivenFilter = now.minus(startDto.getValue(), ChronoUnit.valueOf(startDto.getUnit().getId().toUpperCase()));
    queryDate.gte(formatter.format(dateBeforeGivenFilter));
    return queryDate;
  }

  private RangeQueryBuilder createRollingDateFilter(RollingDateFilterDataDto dateDto, String type) {
    RollingDateFilterStartDto startDto = dateDto.getStart();
    RangeQueryBuilder queryDateFilter = QueryBuilders.rangeQuery(type);
    OffsetDateTime now = LocalDateUtil.getCurrentDateTime();
    if (startDto.getValue() == 0) {
      queryDateFilter.lte(formatter.format(now));
      queryDateFilter.gte(formatter.format(DateFilterUtil.getStartOfCurrentInterval(now, startDto.getUnit())));
    } else {
      OffsetDateTime startOfCurrentInterval = DateFilterUtil.getStartOfCurrentInterval(now, startDto.getUnit());
      OffsetDateTime startOfPreviousInterval = DateFilterUtil.getStartOfPreviousInterval(
        startOfCurrentInterval,
        startDto.getUnit(),
        startDto.getValue()
      );
      queryDateFilter.lt(formatter.format(startOfCurrentInterval));
      queryDateFilter.gte(formatter.format(startOfPreviousInterval));
    }
    return queryDateFilter;
  }

}
