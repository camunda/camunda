/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest.queryparam.adjustment.decorator;

import javax.ws.rs.core.MultivaluedMap;
import java.util.Collections;
import java.util.List;

public class SortOrderListDecorator<T> extends AdjustedResultListDecorator<T> {

  private static final String DESC = "desc";
  private static final String SORT_ORDER = "sortOrder";
  public SortOrderListDecorator(QueryParameterAdjustedResultList<T> decoratedList) {
    super(decoratedList);
  }

  @Override
  public List<T> adjustList() {
    List<T> resultList = decoratedList.adjustList();
    MultivaluedMap<String, String> queryParameters = decoratedList.getQueryParameters();
    if (queryParameters.containsKey(SORT_ORDER)) {
      String sortOrder = queryParameters.getFirst(SORT_ORDER);
      if (sortOrder.equals(DESC)) {
        Collections.reverse(resultList);
      }
    }
    return resultList;
  }

  @Override
  public MultivaluedMap<String, String> getQueryParameters() {
    return decoratedList.getQueryParameters();
  }
}
