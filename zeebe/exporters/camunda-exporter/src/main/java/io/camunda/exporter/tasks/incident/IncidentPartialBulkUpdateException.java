/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.incident;

import java.util.List;

public class IncidentPartialBulkUpdateException extends RuntimeException {
  private final List<String> updatedIds;

  public IncidentPartialBulkUpdateException(final String message, final List<String> updatedIds) {
    super(message);
    this.updatedIds = updatedIds;
  }

  public List<String> getUpdatedIds() {
    return updatedIds;
  }
}
