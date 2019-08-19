/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest.queryparam;

public class QueryParamUtil {

  public static <T> T normalizeNullStringValue(T nullableIn) {
    return "null".equals(nullableIn) ? null : nullableIn;
  }
}
