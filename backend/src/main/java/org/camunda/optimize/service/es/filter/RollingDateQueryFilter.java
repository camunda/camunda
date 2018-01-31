package org.camunda.optimize.service.es.filter;

import org.camunda.optimize.dto.optimize.query.report.filter.data.RollingDateFilterDataDto;
import org.camunda.optimize.service.es.schema.type.ProcessInstanceType;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.List;

/**
 * Helper class that defines mapping rules between FE Dto and ES filters used
 * on event type
 *
 * @author Askar Akhmerov
 */
@Component
public class RollingDateQueryFilter implements QueryFilter<RollingDateFilterDataDto> {

  @Autowired
  private ConfigurationService configurationService;

  @Autowired
  private DateTimeFormatter formatter;

  public void addFilters(BoolQueryBuilder query, List<RollingDateFilterDataDto> dates) {
    if (dates != null && !dates.isEmpty()) {
      RollingDateFilterDataDto toFilter = dates.get(0);
      List<QueryBuilder> filters = query.filter();
      RangeQueryBuilder queryDate = QueryBuilders.rangeQuery(ProcessInstanceType.START_DATE);
      OffsetDateTime now = LocalDateUtil.getCurrentDateTime();
      queryDate.lt(formatter.format(now));

      OffsetDateTime dateBeforeGivenFilter = now.minus(toFilter.getValue(), unitOf(toFilter.getUnit()));
      queryDate.gt(formatter.format(dateBeforeGivenFilter));

      queryDate.format(configurationService.getOptimizeDateFormat());
      filters.add(queryDate);
    }
  }

  private TemporalUnit unitOf(String unit) {
    return ChronoUnit.valueOf(unit.toUpperCase());
  }

}
