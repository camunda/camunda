/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WorkaroundUtil {
  /**
   * This method implements a workaround to address a breaking change introduced in the
   * Elasticsearch client library.
   *
   * <p>In a recent version of the Elasticsearch Java client, the behavior of aggregation values
   * changed. Previously, aggregation results could contain {@code null} values, but in the newer
   * version, {@code null} values are replaced with a default value of 0.0. Additionally, this
   * change also involved converting the aggregation result to a primitive {@code double}, which
   * cannot represent {@code null}. This breaking change has caused significant issues in our APIs,
   * which rely on the presence of {@code null} to distinguish between absent and zero-valued
   * aggregations.
   *
   * <p>To mitigate this, we opted to use the low-level client API to perform the search request, as
   * it does not enforce default deserialization. By retrieving the raw response as a {@code Map},
   * we can manually inspect the aggregation values. If a {@code null} value is found, we replace it
   * with {@code Double.NaN}, which our existing logic handles appropriately. This approach allows
   * us to maintain backward compatibility without waiting for the official fix, which the
   * Elasticsearch team has indicated will be reintroduced in an upcoming minor release.
   *
   * <p>Note that this workaround will be removed once the issue is resolved in the Elasticsearch
   * client library.
   *
   * <p>This solution ensures that our API continues to function correctly, despite the changes in
   * the underlying Elasticsearch client.
   */
  public static void replaceNullWithNanInAggregations(final Map<String, Object> map) {
    for (final Map.Entry<String, Object> entry : map.entrySet()) {
      final String key = entry.getKey();
      final Object value = entry.getValue();

      if (key.contains("avg#")
          || key.contains("max#")
          || key.contains("sum#")
          || key.contains("min#")) {
        final Object o = ((Map<String, Object>) value).get("value");
        if (o == null) {
          ((Map<String, Object>) value).put("value", Double.NaN);
        }
      }

      if (value instanceof HashMap) {
        replaceNullWithNanInAggregations((Map<String, Object>) value);
      } else if (value instanceof final List<?> values) {
        for (final Object o : values) {
          if (o instanceof HashMap) {
            replaceNullWithNanInAggregations((Map<String, Object>) o);
          }
        }
      }
    }
  }
}
