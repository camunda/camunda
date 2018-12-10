package org.camunda.optimize.service.es.filter;

import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.VariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.CanceledInstancesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.CompletedInstancesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.DurationFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.EndDateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ExecutedFlowNodeFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.RunningInstancesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.StartDateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.VariableFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.CanceledInstancesOnlyFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.CompletedInstancesOnlyFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.DurationFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.ExecutedFlowNodeFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.RunningInstancesOnlyFilterDataDto;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class ProcessQueryFilterEnhancer implements QueryFilterEnhancer<ProcessFilterDto> {

  @Autowired
  private StartDateQueryFilter startDateQueryFilter;

  @Autowired
  private EndDateQueryFilter endDateQueryFilter;

  @Autowired
  private ProcessVariableQueryFilter variableQueryFilter;

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


  @Override
  public void addFilterToQuery(BoolQueryBuilder query, List<ProcessFilterDto> filter) {
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

  private List<DurationFilterDataDto> extractDurationFilters(List<ProcessFilterDto> filter) {
    return filter
        .stream()
        .filter(DurationFilterDto.class::isInstance)
        .map(dateFilter -> {
          DurationFilterDto dateFilterDto = (DurationFilterDto) dateFilter;
          return dateFilterDto.getData();
        })
        .collect(Collectors.toList());
  }

  private List<CanceledInstancesOnlyFilterDataDto> extractCanceledInstancesOnlyFilters(List<ProcessFilterDto> filter) {
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

  private List<DateFilterDataDto> extractStartDateFilters(List<ProcessFilterDto> filter) {
    return filter
      .stream()
      .filter(StartDateFilterDto.class::isInstance)
      .map(dateFilter -> {
        StartDateFilterDto dateFilterDto = (StartDateFilterDto) dateFilter;
        return dateFilterDto.getData();
      })
      .collect(Collectors.toList());
  }

  private List<DateFilterDataDto> extractEndDateFilters(List<ProcessFilterDto> filter) {
    return filter
            .stream()
            .filter(EndDateFilterDto.class::isInstance)
            .map(dateFilter -> {
              EndDateFilterDto dateFilterDto = (EndDateFilterDto) dateFilter;
              return dateFilterDto.getData();
            })
            .collect(Collectors.toList());
  }

  private List<VariableFilterDataDto> extractVariableFilters(List<ProcessFilterDto> filter) {
    return filter
      .stream()
      .filter(VariableFilterDto.class::isInstance)
      .map(variableFilter -> {
        VariableFilterDto variableFilterDto = (VariableFilterDto) variableFilter;
        return variableFilterDto.getData();
      })
      .collect(Collectors.toList());
  }

  private List<ExecutedFlowNodeFilterDataDto> extractExecutedFlowNodeFilters(List<ProcessFilterDto> filter) {
    return filter
      .stream()
      .filter(ExecutedFlowNodeFilterDto.class::isInstance)
      .map(executedFlowNodeFilter -> {
        ExecutedFlowNodeFilterDto executedFlowNodeFilterDto = (ExecutedFlowNodeFilterDto) executedFlowNodeFilter;
        return executedFlowNodeFilterDto.getData();
      })
      .collect(Collectors.toList());
  }

  private List<RunningInstancesOnlyFilterDataDto> extractRunningInstancesOnlyFilters(List<ProcessFilterDto> filter) {
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

  private List<CompletedInstancesOnlyFilterDataDto> extractCompletedInstancesOnlyFilters(List<ProcessFilterDto> filter) {
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
