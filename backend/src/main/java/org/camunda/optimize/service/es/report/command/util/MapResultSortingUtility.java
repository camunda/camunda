/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.util;

import org.camunda.optimize.dto.optimize.query.report.single.process.result.duration.AggregationResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.report.single.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.query.report.single.sorting.SortingDto;
import org.camunda.optimize.service.es.report.result.decision.SingleDecisionMapReportResult;
import org.camunda.optimize.service.es.report.result.process.SingleProcessMapDurationReportResult;
import org.camunda.optimize.service.es.report.result.process.SingleProcessMapReportResult;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;

import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class MapResultSortingUtility {

  private MapResultSortingUtility() {
  }

  public static void sortResultData(final SortingDto sorting,
                                    final SingleDecisionMapReportResult resultData) {
    resultData.getResultAsDto().setData(sortResultData(
      sorting,
      resultData.getResultAsDto().getData()
    ));
  }

  public static void sortResultData(final SortingDto sorting,
                                    final SingleProcessMapReportResult resultData) {
    resultData.getResultAsDto().setData(sortResultData(
      sorting, resultData.getResultAsDto().getData()
    ));
  }

  public static void sortResultData(final SortingDto sorting,
                                    final SingleProcessMapDurationReportResult resultData) {
    final List<MapResultEntryDto<AggregationResultDto>> mapResultEntryDtos = sortResultData(
      sorting,
      resultData.getResultAsDto().getData(),
      entry -> entry.getValue().getResultForGivenAggregationType(
        resultData.getReportDefinition().getData().getConfiguration().getAggregationType()
      )
    );
    resultData.getResultAsDto().setData(mapResultEntryDtos);
  }

  private static <V> List<MapResultEntryDto<V>> sortResultData(
    final SortingDto sorting, final List<MapResultEntryDto<V>> resultData) {
    return sortResultData(sorting, resultData, MapResultEntryDto::getValue);
  }

  private static <V> List<MapResultEntryDto<V>> sortResultData(
    final SortingDto sorting,
    final List<MapResultEntryDto<V>> resultData,
    final Function<MapResultEntryDto<V>, ?> valueSupplier) {

    final String sortBy = sorting.getBy().orElse(SortingDto.SORT_BY_KEY);
    final SortOrder sortOrder = sorting.getOrder().orElse(SortOrder.DESC);

    Comparator<MapResultEntryDto<V>> comparator;
    switch (sortBy) {
      default:
      case SortingDto.SORT_BY_KEY:
        comparator = Comparator.comparing(entry -> entry.getKey().toLowerCase());
        break;
      case SortingDto.SORT_BY_VALUE:
        comparator = Comparator.comparing(t -> {
          final Object value = valueSupplier.apply(t);
          if (value == null) {
            // we want to make sure that null values are sorted to the end
            final int last = sortOrder.equals(SortOrder.DESC)? -1 : 1;
            return (Comparable) o -> last;
          } else if (value instanceof Comparable) {
            return ((Comparable) value);
          } else {
            throw new OptimizeRuntimeException(
              "Map Result value does not implement comparable: " + value.getClass().getSimpleName()
            );
          }
        });
        break;
      case SortingDto.SORT_BY_LABEL:
        comparator = Comparator.comparing(entry -> entry.getLabel().toLowerCase());
        break;
    }

    comparator = sortOrder.equals(SortOrder.DESC) ? comparator.reversed() : comparator;

    return resultData
      .stream()
      .sorted(comparator)
      .collect(Collectors.toList());

  }

}
