/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.rest.sorting;

import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.query.goals.ProcessGoalsResponseDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;

import javax.ws.rs.BadRequestException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;


@NoArgsConstructor
public class ProcessGoalSorter extends Sorter<ProcessGoalsResponseDto> {

  private static final Map<String, Comparator<ProcessGoalsResponseDto>> sortComparators = Map.of(
    ProcessGoalsResponseDto.Fields.processName.toLowerCase(),
    Comparator.comparing(ProcessGoalsResponseDto::getProcessName)
  );

  private static final Comparator<ProcessGoalsResponseDto> DEFAULT_PROCESS_GOAL_COMPARATOR =
    sortComparators.get(ProcessGoalsResponseDto.Fields.processName.toLowerCase())
      .thenComparing(ProcessGoalsResponseDto::getProcessDefinitionKey);


  public ProcessGoalSorter(final String sortBy, final SortOrder sortOrder) {
    this.sortRequestDto = new SortRequestDto(sortBy, sortOrder);
  }

  @Override
  public List<ProcessGoalsResponseDto> applySort(final List<ProcessGoalsResponseDto> processGoals) {
    Comparator<ProcessGoalsResponseDto> processGoalSorterComparator = DEFAULT_PROCESS_GOAL_COMPARATOR;
    final Optional<SortOrder> sortOrderOpt = getSortOrder();
    final Optional<String> sortByOpt = getSortBy();
    if (sortByOpt.isPresent()) {
      final String sortBy = sortByOpt.get();
      if (!sortComparators.containsKey(sortBy.toLowerCase())) {
        throw new BadRequestException(String.format("%s is not a sortable field", sortBy));
      } else {
        processGoalSorterComparator = sortComparators.get(sortBy.toLowerCase())
          .thenComparing(DEFAULT_PROCESS_GOAL_COMPARATOR);
        if (sortOrderOpt.isPresent() && SortOrder.DESC.equals(sortOrderOpt.get())) {
          processGoalSorterComparator = processGoalSorterComparator.reversed();
        }
      }
    } else {
      if (sortOrderOpt.isPresent()) {
        throw new BadRequestException("Sort order is not supported when no field selected to sort");
      }
    }
    processGoals.sort(processGoalSorterComparator);
    return processGoals;
  }

}
