/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SuppressionConstants {

  public static final String UNUSED = "unused";
  public static final String UNCHECKED_CAST = "unchecked";
  public static final String RAW_TYPES = "rawtypes";
  public static final String SAME_PARAM_VALUE = "SameParameterValue";
  public static final String OPTIONAL_FIELD_OR_PARAM = "OptionalUsedAsFieldOrParameterType";
  public static final String OPTIONAL_ASSIGNED_TO_NULL = "OptionalAssignedToNull";

}
