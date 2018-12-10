package org.camunda.optimize.service.es.filter;

import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterDataDto;
import org.camunda.optimize.service.es.schema.type.DecisionInstanceType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class EvaluationDateQueryFilter extends DateQueryFilter implements QueryFilter<DateFilterDataDto> {
  @Override
  public void addFilters(BoolQueryBuilder query, List<DateFilterDataDto> filter) {
    addFilters(query, filter, DecisionInstanceType.EVALUATION_DATE_TIME);
  }
}
