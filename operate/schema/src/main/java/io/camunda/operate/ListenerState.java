/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate;

public enum ListenerState {
  ACTIVE,
  COMPLETED,
  FAILED,
  TIMED_OUT,
  CANCELED,
  UNKNOWN;

  public static ListenerState fromZeebeJobIntent(final String jobState) {
    final ListenerState result =
        switch (jobState) {
          case "CREATED", "RETRIES_UPDATED", "MIGRATED" -> ACTIVE;
          case "COMPLETED" -> COMPLETED;
          case "TIMED_OUT" -> TIMED_OUT;
          case "CANCELED" -> CANCELED;
          case "FAILED", "FAIL" -> FAILED;
          default -> UNKNOWN;
        };
    return result;
  }
}
