/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.rest.sorting;

import jakarta.ws.rs.BadRequestException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessOverviewResponseDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;

@NoArgsConstructor
public class ProcessOverviewSorter extends Sorter<ProcessOverviewResponseDto> {

  private static final Map<String, Comparator<ProcessOverviewResponseDto>> sortComparators =
      Map.of(
          ProcessOverviewResponseDto.Fields.processDefinitionName.toLowerCase(Locale.ENGLISH),
          Comparator.comparing(ProcessOverviewResponseDto::getProcessDefinitionName),
          ProcessOverviewResponseDto.Fields.owner.toLowerCase(Locale.ENGLISH),
          Comparator.comparing(
              processOverviewResponseDto -> processOverviewResponseDto.getOwner().getName(),
              Comparator.nullsLast(Comparator.naturalOrder())));

  private static final Comparator<ProcessOverviewResponseDto> DEFAULT_PROCESS_OVERVIEW_COMPARATOR =
      sortComparators
          .get(ProcessOverviewResponseDto.Fields.processDefinitionName.toLowerCase(Locale.ENGLISH))
          .thenComparing(ProcessOverviewResponseDto::getProcessDefinitionKey);

  public ProcessOverviewSorter(final String sortBy, final SortOrder sortOrder) {
    this.sortRequestDto = new SortRequestDto(sortBy, sortOrder);
  }

  @Override
  public List<ProcessOverviewResponseDto> applySort(
      final List<ProcessOverviewResponseDto> processOverviewResponseDtos) {
    final Optional<SortOrder> sortOrderOpt = getSortOrder();
    final Optional<String> sortByOpt = getSortBy();

    if (sortByOpt.isPresent()) {
      final String sortBy = sortByOpt.get();
      Comparator<ProcessOverviewResponseDto> processOverviewSorterComparator;
      if (!sortComparators.containsKey(sortBy.toLowerCase(Locale.ENGLISH))) {
        throw new BadRequestException(String.format("%s is not a sortable field", sortBy));
      } else {
        processOverviewSorterComparator =
            sortComparators
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
