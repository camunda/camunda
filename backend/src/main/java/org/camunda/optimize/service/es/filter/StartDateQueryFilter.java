package org.camunda.optimize.service.es.filter;

import org.camunda.optimize.dto.optimize.query.report.filter.data.startDate.*;
import org.camunda.optimize.service.es.schema.type.ProcessInstanceType;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.List;

import static org.camunda.optimize.service.es.report.command.util.ReportConstants.FIXED_DATE_FILTER;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.RELATIVE_DATE_FILTER;

/**
 * Helper class that defines mapping rules between FE Dto and ES filters used
 * on event type
 */
@Component
public class StartDateQueryFilter implements QueryFilter<StartDateFilterDataDto> {

  private org.slf4j.Logger logger = LoggerFactory.getLogger(getClass());
  @Autowired
  private ConfigurationService configurationService;

  @Autowired
  private DateTimeFormatter formatter;

  public void addFilters(BoolQueryBuilder query, List<StartDateFilterDataDto> dates) {
    if (dates != null) {
      List<QueryBuilder> filters = query.filter();
      for (StartDateFilterDataDto dateDto : dates) {
        RangeQueryBuilder queryDate = null;
        if (FIXED_DATE_FILTER.equals(dateDto.getType())) {
          FixedStartDateFilterDataDto fixedStartDateFilterDataDto = (FixedStartDateFilterDataDto) dateDto;
          queryDate = createFixedStartDateFilter(fixedStartDateFilterDataDto);
        } else if (RELATIVE_DATE_FILTER.equals(dateDto.getType())) {
          RelativeStartDateFilterDataDto relativeStartDateFilterDataDto= (RelativeStartDateFilterDataDto) dateDto;
          queryDate = createRelativeStartDateFilter(relativeStartDateFilterDataDto);
        } else {
          logger.warn("Cannot execute start date filter. Unknown type [{}]", dateDto.getType());
        }
        queryDate.format(configurationService.getOptimizeDateFormat());
        filters.add(queryDate);
      }
    }
  }

  private RangeQueryBuilder createFixedStartDateFilter(FixedStartDateFilterDataDto dateDto) {
    RangeQueryBuilder queryDate = QueryBuilders.rangeQuery(ProcessInstanceType.START_DATE);
    if (dateDto.getEnd() != null) {
      queryDate.lte(formatter.format(dateDto.getEnd()));
    }
    if (dateDto.getStart() != null) {
      queryDate.gte(formatter.format(dateDto.getStart()));
    }
    return queryDate;
  }

  private RangeQueryBuilder createRelativeStartDateFilter(RelativeStartDateFilterDataDto dateDto) {
    RelativeStartDateFilterStartDto startDate = dateDto.getStart();
    RangeQueryBuilder queryDate = QueryBuilders.rangeQuery(ProcessInstanceType.START_DATE);
    OffsetDateTime now = LocalDateUtil.getCurrentDateTime();
    System.out.println(formatter.format(now));
    queryDate.lte(formatter.format(now));

    OffsetDateTime dateBeforeGivenFilter = now.minus(startDate.getValue(), unitOf(startDate.getUnit()));
    queryDate.gte(formatter.format(dateBeforeGivenFilter));
    return queryDate;
  }

  private TemporalUnit unitOf(String unit) {
    return ChronoUnit.valueOf(unit.toUpperCase());
  }


}
