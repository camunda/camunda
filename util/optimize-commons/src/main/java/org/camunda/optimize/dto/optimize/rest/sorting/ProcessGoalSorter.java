/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.rest.sorting;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.query.goals.ProcessDurationGoalResultDto;
import org.camunda.optimize.dto.optimize.query.goals.ProcessGoalsResponseDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;

import javax.ws.rs.BadRequestException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;


@NoArgsConstructor
public class ProcessGoalSorter extends Sorter<ProcessGoalsResponseDto> {

  private static final Map<String, Comparator<ProcessGoalsResponseDto>> sortComparators = Map.of(
    ProcessGoalsResponseDto.Fields.processName.toLowerCase(),
    Comparator.comparing(ProcessGoalsResponseDto::getProcessName),
    ProcessGoalsResponseDto.Fields.owner.toLowerCase(),
    Comparator.comparing(processGoalsResponseDto -> processGoalsResponseDto.getOwner().getName(),
                         Comparator.nullsLast(Comparator.naturalOrder()))
  );

  private static final Comparator<ProcessGoalsResponseDto> DEFAULT_PROCESS_GOAL_COMPARATOR =
    sortComparators.get(ProcessGoalsResponseDto.Fields.processName.toLowerCase())
      .thenComparing(ProcessGoalsResponseDto::getProcessDefinitionKey);

  public ProcessGoalSorter(final String sortBy, final SortOrder sortOrder) {
    this.sortRequestDto = new SortRequestDto(sortBy, sortOrder);
  }

  @Override
  public List<ProcessGoalsResponseDto> applySort(final List<ProcessGoalsResponseDto> processGoals) {
    final Optional<SortOrder> sortOrderOpt = getSortOrder();
    final Optional<String> sortByOpt = getSortBy();

    // For sorting by goal evaluation, we have specific handling as the comparator complex and non-reversible
    if (sortByOpt.isEmpty() || sortByOpt.get().equalsIgnoreCase(ProcessGoalsResponseDto.Fields.durationGoals)) {
      return sortByDurationGoals(processGoals, sortOrderOpt.orElse(SortOrder.DESC));
    }

    final String sortBy = sortByOpt.get();
    Comparator<ProcessGoalsResponseDto> processGoalSorterComparator;
    if (!sortComparators.containsKey(sortBy.toLowerCase())) {
      throw new BadRequestException(String.format("%s is not a sortable field", sortBy));
    } else {
      processGoalSorterComparator = sortComparators.get(sortBy.toLowerCase())
        .thenComparing(DEFAULT_PROCESS_GOAL_COMPARATOR);
      if (sortOrderOpt.isPresent() && SortOrder.DESC.equals(sortOrderOpt.get())) {
        processGoalSorterComparator = processGoalSorterComparator.reversed();
      }
    }
    processGoals.sort(processGoalSorterComparator);
    return processGoals;
  }

  private List<ProcessGoalsResponseDto> sortByDurationGoals(final List<ProcessGoalsResponseDto> processGoals,
                                                            final SortOrder sortOrder) {
    final Map<String, GoalSortingMetrics> metricsByKey = processGoals.stream()
      .collect(Collectors.toMap(ProcessGoalsResponseDto::getProcessDefinitionKey, GoalSortingMetrics::new));
    final Map<String, ProcessGoalsResponseDto> goalResponsesByKey = processGoals.stream()
      .collect(Collectors.toMap(ProcessGoalsResponseDto::getProcessDefinitionKey, Function.identity()));
    final Comparator<GoalSortingMetrics> metricsComparator;
    if (sortOrder == SortOrder.DESC) {
      metricsComparator = Comparator.comparing(
          GoalSortingMetrics::getFailureRatio, Comparator.nullsFirst(Comparator.naturalOrder())).reversed()
        .thenComparing(Comparator.comparing(GoalSortingMetrics::getNumberOfGoalsSet).reversed())
        .thenComparing(GoalSortingMetrics::getProcessDefName).reversed()
        .thenComparing(GoalSortingMetrics::getProcessDefKey).reversed();
    } else {
      metricsComparator = Comparator.comparing
          (GoalSortingMetrics::getFailureRatio, Comparator.nullsLast(Comparator.naturalOrder()))
        .thenComparing(Comparator.comparingLong(GoalSortingMetrics::getNumberOfGoalsSet).reversed())
        .thenComparing(GoalSortingMetrics::getProcessDefName)
        .thenComparing(GoalSortingMetrics::getProcessDefKey);
    }
    return metricsByKey.values()
      .stream()
      .sorted(metricsComparator)
      .map(metrics -> goalResponsesByKey.get(metrics.getProcessDefKey()))
      .collect(Collectors.toList());
  }

  @Data
  @AllArgsConstructor
  private static class GoalSortingMetrics {
    private String processDefKey;
    private String processDefName;
    private long numberOfGoalsSet;
    private Double failureRatio;

    private GoalSortingMetrics(final ProcessGoalsResponseDto goal) {
      this.processDefKey = goal.getProcessDefinitionKey();
      this.processDefName = goal.getProcessName();
      final Map<Boolean, Long> countBySuccess = goal.getDurationGoals().getResults()
        .stream()
        .map(ProcessDurationGoalResultDto::getSuccessful)
        .filter(Objects::nonNull)
        .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
      final long success = countBySuccess.getOrDefault(Boolean.TRUE, 0L);
      final long failure = countBySuccess.getOrDefault(Boolean.FALSE, 0L);
      final long numberOfEvaluatedGoals = success + failure;
      this.numberOfGoalsSet = goal.getDurationGoals().getResults().size();
      if (numberOfEvaluatedGoals == 0) {
        this.failureRatio = null;
      } else {
        this.failureRatio = (double) failure / (double) numberOfEvaluatedGoals;
      }
    }
  }

}
