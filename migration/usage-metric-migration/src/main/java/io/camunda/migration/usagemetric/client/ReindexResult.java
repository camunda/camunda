/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.usagemetric.client;

public record ReindexResult(String taskId, String source, String destination, String message) {

  public static ReindexResult failure(
      final String source, final String destination, final String message) {
    return new ReindexResult(null, source, destination, message);
  }

  public static ReindexResult success(
      final String taskId, final String source, final String destination) {
    return new ReindexResult(taskId, source, destination, null);
  }
}
