/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest.queryparam.adjustment.decorator;

import javax.ws.rs.core.MultivaluedMap;
import java.util.List;

public abstract class AdjustedResultListDecorator<T> implements QueryParameterAdjustedResultList<T> {

  protected QueryParameterAdjustedResultList<T> decoratedList;

  public AdjustedResultListDecorator(QueryParameterAdjustedResultList<T> decoratedList) {
    this.decoratedList = decoratedList;
  }

  @Override
  public List<T> adjustList() {
    return decoratedList.adjustList();
  }

  @Override
  public MultivaluedMap<String, String> getQueryParameters() {
    return decoratedList.getQueryParameters();
  }
}
