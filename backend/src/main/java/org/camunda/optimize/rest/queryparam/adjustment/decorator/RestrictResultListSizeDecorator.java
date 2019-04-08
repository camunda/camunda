/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest.queryparam.adjustment.decorator;

import javax.ws.rs.core.MultivaluedMap;
import java.util.List;

public class RestrictResultListSizeDecorator<T> extends AdjustedResultListDecorator<T> {

  public RestrictResultListSizeDecorator(QueryParameterAdjustedResultList<T> decoratedList) {
    super(decoratedList);
  }

  @Override
  public List<T> adjustList() {
    List<T> resultList = decoratedList.adjustList();
    MultivaluedMap<String, String> queryParameters = decoratedList.getQueryParameters();
    if (queryParameters.containsKey("numResults")) {
      String maxNumberOfResults = queryParameters.getFirst("numResults");
      try {
        int maxNum = Integer.parseInt(maxNumberOfResults);
        maxNum = Math.min(resultList.size(), maxNum);
        resultList = resultList.subList(0, maxNum);
      } catch (NumberFormatException ignored) {}
    }
    return resultList;
  }

  @Override
  public MultivaluedMap<String, String> getQueryParameters() {
    return decoratedList.getQueryParameters();
  }
}
