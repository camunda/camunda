/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.util;

import java.util.Arrays;
import java.util.stream.Collectors;

public final class AggregationNameUtil {

  private static final String[] ILLEGAL_CHARS = {"[", "]", ">"};
  private static final String ILLEGAL_CHAR_REGEX =
      Arrays.stream(ILLEGAL_CHARS)
          .map(illegalChar -> "\\" + illegalChar)
          .collect(Collectors.joining("|"));

  private AggregationNameUtil() {}

  public static boolean containsIllegalChar(final String aggregationName) {
    return Arrays.stream(ILLEGAL_CHARS).anyMatch(aggregationName::contains);
  }

  public static String sanitiseAggName(final String aggregationName) {
    return aggregationName.replaceAll(ILLEGAL_CHAR_REGEX, "_");
  }
}
