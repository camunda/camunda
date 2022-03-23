/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.rest.sorting;

import com.google.common.collect.ImmutableMap;
import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.query.event.sequence.EventCountResponseDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;

import javax.ws.rs.BadRequestException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.nullsFirst;
import static org.camunda.optimize.dto.optimize.query.event.sequence.EventCountResponseDto.Fields.count;
import static org.camunda.optimize.dto.optimize.query.event.sequence.EventCountResponseDto.Fields.eventName;
import static org.camunda.optimize.dto.optimize.query.event.sequence.EventCountResponseDto.Fields.group;
import static org.camunda.optimize.dto.optimize.query.event.sequence.EventCountResponseDto.Fields.source;

@NoArgsConstructor
public class EventCountSorter extends Sorter<EventCountResponseDto> {

  private static final Comparator<EventCountResponseDto> SUGGESTED_COMPARATOR =
    Comparator.comparing(EventCountResponseDto::isSuggested, nullsFirst(naturalOrder())).reversed();
  private static final Comparator<EventCountResponseDto> GROUP_COMPARATOR =
    Comparator.comparing(EventCountResponseDto::getGroup, nullsFirst(String.CASE_INSENSITIVE_ORDER));
  private static final Comparator<EventCountResponseDto> SOURCE_COMPARATOR =
    Comparator.comparing(EventCountResponseDto::getSource, nullsFirst(String.CASE_INSENSITIVE_ORDER));
  private static final Comparator<EventCountResponseDto> EVENT_NAME_COMPARATOR =
    Comparator.comparing(eventCountDto -> Optional.ofNullable(eventCountDto.getEventLabel())
      .orElse(eventCountDto.getEventName()), nullsFirst(String.CASE_INSENSITIVE_ORDER));
  private static final Comparator<EventCountResponseDto> COUNTS_COMPARATOR =
    Comparator.comparing(EventCountResponseDto::getCount, nullsFirst(naturalOrder()));

  private static final Comparator<EventCountResponseDto> DEFAULT_COMPARATOR = nullsFirst(GROUP_COMPARATOR.thenComparing(
    SOURCE_COMPARATOR).thenComparing(EVENT_NAME_COMPARATOR).thenComparing(COUNTS_COMPARATOR));

  private static final ImmutableMap<String, Comparator<EventCountResponseDto>> sortComparators = ImmutableMap.of(
    group.toLowerCase(), GROUP_COMPARATOR,
    source.toLowerCase(), SOURCE_COMPARATOR,
    eventName.toLowerCase(), EVENT_NAME_COMPARATOR,
    count.toLowerCase(), COUNTS_COMPARATOR
  );

  public EventCountSorter(final String sortBy, final SortOrder sortOrder) {
    this.sortRequestDto = new SortRequestDto(sortBy, sortOrder);
  }

  @Override
  public List<EventCountResponseDto> applySort(List<EventCountResponseDto> eventCounts) {
    Comparator<EventCountResponseDto> eventCountSorter;
    final Optional<SortOrder> sortOrderOpt = getSortOrder();
    final Optional<String> sortByOpt = getSortBy();
    if (sortByOpt.isPresent()) {
      final String sortBy = sortByOpt.get();
      if (!sortComparators.containsKey(sortBy.toLowerCase())) {
        throw new BadRequestException(String.format("%s is not a sortable field", sortBy));
      }
      eventCountSorter = sortComparators.get(sortBy.toLowerCase())
        .thenComparing(DEFAULT_COMPARATOR);
      if (sortOrderOpt.isPresent() && SortOrder.DESC.equals(sortOrderOpt.get())) {
        eventCountSorter = eventCountSorter.reversed();
      }
    } else {
      if (sortOrderOpt.isPresent()) {
        throw new BadRequestException("Sort order is not supported when no field selected to sort");
      }
      eventCountSorter = DEFAULT_COMPARATOR;
    }
    eventCounts.sort(SUGGESTED_COMPARATOR.thenComparing(eventCountSorter));
    return eventCounts;
  }
}
