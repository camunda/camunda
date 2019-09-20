/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest.queryparam.adjustment;

import org.camunda.optimize.dto.optimize.rest.AuthorizedReportDefinitionDto;
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

  private static final String LAST_MODIFIED = "lastModified";
  private static final String NAME = "name";

  static {
    reportComparators.put(
      LAST_MODIFIED,
      Comparator.comparing(o -> ((AuthorizedReportDefinitionDto) o).getDefinitionDto().getLastModified()).reversed()
    );
    reportComparators.put(
      NAME,
      Comparator.comparing(o -> ((AuthorizedReportDefinitionDto) o).getDefinitionDto().getName())
    );
  }

  public static List<AuthorizedReportDefinitionDto> adjustReportResultsToQueryParameters(
    List<AuthorizedReportDefinitionDto> resultList,
    MultivaluedMap<String, String> queryParameters
  ) {
    Comparator sorting;

    List<String> key = queryParameters.get(ORDER_BY);
    String queryParam = (key == null || key.isEmpty()) ? LAST_MODIFIED : key.get(0);

    sorting = reportComparators.get(queryParam);

    return adjustResultListAccordingToQueryParameters(resultList, queryParameters, sorting, queryParam);
  }

  @SuppressWarnings("unchecked")
  private static <T> List<T> adjustResultListAccordingToQueryParameters(
    List<T> resultList,
    MultivaluedMap<String, String> queryParameters,
    Comparator comparator,
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

}
