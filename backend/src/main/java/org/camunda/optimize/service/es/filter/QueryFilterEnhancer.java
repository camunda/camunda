package org.camunda.optimize.service.es.filter;

import org.camunda.optimize.dto.optimize.query.report.single.filter.*;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.*;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.VariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.startDate.DateFilterDataDto;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class QueryFilterEnhancer {

  @Autowired
  private StartDateQueryFilter startDateQueryFilter;

  @Autowired
  private EndDateQueryFilter endDateQueryFilter;

  @Autowired
  private VariableQueryFilter variableQueryFilter;

  @Autowired
  private ExecutedFlowNodeQueryFilter executedFlowNodeQueryFilter;

  @Autowired
  private DurationQueryFilter durationQueryFilter;

  @Autowired
  private RunningInstancesOnlyQueryFilter runningInstancesOnlyQueryFilter;

  @Autowired
  private CompletedInstancesOnlyQueryFilter completedInstancesOnlyQueryFilter;

  @Autowired
  private CanceledInstancesOnlyQueryFilter canceledInstancesOnlyQueryFilter;


  public void addFilterToQuery(BoolQueryBuilder query, List<FilterDto> filter) {
    if (filter != null) {
      startDateQueryFilter.addFilters(query, extractStartDateFilters(filter));
      endDateQueryFilter.addFilters(query, extractEndDateFilters(filter));
      variableQueryFilter.addFilters(query, extractVariableFilters(filter));
      executedFlowNodeQueryFilter.addFilters(query, extractExecutedFlowNodeFilters(filter));
      durationQueryFilter.addFilters(query, extractDurationFilters(filter));
      runningInstancesOnlyQueryFilter.addFilters(query, extractRunningInstancesOnlyFilters(filter));
      completedInstancesOnlyQueryFilter.addFilters(query, extractCompletedInstancesOnlyFilters(filter));
      canceledInstancesOnlyQueryFilter.addFilters(query, extractCanceledInstancesOnlyFilters(filter));
    }
  }

  private List<DurationFilterDataDto> extractDurationFilters(List<FilterDto> filter) {
    return filter
        .stream()
        .filter(DurationFilterDto.class::isInstance)
        .map(dateFilter -> {
          DurationFilterDto dateFilterDto = (DurationFilterDto) dateFilter;
          return dateFilterDto.getData();
        })
        .collect(Collectors.toList());
  }

  private List<CanceledInstancesOnlyFilterDataDto> extractCanceledInstancesOnlyFilters(List<FilterDto> filter) {
    return filter
      .stream()
      .filter(CanceledInstancesOnlyFilterDto.class::isInstance)
      .map(canceledInstancesOnlyFilter -> {
        CanceledInstancesOnlyFilterDto canceledInstancesOnlyFilterDto =
                (CanceledInstancesOnlyFilterDto) canceledInstancesOnlyFilter;
        return canceledInstancesOnlyFilterDto.getData();
      })
      .collect(Collectors.toList());
  }

  private List<DateFilterDataDto> extractStartDateFilters(List<FilterDto> filter) {
    return filter
      .stream()
      .filter(StartDateFilterDto.class::isInstance)
      .map(dateFilter -> {
        StartDateFilterDto dateFilterDto = (StartDateFilterDto) dateFilter;
        return dateFilterDto.getData();
      })
      .collect(Collectors.toList());
  }

  private List<DateFilterDataDto> extractEndDateFilters(List<FilterDto> filter) {
    return filter
            .stream()
            .filter(EndDateFilterDto.class::isInstance)
            .map(dateFilter -> {
              EndDateFilterDto dateFilterDto = (EndDateFilterDto) dateFilter;
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

  private List<RunningInstancesOnlyFilterDataDto> extractRunningInstancesOnlyFilters(List<FilterDto> filter) {
    return filter
      .stream()
      .filter(RunningInstancesOnlyFilterDto.class::isInstance)
      .map(runningInstancesOnlyFilter -> {
        RunningInstancesOnlyFilterDto runningInstancesOnlyFilterDto =
          (RunningInstancesOnlyFilterDto) runningInstancesOnlyFilter;
        return runningInstancesOnlyFilterDto.getData();
      })
      .collect(Collectors.toList());
  }

  private List<CompletedInstancesOnlyFilterDataDto> extractCompletedInstancesOnlyFilters(List<FilterDto> filter) {
    return filter
      .stream()
      .filter(CompletedInstancesOnlyFilterDto.class::isInstance)
      .map(completedInstancesOnlyFilter -> {
        CompletedInstancesOnlyFilterDto completedInstancesOnlyFilterDto =
          (CompletedInstancesOnlyFilterDto) completedInstancesOnlyFilter;
        return completedInstancesOnlyFilterDto.getData();
      })
      .collect(Collectors.toList());
  }

}
