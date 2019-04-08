/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest.queryparam.adjustment.decorator;

import javax.ws.rs.core.MultivaluedMap;
import java.util.Comparator;
import java.util.List;

public class OrderByQueryParamResultListDecorator<T> extends AdjustedResultListDecorator<T> {

  public static final String ORDER_BY = "orderBy";
  private String queryParamValueToOrderBy;
  private Comparator<T> comparator;

  public OrderByQueryParamResultListDecorator(
      QueryParameterAdjustedResultList<T> decoratedList,
      String queryParamValueToOrderBy,
      Comparator<T> comparator
  ) {
    super(decoratedList);
    this.queryParamValueToOrderBy = queryParamValueToOrderBy;
    this.comparator = comparator;
  }

  @Override
  public List<T> adjustList() {
    List<T> resultList = decoratedList.adjustList();
    MultivaluedMap<String, String> queryParameters = decoratedList.getQueryParameters();
    if (queryParameters.containsKey(ORDER_BY)) {
      String orderBy = queryParameters.getFirst(ORDER_BY);
      if (orderBy.equals(queryParamValueToOrderBy)) {
        resultList.sort(comparator);
      }
    }
    return resultList;
  }

  @Override
  public MultivaluedMap<String, String> getQueryParameters() {
    return decoratedList.getQueryParameters();
  }
}
