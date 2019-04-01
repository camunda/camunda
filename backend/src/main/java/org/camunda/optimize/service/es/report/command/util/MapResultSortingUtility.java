package org.camunda.optimize.service.es.report.command.util;

import org.camunda.optimize.dto.optimize.query.report.single.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.query.report.single.sorting.SortingDto;
import org.camunda.optimize.service.es.report.result.decision.SingleDecisionMapReportResult;
import org.camunda.optimize.service.es.report.result.process.SingleProcessMapDurationReportResult;
import org.camunda.optimize.service.es.report.result.process.SingleProcessMapReportResult;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public class MapResultSortingUtility {

  private MapResultSortingUtility() {
  }

  public static void sortResultData(final SortingDto sorting,
                                    final SingleDecisionMapReportResult resultData) {
    resultData.getResultAsDto().getData(MapResultSortingUtility.sortResultData(
      sorting,
      resultData.getResultAsDto().getData()
    ));
  }

  public static void sortResultData(final SortingDto sorting,
                                    final SingleProcessMapReportResult resultData) {
    resultData.getResultAsDto().setData(MapResultSortingUtility.sortResultData(
      sorting, resultData.getResultAsDto().getData()
    ));
  }

  public static void sortResultData(final SortingDto sorting,
                                    final SingleProcessMapDurationReportResult resultData) {
    resultData.getResultAsDto().setData(MapResultSortingUtility.sortResultData(
      sorting,
      resultData.getResultAsDto().getData(),
      entry -> entry.getValue().getResultForGivenAggregationType(
        resultData.getReportDefinition().getData().getConfiguration().getAggregationType()
      )
    ));
  }


  private static <K extends Comparable<K>, V extends Comparable<V>> Map<K, V> sortResultData(
    final SortingDto sorting, final Map<K, V> resultData) {
    return sortResultData(sorting, resultData, Map.Entry::getValue);
  }

  private static <K extends Comparable<K>, V> Map<K, V> sortResultData(
    final SortingDto sorting,
    final Map<K, V> resultData,
    final Function<Map.Entry<K, V>, ? extends Comparable> valueSupplier) {

    final String sortBy = sorting.getBy().orElse(SortingDto.SORT_BY_KEY);
    final SortOrder sortOrder = sorting.getOrder().orElse(SortOrder.DESC);

    Comparator<Map.Entry<K, V>> comparator;
    switch (sortBy) {
      default:
      case SortingDto.SORT_BY_KEY:
        comparator = Comparator.comparing(Map.Entry::getKey);
        break;
      case SortingDto.SORT_BY_VALUE:
        comparator = Comparator.comparing(valueSupplier);
        break;
    }

    comparator = sortOrder.equals(SortOrder.DESC) ? comparator.reversed() : comparator;

    return resultData
      .entrySet().stream()
      .sorted(comparator)
      .collect(toLinkedHashMapCollector());

  }

  private static <K, V> Collector<Map.Entry<K, V>, ?, LinkedHashMap<K, V>> toLinkedHashMapCollector() {
    return Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (x, y) -> y, LinkedHashMap::new);
  }
}
