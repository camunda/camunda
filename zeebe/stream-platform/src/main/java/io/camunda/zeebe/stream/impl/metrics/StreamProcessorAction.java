/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl.metrics;

/** What the stream processor did with a record, as counted by {@link ProcessingMetrics}. */
public enum StreamProcessorAction {
  WRITTEN("written"),
  SKIPPED("skipped"),
  PROCESSED("processed");

  private final String label;

  StreamProcessorAction(final String label) {
    this.label = label;
  }

  public String label() {
    return label;
  }
}
