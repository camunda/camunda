/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.util;

import org.camunda.optimize.dto.optimize.query.report.single.result.HyperMapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.sorting.SortingDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.service.es.report.result.decision.SingleDecisionMapReportResult;
import org.camunda.optimize.service.es.report.result.process.SingleProcessMapDurationReportResult;
import org.camunda.optimize.service.es.report.result.process.SingleProcessMapReportResult;

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
      resultData.getResultAsDto().getData(),
      VariableType.STRING
    ));
  }

  public static void sortResultData(final SortingDto sorting,
                                    final SingleDecisionMapReportResult resultData,
                                    VariableType keyType) {
    resultData.getResultAsDto().setData(sortResultData(
      sorting,
      resultData.getResultAsDto().getData(),
      keyType
    ));
  }

  public static void sortResultData(final SortingDto sorting,
                                    final SingleProcessMapReportResult resultData) {
    resultData.getResultAsDto().setData(sortResultData(
      sorting, resultData.getResultAsDto().getData(),
      VariableType.STRING
    ));
  }

  public static void sortResultData(final SortingDto sorting,
                                    final SingleProcessMapReportResult resultData,
                                    final VariableType keyType) {
    resultData.getResultAsDto().setData(sortResultData(
      sorting, resultData.getResultAsDto().getData(), keyType
    ));
  }

  public static void sortResultData(final SortingDto sorting,
                                    final SingleProcessMapDurationReportResult resultData,
                                    final VariableType keyType) {
    resultData.getResultAsDto().setData(sortResultData(
      sorting, resultData.getResultAsDto().getData(), keyType
    ));
  }


  public static void sortResultData(final SortingDto sorting,
                                    final SingleProcessMapDurationReportResult resultData) {
    final List<MapResultEntryDto<Long>> mapResultEntryDtos = sortResultData(
      sorting,
      resultData.getResultAsDto().getData(),
      VariableType.STRING
    );
    resultData.getResultAsDto().setData(mapResultEntryDtos);
  }

  public static void sortResultData(final SortingDto sorting,
                                    final HyperMapResultEntryDto<Long> resultData) {
    final List<MapResultEntryDto<Long>> mapResultEntryDtos = sortResultData(
      sorting,
      resultData.getValue(),
      VariableType.STRING
    );
    resultData.setValue(mapResultEntryDtos);
  }

  private static <V extends Comparable> List<MapResultEntryDto<V>> sortResultData(
    final SortingDto sorting,
    final List<MapResultEntryDto<V>> resultData,
    final VariableType keyType) {

    final String sortBy = sorting.getBy().orElse(SortingDto.SORT_BY_KEY);
    final SortOrder sortOrder = sorting.getOrder().orElse(SortOrder.DESC);

    final Function<MapResultEntryDto<V>, Comparable> valueToSortByExtractor;
    switch (sortBy) {
      default:
      case SortingDto.SORT_BY_KEY:
        valueToSortByExtractor = VariableType.getNumericTypes().contains(keyType)
          ? entry -> Double.valueOf(entry.getKey()) : entry -> entry.getKey().toLowerCase();
        break;
      case SortingDto.SORT_BY_VALUE:
        valueToSortByExtractor = MapResultEntryDto::getValue;
        break;
      case SortingDto.SORT_BY_LABEL:
        valueToSortByExtractor = entry -> entry.getLabel().toLowerCase();
        break;
    }

    final Comparator<MapResultEntryDto<V>> comparator;
    switch (sortOrder) {
      case DESC:
        comparator = Comparator.comparing(valueToSortByExtractor, Comparator.nullsLast(Comparator.reverseOrder()));
        break;
      default:
      case ASC:
        comparator = Comparator.comparing(valueToSortByExtractor, Comparator.nullsLast(Comparator.naturalOrder()));
        break;
    }

    return resultData
      .stream()
      .sorted(comparator)
      .collect(Collectors.toList());

  }

}
