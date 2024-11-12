/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.util;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.function.Function;

public class ConversionUtils {

  public static final Function<String, Long> STRING_TO_LONG =
      (aString) -> aString == null ? null : Long.valueOf(aString);
  public static final Function<Long, String> LONG_TO_STRING =
      (aLong) -> aLong == null ? null : String.valueOf(aLong);

  public static String toStringOrNull(Object object) {
    return toStringOrDefault(object, null);
  }

  public static String toStringOrDefault(Object object, String defaultString) {
    return object == null ? defaultString : object.toString();
  }

  public static Long toLongOrNull(String aString) {
    return toLongOrDefault(aString, null);
  }

  public static Long toLongOrDefault(String aString, Long defaultValue) {
    return aString == null ? defaultValue : Long.valueOf(aString);
  }

  public static String toHostAndPortAsString(InetSocketAddress address) {
    return String.format("%s:%d", address.getHostName(), address.getPort());
  }

  public static boolean stringIsEmpty(String aString) {
    return aString == null || aString.isEmpty();
  }

  public static <A> String[] toStringArray(A[] arr) {
    return Arrays.copyOf(arr, arr.length, String[].class);
  }
}
