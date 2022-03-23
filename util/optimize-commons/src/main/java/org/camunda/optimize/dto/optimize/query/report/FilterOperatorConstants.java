/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class FilterOperatorConstants {

  public static final String IN = "in";
  public static final String NOT_IN = "not in";

  public static final String CONTAINS = "contains";
  public static final String NOT_CONTAINS = "not contains";

  public static final String LESS_THAN = "<";
  public static final String LESS_THAN_EQUALS = "<=";
  public static final String GREATER_THAN = ">";
  public static final String GREATER_THAN_EQUALS = ">=";

}
