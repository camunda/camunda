/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report;

import com.google.common.collect.ImmutableSet;

import java.util.Set;

public class FilterOperatorConstants {

  public static final String IN = "in";
  public static final String NOT_IN = "not in";

  public static final String LESS_THAN = "<";
  public static final String LESS_THAN_EQUALS = "<=";
  public static final String GREATER_THAN = ">";
  public static final String GREATER_THAN_EQUALS = ">=";

  public static final Set<String> RELATIVE_OPERATORS = ImmutableSet.of(
    LESS_THAN,
    LESS_THAN_EQUALS,
    GREATER_THAN,
    GREATER_THAN_EQUALS
  );
}
