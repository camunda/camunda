package org.camunda.optimize.service.es.filter;

import org.camunda.optimize.dto.optimize.query.report.filter.DateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.filter.ExecutedFlowNodeFilterDto;
import org.camunda.optimize.dto.optimize.query.report.filter.FilterDto;
import org.camunda.optimize.dto.optimize.query.report.filter.VariableFilterDto;
import org.camunda.optimize.dto.optimize.query.report.filter.data.DateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.filter.data.ExecutedFlowNodeFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.filter.data.VariableFilterDataDto;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class QueryFilterEnhancer {

  @Autowired
  private DateQueryFilter dateQueryFilter;

  @Autowired
  private VariableQueryFilter variableQueryFilter;

  @Autowired
  private ExecutedFlowNodeQueryFilter executedFlowNodeQueryFilter;

  public void addFilterToQuery(BoolQueryBuilder query, List<FilterDto> filter) {
    if (filter != null) {
      dateQueryFilter.addFilters(query, extractDateFilters(filter));
      variableQueryFilter.addFilters(query, extractVariableFilters(filter));
      executedFlowNodeQueryFilter.addFilters(query, extractExecutedFlowNodeFilters(filter));
    }
  }

  private List<DateFilterDataDto> extractDateFilters(List<FilterDto> filter) {
    return filter
      .stream()
      .filter(DateFilterDto.class::isInstance)
      .map(dateFilter -> {
        DateFilterDto dateFilterDto = (DateFilterDto) dateFilter;
        return dateFilterDto.getData();
      })
      .collect(Collectors.toList());
  }

  private List<VariableFilterDataDto> extractVariableFilters(List<FilterDto> filter) {
    return filter
      .stream()
      .filter(VariableFilterDto.class::isInstance)
      .map(variableFilter -> {
        VariableFilterDto variableFilterDto = (VariableFilterDto) variableFilter;
        return variableFilterDto.getData();
      })
      .collect(Collectors.toList());
  }

  private List<ExecutedFlowNodeFilterDataDto> extractExecutedFlowNodeFilters(List<FilterDto> filter) {
    return filter
      .stream()
      .filter(ExecutedFlowNodeFilterDto.class::isInstance)
      .map(executedFlowNodeFilter -> {
        ExecutedFlowNodeFilterDto executedFlowNodeFilterDto = (ExecutedFlowNodeFilterDto) executedFlowNodeFilter;
        return executedFlowNodeFilterDto.getData();
      })
      .collect(Collectors.toList());
  }


}
