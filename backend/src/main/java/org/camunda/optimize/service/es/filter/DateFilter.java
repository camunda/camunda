package org.camunda.optimize.service.es.filter;

import org.camunda.optimize.dto.optimize.query.DateFilterDto;
import org.camunda.optimize.dto.optimize.query.FilterMapDto;
import org.camunda.optimize.service.es.schema.type.ProcessInstanceType;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.text.SimpleDateFormat;
import java.util.List;

import static org.camunda.optimize.service.es.filter.FilterOperatorConstants.GREATER_THAN;
import static org.camunda.optimize.service.es.filter.FilterOperatorConstants.GREATER_THAN_EQUALS;
import static org.camunda.optimize.service.es.filter.FilterOperatorConstants.LESS_THAN;
import static org.camunda.optimize.service.es.filter.FilterOperatorConstants.LESS_THAN_EQUALS;

/**
 * Helper class that defines mapping rules between FE Dto and ES filters used
 * on event type
 *
 * @author Askar Akhmerov
 */
@Component
public class DateFilter implements QueryFilter {

  @Autowired
  private ConfigurationService configurationService;

  private SimpleDateFormat formatter;

  @PostConstruct
  private void init() {
    formatter = new SimpleDateFormat(configurationService.getDateFormat());
  }

  public void addFilters(BoolQueryBuilder query, FilterMapDto filter) {
    this.addDateFilters(query, filter.getDates());
  }

  private void addDateFilters(BoolQueryBuilder query, List<DateFilterDto> dates) {
    if (dates != null) {
      List<QueryBuilder> filters = query.filter();
      for (DateFilterDto dateDto : dates) {
        RangeQueryBuilder queryDate = QueryBuilders.rangeQuery(mapTimeColumn(dateDto));
        queryDate = addBoundaries(queryDate, dateDto);
        filters.add(queryDate);
      }
    }
  }

  private RangeQueryBuilder addBoundaries(RangeQueryBuilder queryDate, DateFilterDto dto) {

    if (LESS_THAN.equalsIgnoreCase(dto.getOperator())) {
      queryDate.lt(formatter.format(dto.getValue()));
    } else if (LESS_THAN_EQUALS.equalsIgnoreCase(dto.getOperator())) {
      queryDate.lte(formatter.format(dto.getValue()));
    } else if (GREATER_THAN.equalsIgnoreCase(dto.getOperator())) {
      queryDate.gt(formatter.format(dto.getValue()));
    } else if (GREATER_THAN_EQUALS.equalsIgnoreCase(dto.getOperator())) {
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
