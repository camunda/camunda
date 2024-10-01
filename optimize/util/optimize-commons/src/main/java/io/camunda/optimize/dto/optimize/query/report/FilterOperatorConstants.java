/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report;

public class FilterOperatorConstants {

  public static final String IN = "in";
  public static final String NOT_IN = "not in";

  public static final String CONTAINS = "contains";
  public static final String NOT_CONTAINS = "not contains";

  public static final String LESS_THAN = "<";
  public static final String LESS_THAN_EQUALS = "<=";
  public static final String GREATER_THAN = ">";
  public static final String GREATER_THAN_EQUALS = ">=";

  private FilterOperatorConstants() {}
}
