package org.camunda.optimize.service.es.mapping;

import org.camunda.optimize.dto.optimize.DateFilterDto;
import org.camunda.optimize.dto.optimize.FilterMapDto;
import org.camunda.optimize.service.es.schema.type.ProcessInstanceType;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.ConfigurationService;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.text.SimpleDateFormat;
import java.util.List;

/**
 * Helper class that defines mapping rules between FE Dto and ES filters used
 * on event type
 *
 * @author Askar Akhmerov
 */
@Component
public class DateFilterHelper {
  @Autowired
  private ConfigurationService configurationService;

  private SimpleDateFormat formatter;

  @PostConstruct
  private void init() {
    formatter = new SimpleDateFormat(configurationService.getDateFormat());
  }

  public BoolQueryBuilder addFilters(BoolQueryBuilder query, FilterMapDto filter) {
    return this.addDateFilters(query, filter.getDates());
  }

  public BoolQueryBuilder addDateFilters(BoolQueryBuilder query, List<DateFilterDto> dates) {
    if (dates != null) {
      List<QueryBuilder> filters = query.filter();
      for (DateFilterDto dateDto : dates) {
        RangeQueryBuilder queryDate = QueryBuilders.rangeQuery(mapTimeColumn(dateDto));
        queryDate = addBoundaries(queryDate, dateDto);
        filters.add(queryDate);
      }
    }
    return query;
  }

  private RangeQueryBuilder addBoundaries(RangeQueryBuilder queryDate, DateFilterDto dto) {

    if (dto.LESS.equalsIgnoreCase(dto.getOperator())) {
      queryDate.lt(formatter.format(dto.getValue()));
    } else if (dto.LESS_OR_EQUAL.equalsIgnoreCase(dto.getOperator())) {
      queryDate.lte(formatter.format(dto.getValue()));
    } else if (dto.GRATER.equalsIgnoreCase(dto.getOperator())) {
      queryDate.gt(formatter.format(dto.getValue()));
    } else if (dto.GRATER_OR_EQUAL.equalsIgnoreCase(dto.getOperator())) {
      queryDate.gte(formatter.format(dto.getValue()));
    }

    queryDate.format(configurationService.getDateFormat());
    return queryDate;
  }

  private String mapTimeColumn(DateFilterDto dateDto) {
    String result = null;
    if (DateFilterDto.START_DATE.equalsIgnoreCase(dateDto.getType())) {
      result = ProcessInstanceType.START_DATE;
    }
    if (DateFilterDto.END_DATE.equalsIgnoreCase(dateDto.getType())) {
      result = ProcessInstanceType.END_DATE;
    }
    if (result == null) {
      throw new OptimizeRuntimeException("invalid date column provided for mapping");
    }
    return result;
  }
}
