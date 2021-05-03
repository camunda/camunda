/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.test.util.collection;

import java.util.HashMap;
import java.util.Map;

public final class Maps {
  @SuppressWarnings("unchecked")
  public static <K, V> Map<K, V> of(final Map.Entry... entries) {
    final Map<K, V> map = new HashMap<>();

    for (final Map.Entry entry : entries) {
      map.put((K) entry.getKey(), (V) entry.getValue());
    }

    return map;
  }
}
