/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest.queryparam.adjustment.decorator;

import javax.ws.rs.core.MultivaluedMap;
import java.util.List;

public class OriginalResultList<T> implements QueryParameterAdjustedResultList<T> {

  private List<T> resultList;
  private MultivaluedMap<String, String> multivaluedMap;

  public OriginalResultList(List<T> resultList, MultivaluedMap<String, String> multivaluedMap) {
    this.resultList = resultList;
    this.multivaluedMap = multivaluedMap;
  }

  @Override
  public List<T> adjustList() {
    return resultList;
  }

  @Override
  public MultivaluedMap<String, String> getQueryParameters() {
    return multivaluedMap;
  }
}
