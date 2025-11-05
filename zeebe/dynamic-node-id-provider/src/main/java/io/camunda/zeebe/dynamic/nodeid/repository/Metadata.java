/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.nodeid.repository;

import io.camunda.zeebe.dynamic.nodeid.Lease;
import java.util.Map;

public record Metadata(String task, long expiry) {

  // KEYS MUST BE LOWERCASE
  private static final String TASK_ID_KEY = "taskid";
  private static final String EXPIRY_KEY = "expiry";

  //    private static final String NODE_VERSIONKEY = "nodeversion";

  public static Metadata fromLease(final Lease lease) {
    return new Metadata(lease.taskId(), lease.timestamp());
  }

  public Map<String, String> asMap() {
    return Map.of(TASK_ID_KEY, task, EXPIRY_KEY, String.valueOf(expiry));
  }

  public static Metadata fromMap(final Map<String, String> map) {
    if (map.isEmpty()) {
      return null;
    }
    return new Metadata(map.get(TASK_ID_KEY), Long.parseLong(map.get(EXPIRY_KEY)));
  }
}
