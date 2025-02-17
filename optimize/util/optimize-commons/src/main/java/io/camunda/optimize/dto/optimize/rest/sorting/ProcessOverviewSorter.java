/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest.sorting;

import io.camunda.optimize.dto.optimize.query.processoverview.ProcessOverviewResponseDto;
import io.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import io.camunda.optimize.rest.exceptions.BadRequestException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class ProcessOverviewSorter extends Sorter<ProcessOverviewResponseDto> {

  private static final Map<String, Comparator<ProcessOverviewResponseDto>> SORT_COMPARATORS =
      Map.of(
          ProcessOverviewResponseDto.Fields.processDefinitionName.toLowerCase(Locale.ENGLISH),
          Comparator.comparing(ProcessOverviewResponseDto::getProcessDefinitionName),
          ProcessOverviewResponseDto.Fields.owner.toLowerCase(Locale.ENGLISH),
          Comparator.comparing(
              processOverviewResponseDto -> processOverviewResponseDto.getOwner().getName(),
              Comparator.nullsLast(Comparator.naturalOrder())));

  private static final Comparator<ProcessOverviewResponseDto> DEFAULT_PROCESS_OVERVIEW_COMPARATOR =
      SORT_COMPARATORS
          .get(ProcessOverviewResponseDto.Fields.processDefinitionName.toLowerCase(Locale.ENGLISH))
          .thenComparing(ProcessOverviewResponseDto::getProcessDefinitionKey);

  public ProcessOverviewSorter(final String sortBy, final SortOrder sortOrder) {
    sortRequestDto = new SortRequestDto(sortBy, sortOrder);
  }

  public ProcessOverviewSorter() {}

  @Override
  public List<ProcessOverviewResponseDto> applySort(
      final List<ProcessOverviewResponseDto> processOverviewResponseDtos) {
    final Optional<SortOrder> sortOrderOpt = getSortOrder();
    final Optional<String> sortByOpt = getSortBy();

    if (sortByOpt.isPresent()) {
      final String sortBy = sortByOpt.get();
      Comparator<ProcessOverviewResponseDto> processOverviewSorterComparator;
      if (!SORT_COMPARATORS.containsKey(sortBy.toLowerCase(Locale.ENGLISH))) {
        throw new BadRequestException(String.format("%s is not a sortable field", sortBy));
      } else {
        processOverviewSorterComparator =
            SORT_COMPARATORS
                .get(sortBy.toLowerCase(Locale.ENGLISH))
                .thenComparing(DEFAULT_PROCESS_OVERVIEW_COMPARATOR);
        if (sortOrderOpt.isPresent() && SortOrder.DESC.equals(sortOrderOpt.get())) {
          processOverviewSorterComparator = processOverviewSorterComparator.reversed();
        }
      }
      processOverviewResponseDtos.sort(processOverviewSorterComparator);
      return processOverviewResponseDtos;
    } else {
      processOverviewResponseDtos.sort(DEFAULT_PROCESS_OVERVIEW_COMPARATOR);
      return processOverviewResponseDtos;
    }
  }
}
