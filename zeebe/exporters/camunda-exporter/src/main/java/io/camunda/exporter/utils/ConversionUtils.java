/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.utils;

public class ConversionUtils {
  public static Long toLongOrNull(final String aString) {
    return toLongOrDefault(aString, null);
  }

  public static Long toLongOrDefault(final String aString, final Long defaultValue) {
    return aString == null ? defaultValue : Long.valueOf(aString);
  }
}
