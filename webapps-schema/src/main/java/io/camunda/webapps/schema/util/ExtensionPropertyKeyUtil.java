/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.util;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class ExtensionPropertyKeyUtil {

  private static final String DOT = ".";
  private static final String DOT_REPLACEMENT = "\uFF0E";

  private ExtensionPropertyKeyUtil() {}

  public static String encode(final String key) {
    return key == null ? null : key.replace(DOT, DOT_REPLACEMENT);
  }

  public static String decode(final String key) {
    return key == null ? null : key.replace(DOT_REPLACEMENT, DOT);
  }

  public static Map<String, String> encodeMap(final Map<String, String> map) {
    return transformKeys(map, ExtensionPropertyKeyUtil::encode);
  }

  public static Map<String, String> decodeMap(final Map<String, String> map) {
    return transformKeys(map, ExtensionPropertyKeyUtil::decode);
  }

  private static Map<String, String> transformKeys(
      final Map<String, String> map, final Function<String, String> keyTransformer) {
    if (map == null || map.isEmpty()) {
      return map;
    }
    return map.entrySet().stream()
        .collect(
            Collectors.toMap(entry -> keyTransformer.apply(entry.getKey()), Map.Entry::getValue));
  }
}
