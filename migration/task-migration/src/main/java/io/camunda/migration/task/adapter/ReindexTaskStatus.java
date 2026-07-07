/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.task.adapter;

public record ReindexTaskStatus(
    String taskId,
    boolean found,
    boolean completed,
    long total,
    long created,
    long updated,
    long deleted) {

  public static ReindexTaskStatus notFound() {
    return new ReindexTaskStatus("", false, false, 0, 0, 0, 0);
  }
}
