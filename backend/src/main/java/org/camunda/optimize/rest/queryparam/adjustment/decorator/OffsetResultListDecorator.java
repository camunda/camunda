/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest.queryparam.adjustment.decorator;

import javax.ws.rs.core.MultivaluedMap;
import java.util.List;

public class OffsetResultListDecorator<T> extends AdjustedResultListDecorator<T> {

  public OffsetResultListDecorator(QueryParameterAdjustedResultList<T> decoratedList) {
    super(decoratedList);
  }

  @Override
  public List<T> adjustList() {
    List<T> resultList = decoratedList.adjustList();
    MultivaluedMap<String, String> queryParameters = decoratedList.getQueryParameters();
    if (queryParameters.containsKey("resultOffset")) {
      String resultOffset = queryParameters.getFirst("resultOffset");
      try {
        int offset = Integer.parseInt(resultOffset);
        resultList = resultList.subList(offset, resultList.size());
      } catch (NumberFormatException ignored) {}
    }
    return resultList;
  }

  @Override
  public MultivaluedMap<String, String> getQueryParameters() {
    return decoratedList.getQueryParameters();
  }
}
