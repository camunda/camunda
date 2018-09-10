package org.camunda.optimize.service.es.filter;

import org.camunda.optimize.dto.optimize.query.report.single.filter.data.startDate.DateFilterDataDto;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.END_DATE;

@Component
public class EndDateQueryFilter implements QueryFilter<DateFilterDataDto> {
  @Autowired
  private DateTimeFormatter formatter;

  @Autowired
  private ConfigurationService configurationService;

  @Override
  public void addFilters(BoolQueryBuilder query, List<DateFilterDataDto> filter) {
    DateQueryFilter.addFilters(query, filter, formatter, configurationService, END_DATE);
  }
}
