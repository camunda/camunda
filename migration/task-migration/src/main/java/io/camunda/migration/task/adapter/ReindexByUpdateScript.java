/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.task.adapter;

import java.util.Map;

public final class ReindexByUpdateScript {

  public static String getScript() {
    return """
            if (ctx._source.lastReindexedAt == null) {
                if (ctx._source.status == 'pending') {
                    ctx._source.status = params.newStatus;
                }
                if (ctx._source.count != null && ctx._source.count < params.threshold) {
                    ctx._source.count += 1;
                }
                ctx._source.lastReindexedAt = params.timestamp;
            }
        """;
  }

  public static Map<String, Object> getParams() {
    return Map.of(
        "newStatus", "completed", "threshold", 10, "timestamp", java.time.Instant.now().toString());
  }
}
