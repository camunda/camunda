/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest.queryparam.adjustment;

import org.camunda.optimize.dto.optimize.query.collection.CollectionEntity;
import org.camunda.optimize.dto.optimize.query.collection.ResolvedCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.rest.queryparam.adjustment.decorator.OffsetResultListDecorator;
import org.camunda.optimize.rest.queryparam.adjustment.decorator.OrderByQueryParamResultListDecorator;
import org.camunda.optimize.rest.queryparam.adjustment.decorator.OriginalResultList;
import org.camunda.optimize.rest.queryparam.adjustment.decorator.QueryParameterAdjustedResultList;
import org.camunda.optimize.rest.queryparam.adjustment.decorator.RestrictResultListSizeDecorator;
import org.camunda.optimize.rest.queryparam.adjustment.decorator.SortOrderListDecorator;

import javax.ws.rs.core.MultivaluedMap;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QueryParamAdjustmentUtil {


  private static final String ORDER_BY = "orderBy";
  private static final Map<String, Comparator> reportComparators = new HashMap<>();
  private static final Map<String, Comparator> collectionComparators = new HashMap<>();
  private static final Map<String, Comparator> entitiesComparators = new HashMap<>();

  private static final String LAST_MODIFIED = "lastModified";
  private static final String NAME = "name";
  private static final String CREATED = "created";

  static {
    reportComparators.put(
      LAST_MODIFIED, Comparator.comparing(ReportDefinitionDto<ReportDataDto>::getLastModified).reversed()
    );
    reportComparators.put(
      NAME, Comparator.comparing(ReportDefinitionDto<ReportDataDto>::getName)
    );

    collectionComparators.put(CREATED, Comparator.comparing(ResolvedCollectionDefinitionDto::getCreated).reversed());
    collectionComparators.put(NAME, Comparator.comparing(ResolvedCollectionDefinitionDto::getName));

    entitiesComparators.put(LAST_MODIFIED, Comparator.comparing(CollectionEntity::getLastModified).reversed());
    entitiesComparators.put(NAME, Comparator.comparing(CollectionEntity::getName));
  }

  public static List<ReportDefinitionDto> adjustReportResultsToQueryParameters(
    List<ReportDefinitionDto> resultList,
    MultivaluedMap<String, String> queryParameters
  ) {
    Comparator<ReportDefinitionDto> sorting;

    List<String> key = queryParameters.get(ORDER_BY);
    String queryParam = (key == null || key.isEmpty()) ? LAST_MODIFIED : key.get(0);

    sorting = reportComparators.get(queryParam);

    return adjustResultListAccordingToQueryParameters(resultList, queryParameters, sorting, queryParam);
  }

  public static List<DashboardDefinitionDto> adjustDashboardResultsToQueryParameters(
    List<DashboardDefinitionDto> resultList,
    MultivaluedMap<String, String> queryParameters
  ) {
    Comparator<DashboardDefinitionDto> sorting =
      Comparator.comparing(DashboardDefinitionDto::getLastModified).reversed();

    return adjustResultListAccordingToQueryParameters(resultList, queryParameters, sorting, "lastModified");
  }

  private static <T> List<T> adjustResultListAccordingToQueryParameters(
    List<T> resultList,
    MultivaluedMap<String, String> queryParameters,
    Comparator<T> comparator,
    String orderField
  ) {
    QueryParameterAdjustedResultList<T> adjustedResultList =
      new RestrictResultListSizeDecorator<>(
        new OffsetResultListDecorator<>(
          new SortOrderListDecorator<>(
            new OrderByQueryParamResultListDecorator<>(
              new OriginalResultList<>(resultList, queryParameters),
              orderField,
              comparator
            )
          )
        )
      );
    resultList = adjustedResultList.adjustList();

    return resultList;
  }

  public static List<ResolvedCollectionDefinitionDto> adjustCollectionResultsToQueryParameters(
    List<ResolvedCollectionDefinitionDto> resultList,
    MultivaluedMap<String, String> queryParameters
  ) {
    List<String> key = queryParameters.get(ORDER_BY);
    String queryParam = (key == null || key.isEmpty()) ? CREATED : key.get(0);
    Comparator<ResolvedCollectionDefinitionDto> comparator = collectionComparators.get(queryParam);

    return adjustResultListAccordingToQueryParameters(resultList, queryParameters, comparator, queryParam);
  }

  public static List<CollectionEntity> adjustEntityResultsToQueryParameters(List<CollectionEntity> resultList,
                                                                            MultivaluedMap<String, String> queryParameters
  ) {
    List<String> key = queryParameters.get(ORDER_BY);
    String queryParam = (key == null || key.isEmpty()) ? LAST_MODIFIED : key.get(0);
    Comparator<CollectionEntity> comparator = entitiesComparators.get(queryParam);

    return adjustResultListAccordingToQueryParameters(resultList, queryParameters, comparator, queryParam);
  }
}
